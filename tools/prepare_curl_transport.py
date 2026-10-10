"""Build Android curl_cffi/CFFI from pinned official release inputs.

No login cookies, account credentials, TLS exceptions or proxy services.
Both ABIs use the same source and upstream Android libcurl release.
"""
import hashlib
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import tarfile
import urllib.request
import zipfile

SOURCES = {
    'cffi': ('https://files.pythonhosted.org/packages/9e/ef/008a1939e372c06329a3fce4279c02f328488f3526744906eeec3da7ad5f/cffi-2.1.1.tar.gz',
             'dd31f52ea1086513bb9df30f8fcee9b8918323ae067a3d5b78bc826a000712be'),
    'curl_cffi': ('https://files.pythonhosted.org/packages/82/e1/730125c43e3e331d98e17af3cb310ba526b3f1101b7635ca23d976ebfcf5/curl_cffi-0.16.3.tar.gz',
                  'd15d0c2a35f2d75bec430c28946c2a833f421c85773bdb0795182cc5c515665b'),
    'libffi_headers': ('https://github.com/libffi/libffi/releases/download/v3.4.8/libffi-3.4.8.tar.gz',
                       'bc9842a18898bfacb0ed1252c4febcc7e78fa139fd27fdc7a3e30d9d9356119b'),
    'certifi': ('https://files.pythonhosted.org/packages/0b/a7/71ac2cff56fec219ed242bb11b8efb69fcc4bec75db06fb7bfe35de520e6/certifi-2026.7.22-py3-none-any.whl',
                '62f22742b58a1a33014a2b6b706588a8d7e2a88ae7bd1a6ebe8c992928483775'),
    'pycparser': ('https://files.pythonhosted.org/packages/a0/e3/59cd50310fc9b59512193629e1984c1f95e5c8ae6e5d8c69532ccc65a7fe/pycparser-2.23-py3-none-any.whl',
                  'e5c6e8d3fbad53479cab09ac03729e0a9faf2bee3db8208a550daf5af81a5934'),
}
CURL = {
    'arm64-v8a': ('aarch64-linux-android', '460a44dc6515cce77d7bbb477a284ea95f612c917ff8d85cd9a7f932a04e9809'),
    'x86_64': ('x86_64-linux-android', '4f5b48d90507593e80b250aaaf725ea01f7ea441589491e21e78240f657e118f'),
}


def acquire(root, url, digest):
    cache = root / '.tools/curl-transport'
    cache.mkdir(parents=True, exist_ok=True)
    target = cache / url.rsplit('/', 1)[-1]
    if not target.exists():
        with urllib.request.urlopen(url, timeout=90) as response, target.open('wb') as stream:
            while chunk := response.read(1024 * 1024):
                stream.write(chunk)
    with target.open('rb') as stream:
        actual = hashlib.file_digest(stream, 'sha256').hexdigest()
    if actual != digest:
        raise ValueError('curl transport input SHA-256 mismatch: ' + target.name)
    return target


def safe_name(name):
    path = PurePosixPath(name)
    return (not path.is_absolute() and '..' not in path.parts and '\\' not in name
            and ':' not in name and '\x00' not in name)


def unpack_tar(path, target):
    # Only ordinary source files; never extract archive symlinks or hardlinks.
    with tarfile.open(path, 'r:gz') as archive:
        for entry in archive:
            if not safe_name(entry.name):
                raise ValueError('Unsafe transport source archive path')
            if not entry.isfile():
                continue
            destination = target / entry.name.removeprefix('./')
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(archive.extractfile(entry).read())


