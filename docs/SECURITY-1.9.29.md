# v1.9.29 security update

The old Jackson 2.11.1, Commons IO 2.5 and Commons Compress 1.12 runtime selections are replaced with Jackson BOM 2.22.4, IO 2.22.0 and Compress 1.28.0. `securityRuntimeInventory` checks the actual selected versions and exports hashes. All 56 resolved Maven artifacts had no matching OSV advisory at the 2026-10-22 query. This is not a guarantee that the app or bundled native libraries have no vulnerabilities.

JSON factories created by the upstream downloader receive bounded read constraints before the downloader singleton initializes: depth 128, number length 1000, string 4 MiB and document 16 MiB. Large/deep metadata now fails instead of being parsed without those bounds. Normal Korean metadata is regression tested. Default polymorphic typing is not enabled.

Java API desugaring preserves the declared Android API 24 minimum with modern dependencies. The actual download test ran on Android API 35 x86_64, not a physical phone or API 24 device. It downloads an owned 12-second synthetic MP4 over HTTPS with its own test CA, checks output media metadata and UI/notification progress, then removes only its test output. It does not bypass external-service authentication. The instrumentation startup was fixed to wait for Application/engine initialization before using FFmpeg.

Delivery uses a non-debuggable release build signed with the existing debug identity for upgrade compatibility. The filename explicitly says `debugkey`; production key migration and store distribution are separate. Existing debug installations can be updated in place. Store signing validation still rejects debug certificates.

Resolved artifacts, hashes, POM license metadata and embedded original notices are in `store/dependency-inventory.json` and `store/THIRD_PARTY_NOTICES.txt`, with the latter also available inside the APK. `store/native-runtime-inventory.json` records both ABI payloads, native hashes and existing Python/curl build manifests. This update keeps the pinned Python 3.14.8 and FFmpeg/youtubedl-android 0.18.1 payloads; their full native/external-service commercial rights review remains unresolved, so storeRelease remains blocked.

References: https://github.com/FasterXML/jackson-databind , https://commons.apache.org/proper/commons-io/ , https://commons.apache.org/proper/commons-compress/ , https://developer.android.com/studio/write/java8-support , https://osv.dev/ .
