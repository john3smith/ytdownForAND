"""Host-side tests of official-runtime packaging; no device/UI assumptions."""
import io
import tarfile
import tempfile
import unittest
import zipfile
from pathlib import Path
import prepare_python_runtime as runtime


class RuntimePackagingTest(unittest.TestCase):
    def test_retains_ffmpeg_dependencies_without_overwriting_official_python(self):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive:
            for name in ('libexpat.so.1', 'libz.so.1', 'liblzma.so.5',
                         'liblzma.so', 'libpython3.12.so', 'libssl.so'):
                archive.writestr('usr/lib/' + name, b'\x7fELFupstream')
            link = zipfile.ZipInfo('usr/lib/libexpat.so')
            link.external_attr = 0o120777 << 16
            archive.writestr(link, b'libexpat.so.1')
            archive.writestr('usr/lib/python3.12/os.py', b'old Python')
        payload = {'usr/lib/libssl.so': b'official'}
        with zipfile.ZipFile(buffer) as archive:
            runtime.retain_native_support(payload, archive)
        self.assertEqual(b'\x7fELFupstream', payload['usr/lib/libexpat.so.1'])
        self.assertEqual(b'\x7fELFupstream', payload['usr/lib/libexpat.so'])
        self.assertEqual(b'\x7fELFupstream', payload['usr/lib/libz.so.1'])
        self.assertEqual(b'\x7fELFupstream', payload['usr/lib/liblzma.so.5'])
        self.assertEqual(b'official', payload['usr/lib/libssl.so'])
        self.assertNotIn('usr/lib/liblzma.so', payload)
        self.assertNotIn('usr/lib/libpython3.12.so', payload)
        self.assertNotIn('usr/lib/python3.12/os.py', payload)

    def test_rejects_traversal_and_absolute_paths(self):
        for name in ('../secret', '/etc/passwd', 'prefix/../secret', 'prefix\\secret'):
            self.assertFalse(runtime.safe_member(name))
        self.assertTrue(runtime.safe_member('prefix/lib/python3.14/os.py'))

    def test_both_supported_abis_are_pinned(self):
        self.assertEqual({'arm64-v8a', 'x86_64'}, set(runtime.ARCHIVES))
        for _, digest in runtime.ARCHIVES.values():
            self.assertEqual(64, len(digest))
            int(digest, 16)

    def test_filters_test_and_bytecode_files_and_resolves_safe_links(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'test.tar.gz'
            with tarfile.open(path, 'w:gz') as archive:
                for name, content in {
                    'prefix/lib/libpython3.14.so': b'library',
                    'prefix/lib/python3.14/os.py': b'pass',
                    'prefix/lib/python3.14/test/test_os.py': b'ignored',
                    'prefix/lib/python3.14/__pycache__/os.pyc': b'ignored',
                    'prefix/include/python3.14/Python.h': b'header',
                }.items():
                    entry = tarfile.TarInfo(name)
                    entry.size = len(content)
                    archive.addfile(entry, io.BytesIO(content))
                link = tarfile.TarInfo('prefix/lib/libpython3.so')
                link.type = tarfile.SYMTYPE
                link.linkname = 'libpython3.14.so'
                archive.addfile(link)
            payload, headers = runtime.payload_files(path)
            self.assertEqual(b'library', payload['usr/lib/libpython3.so'])
            self.assertEqual(b'pass', payload['usr/lib/python3.14/os.py'])
            self.assertEqual(3, len(payload))
            self.assertEqual(b'header', headers['include/python3.14/Python.h'])

    def test_rejects_escaping_symlink(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'test.tar.gz'
            with tarfile.open(path, 'w:gz') as archive:
                link = tarfile.TarInfo('prefix/lib/libpython3.so')
                link.type = tarfile.SYMTYPE
                link.linkname = '../../../secret'
                archive.addfile(link)
            with self.assertRaises(ValueError):
                runtime.payload_files(path)

    def test_rejects_cyclic_symlink(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'test.tar.gz'
            with tarfile.open(path, 'w:gz') as archive:
                link = tarfile.TarInfo('prefix/lib/libpython3.so')
                link.type = tarfile.SYMTYPE
                link.linkname = 'libpython3.so'
                archive.addfile(link)
            with self.assertRaises(ValueError):
                runtime.payload_files(path)


if __name__ == '__main__':
    unittest.main()
