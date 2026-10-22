# Release signing and Android 13 migration

Version 1.9.30 uses an independently generated ytdownForAND production
identity on Android 13 (API 33) and later. Android 7
through 12L retains the legacy signature for update compatibility. Debugging is
disabled on every supported Android version. APK Signature Scheme v3.1 and an
old-to-new signing lineage preserve in-place updates; never uninstall or clear
data to work around a signature mismatch.

Keys are stored outside the repository in `%LOCALAPPDATA%/ytdownForAND/Signing`.
Access is limited to the current Windows account and SYSTEM. Keystore passwords
are protected using Windows DPAPI CurrentUser and supplied only through child
process environment variables. Private keys, passwords, and lineage files are
not included in Git or release assets. Do not copy DPAPI state alone to another
account and assume recovery will work; arrange an owner-controlled encrypted
recovery backup before moving or reinstalling this Windows account.

The approved key setup is explicit and idempotent:
`python tools/sign_release.py init`. Normal builds never create a key.
`python tools/sign_release.py status` verifies stored material and access control.
`assembleRelease` builds an unsigned input. Then run
`python tools/sign_release.py sign --input INPUT.apk --output NEW_VERSION.apk`.
The signer refuses overwrite, unexpected packages, debugging and misalignment;
it verifies the API-specific certificate at API 24/26/28/32/33/35/36.
Use `tools/verify_release_apk.py` with the trusted public fingerprints in
`docs/signing-certificates.json` for an independent final delivery check.

The lineage grants installed-data and signature-permission migration. The
signature-permission capability is required to retain AndroidX's existing
DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION during an in-place update. This retains
legacy-key trust for signature permissions; it does not enable old-key APK
rollback. Shared-UID, rollback and auth capabilities are disabled. This does not turn an
old Android system into a modern signing verifier. The legacy development key
remains a compatibility limitation on Android 12L and earlier.

Existing historical releases are unchanged. This signing migration does not
itself approve app-store submission or resolve other store/business gates.

Official references:
- https://developer.android.com/tools/apksigner
- https://source.android.com/docs/security/features/apksigning/v3-1
