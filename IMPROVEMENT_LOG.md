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
