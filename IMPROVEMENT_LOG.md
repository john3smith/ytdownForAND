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

## 2026-10-31: cycle 3 - direct-video stream finalization

- Investigation: compared direct-image and direct-video save/finalization order
  with the verified staged StreamVideoPublisher path. Selected a concrete direct-
  video defect: complete=true ran inside try-with-resources, before buffered
  output flush and stream closes. A close/flush error therefore entered finally
  with complete=true and published a failed download. Opening the response could
  also fail before the already-open output stream gained a resource owner.
- Priority: incomplete direct videos could be visible as finished downloads.
  Confined production changes to resource lifetime and completion ordering.
- Before/after: an executable host harness compiled the exact unmodified cleanup
  block with the actual MediaTransfer and controlled stream/resolver doubles.
  Before: flush, output-close and input-close faults published the failed item;
  response-open failure leaked the output. After: all seven cases passed, including
  success, these four faults, short input and cancellation. These host faults do
  not claim physical Android storage failure or full Android behavior by themselves.
- Change: own buffered output before opening response input, finish both resource
  closes, then set complete. Existing finally deletes failed attempts. No network,
  authentication, cookie, permission, destination or UI policy changed.
- Android regression: the existing instrumentation now invokes the actual private
  direct-video method against real MediaStore after its genuine owned HTTPS MP4
  download. A test-process HTTP response fixture verifies exact saved bytes/hash,
  video/mp4 type, 12-second duration and 320-pixel video track on success. Controlled
  response close/open faults throw IOException, leave no owned MediaStore item and
  emit no completion log. Unique test-owned exports alone are removed. This test
  handler does not change TLS, trust or any production code/network configuration.
- Required final checks: 172 JVM tests / 27 suites, zero failures/errors; lint zero
  errors and 24 existing warnings; isolated x86_64 debug app/test APKs and security
  inventory passed. All 56 dependency hashes independently matched. The 11 Python
  runtime/transport packaging checks passed. A PowerShell native-stderr wrapper
  marked their logging redirection as an error despite unittest reporting OK; its
  original wrapper evidence is retained and decoded explicitly to UTF-8. The first
  direct invocation exited successfully. No failed check is reported as passed.
- Build binding: after adding the regression while the initial build was running,
  a final incremental full check succeeded with application/test input hashes
  captured before and verified unchanged afterward.
- Regular emulator YTDown_API_35 / emulator-5554 used isolated .cycle3. Waited for
  other app device actions to finish and verified the prior isolated YTDown screen
  before installing. Genuine owned HTTPS download, publication, 99% finalization,
  100% completion, notification/unknown/stopped progress and prior startup fallback
  regressions passed. The real MP4 was nonempty with valid metadata. No production
  package, user login/media or physical device was used.
- Limits: output flush/close faults are host injections; response open/close faults
  are Android injections. Live upstream update/authenticated third-party downloads,
  physical storage failure, ARM64 deployment and performance gains are unverified.
- Restore: pre-change source archive and SHA-256 verified; existing untracked
  instruction file preserved. Extract separately for review, never reset active work.
- Publication: local-only intermediate cycle, final publication remains cycle 20.
  No source push, Release, production signing/install, APK/LAN delivery or service.
  Existing version 1.9.32/code 71 and published files remain unchanged. No dependency,
  asset, native input or third-party license/notice configuration changed.
- Next investigate image response-open failure resource lifetime and MediaStore
  update failure/zero-row commit behavior, then pending-task cancellation across
  connectivity changes. These are candidates, not newly verified defects.

## 2026-10-31: cycle 4 - verify MediaStore publication

- Investigation: inspected direct image/video publication against the existing
  staged publisher's checked commit. Both direct methods set complete before
  clearing IS_PENDING in finally and ignored the update count. A zero-row update
  falsely returned success; a thrown update error bypassed deletion, retaining
  the current attempt's pending item. This is distinct from cycle 3's stream
  close ordering; no queue/connectivity defect is claimed.
- Priority: false completion and orphaned pending downloads affect both direct
  media paths. Move the commit inside the guarded operation, require exactly one
  updated row and set complete only after that succeeds. Existing failure cleanup
  removes only the newly inserted URI. Legacy file storage behavior is unchanged.
- Reproduction: exact unmodified image and video source blocks compiled with the
  real MediaTransfer and host resolver/stream doubles. Before: commit-zero and
  commit-throw failed in both paths (four failures). After: ten checks passed for
  normal completion, both commit failures, short input and cancellation. These
  injections do not demonstrate a physical Android storage failure.
