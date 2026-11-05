"""Replace FFmpeg executables and dependency archive without changing Java API."""
import argparse, io, json, hashlib, zipfile
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--base',type=Path,required=True)
p.add_argument('--native',type=Path,required=True)
p.add_argument('--abi',choices=['arm64-v8a','x86_64'],required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
manifest=json.loads((Path(__file__).resolve().parents[1]/'docs/minimal-ffmpeg-binaries.json').read_text())
payload={}
shared={}
for name in manifest[a.abi]:
    data=(a.native/name).read_bytes()
    if data[:4]!=b'\x7fELF':raise ValueError('Not an ELF executable')
    if int.from_bytes(data[18:20],'little') != {'arm64-v8a':183,'x86_64':62}[a.abi]:
        raise ValueError('ELF architecture mismatch')
    if hashlib.sha256(data).hexdigest()!=manifest[a.abi][name]:
        raise ValueError('Native binary does not match the validated manifest')
    if name in ['ffmpeg','ffprobe']:
        payload['jni/'+a.abi+'/lib'+name+'.so']=data
    else:
        if not name.startswith('lib') or not name.endswith('.so') or '/' in name:
            raise ValueError('Unexpected dependency name')
        shared[name]=data
buf=io.BytesIO()
with zipfile.ZipFile(buf,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    z.writestr('usr/lib/minimal-ffmpeg.txt','Shared FFmpeg libraries. See bundled corresponding-source notice.')
    for name,data in shared.items():z.writestr('usr/lib/'+name,data)
payload['jni/'+a.abi+'/libffmpeg.zip.so']=buf.getvalue()
a.output.parent.mkdir(parents=True,exist_ok=True)
tmp=a.output.with_suffix('.tmp')
with zipfile.ZipFile(a.base) as source,zipfile.ZipFile(tmp,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as out:
    for item in source.infolist():
        if not item.filename.startswith('jni/'):
            out.writestr(item,source.read(item.filename))
    for name,data in payload.items():out.writestr(name,data)
tmp.replace(a.output)
print(json.dumps({'file':str(a.output),'bytes':a.output.stat().st_size,'sha256':hashlib.sha256(a.output.read_bytes()).hexdigest()}))
