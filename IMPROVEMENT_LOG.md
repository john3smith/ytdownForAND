# YTDown Android improvement log

## 2026-10-31: recursive improvement setup (cycle zero)

- Status: preparation only; zero implementation rounds completed. This entry does
  not claim a new defect, changed application behavior, build or download success.
- Baseline: version 1.9.32, versionCode 71; source commit
  `f2da4a0eef9ffd75dc6ed6f9b782602633d2afe7` matches the protected GitHub main branch.
  The existing published release remains unchanged.
- Scope: at most 20 verified improvement rounds using the existing coordinator.
  Reassess reproducible bugs, user impact and regression risk before every change.
  Stop or hold on the documented evidence-based conditions and preserve approvals,
  user stops, usage gates and uncertain external outcomes.
- Publication: intermediate rounds retain a source restore point, a scoped local
  commit, required validation and their final-message report. Source publication,
  a signed Release and deployment are reserved for the designated final round.
  Earlier termination preserves unpublished changes for later authorized work.
- Restore: a local Git archive of the baseline and its SHA-256 are retained outside
  project source. The pre-existing untracked release instructions are preserved.
  Recovery must use a separate review folder and must not reset active work.
- Verification so far: repository owner, existing public visibility, remote main
  commit, release metadata and native coordinator compatibility were checked.
  No application build, emulator interaction, actual download, signing, installation,
  source push, Release publication or delivery occurred during setup.
- First investigation: download cancellation and queue completion; shared and
  clipboard links with persisted options; file validation and cleanup on failures.
  These are investigation areas, not confirmed defects.
- Required validation for implementation rounds: relevant existing unit checks,
  static analysis, build and regular Android Emulator + ADB behavior checks. Changes
  to download behavior must include an authorized real download through completion,
  a nonempty saved file and expected media/audio/video metadata. Site authentication
  and security checks must remain intact; unverified paths stay explicitly pending.
- Final release: retain numeric versions and increasing versionCode, existing
  signing lineage, dependency/license notices and protected-branch review. Verify
  the GitHub asset and identical LAN-served APK bytes. Telegram reports contain
  links without APK attachments. Performance changes require actual measurements.
- Next step: after confirmed delivery of the setup report, the existing coordinator
  alone admits round one, respecting overlapping-app FIFO and current work gates.

## 2026-10-31: cycle 1 - reject incomplete direct media transfers

- Investigation: reviewed cancellation/generation checks, pending-task FIFO,
  structured image/direct-video saving, and failure cleanup. Chose direct-media
  completeness because a concrete shared helper could report success after an
  early EOF, allowing callers to mark truncated media ready. The direct-video
  caller only warned about small files; image dimension decoding alone did not
  prove that all advertised bytes arrived. No queue defect is claimed.
- Priority: incorrect completed downloads affect both direct images and videos.
  The application fix is confined to the existing copy helper and its unit tests,
  preserving authentication, transport selection, pending jobs and destinations.
- Reproduction before patch: 7 focused JVM tests ran; the original 3 passed and
  4 new regression cases failed because no exception was thrown. Cases covered
  premature EOF (1,024 received / 2,048 expected), empty known/unknown-length
  content, bytes beyond the advertised length, and cancellation during EOF read.
- Change: check cancellation immediately after every read, reject an advertised
  length overrun, reject premature EOF, and reject empty media. Exceptions enter
  the existing callers' failure cleanup before their completion marker is set.
  Nonempty unknown-length transfers and progress coalescing remain supported.
- Focused verification after patch: all 7 tests passed, including the existing
  byte-preservation, progress-coalescing and cancellation checks. No throughput
  or memory improvement was measured or claimed.
- Build-check repair within this round: the first complete command passed the
  app/test APK, unit and lint tasks but failed securityRuntimeInventory with
  Java heap space at the whole-artifact byte-array read. The original failed log
  is preserved. Changed only that task's hash calculation to read 64 KiB chunks;
  dependency selection and SHA-256 output format are unchanged. The same complete
  command then succeeded under the same 768 MiB JVM heap cap (81 tasks, including
  valid unchanged task outputs). All 56 artifact hashes independently matched
  Python's streaming SHA-256 calculation. No maximum process-memory reduction
  is claimed because process peak memory was not measured.
- Confirmed static/build checks: 166 unit tests in 26 suites, zero failures/errors;
  Android lint zero errors and 24 warnings; x86_64 isolated debug app/test APKs
  built; security runtime inventory passed. The 24 lint warnings remain unresolved.
- Status: implemented and locally validated; this is one intermediate improvement
  round. Regular Android Emulator YTDown_API_35 / emulator-5554 passed the existing
  owned loopback HTTPS download instrumentation in the separate .cycle1 package:
  14,452-byte synthetic MP4, 12,000 ms duration, verified 320-pixel video track,
  20 intermediate progress samples, 99% finalization and 100% completion. Notification
  progress, unknown-duration UI and stopped progress behavior passed. The test's
  own MediaStore export was removed by its existing cleanup; no real user media
  was used. Clipboard reads were blocked only for the test package. Authenticated
  third-party downloads, a physical phone and ARM64 production deployment remain
  untested in this round.