- Android regression: actual private image/video download methods ran against
  real MediaStore. A test-only local wrapped provider delegates insert, file I/O,
  query and deletion to the real resolver and controls only update's return/throw.
  Six success/zero/throw cases passed. Success required nonpending metadata,
  exact filename/type/size and SHA-256; the generated PNG additionally decoded to
  640x480. Failed commits threw, deleted the owned URI and emitted no completion
  log. Only unique attempt-owned test exports were removed. Synthetic responses
  stay within the test process; no production TLS/authentication change.
- Required final checks: 172 JVM tests / 27 suites, zero failures/errors;
  lint zero errors and 24 warnings; isolated x86_64 debug app/test APK builds,
  security inventory with all 56 independent hashes matched; 11 Python
  runtime/transport packaging checks. Input hashes bind final checks and runtime
  evidence to the finished application/test sources.
- Same-round repair: the initial Android compilation caught a missing IOException
  import introduced by this patch. Added that import and reran the full checks.
  Original failed log/result are retained separately; that attempt is not passed.
  The first Python packaging invocation also used the wrong working directory;
  preserved its import-error log, corrected the tools-directory invocation and
  confirmed all 11 tests passed with an actual zero exit code.
- Regular Android Emulator YTDown_API_35 / emulator-5554, isolated .cycle4:
  genuine owned loopback HTTPS MP4 transfer/publication with a nonempty saved file,
  12-second duration and 320-pixel video track; intermediate/99%/100% progress,
  notification and startup readiness checks passed, along with prior direct-video
  response-open/close failure cleanup. Original package and user data preserved.
- Limits: commit errors are controlled return/exception injection, with actual
  Android MediaStore I/O around them. Physical storage failures, authenticated
  third-party downloads, live upstream updater recovery and ARM64 production
  behavior remain unverified. No performance improvement was measured or claimed.
- Restore: pre-change source archive and SHA-256 verified; existing untracked
  instruction preserved. Extract into a separate review folder, never reset work.
- Publication: local-only intermediate cycle; final publication stays cycle 20.
  No push, Release, production signing/install, APK attachment or LAN serving.
  Version 1.9.32/code 71 and published main/assets remain unchanged. No dependency,
  native input, permission, asset or third-party notice configuration change.
- Remaining candidates: image response-open resource lifetime and destination
  open exceptions; pending-task cancellation/connectivity changes; progress at
  the copy-to-publication boundary. These are investigation areas, not new fixes.

## 2026-10-31: cycle 5 - destination open resource lifetime (runtime deferred)

- Status: deferred for queued user work, not a completed improvement cycle.
  Preserve the same round for the native coordinator to resume after report
  delivery and user work; no new plan, queue mutation or manual retry registration.
- Investigation: both direct image/video methods opened their MediaStore/file
  destination before the failure-cleanup guard. An insert exception leaked the
  connection; an output-open exception additionally retained the newly inserted
  pending item. Images also opened response input before owning output, leaking
  output if response opening failed. These precede cycle 4's checked commit and
  cycle 3's video stream-close ordering, rather than repeating those fixes.
- Change: protect destination allocation/open with the existing cleanup guard;
  own image buffered output before opening response input. On failure, disconnect
  and delete only this attempt's URI/file. No deletion of historical downloads.
- Before/after: compiled exact unmodified source blocks with the real MediaTransfer
  and controlled host stream/resolver doubles. Five failures reproduced before:
  insert/output exceptions for both paths and response-open for images. After:
  18 cases passed covering success, insert exception/null, output exception/null,
  response open/close, short input and cancellation. These do not prove physical
  Android storage failure or a successful Android runtime execution.
- Static/build validation passed: 172 JVM tests / 27 suites, zero
  failures/errors; lint zero errors and 24 warnings; isolated .cycle5
  x86_64 app/test APK builds; 56 independent dependency hashes matched;
  11 Python runtime/transport packaging checks. Application/test input hashes
  were captured before build and confirmed unchanged after all final checks.
- Runtime: NOT RUN this round. Regular emulator identity/readiness and the prior
  isolated YTDown foreground were read-only observed. A new queued user command
  appeared before installation/instrumentation, so saved local work and yielded.
  No runtime pass is inferred from a build or host double. Extended the existing
  real-MediaStore instrumentation for output-open and response-open/close faults
  for images/videos; these checks compiled but await execution, along with the
  existing genuine owned HTTPS MP4 download, metadata and progress regressions.
- Restore: verified source-only pre-change ZIP and hash retained locally;
  extract into a separate review directory, never reset active work or user data.
  Existing untracked project instructions were preserved and hash verified.
- Publication: intermediate local-only checkpoint. No push, Release, production
  signing/install, APK delivery, LAN link or service. Existing version 1.9.32/code
  71 and published main/assets remain unchanged; final publication stays cycle 20.
  No new dependencies, native inputs, permissions, assets or notice changes.
