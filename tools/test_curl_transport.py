"""Deterministic host packaging/security tests; no device assumptions."""
import io
from pathlib import Path
import tarfile
import tempfile
import unittest
import zipfile
import prepare_curl_transport as transport


class TransportPackagingTest(unittest.TestCase):
    def test_rejects_traversal(self):
        for name in ('../key', '/etc/passwd', 'src/../../key', 'src\\key', 'C:/key', 'src/key:stream', 'src/\x00key'):
            self.assertFalse(transport.safe_name(name))
        self.assertTrue(transport.safe_name('curl_cffi/requests/session.py'))

    def test_archive_links_are_never_extracted(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive = root / 'source.tar.gz'
            with tarfile.open(archive, 'w:gz') as tar:
                item = tarfile.TarInfo('source/source.py')
                item.size = 4
                tar.addfile(item, io.BytesIO(b'pass'))
                link = tarfile.TarInfo('source/link')
                link.type = tarfile.SYMTYPE
                link.linkname = '/etc/passwd'
                tar.addfile(link)
            transport.unpack_tar(archive, root / 'out')
            self.assertEqual(b'pass', (root / 'out/source/source.py').read_bytes())
            self.assertFalse((root / 'out/source/link').exists())

    def test_cached_hash_mismatch_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            cache = root / '.tools/curl-transport'
            cache.mkdir(parents=True)
            (cache / 'input.whl').write_bytes(b'corrupted')
            with self.assertRaisesRegex(ValueError, 'SHA-256'):
                transport.acquire(root, 'https://invalid.example/input.whl', '0' * 64)

    def test_package_metadata_and_ca_bundle_are_preserved(self):
        with tempfile.TemporaryDirectory() as folder:
            archive = Path(folder) / 'package.whl'
            with zipfile.ZipFile(archive, 'w') as wheel:
                wheel.writestr('certifi/cacert.pem', b'CA')
                wheel.writestr('certifi/__init__.py', b'pass')
                wheel.writestr('certifi-1.dist-info/METADATA', b'Name: certifi')
                wheel.writestr('other/secret', b'ignored')
            payload = {}
            transport.add_pure_package(payload, archive, 'certifi')
            self.assertEqual(3, len(payload))
            self.assertEqual(b'CA', payload['usr/lib/python3.14/site-packages/certifi/cacert.pem'])

    def test_pinned_android_inputs_exist_for_both_abis(self):
        self.assertEqual({'arm64-v8a', 'x86_64'}, set(transport.CURL))
        for _, digest in (*transport.CURL.values(), *transport.SOURCES.values()):
            self.assertEqual(64, len(digest))
            int(digest, 16)


if __name__ == '__main__':
    unittest.main()