- Initial observations found ADB connected but Android
  framework services missing; host memory pressure and Binder failures are
  recorded locally. The framework and existing app screen recovered without
  this round restarting the emulator or stopping other user jobs. These initial
  failed observations are not passing app checks.
- Additional post-test observation: reopening the isolated test app showed an
  automatic yt-dlp update failure. Its process log identifies a SocketException:
  "Software caused connection abort". The earlier owned-media HTTPS download and
  media/UI assertions passed; that result does not prove live updater or site
  access. No network/VPN settings or other app jobs were changed to hide this
  failure. The startup updater/readiness behavior remains an explicit next
  investigation area and is not recorded as a passed update check.
- Restore: the pre-change Git source archive, archive/manifest hashes, preserved
  instruction-file hash and recovery method are retained outside project source.
  Restore into a separate review directory; do not reset existing work.
- Publication: local-only intermediate round. No source push, Release, production
  signing, operational installation, APK delivery or LAN serving is authorized
  here. Existing version 1.9.32/versionCode 71 and published assets are unchanged.
- Dependencies and rights: no dependency version, native source/input, asset or
  third-party license configuration changed; no external media was incorporated
  into source.
- Next investigation: startup engine readiness when the automatic update fails;
  then buffered output flush/close failure and publication order, and pending-task
  cancellation/resumption across connectivity changes.

## 2026-10-31: cycle 2 - installed engine readiness after update-check failure

- Investigation: followed the separate startup failure retained by cycle 1.
  Application initialization extracts and initializes yt-dlp/FFmpeg successfully,
  but MainActivity previously set engineReady only after an online update check
  succeeded. Its exception path enabled the retry button without marking the
  installed engine ready; beginDownload immediately retried the same online
  check. An update-server connection failure therefore prevented even a usable
  installed engine from downloading. This is distinct from cycle 1's media-copy
  completeness checks; no new network reachability fix is claimed.
- Priority: readiness affects all download entry points after startup. Confined
  the change to startup policy, status text and focused regressions. Kept the
  normal stable-channel update attempt and existing shared/clipboard entry paths.
- Change: capture an ordinary update-check exception, then actually execute the
  installed yt-dlp with --version. Only a successful execution with a nonempty
  version enables download/account controls. An update warning remains visible
  and logged. If installed execution fails, keep engineReady false, disable
  account download and retain the existing retry path. Initialization failures
  and fatal update errors do not enable the installed-engine fallback.
- Focused verification: six JVM checks passed for update success/failure,
  working/broken installed engines, empty versions and fatal errors. Android
  instrumentation additionally injects update failure while running the real
  installed engine, checks ready controls/status, and distinguishes a broken
  engine before the existing synthetic HTTPS media download test.
- Final required checks passed: 172 JVM tests in 27 suites, zero failures/errors;
  Android lint zero errors and the same 24 pre-existing warnings; isolated x86_64
  debug app/test APK builds and security runtime inventory. Removed the obsolete
  update-failure string after the initial lint run found one additional unused
  resource warning. The final complete incremental command passed, with input
  hashes confirming application/test sources stayed unchanged during validation.
- Regular Android Emulator YTDown_API_35 / emulator-5554: injected updater failure
  while actually executing installed yt-dlp; verified ready status and both download
  controls. A separately injected broken-engine probe kept engineReady false. The
  existing owned loopback HTTPS MP4 test then really downloaded and published
  14,452 bytes, checked a 12,000 ms duration and valid 320-pixel video track, and
  produced 22 intermediate progress samples. Finalization stayed at 99%, success
  reached 100%; notification, unknown-duration and stopped-progress checks passed.
  Only the test's own synthetic MediaStore export was removed by its cleanup.
- Visual QA: initial captures were obscured by the completed cycle 1 test app's
  existing ANR dialog; these captures are not an unobstructed UI pass. Confirmed
  its package/window and the prior full-message receipt, closed only that old
  isolated app through its dialog, then captured the current .cycle2 ready screen.
  Normal and 320dp-wide / font-scale 1.3 views showed complete download controls
  and readable wrapped readiness status; scrolled to the intended status card.
  Verified restoration of the emulator's original size and font scale afterward.
- Limits: updater failure tests use controlled fault injection, not a claim that
  the previously observed upstream connection failure is fixed. Installed-engine
  execution and owned HTTPS media transfer are real. Authenticated third-party
  downloads, physical devices, keyboard layouts and ARM64 production deployment
  are outside this round's tested scope. No production data or login was used.
- Restore: a verified source-only archive of the pre-change local HEAD and its
  SHA-256, preserved untracked instruction hash and recovery method remain in
  local operational evidence. Extract into a separate review directory; never
  reset active work or restore user data from source archives.
- Publication: local-only intermediate cycle. No source push, Release, production
  signing/install, APK delivery or LAN service. Version 1.9.32/code 71 and existing
  published assets are unchanged. Final publication remains designated cycle 20.
- Dependencies and rights: no new dependency, version, permission, native input,
  external asset or third-party notice configuration. Synthetic media and generated
  TLS credentials stay in isolated test/runtime outputs, outside committed source.
- Measurements: the startup decision behavior is covered; throughput, memory
  peaks and startup-duration improvement are not measured or claimed.
- Next investigation: buffered output flush/close failures and publication order,
  then pending-task cancellation/resumption across connectivity changes.
