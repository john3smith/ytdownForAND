"""Windows-only explicit signing setup/rotation. Never prints private keys or passwords.

Run with the project's existing Python environment (pywin32). Keys live outside the
repository, ACL-restricted to the current Windows user and SYSTEM. Passwords are
DPAPI CurrentUser ciphertext; subprocesses receive passwords only in child env.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys

PACKAGE='com.local.ytdown'
MIN_SDK=24
LEGACY_SHA='97a629dccea6d8cc04480c762dd88b8d31c9eba5fc06ba9e95df130fbab01765'
DEFAULT_JDK=Path('C:/ytdownforand/.tools/jdk-17.0.20.1+1')
DEFAULT_TOOLS=Path('C:/ytdownforand/.tools/android-sdk/build-tools/36.0.0')
DIR=Path(os.environ.get('LOCALAPPDATA',''))/'ytdownForAND'/'Signing'
ALIASES={'legacy':'legacy','release':'release'}
FILES={'legacy':'legacy.p12','release':'release.p12'}

def command(args,env=None):
    result=subprocess.run([str(x) for x in args],stdin=subprocess.DEVNULL,capture_output=True,
        env=env,creationflags=subprocess.CREATE_NO_WINDOW,timeout=120)
    if result.returncode:raise RuntimeError('Signing tool failed; exit code '+str(result.returncode))
    return result.stdout

def hash_file(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def owner_sid():
    import win32api,win32security
    return win32security.ConvertSidToStringSid(win32security.LookupAccountName(None,win32api.GetUserName())[0])

def verify_acl():
    import win32security as w
    allowed={owner_sid(),'S-1-5-18'}
    for path in [DIR,*DIR.iterdir()]:
        if path.is_symlink() or path.is_junction():raise RuntimeError('Signing paths must not be links')
        sd=w.GetFileSecurity(str(path),w.DACL_SECURITY_INFORMATION)
        acl=sd.GetSecurityDescriptorDacl()
        if acl is None or acl.GetAceCount()!=2:raise RuntimeError('Unexpected signing ACL')
        seen=set()
        for i in range(acl.GetAceCount()):
            ace=acl.GetAce(i)
            if ace[0][0]!=w.ACCESS_ALLOWED_ACE_TYPE:raise RuntimeError('Unexpected signing ACL entry')
            seen.add(w.ConvertSidToStringSid(ace[2]))
        if seen!=allowed:raise RuntimeError('Signing ACL includes an unexpected principal')

def prepare_directory():
    import win32security as w
    if not os.environ.get('LOCALAPPDATA') or str(DIR).startswith('\\\\'):raise RuntimeError('Local signing directory required')
    if DIR.exists():verify_acl();return
    DIR.mkdir(parents=True)
    sd=w.ConvertStringSecurityDescriptorToSecurityDescriptor(
        'D:P(A;OICI;FA;;;'+owner_sid()+')(A;OICI;FA;;;SY)',w.SDDL_REVISION_1)
    w.SetFileSecurity(str(DIR),w.DACL_SECURITY_INFORMATION|w.PROTECTED_DACL_SECURITY_INFORMATION,sd)
    verify_acl()

def passwords():
    import win32crypt
    verify_acl()
    values=json.loads(win32crypt.CryptUnprotectData((DIR/'passwords.dpapi').read_bytes(),None,None,None,1)[1])
    if set(values)!={'legacy','release'} or any(not isinstance(x,str) or len(x)<40 for x in values.values()):
        raise RuntimeError('Invalid protected password state')
    return values

def child_env(values):
    env=os.environ.copy()
    for role,value in values.items():env['APP_SIGN_'+role.upper()]=value
    return env

def cert(jdk,path,alias,variable,env):
    raw=command([jdk/'bin/keytool.exe','-exportcert','-keystore',path,'-alias',alias,'-storepass:env',variable],env)
    return hashlib.sha256(raw).hexdigest()

def signer(role):
    variable='env:APP_SIGN_'+role.upper()
    return ['--ks',DIR/FILES[role],'--ks-key-alias',ALIASES[role],'--ks-pass',variable,'--key-pass',variable]

def apk_signer(jdk,build_tools,*args,env=None):
    return command([jdk/'bin/java.exe','-jar',build_tools/'lib/apksigner.jar',*args],env)

def inspect_legacy(args):
    # Android's standard development-keystore credential is not a release secret.
    env=child_env({'original': 'android'})
    fingerprint=cert(args.jdk,args.legacy,'androiddebugkey','APP_SIGN_ORIGINAL',env)
    if fingerprint!=LEGACY_SHA:raise RuntimeError('Legacy key does not match the published APK')
    return {'legacy_certificate_sha256':fingerprint,'matches_verified_legacy':True}

def initialize(args):
    import win32crypt
    inspect_legacy(args)
    prepare_directory()
    if (DIR/'metadata.json').exists():return status(args)
    known={'legacy.p12','release.p12','passwords.dpapi','lineage.bin'}
    if any(p.name not in known for p in DIR.iterdir()):raise RuntimeError('Unexpected partial setup files; inspect manually')
    if not (DIR/'passwords.dpapi').exists():
        if any(DIR.iterdir()):raise RuntimeError('Partial setup lacks protected passwords; do not overwrite')
        raw=json.dumps({role:secrets.token_urlsafe(48) for role in ALIASES}).encode('utf-8')
        with (DIR/'passwords.dpapi').open('xb') as output:
            output.write(win32crypt.CryptProtectData(raw,'ytdownForAND signing',None,None,None,1))
    values=passwords();env=child_env(values)
    env['APP_SIGN_ORIGINAL']='android'
    if not (DIR/'legacy.p12').exists():
        command([args.jdk/'bin/keytool.exe','-importkeystore','-noprompt','-srckeystore',args.legacy,
            '-srcalias','androiddebugkey','-srcstorepass:env','APP_SIGN_ORIGINAL',
            '-destkeystore',DIR/'legacy.p12','-deststoretype','PKCS12','-destalias',ALIASES['legacy'],
            '-deststorepass:env','APP_SIGN_LEGACY','-destkeypass:env','APP_SIGN_LEGACY'],env)
    if cert(args.jdk,DIR/'legacy.p12',ALIASES['legacy'],'APP_SIGN_LEGACY',env)!=LEGACY_SHA:
        raise RuntimeError('Protected legacy copy certificate mismatch')
    if not (DIR/'release.p12').exists():
        command([args.jdk/'bin/keytool.exe','-genkeypair','-noprompt','-storetype','PKCS12',
            '-keystore',DIR/'release.p12','-alias',ALIASES['release'],'-keyalg','RSA','-keysize','4096',
            '-sigalg','SHA256withRSA','-validity','10950','-dname','CN=ytdownForAND Release,O=ytdownForAND,C=KR',
            '-storepass:env','APP_SIGN_RELEASE','-keypass:env','APP_SIGN_RELEASE'],env)
    new_sha=cert(args.jdk,DIR/'release.p12',ALIASES['release'],'APP_SIGN_RELEASE',env)
    if new_sha==LEGACY_SHA:raise RuntimeError('A different release key is required')
    if not (DIR/'lineage.bin').exists():
        apk_signer(args.jdk,args.build_tools,'rotate','--out',DIR/'lineage.bin','--old-signer',*signer('legacy'),
            '--set-installed-data','true','--set-shared-uid','false','--set-permission','true',
            '--set-rollback','false','--set-auth','false','--new-signer',*signer('release'),env=env)
    metadata={'format':1,'rotation_min_sdk':33,'legacy_signature_permission':True,'legacy_certificate_sha256':LEGACY_SHA,
        'release_certificate_sha256':new_sha,'lineage_sha256':hash_file(DIR/'lineage.bin'),
        'legacy_keystore_sha256':hash_file(DIR/'legacy.p12'),'release_keystore_sha256':hash_file(DIR/'release.p12')}
    with (DIR/'metadata.json').open('x',encoding='utf-8') as output:json.dump(metadata,output,indent=2)
    verify_acl()
    return status(args)

def status(args):
    values=passwords();env=child_env(values)
    metadata=json.loads((DIR/'metadata.json').read_text(encoding='utf-8'))
    for role in ALIASES:
        actual=cert(args.jdk,DIR/FILES[role],ALIASES[role],'APP_SIGN_'+role.upper(),env)
        if actual!=metadata[role+'_certificate_sha256'] or hash_file(DIR/FILES[role])!=metadata[role+'_keystore_sha256']:
            raise RuntimeError('Signing material does not match protected metadata')
    if metadata['legacy_certificate_sha256']!=LEGACY_SHA or metadata['rotation_min_sdk']!=33 or hash_file(DIR/'lineage.bin')!=metadata['lineage_sha256']:
        raise RuntimeError('Signing lineage metadata mismatch')
    apk_signer(args.jdk,args.build_tools,'lineage','--in',DIR/'lineage.bin','--print-certs')
    return dict(metadata,key_directory=str(DIR),acl_verified=True,passwords_dpapi_current_user=True)

def identity(path,args):
    details=command([args.build_tools/'aapt.exe','dump','badging',path]).decode('utf-8')
    package=re.search(r"package: name='([^']+)'",details).group(1)
    permitted={PACKAGE}
    if package not in permitted or 'application-debuggable' in details:raise RuntimeError('Expected non-debuggable package required')
    command([args.build_tools/'zipalign.exe','-c','-P','16','4',path])
    return package

def active_certificates(output,api):
    # SDK36 prints all SDK-targeted signers. Select exactly the one active on this API.
    ranges=re.findall(r'^Signer \(minSdkVersion=(\d+), maxSdkVersion=(\d+)\) certificate SHA-256 digest: ([a-f0-9]{64})\s*$',output,re.MULTILINE)
    if ranges:return [digest for minimum,maximum,digest in ranges if int(minimum)<=api<=int(maximum)]
    return re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-f0-9]{64})\s*$',output,re.MULTILINE)

def verify_apk(path,metadata,args):
    package=identity(path,args)
    verified={}
    for api in sorted({MIN_SDK,26,28,32,33,35,36}):
        output=apk_signer(args.jdk,args.build_tools,'verify','--verbose','--print-certs',
            '--min-sdk-version',str(api),'--max-sdk-version',str(api),path).decode('utf-8')
        expected=metadata['legacy_certificate_sha256'] if api<33 else metadata['release_certificate_sha256']
        if active_certificates(output,api)!=[expected]:raise RuntimeError('Unexpected verified certificate at API '+str(api))
        verified[str(api)]={'verified':True,'certificate_sha256':expected}
    return {'package':package,'filename':path.name,'size':path.stat().st_size,
        'sha256':hash_file(path),'rotation_min_sdk':33,'non_debuggable':True,'api_verification':verified}

def verify(args):
    result=verify_apk(args.input,status(args),args)
    if args.receipt:
        with args.receipt.open('x',encoding='utf-8') as output:json.dump(result,output,indent=2)
    return result

def sign(args):
    metadata=status(args)
    if args.output.exists() or args.output.with_suffix('.signing-receipt.json').exists():raise RuntimeError('Output already exists; never overwrite a signed artifact')
    identity(args.input,args)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    apk_signer(args.jdk,args.build_tools,'sign','--in',args.input,'--out',args.output,
        '--min-sdk-version',str(MIN_SDK),'--rotation-min-sdk-version','33','--v1-signing-enabled','false',
        '--v2-signing-enabled','true','--v3-signing-enabled','true','--v4-signing-enabled','false',
        '--debuggable-apk-permitted','false','--lineage',DIR/'lineage.bin',*signer('legacy'),
        '--next-signer',*signer('release'),env=child_env(passwords()))
    result=verify_apk(args.output,metadata,args)
    with args.output.with_suffix('.signing-receipt.json').open('x',encoding='utf-8') as output:json.dump(result,output,indent=2)
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['inspect-legacy','init','status','sign','verify'])
    parser.add_argument('--jdk',type=Path,default=DEFAULT_JDK)
    parser.add_argument('--build-tools',type=Path,default=DEFAULT_TOOLS)
    parser.add_argument('--legacy',type=Path,default=Path(os.environ.get('USERPROFILE',''))/'.android/debug.keystore')
    parser.add_argument('--input',type=Path);parser.add_argument('--output',type=Path)
    parser.add_argument('--receipt',type=Path,help='Optional new verification receipt; never overwrites an existing file')
    args=parser.parse_args()
    try:
        if sys.platform!='win32':raise RuntimeError('Windows DPAPI environment required')
        if args.action in {'sign'} and (args.input is None or args.output is None):parser.error('sign requires --input and --output')
        if args.action=='verify' and args.input is None:parser.error('verify requires --input')
        result={'inspect-legacy':inspect_legacy,'init':initialize,'status':status,'sign':sign,'verify':verify}[args.action](args)
        print(json.dumps(result,ensure_ascii=True))
    except Exception as error:
        print(json.dumps({'ok':False,'error_type':type(error).__name__,'message':str(error) if isinstance(error,RuntimeError) else 'Signing setup failed; secret-bearing tool output is suppressed'}))
        sys.exit(1)