- Limits/next: complete the pending runtime checks in this same cycle, then
  bind passed validation to the final local commit before completed status.
  Physical storage failure, ARM64, authenticated third-party downloads and
  performance gains are unverified. Later candidates: pending cancellation and
  connectivity transitions, and copy-to-publication progress; not new fixes yet.

### Cycle 5 resumed - required runtime completed in the same round

- The two queue-yield checkpoints and full final-message receipt were reconciled
  before continuing. No prior application change, upload or message was repeated.
  This completes the pending fifth round; it is not another improvement cycle.
- Regular YTDown_API_35 emulator, isolated .cycle5 x86_64 debug app/test APKs:
  all extended direct image/video success, zero-row/throw commit, destination-open,
  response-open and response-close cases passed with actual MediaStore insertion,
  file I/O, query and deletion. Failures removed only their own new item and did
  not emit completion. Error outcomes are controlled injection, not physical
  Android storage failures. Success checks verify MIME, size, filename, SHA-256,
  PNG dimensions or MP4 duration/track as appropriate.
- Genuine owned loopback HTTPS MP4 transfer completed with nonempty media,
  12-second duration and 320-pixel video track. Intermediate progress, 99% during
  finalization, verified 100% completion, notification progress, unknown-duration
  and stopped-state behavior passed. Existing-engine readiness after an injected
  update failure and broken-engine rejection also passed with the actual installed
  engine probe. Live upstream recovery and authenticated sites remain untested.
- Retained required checks were reconciled against unchanged exact build inputs:
  172 JVM tests / 27 suites, zero errors/failures; lint zero errors / 24 warnings;
  isolated app/test builds; all 56 dependency hashes; 11 Python packaging checks;
  18 source-block host lifetime cases. Passed checks were not needlessly rerun.
- Both production version/install timestamps and untracked project instructions
  remained unchanged. The prechange source ZIP and SHA-256 were verified again;
  restore by extracting into a separate review directory, never reset active work.
- Status: completed locally, final publication remains cycle 20. No source push,
  Release, production signing/install, APK/LAN delivery or service was performed.
  No new dependency, native input, permission, asset or notice change. Physical
  storage faults, ARM64 production, third-party authenticated downloads and
  performance gains remain unverified. No measured performance claim is made.
- Next investigation: pending-task cancellation and connectivity transitions,
  followed by progress at copy-to-publication boundaries. These remain candidates;
  this round does not implement or count them as improvements.

## 2026-10-31: cycle 6 - cancel pending-only downloads

- Status: completed locally. This remains the sixth round, not a new plan.
- New finding: while only pending downloads remain (including Wi-Fi-only waiting),
  updateQueueUi did not enable Cancel, setDownloadingUi(false) disabled it, and
  both cancellation entry points returned before clearing pending tasks. Cancelling
  an active account while preserving later work also left pending controls disabled.
- Priority: users cannot use the existing confirmed cancel-all operation for
  waiting work. Enable Cancel whenever active, bulk or pending work exists and
  accept pending-only work in the two existing guards. Keep the one-second,
  two-press confirmation, selected-item removal and clearPending=false behavior.
- Before/after: exact original MainActivity method bodies and the real DownloadQueue
  compiled with host Android/control doubles. Four cases failed before; seven
  passed after: pending controls, confirmed/direct cancellation, expired
  confirmation, active preserve/clear, empty queue. Host doubles do not prove
  actual Android interactions or a real connectivity transition.
- Required local checks: 172 JVM tests / 27 suites, zero failures/errors;
  lint zero errors / 24 warnings; isolated .cycle6 x86_64 app/test builds;
  56 independent dependency hashes; 11 Python packaging checks.
  Final build/test input hashes were captured and reconciled unchanged.
- Runtime: isolated emulator instrumentation and genuine owned HTTPS MP4 download completed; controlled queued-only button regression passed.
  Added a controlled pending-only Android regression using real Cancel clicks,
  expired confirmation and an empty resume callback. Physical Wi-Fi transitions
  are not claimed. Required owned HTTPS download/metadata/progress regression
  passed.
- Restore: verified source-only prechange ZIP and SHA-256; extract separately for
  review, never reset active work. Existing untracked instructions preserved.
- Publication: local-only, no push, Release, production signing/install, APK/LAN
  delivery or service; designated final publication remains cycle 20. Existing
  main and v1.9.32 asset metadata unchanged. No dependency, permission, native,
  asset or license/notice configuration change. Shared-context dirty/diverged
  checkout preserved; no pull, merge, stash, reset or push.
- Limits/next: next investigate actual connectivity loss/recovery and bulk queue handoff.
  Physical devices, ARM64 production, authenticated third-party downloads and
  performance improvement remain unverified; no measured performance claim.