def compile_native(command):
    result = subprocess.run([str(arg) for arg in command], capture_output=True,
                            text=True, encoding='utf-8', errors='replace',
                            creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    if result.returncode:
        raise RuntimeError('Android transport compile failed:\n' + result.stderr)


def add_pure_package(payload, archive_path, package):
    with zipfile.ZipFile(archive_path) as archive:
        for name in archive.namelist():
            if not safe_name(name):
                raise ValueError('Unsafe Python wheel path')
            if name.startswith(package + '/') and not name.endswith('/'):
                payload['usr/lib/python3.14/site-packages/' + name] = archive.read(name)
            elif '.dist-info/' in name and name.rsplit('/', 1)[-1] in {'METADATA', 'LICENSE', 'licenses/LICENSE'}:
                payload['usr/lib/python3.14/site-packages/' + name] = archive.read(name)


def prepare(root, abi, payload):
    # CFFI is a host code generator as well as a separately cross-compiled backend.
    from cffi import FFI
    triple, curl_hash = CURL[abi]
    stage = root / 'build/python-runtime' / abi
    sources = stage / 'transport-sources'
    sources.mkdir(parents=True, exist_ok=True)
    for key in ('cffi', 'curl_cffi', 'libffi_headers'):
        unpack_tar(acquire(root, *SOURCES[key]), sources)
    curl_url = ('https://github.com/lexiforest/curl-impersonate/releases/download/v2.2.2/'
                f'libcurl-impersonate-v2.2.2.{triple}.tar.gz')
    curl_dir = sources / 'libcurl'
    unpack_tar(acquire(root, curl_url, curl_hash), curl_dir)
    curl_archive = next(curl_dir.rglob('libcurl-impersonate.a'))
    ffi = sources / 'libffi-3.4.8'
    include = stage / 'transport-include'
    include.mkdir(parents=True, exist_ok=True)
    ffi_header = (ffi / 'include/ffi.h.in').read_text(encoding='utf-8')
    for key, value in {'VERSION': '3.4.8', 'TARGET': 'AARCH64' if abi == 'arm64-v8a' else 'X86_64',
                       'HAVE_LONG_DOUBLE': '1', 'FFI_EXEC_TRAMPOLINE_TABLE': '0'}.items():
        ffi_header = ffi_header.replace('@' + key + '@', value)
    if re.search(r'@[A-Z_]+@', ffi_header):
        raise ValueError('Unconfigured FFI header')
    (include / 'ffi.h').write_text(ffi_header, encoding='utf-8')
    arch = 'aarch64' if abi == 'arm64-v8a' else 'x86'
    (include / 'ffitarget.h').write_bytes((ffi / 'src' / arch / 'ffitarget.h').read_bytes())
    native_ffi = stage / 'libffi.so'
    native_ffi.write_bytes(payload['usr/lib/libffi.so'])
    ndk = Path(os.environ.get('ANDROID_NDK_HOME') or root / '.tools/android-sdk/ndk/27.2.12479018')
    host = 'windows-x86_64' if os.name == 'nt' else 'linux-x86_64'
    toolchain = ndk / 'toolchains/llvm/prebuilt' / host
    compiler = toolchain / 'bin' / ('clang.exe' if os.name == 'nt' else 'clang')
    flags = [compiler, f'--target={triple}24', '-shared', '-fPIC', '-O2',
             '-Wl,-z,max-page-size=16384', '-Wl,--no-undefined',
             '-I' + str(stage / 'include/python3.14'), '-I' + str(include), '-L' + str(stage)]
    backend = stage / '_cffi_backend.so'
    compile_native(flags + [sources / 'cffi-2.1.1/src/c/_cffi_backend.c',
                           '-DUSE__THREAD', '-DHAVE_FFI_PREP_CIF_VAR', '-DHAVE_FFI_PREP_CLOSURE_LOC',
                           '-lffi', '-lpython3.14', '-ldl', '-lm', '-o', backend])
    curl_source = sources / 'curl_cffi-0.16.3'
    generator = FFI()
    generator.cdef((curl_source / 'ffi/cdef.c').read_text(encoding='utf-8'))
    generator.set_source('curl_cffi._wrapper', '#include "shim.h"')
    wrapper_c = stage / 'curl-wrapper.c'
    generator.emit_c_code(str(wrapper_c))
    wrapper = stage / 'curl-wrapper.so'
    curl_header = next(curl_dir.rglob('curl.h')).parent.parent
    compile_native(flags + ['-I' + str(curl_source / 'include'), '-I' + str(curl_source / 'ffi'),
                           '-I' + str(curl_header), wrapper_c, curl_source / 'ffi/shim.c',
                           '-Wl,--whole-archive', curl_archive, '-Wl,--no-whole-archive',
                           '-lc++_shared', '-lpython3.14', '-ldl', '-lm', '-llog', '-lz', '-o', wrapper])
    site = 'usr/lib/python3.14/site-packages/'
    payload[site + '_cffi_backend.so'] = backend.read_bytes()
    payload[site + 'curl_cffi/_wrapper.so'] = wrapper.read_bytes()
    for key, path in [('cffi', sources / 'cffi-2.1.1/src/cffi'), ('curl_cffi', curl_source / 'curl_cffi')]:
        for source in path.rglob('*'):
            if source.is_file() and source.suffix == '.py':
                payload[site + key + '/' + source.relative_to(path).as_posix()] = source.read_bytes()
    for key in ('certifi', 'pycparser'):
        add_pure_package(payload, acquire(root, *SOURCES[key]), key)
    for key, source_dir in [('cffi', sources / 'cffi-2.1.1'), ('curl_cffi', curl_source)]:
        payload[site + key + '-LICENSE'] = (source_dir / 'LICENSE').read_bytes()
        version = '2.1.1' if key == 'cffi' else '0.16.3'
        payload[site + f'{key}-{version}.dist-info/METADATA'] = (source_dir / 'PKG-INFO').read_bytes()
    for license_file in curl_dir.glob('LICENSE*'):
        payload['usr/share/licenses/curl-transport/' + license_file.name] = license_file.read_bytes()
    # libcurl is built with current NDK libc++; retain compatible modern runtime.
    cpp = toolchain / 'sysroot/usr/lib' / triple / 'libc++_shared.so'
    payload['usr/lib/libc++_shared.so'] = cpp.read_bytes()
    return {'curl_cffi': '0.16.3', 'cffi': '2.1.1', 'libcurl': '2.2.2',
            'libcurl_sha256': curl_hash, 'sources_sha256': {key: value[1] for key, value in SOURCES.items()}}
