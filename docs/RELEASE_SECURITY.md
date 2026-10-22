# Release signing gate

`release` explicitly disables debugging and does not select a development key.
`assembleRelease` produces an **unsigned build input**, not an installable release.
Never publish debug/unsigned APKs as the security-fixed release.

Before distributing, use the owner's approved production identity. A new key
requires an explicit migration decision and an old-to-new signing lineage to
preserve updates. Do not uninstall or clear user data to bypass signatures.
Do not generate a key implicitly on a build, or put keys/passwords in this repo.

After signing, run `tools/verify_release_apk.py APK --package APPLICATION_ID
--release-sha256 EXPECTED_PRODUCTION_CERTIFICATE_SHA256` with the Android JDK and
build-tools paths. For an approved API 33 rotation, additionally pass
`--legacy-sha256 EXPECTED_LEGACY_CERTIFICATE_SHA256 --rotation-api 33`.
The expected fingerprints must come from the owner's trusted key record, not
from the candidate APK. Every supported API boundary is cryptographically
checked. Debuggable APKs and an Android Debug certificate active on modern
Android are rejected. Older-Android compatibility signing is reported explicitly.
The verifier also checks package, alignment, test-only flags and common secret
file types. This is not a complete secret scanner or app-store approval.

Key creation/migration and publication remain pending until the owner chooses
the signing identity. Existing historical releases are not overwritten.
Store-specific publisher, privacy, and external-rights gates remain in effect.

Official references:
- https://developer.android.com/tools/apksigner
- https://source.android.com/docs/security/features/apksigning/v3-1
