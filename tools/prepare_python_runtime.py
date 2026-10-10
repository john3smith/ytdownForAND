"""Repackage ONLY Python in the pinned upstream AAR; keep its Java API and FFmpeg.

Generated AARs/native executables are build outputs, not vendored Git binaries.
CPython archives are verified against python.org's release SHA-256 values.
No credentials, browser profiles, or existing device data are read by this tool.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import posixpath
import stat
import subprocess
import tarfile
import urllib.request
import zipfile

VERSION = "3.14.8"
BASE_SHA256 = "579b5fb480892b1abc2b218c2089699d52759cc8d7ba256bf876453f0365faef"
ARCHIVES = {
    "arm64-v8a": ("aarch64-linux-android", "11324313957da3736f44e90257304d288864321a4dd376ed0cc35a54eacf9a33"),
    "x86_64": ("x86_64-linux-android", "58eb3b2d76ef57e076985a0a6b093cf08906d65ea4ab78cccc6d894d4250c972"),
}


def sha256(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def safe_member(name):
    path = PurePosixPath(name)
    return not path.is_absolute() and ".." not in path.parts and "\\" not in name


def acquire(root, abi):
    triple, expected = ARCHIVES[abi]
    cache = root / ".tools/python314-official"
    cache.mkdir(parents=True, exist_ok=True)
    archive = cache / f"python-{VERSION}-{triple}.tar.gz"
    if not archive.exists():
        with urllib.request.urlopen(f"https://www.python.org/ftp/python/{VERSION}/{archive.name}", timeout=90) as response:
            with archive.open("wb") as stream:
                while chunk := response.read(1024 * 1024):
                    stream.write(chunk)
    if sha256(archive) != expected:
        raise ValueError("Official Python archive SHA-256 mismatch")
    return archive, triple, expected


def payload_files(archive):
    """Resolve trusted archive symlinks in memory instead of extracting links."""
    result = {}
    with tarfile.open(archive, "r:gz") as source:
        entries = {entry.name.removeprefix("./"): entry for entry in source}

        def read(name, visited=None):
            visited = set() if visited is None else visited
            if name in visited or not safe_member(name):
                raise ValueError("Invalid archive link")
            visited.add(name)
            entry = entries[name]
            if entry.issym():
                destination = str(PurePosixPath(name).parent / entry.linkname)
                return read(destination, visited)
            if entry.islnk():
                return read(entry.linkname.removeprefix("./"), visited)
            if not entry.isfile():
                raise ValueError("Unexpected archive member")
            return source.extractfile(entry).read()

        for name, entry in entries.items():
            if not safe_member(name):
                raise ValueError("Unsafe archive path")
            if not name.startswith("prefix/lib/") or entry.isdir():
                continue
            relative = name[len("prefix/"):]
            parts = PurePosixPath(relative).parts
            if "test" in parts or "tests" in parts or "__pycache__" in parts:
                continue
            if relative.endswith((".pyc", ".a")):
                continue
            result["usr/" + relative] = read(name)
        # Headers are build inputs only and never packaged into the APK.
        headers = {name[len("prefix/"):]: read(name) for name, entry in entries.items()
                   if name.startswith("prefix/include/python3.14/") and entry.isfile()}
    return result, headers


def compile_launcher(root, abi, triple, payload, headers):
    staging = root / "build/python-runtime" / abi
    staging.mkdir(parents=True, exist_ok=True)
    for name, content in headers.items():
        target = staging / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(content)
    library = staging / "libpython3.14.so"
    library.write_bytes(payload["usr/lib/libpython3.14.so"])
    ndk = Path(os.environ.get("ANDROID_NDK_HOME") or root / ".tools/android-sdk/ndk/27.2.12479018")
    host = "windows-x86_64" if os.name == "nt" else "linux-x86_64"
    compiler = ndk / "toolchains/llvm/prebuilt" / host / "bin" / ("clang.exe" if os.name == "nt" else "clang")
    if not compiler.is_file():
        raise FileNotFoundError("NDK r27c is required; set ANDROID_NDK_HOME")
    executable = staging / "libpython.so"
    command = [str(compiler), f"--target={triple}24", "-fPIE", "-pie", "-O2",
               "-Wl,-z,max-page-size=16384", "-Wl,--build-id=sha1",
               "-I" + str(staging / "include/python3.14"), str(root / "tools/python-launcher.c"),
               "-L" + str(staging), "-lpython3.14", "-ldl", "-lm", "-o", str(executable)]
    completed = subprocess.run(command, capture_output=True, text=True, encoding="utf-8",
                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    if completed.returncode:
        raise RuntimeError("Native launcher compilation failed: " + completed.stderr)
    return executable.read_bytes()


def retain_native_support(payload, old):
    """Keep upstream FFmpeg's dependency closure without replacing CPython.

    FFmpeg's fontconfig/freetype/etc. share versioned libraries from the old
    Python payload. These are NOT Python extensions. Official 3.14 libraries
    take precedence. Do not inject an unversioned liblzma.so ahead of Android's
    platform library; FFmpeg's versioned liblzma.so.5 remains available.
    """
    def read_native(name, visited=None):
        visited = set() if visited is None else visited
        if name in visited or not name.startswith("usr/lib/") or not safe_member(name):
            raise ValueError("Invalid native support link")
        visited.add(name)
        info = old.getinfo(name)
        content = old.read(name)
        if stat.S_ISLNK(info.external_attr >> 16):
            destination = posixpath.normpath(str(PurePosixPath(name).parent / content.decode("utf-8")))
            return read_native(destination, visited)
        if not content.startswith(b"\x7fELF"):
            raise ValueError("Native support library is not ELF")
        return content

    for name in old.namelist():
        path = PurePosixPath(name)
        if (path.parent != PurePosixPath("usr/lib") or not safe_member(name)
                or ".so" not in path.name or path.name.startswith("libpython")
                or path.name == "liblzma.so"):
            continue
        if name not in payload:
            payload[name] = read_native(name)


def prepare(root, abi, base, output):
    if sha256(base) != BASE_SHA256:
        raise ValueError("Base youtubedl-android 0.18.1 AAR hash mismatch")
    archive, triple, digest = acquire(root, abi)
    payload, headers = payload_files(archive)
    launcher = compile_launcher(root, abi, triple, payload, headers)
    with zipfile.ZipFile(base) as original:
        with zipfile.ZipFile(io.BytesIO(original.read(f"jni/{abi}/libpython.zip.so"))) as old:
            # Keep mutagen and ABI-stable Cryptodome modules, not CPython 3.12 extensions.
            marker = "usr/lib/python3.12/site-packages/"
            for name in old.namelist():
                if name.startswith(marker) and not name.endswith("/") and "__pycache__" not in name:
                    if ".cpython-" in name or name.endswith(".pyc"):
                        continue
                    payload[name.replace(marker, "usr/lib/python3.14/site-packages/", 1)] = old.read(name)
            # Existing upstream CA bundle preserves TLS validation.
            payload["usr/etc/tls/cert.pem"] = old.read("usr/etc/tls/cert.pem")
            retain_native_support(payload, old)
        manifest = {"python": VERSION, "abi": abi, "source": "https://www.python.org/downloads/release/python-3148/",
                    "source_sha256": digest, "base_library": "0.18.1", "curl_cffi": None}
        payload["usr/python-runtime.json"] = json.dumps(manifest, sort_keys=True).encode()
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as runtime:
            for name, content in sorted(payload.items()):
                item = zipfile.ZipInfo(name, date_time=(2026, 10, 1, 0, 0, 0))
                item.compress_type = zipfile.ZIP_DEFLATED
                item.external_attr = 0o100644 << 16
                runtime.writestr(item, content)
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as result:
            for item in original.infolist():
                if item.filename.startswith("jni/") and item.filename.rsplit("/", 1)[-1] in {"libpython.so", "libpython.zip.so"}:
                    continue
                result.writestr(item, original.read(item))
            result.writestr(f"jni/{abi}/libpython.so", launcher)
            result.writestr(f"jni/{abi}/libpython.zip.so", buffer.getvalue())
            result.writestr("assets/python-runtime.json", json.dumps(manifest, sort_keys=True))
    print(json.dumps({**manifest, "aar": str(output), "aar_sha256": sha256(output)}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--abi", choices=ARCHIVES, required=True)
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    prepare(Path(__file__).resolve().parents[1], args.abi, args.base, args.output)
