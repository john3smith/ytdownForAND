"""Fail-closed APK delivery check. Accept only trusted certificate fingerprints."""
import argparse, hashlib, json, os, re, subprocess, zipfile
from pathlib import Path

def run(args):
    result=subprocess.run([str(x) for x in args],capture_output=True,
        creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0),timeout=120)
    if result.returncode:
        raise ValueError('APK tool verification failed: '+Path(str(args[0])).name)
    return result.stdout.decode('utf-8',errors='strict')

def fingerprint(value):
    value=value.lower().replace(':','')
    if not re.fullmatch('[a-f0-9]{64}',value):
        raise argparse.ArgumentTypeError('Expected a SHA-256 certificate fingerprint')
    return value

def active_certificates(output,api):
    ranges=re.findall(r'^Signer \(minSdkVersion=(\d+), maxSdkVersion=(\d+)\) certificate SHA-256 digest: ([a-f0-9]{64})\s*$',output,re.M)
    if ranges:return [sha for low,high,sha in ranges if int(low)<=api<=int(high)]
    return re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-f0-9]{64})\s*$',output,re.M)

def verify(args):
    if args.legacy_sha256==args.release_sha256:
        raise ValueError('Production and legacy identities must differ')
    if args.rotation_api and (args.rotation_api!=33 or not args.legacy_sha256):
        raise ValueError('Only an explicit API 33 migration with a trusted legacy identity is supported')
    if args.legacy_sha256 and not args.rotation_api:
        raise ValueError('A legacy identity needs an explicit rotation API')
    tools=args.build_tools
    aapt=tools/('aapt2.exe' if os.name=='nt' else 'aapt2')
    badging=run([aapt,'dump','badging',args.apk])
    match=re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",badging)
    if not match or match[1]!=args.package:raise ValueError('Unexpected APK package')
    if 'application-debuggable' in badging:raise ValueError('Forbidden manifest flag: debuggable')
    manifest=run([aapt,'dump','xmltree',args.apk,'--file','AndroidManifest.xml'])
    for flag in ['debuggable','testOnly']:
        for value in re.findall(r'A: (?:android|http://schemas.android.com/apk/res/android):'+flag+r'\([^\n]*',manifest):
            if not re.search(r'=(?:false|\(type 0x12\)0x0)\s*$',value):raise ValueError('Forbidden manifest flag: '+flag)
    sdk=re.search(r"(?:minSdkVersion|sdkVersion):'(\d+)'",badging)
    if not sdk:raise ValueError('APK minimum SDK could not be determined')
    minimum=int(sdk[1])
    run([tools/('zipalign.exe' if os.name=='nt' else 'zipalign'),'-c','-P','16','4',args.apk])
    java=args.jdk/'bin'/('java.exe' if os.name=='nt' else 'java')
    base=[java,'-jar',tools/'lib/apksigner.jar','verify','--verbose','--print-certs']
    apis=sorted({minimum, *[v for v in [24,26,28,32,33,35,36] if v>=minimum]})
    records={}
    for api in apis:
        details=run([*base,'--min-sdk-version',str(api),'--max-sdk-version',str(api),args.apk])
        expected=args.legacy_sha256 if args.rotation_api and api<args.rotation_api else args.release_sha256
        if active_certificates(details,api)!=[expected]:raise ValueError('Unexpected active signing identity at API '+str(api))
        # DN text can contain old lineage certificates: inspect the trusted modern
        # certificate separately below rather than rejecting any historical DN.
        records[str(api)]={'verified':True,'certificate_sha256':expected,'legacy':expected==args.legacy_sha256}
    details=run([*base,'--min-sdk-version','33','--max-sdk-version','36',args.apk])
    lines=details.splitlines()
    for i,line in enumerate(lines):
        if args.release_sha256 in line and 'certificate SHA-256 digest:' in line:
            preceding='\n'.join(lines[max(0,i-1):i])
            if 'android debug' in preceding.lower():raise ValueError('Android Debug is not a production identity')
    with zipfile.ZipFile(args.apk) as archive:
        blocked=[name for name in archive.namelist() if Path(name).suffix.lower() in {'.jks','.keystore','.p12','.pfx','.dpapi'} or Path(name).name in {'.env','passwords.json'}]
        if blocked:raise ValueError('Forbidden credential file type inside APK')
    return {'filename':args.apk.name,'bytes':args.apk.stat().st_size,
        'sha256':hashlib.sha256(args.apk.read_bytes()).hexdigest(),'package':match[1],
        'version_code':int(match[2]),'version':match[3],'non_debuggable':True,
        'rotation_api':args.rotation_api,'api_verification':records}

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('apk',type=Path);p.add_argument('--package',required=True)
    p.add_argument('--release-sha256',required=True,type=fingerprint)
    p.add_argument('--legacy-sha256',type=fingerprint);p.add_argument('--rotation-api',type=int)
    p.add_argument('--jdk',type=Path,default=Path(os.environ.get('JAVA_HOME','C:/ytdownforand/.tools/jdk-17.0.20.1+1')))
    p.add_argument('--build-tools',type=Path,default=Path('C:/ytdownforand/.tools/android-sdk/build-tools/36.0.0'))
    p.add_argument('--receipt',type=Path)
    try:
        args=p.parse_args();record=verify(args)
        if args.receipt:
            with args.receipt.open('x',encoding='utf-8') as f:json.dump(record,f,indent=2)
        print(json.dumps(record))
    except (ValueError,OSError,subprocess.SubprocessError) as e:
        raise SystemExit('Release blocked: '+str(e))
