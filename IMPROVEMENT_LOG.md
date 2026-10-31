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

## 2026-10-31: cycle 7 - release screen-on flag while bulk downloads wait for Wi-Fi

- Status: completed locally. The seventh round only; no new plan or queue changes.
- New finding/priority: after account discovery or between bulk media items,
  startNextBulkDownload returned for Wi-Fi-only waiting without clearing
  FLAG_KEEP_SCREEN_ON. An indefinite network wait could therefore keep the screen
  awake. The ordinary pending queue already clears this flag in its waiting path.
- Fix: clear only the window's screen-on flag at the existing bulk Wi-Fi wait.
  Preserve bulk ownership, FIFO media/options, foreground service, cancellation,
  connectivity callbacks and active downloads. startDownload restores the flag
  when a media download actually resumes. No UI layout or dependency change.
- Reproduction: exact original method with controlled network/window doubles
  failed the offline screen-release case; the modified method passed all seven
  cases, including pending preservation, reconnect FIFO/options/screen-on,
  unrestricted mode, active transfer, discovery and inactive bulk guards.
- Required checks: 172 JVM tests / 27 suites, zero failures/errors;
  lint zero errors / 24 warnings; isolated .cycle7 x86_64 app/test builds;
  56 independently matched dependency hashes; 11 Python packaging
  checks. Final build/test input hashes were captured and reconciled unchanged.
- Runtime: regular emulator actual Wi-Fi loss/recovery and actual Android window flag passed. Controlled bulk items/ownership remained untouched; Wi-Fi was restored in finally. Genuine owned HTTPS MP4 download completed with nonempty file, duration/track metadata, progress/notification and MediaStore regressions passed.
- Restore: verified prechange source ZIP and SHA-256; extract in a separate review
  directory. Preserve existing untracked instructions and all user data.
- Publication: intermediate local-only cycle, final publication remains cycle 20.
  No push, Release, production signing/install, APK/LAN delivery or serving.
  Production version 1.9.32/code71 and published main/asset metadata unchanged.
  No new dependency, permission, native binary, asset or license/notice change.
  Dirty/diverged shared-context checkout preserved without pull/merge/reset/stash.
- Limits: controlled bulk state does not prove authenticated account discovery or
  an upstream bulk download. No battery/power/performance measurement is claimed.
  Physical devices and ARM64 production remain untested in this local cycle.
- Next investigation: account discovery callback ownership/lifecycle and queue
  handoff after discovery errors, without repeating this screen-on flag fix.

## 2026-10-31: cycle 8 - clean up partially started account discoveries

- Status: completed locally after same-round continuation and validation recovery. The eighth round only; no new plan or queue changes.
- New finding/priority: MainActivity caught X/Instagram discovery start errors
  after dropping the discovery reference. finishBulkDiscovery therefore could
  not cancel a partially initialized WebView or remove its pending callbacks.
  This can leave a failed search attached while the queue proceeds.
- Fix: retain the reference in both start-error catches and let the existing
  finishBulkDiscovery cancel and clear it. Keep success/asynchronous-error
  behavior, owner guards, cancellation and queue handoff unchanged. No layout,
  permission, native input, dependency, asset or license/notice change.
- Before/after: exact original start and finish method bodies with host doubles
  failed four lifecycle-cancellation cases (two after partial resource setup,
  two before setup). Modified methods passed all ten cases: both platforms,
  partial/early start faults, successful startup, asynchronous errors and late
  callbacks from an older owner. Host doubles are not physical failure evidence.
- Required checks: 172 JVM tests / 27 suites, zero failures/errors;
  lint zero errors / 24 warnings; isolated .cycle8 x86_64 app/test builds;
  56 independently matched dependency hashes; 11 Python packaging
  checks. Exact final build/test inputs were captured and reconciled unchanged.
- Runtime: regular emulator actual X/Instagram partial WebView attachment failures cleaned up the real views, ownership, bulk/scanning state and screen-on flag before any upstream site loaded. The owned HTTPS MP4 completed with 14452 bytes, 12000 ms duration, valid 320-pixel video track and 22 intermediate samples. Progress/notification, real MediaStore success/fault cleanup, pending cancellation and actual Wi-Fi loss/reconnection checks passed. Fresh startup, in-progress and final isolated-app screens were reviewed.
- Restore: source-only prechange ZIP and SHA-256 verified. Extract separately for
  review; do not reset active work/user data. Untracked instructions preserved.
- Publication: intermediate local-only; no push, Release, production signing/
  install, APK/LAN delivery or server. Final publication remains cycle 20.
  Production v1.9.32/code71 and published main/asset metadata remain unchanged.
  Shared-context dirty/diverged checkout preserved without pull/merge/reset/stash.
- Limits: startup failures are controlled injection, not a naturally observed
  WebView allocation failure; authenticated upstream account discovery, physical
  phones and ARM64 production are not tested. No performance measurement/claim.
- Next investigation: X discovery renderer termination and Activity lifecycle
  ownership. Confirm a new defect before changing either path.
- Same-round recovery: the first instrumentation attempt passed discovery cleanup
  but failed transfer verification after Android reclaimed in-flight cache files
  under low storage. The test-only fixture and startup evidence now use filesDir;
  assertions are unchanged and provide clearer transfer diagnostics. The first
  corrected test-APK install was explicitly rejected for insufficient space.
  Three inactive earlier isolated test runtimes were backed up locally and all
  4,068 file hashes verified before offloading their generated engine folders.
  Production apps, preferences and media were preserved. No threshold/security
  override or unrelated app/data cleanup was used. The final install and full
  instrumentation passed; failed attempts and recovery evidence remain local.
- Continuation changed only test fixture storage/diagnostics and this log.
  Test APK rebuilt and lint rerun; prior 172-test JVM/application build results
  remain valid against unchanged production inputs and unchanged application APK.
  No natural allocation-failure, authenticated upstream or performance claim.

## 2026-10-31: cycle 9 - handle X account discovery renderer loss

- Status: completed locally after same-round continuation and test-helper correction.
- Finding/priority: X discovery's WebViewClient inherited the unhandled renderer
  termination default. Unlike the existing Instagram handler, it did not report
  a controlled search error or release its WebView and scan callbacks. Renderer
  loss can therefore reach Android's unhandled failure path instead of the queue.
- Fix: add the renderer callback, record the event, invoke the existing fail
  cleanup/error path and return handled. Do not publish partial search results.
  The finished guard preserves single delivery after failure/cancel. Only eight
  production lines changed; no layout, dependency, permission, native binary,
  asset or license/notice input changed.
- Before/after: extracted original handler/default plus actual cancel/fail/cleanup
  method bodies compiled with host resource/control doubles: four expected
  unhandled cases failed before, all five cases passed after, including ordinary
  cancellation, partial-state, duplicate/late error and post-cancel callbacks.
  This is host reproduction, not an actual Android renderer crash.
- Required local checks: 172 JVM tests / 27 suites, zero failures/errors;
  lint zero errors / 23 warnings; isolated x86_64 app/test APK builds;
  56 independently verified dependency hashes and 11 Python packaging tests.
  Exact tested application and instrumentation inputs were hash reconciled.
- Runtime: regular emulator controlled renderer-loss delivery passed all four
  cases with real WebViews: active empty/partial results, after cancellation and
  after a prior failure. Real views and pending callbacks were removed before
  error delivery; duplicate/late events did not publish partial success or
  duplicate errors. The owned HTTPS MP4 completed with 14452 bytes,
  12000 ms duration, valid 320-pixel video track and 22 intermediate samples.
  Progress/notification, actual MediaStore success/fault cleanup, pending
  cancellation and real Wi-Fi loss/recovery checks passed. Fresh startup,
  in-progress and final isolated-app screenshots were reviewed.
- Restore: source-only prechange ZIP and SHA-256 verified; extract separately for
  review, never reset active work or user data. Untracked AGENTS.md preserved.
- Publication: completed local-only intermediate round. No push, Release, production signing/install,
  APK/LAN delivery/service or operational activation. Publication remains cycle
  20. Existing published main, v1.9.32 asset metadata and production install
  version/timestamps unchanged. Shared-context dirty/diverged checkout preserved.
- Same-round recovery: the first Android run crashed in the test-only reflection
  helper because it searched MainActivity.class for a discovery object's field.
  Original failure/logs and the original test source were preserved. The getter
  now reflects the supplied object's actual class; no assertions were weakened.
  Corrected test APK and lint were rerun, then the full instrumentation passed.
  Production inputs and application APK remain byte-identical to the checkpoint;
  prior JVM/application/packaging checks were reconciled rather than rerun.
- Remaining/next investigation: Activity destruction and discovery ownership;
  this continuation does not count as another round or register new work.
  ARM64 production, physical phones, natural renderer crash and authenticated
  upstream downloads remain unverified. No performance improvement is claimed.

## 2026-10-31: cycle 10 - skip account diagnostic dialogs after Activity exit

- Status: completed locally, tenth round only; publication remains cycle 20.
- Finding/priority: X/Instagram discovery error callbacks can outlive an Activity
  window. The shared diagnostic presenter unconditionally created an AlertDialog.
  The existing lifecycle intentionally permits unfinished background work after
  non-finishing destruction, so a late error can reach this presenter.
- Before evidence: actual regular-emulator live dialog displayed successfully;
  after actual Activity finish/destruction, the same presenter raised an exception.
  An exact-method host harness reproduced three invalid-window cases (finishing,
  destroyed, both), with the live case passing. Host window behavior is controlled.
- Fix: preserve diagnostic logging, then skip only dialog creation while the
  Activity is destroyed or finishing. Two production lines added; discovery
  callbacks, ownership, queue processing and download/cancellation remain intact.
- After: all four host cases passed, with logs retained. Regular emulator showed
  the live dialog, then finished/destroyed the real Activity and accepted a late
  controlled diagnostic call without a window exception. No real upstream account
  error or independent configuration recreation is claimed.
- Checks: 172 JVM tests/27 suites, zero failures/errors; lint zero errors / 23
  warnings; isolated x86_64 app/test builds; 11 Python packaging tests and 56
  dependency hashes verified. Exact final app/test build inputs reconciled.
- Runtime: owned loopback HTTPS MP4 transfer completed: 14452 bytes, 12000 ms,
  valid 320-pixel video track, 22 intermediate progress samples. Existing real
  MediaStore success/fault cleanup, discovery resource/renderer checks, pending
  cancellation, actual Wi-Fi loss/recovery and engine readiness checks passed.
  Fresh startup, transfer and final screens reviewed; fixture data only.
- Restore: verified source-only prechange ZIP and hash retained locally. Extract
  separately for review; do not reset active work or user data. Existing untracked
  AGENTS.md preserved.
- Recovery: host test double initially had an invalid nested-class definition;
  corrected the local harness before recording baseline failures. Runtime preflight
  found the old isolated cycle2 task exposed after cycle8 finish; verified same-app
  exclusion, preserved other tasks and admitted only existing isolated YTDown apps.
  No app/runtime assertion was relaxed or failed check reported as passed.
- Publication: local commit only; no push, Release, production signing/install,
  APK/LAN delivery/server or operational activation. Production package version,
  install times and APK SHA-256, published main and Release asset unchanged.
  No dependency, permission, native binary, asset or license/notice input changed.
  Shared-context dirty checkout preserved without pull/merge/reset/stash/push.
- Limits: physical phones, ARM64 production, authenticated upstream downloads and
  a natural late discovery error remain untested. No performance/power claim.
- Next investigation: account discovery resources and ownership across Activity
  recreation; distinguish intended background completion from stale-window work.

## 2026-10-31: cycle 11 - bound stalled X discovery loading and JavaScript waits

- Status: completed locally, eleventh round only; final publication remains cycle 20.
- Investigation: reviewed Activity destruction, account owner guards and discovery
  resource lifetime. The intended background completion paths were preserved.
  Found a separate liveness fault: X's ten-minute check was reached only by a
  page-driven scan, and its evaluating guard returned before the deadline check.
  A page that never finishes or an evaluation without callback can retain the
  discovery WebView, scanning owner and foreground work indefinitely.
- Fix: schedule an independent ten-minute Handler deadline before initial URL
  loading; remove it through the existing shared cleanup path. The finished guard
  prevents late duplicate terminal delivery. Keep the existing X partial-result
  timeout policy, cancellation, owner handling, and scan behavior unchanged.
  Seven production lines; no layout or dependency/permission/native/asset change.
- Before/after: extracted actual production scan/terminal/cleanup methods with
  host resource/Handler doubles failed loading, evaluation and partial-result
  stalled cases before; all five cases passed after, including cancellation and
  prior success. Host scheduling is controlled, not Android timing evidence.
  Android baseline independently reproduced retained real WebView on a stalled
  initial page; corrected real Handler registration and all five terminal cleanup
  cases passed. Deadline delivery is controlled without a ten-minute wall wait;
  upstream pages are blocked and no natural network stall is claimed.
- Required checks: 172 JVM tests / 27 suites, no failures/errors; lint no errors /
  23 warnings; isolated x86_64 app/test builds; 11 Python packaging tests;
  56 dependency hashes verified; exact final app/test build inputs reconciled.
- Runtime: owned HTTPS MP4 completed with 14452 bytes, 12000 ms duration,
  valid 320-pixel video track and 22 intermediate samples. Actual MediaStore
  success/fault cleanup, prior renderer/partial-discovery cleanup, cancellation,
  Wi-Fi loss/recovery and engine readiness regressions passed. Fresh startup,
  transfer and final isolated-app screens reviewed.
- Restore: verified prechange source-only ZIP and SHA-256; extract separately for
  review, never reset active work/user data. Untracked AGENTS.md preserved.
- Same-round preflight: first Android attempt stopped at the foreground guard
  while another app owned the emulator, before install or interaction. Rechecked
  after independent host/build work and proceeded only from isolated YTDown with
  no active instrumentation; other app/user work was not stopped.
  The first baseline run failed in the test setter, which reflected MainActivity
  instead of the supplied discovery class. The setter now uses the actual class;
  main-thread failures are returned to the instrumentation thread. Corrected test
  build/lint/input hashes were rerun before baseline and final runtime checks.
  Original failed output/crash evidence is retained; assertions were not relaxed.
- Publication: local commit only; no push, Release, production signing/install,
  APK/LAN delivery/server or operational activation. Production package version,
  install times and APK bytes, published main and Release asset unchanged.
  No new dependency, permission, native binary, asset or license/notice inputs.
  Shared-context dirty/diverged state preserved without pull/merge/reset/push.
- Limits: physical devices, ARM64 production, authenticated upstream downloads,
  natural stalled page and elapsed ten-minute timing are untested. No measured
  performance/power improvement is claimed.
- Next investigation: Instagram discovery's independent timeout coverage and
  resolver ownership. Confirm/reproduce independently before changing that path.

## 2026-10-31: cycle 12 - bound stalled Instagram profile discovery

- Status: completed locally, twelfth round only; final publication remains cycle 20.
- Investigation: independently reviewed Instagram profile loading, JavaScript
  evaluation, partial-result handling, cleanup and post-owner resolution. The
  profile timeout was checked only by a page-driven scan, after the evaluating
  guard. Initial loading or an evaluation without callback could leave discovery
  resources retained indefinitely. Resolver already checks finished in posted
  callbacks and uses the bounded HTTP reader; its ownership is unchanged here.
- Priority: reproducible discovery liveness/resource fault, small bounded change;
  no speculative feature or unrelated refactor. Instagram deliberately treats
  incomplete media lists as failure, unlike the X partial-result timeout policy.
- Fix: schedule an independent ten-minute profile deadline before initial load,
  reuse the existing Instagram failure diagnostic including partial item count,
  and remove the timer in shared cleanup. The existing fail guard prevents late
  duplicate terminal delivery. Cancel and successful completion remove the timer.
- Before/after: actual production runnable/terminal/cleanup extraction with host
  resource and Handler doubles reproduced three stalled failures; all five cases
  passed after, including cancellation and prior completion. Real regular-emulator
  baseline separately reproduced a retained WebView before page completion.
  Corrected real Handler registration and five terminal/cleanup cases passed.
  Timers are dispatched under test control without a ten-minute wall wait;
  network is blocked, and no natural upstream stall is claimed.
- Checks: 172 JVM tests / 27 suites, no failures/errors; lint no errors / 23
  warnings; isolated x86_64 app/test builds; 11 Python packaging tests; 56 pinned
  dependency artifact hashes checked; final tested-source inputs reconciled.
- Runtime: owned HTTPS MP4 completed with 14452 bytes, 12000 ms duration,
  a valid 320-pixel video track and 22 intermediate progress samples. Real
  MediaStore success/fault cleanup, X deadlines/renderer cleanup, partial setup,
  pending cancellation, Wi-Fi loss/recovery and engine readiness regressions pass.
  Fresh startup, download progress and final ready screens visually reviewed.
- Restore: verified prechange source-only ZIP and SHA-256; extract separately for
  review, never reset active work/user data. Untracked AGENTS.md preserved.
- Same-round recovery: initial local test-preparation assertions caught exact
  argument parsing and fixture URL differences before changing source. Corrected
  the preparation script; the unchanged preliminary build is retained separately.
  The first Android baseline preflight saw another app in foreground and stopped
  before install/interaction; independent host/build work continued. Baseline
  application/test bytes were preserved for later verification. No other app,
  instrumentation, queue, approval or relay was stopped or modified.
- Publication: local commit only; no source/shared-context push, Release,
  production signing/install, APK/LAN delivery/server or operational activation.
  Published main/Release asset, production package version/timestamps and APK bytes
  unchanged. No dependency, permission, asset/native binary or license inputs
  changed. Dirty shared-context checkout preserved without pull/merge/reset/push.
- Limits: physical devices, ARM64 production, authenticated upstream downloads,
  natural network stalls and elapsed ten-minute timing untested. No measured
  performance or power improvement claim.
- Next investigation: Instagram post-owner resolver cancellation and callback
  ownership; independently reproduce any fault before changing that path.

## 2026-10-31: cycle 13 - investigate Instagram resolver callback ownership

- Status: no_improvement; investigation only, not a new code improvement.
  This one round does not terminate the approved twenty-round coordinator plan.
- Reviewed the post-link resolver worker, main-Handler success/error callbacks,
  cancellation, profile setup failure and HTTP/cookie/body resource boundaries.
  Both posted callbacks check finished before starting a profile or reporting an
  error. Cancellation marks finished before shared cleanup. The owner request
  retains its source/cookie inputs; direct profile links skip the resolver.
- New investigation evidence: extracted the exact current start/cancel/fail/
  cleanup methods into a local Java host harness. Eight cases passed: live
  success/error, cancellation before worker success/error, cancellation after
  success/error is queued, profile setup failure, and direct-profile routing.
  The worker is real Java; HTTP and main dispatch are controlled doubles.
  This is evidence about existing guards, not an Android resolver bug fix.
- Harness recovery: an initial fixture classified profile-setup failure as a
  network error. Corrected fixture selection and reran all eight assertions;
  original failure retained. No production code or assertion was weakened.
- Required checks: 172 JVM tests / 27 suites freshly rerun, no failures/errors;
  lint current unchanged inputs has no errors / 23 warnings (analysis was
  incremental); isolated x86_64 app/test incremental builds and exact source
  fingerprints verified; 11 Python packaging tests and 56 dependency hashes pass.
- Existing runtime regression suite rerun on regular emulator using isolated
  cycle8 package. Owned synthetic HTTPS MP4 completed: 14452 bytes, 12000 ms,
  22 intermediate samples, valid video metadata. Prior discovery deadlines,
  renderer/setup/MediaStore cleanup, cancellation, Wi-Fi restoration and engine
  readiness checks pass. Startup/progress/final ready screens visually reviewed.
  These regressions confirm existing behavior and are not new improvements.
- Limits: resolver network/callbacks in the fresh host checks are controlled.
  The HTTP reader has byte and per-read idle bounds, not a proven whole-request
  deadline. Cancellation of in-flight resolver network I/O, natural slow trickle,
  real authenticated Instagram pages and Activity recreation remain unverified.
  No code change without a reproduced fault; no performance/power claim.
- Restore: verified pre-investigation tracked-source ZIP and SHA-256, untracked
  AGENTS.md preserved. Extract separately for review; do not reset active work.
- Publication: log-only local commit; no app source/dependency/license/permission/
  asset changes. No push, Release, signing, production install/activation, APK/LAN
  delivery/server or duplicate Telegram send. Production APK/version/install times
  and published main/asset unchanged; final publication remains round 20.
  Dirty shared-context checkout preserved without pull/merge/reset/push.
- Next investigation: resolver I/O lifetime under controlled slow response and
  cancellation; then LoginActivity navigation/renderer cleanup. Independently
  reproduce a user-visible fault before selecting any patch. Only the existing
  coordinator may admit the next round after final-message delivery verification.

## 2026-10-31: cycle 14 - resolver deadline checkpoint, user work yield

- Status: deferred / queued_user_work; this is not a completed improvement round.
- Reproduced a separate pre-profile liveness gap: the Instagram post-owner HTTP
  resolver has byte and per-read idle bounds, but no independent UI deadline.
  A response that keeps arriving within each socket read timeout can retain the
  account discovery UI beyond the profile scan's intended bounded wait. The
  profile deadline added earlier starts only after owner resolution completes.
- Change: seven production lines add a ten-minute owner-resolution deadline using
  the existing Handler and fail/cleanup path. Cancel/failure remove it; successful
  resolution removes it before the profile stage gets its own existing deadline.
  Finished guards suppress success/error callbacks arriving after a timeout.
- Before/after: the exact production resolver entry/cleanup harness fails before
  with no registered resolver deadline. Ten host cases pass after: timeout with
  late success/error, timeout with already queued success/error, cancel with late
  or queued callbacks, normal success/error and direct-profile routing. Real Java
  worker, controlled resolver and Handler dispatch; no elapsed ten-minute claim.
- Checks completed: 172 JVM tests / 27 suites freshly executed, no failures or
  errors; lint no errors / 23 warnings; isolated x86_64 application and
  existing test APK build; 11 Python tests; 56 dependency hashes; build input
  identity and original production package/version/install times/APK hash retained.
- Yield: a fresh durable queue check found a later user command before any
  emulator installation or interaction. The runtime preflight stopped at this
  guard. This is queue-aware yielding, not an Android test failure. Existing app
  regressions and owned HTTPS media download have NOT run for this new source.
- Restore: verified prechange tracked-source ZIP and hash, with original source
  content checked against the parent commit. Extract separately for review; do
  not reset active work or user data. Untracked AGENTS.md preserved.
- Checkpoint recovery: archive/blob and archive/working byte comparisons caught
  Git archive CRLF conversion versus Git blob and working-file LF. Recorded byte
  counts and line endings; prechange working bytes match the exact parent blob
  SHA-256, and normalized archive source equals it. No backup was replaced.
- Publication: local checkpoint only, no push/shared-context push/Release,
  production signing/install/activation, APK/LAN server or delivery. Remote main
  and existing Release asset unchanged. No dependency/license/permission/asset
  changes. Dirty shared-context checkout preserved.
- Limits: this bounds discovery UI waiting, not in-flight socket lifetime. The
  worker is still daemonized; socket cancellation and naturally slow upstream,
  authenticated Instagram and Activity recreation are unverified. No measured
  performance/power claim or complete runtime validation claim.
- Resume THIS cycle: verify this checkpoint and latest queue/ownership, perform
  the existing regular-emulator regression suite and real owned HTTPS MP4 through
  completion, inspect its output metadata and screens, then finalize validation
  and result. Do not repeat the patch/build/commit unnecessarily. Only the native
  coordinator resumes this round after user work and final report receipt.

## 2026-10-31: cycle 14 - resumed runtime evidence verified and completed

- Completion of the SAME resolver-deadline round after yielding to user work.
  No new improvement, resolver patch, build, installation or download was repeated.
- Read-only reconciliation: the existing patch commit is an ancestor of the
  independently requested cumulative v1.9.33 release. Since the checkpoint,
  tracked changes are only README and versionCode 72 / versionName 1.9.33.
  Exact application, Android/JVM tests and dependency inputs remain identical.
  Restore ZIP, checkpoint evidence and prior complete report receipts verified.
- Reused host evidence: baseline reproduced the missing owner-resolution UI
  deadline; all ten post-fix timeout/late callback/queued callback/cancel/normal
  completion/direct-profile cases passed. This remains a controlled resolver and
  Handler harness, not a ten-minute real upstream network timing measurement.
- The intervening direct deployment already ran the required isolated regular
  emulator regressions on this code. Verified the original instrumentation log
  hash, actual installed/build APK hashes and build commit/tree identity. Owned
  local HTTPS synthetic MP4 completed: 14452 bytes, 12000 ms duration, 320-pixel
  video track and 22 intermediate progress samples, with 99 percent finalizing
  and 100 percent completed UI. MediaStore cleanup, discovery deadline/renderer
  cleanup, cancellation, engine readiness and Wi-Fi restoration passed.
- Other retained checks: 172 JVM tests / 27 suites each Debug and Release, zero
  failures/errors/skips; lint zero errors / 23 warnings; 11 Python packaging
  tests and 56 dependency file hashes pass. Reuse is explicit: no new execution
  of these tests is claimed in this resume. Reviewed retained startup, progress
  and signed x86_64 ready captures, with no visible control clipping.
- Publication distinction: this resume adds ONLY a local completion-log commit
  and local evidence. It performs no push, Release upload, signing, installation,
  operational activation, APK delivery or server restart. The prior separately
  authorized deployment already published v1.9.33 and installed the signed
  x86_64 validation variant. Its existing result is preserved, not reported as
  a new intermediate-cycle deployment. Further publication remains round 20.
- Limits: owner deadline bounds the UI wait, not cancellation of the daemon
  resolver socket. Natural slow upstream, authenticated Instagram and physical
  ARM64 runtime remain untested. The published ARM64 APK cannot be validated via
  this emulator's ARM64 translation; unchanged native libraries and successful
  native x86_64 checks do not prove physical ARM64 behavior. No performance/power
  improvement claim. No new dependency, license, permission or external asset.
- Verified prechange restore is retained; extract separately for inspection,
  never reset active work or data. Dirty shared-context checkout is preserved
  without pull/reset/merge or push; a separate local handoff records completion.
- Next investigation for the coordinator: LoginActivity navigation and renderer
  lifecycle cleanup; resolver socket cancellation remains a bounded future
  investigation. Do not schedule another round here; native coordinator alone
  admits it after this final Telegram message's full delivery receipt.

## 2026-10-31: cycle 15 - investigate login navigation and renderer lifecycle

- Status: no_improvement. Investigation only; no newly reproduced application
  defect and no application code change. This does not end the approved plan.
- Reviewed LoginActivity navigation, generation-bound asynchronous login-state
  callbacks, main-frame versus subresource errors, renderer removal/destruction,
  Activity destruction, readiness timers and pending proxy activation/release.
- New evidence: compiled exact current production WebViewClient, transport,
  save/readiness and destruction methods with the exact LoginPageReadiness class
  in a Java 17 host harness. Fourteen cases pass: unfinished page rejection;
  live positive result; repeated click and negative result; old result after new
  page settles; main-frame network/HTTP failures; subresource errors; untrusted
  page rejection; renderer detach/destroy/timer cleanup and late result; finishing
  Activity; destroyed Activity; live proxy activation; destroy-before-activation
  with asynchronous lease release; renderer loss before proxy activation.
  Existing guards reject stale results. No speculative lifecycle patch selected.
- Limits: Android, proxy and cookie-export effects are controlled doubles in the
  new harness. It does not prove a natural renderer crash, real navigation/JS
  callback timing, actual proxy service closure or successful login/persistence.
  Existing proxy lease is released on Activity destruction; no user-visible fault
  from its retention on the terminal renderer-error screen was reproduced.
- Validation: fresh 172 JVM tests / 27 suites EACH Debug and Release, zero
  failures/errors/skips; 11 fresh Python tests; lint zero errors / 23
  warnings (incremental analysis); isolated x86_64 app/test builds up to date;
  56 dependency artifact hashes verified. These regressions are not improvements.
- Runtime: read-only reverified the prior independently authorized deployment's
  exact application/test/build inputs and original regular-emulator evidence.
  Its owned HTTPS MP4 completed at 14452 bytes / 12000 ms with 22 intermediate
  progress samples. This round performs no new emulator interaction, installation
  or download. That evidence is regression coverage, not new LoginActivity
  Android verification. Physical ARM64 and authenticated upstream remain untested.
- Restore: verified pre-investigation tracked-source ZIP and SHA-256. Untracked
  AGENTS.md preserved. Extract separately for inspection; never reset active work
  or user data. Only this log is committed locally; no performance claim.
- Publication: no source/shared-context push, Release, signing, production
  install/activation, APK/LAN delivery/server or duplicate report send. Existing
  v1.9.33 asset, remote main and installed package hashes/times are unchanged.
  Final publication remains round 20. No dependency/license/permission/asset
  change. Dirty shared-context checkout preserved without pull/merge/reset/push.
- Next investigation: AuthCookieStore atomic session export failure recovery and
  preservation of an existing valid session, using synthetic input; independently
  reproduce a fault before changing code. In-flight owner-resolver socket lifetime
  remains unverified. Only the native coordinator admits the next round after
  this final Telegram message's full encrypted delivery receipt.

## 2026-10-31: cycle 16 - investigate cookie export failure preservation

- Status: no_improvement. No newly reproduced application defect; application
  source unchanged. This investigation does not terminate the approved plan.
- New area: AuthCookieStore export transaction and existing-session preservation,
  with exact current production AuthCookieStore and NetscapeCookie compiled into
  a Java 17 host harness. Only synthetic cookie fixtures were used; no real
  user session, browser login, credential or authentication material was read.
- Twenty host cases pass: first export and replacement, start/I/O/runtime
  failure with and without a prior file (six cases), unconfirmed prior-session
  reuse without browser reads, guest rejection, confirmed empty export preserving
  the previous file, ordinary and detailed cookie-reader failures, metadata
  scope/HttpOnly preservation, invalid/expired/cross-domain rejection, existing
  YouTube/X/Instagram auth exports, selected-platform clear, missing/short files.
- Existing guards preserve previous bytes on controlled failed writes, propagate
  exceptions, do not advertise failed first saves and retain trusted manual
  confirmation requirements. No speculative transaction or auth-policy change.
  Existing non-masked-platform stale-cookie invalidation is left unchanged.
- Limits: file I/O is real Java, but Android CookieManager, feature support and
  AtomicFile are controlled doubles. This does NOT prove Android AtomicFile
  fsync/rename behavior, power-loss/process-death durability, legacy .bak recovery,
  actual WebView cookie reads, disk exhaustion or authenticated website behavior.
  Unverified backup recovery is a future investigation, not a confirmed defect.
- Fresh validation: 172 JVM tests / 27 suites EACH Debug and Release, zero
  failures/errors/skips; 11 Python tests; lint zero errors / 23 warnings
  (incremental analysis); isolated x86_64 app/test incremental builds; 56 actual
  dependency artifact hashes verified. These regressions are not improvements.
- Runtime: reverified prior exact application/test/build inputs and retained
  regular-emulator runtime/download evidence read-only. The prior owned HTTPS
  synthetic MP4 completed at 14452 bytes / 12000 ms with 22 intermediate samples.
  No new installation, emulator interaction or download in this round, and no
  new Android cookie-persistence, physical ARM64 or real-login verification.
- Restore: verified pre-investigation tracked-source ZIP and SHA-256; untracked
  AGENTS.md preserved. Extract separately for inspection; never reset active
  work or user data. Only this investigation log is committed locally.
- Publication: no source/shared-context push, Release, signing, production
  install/activation, APK/LAN delivery/server or duplicate report send. Existing
  v1.9.33 asset, remote main, installed package hashes/times remain unchanged.
  Final publication stays round 20. No dependency/license/permission/asset
  change or performance claim. Dirty shared-context is preserved without pull,
  merge, reset or push; a separate local handoff records this investigation.
- Next area: real Android AtomicFile legacy backup/recovery and explicit logout
  cleanup under interrupted writes, with isolated synthetic files and a suitable
  API-level environment. First reproduce a user-visible fault before patching.
  In-flight owner-resolver socket lifetime also remains unverified. Only the
  native coordinator admits the next round after full final-message receipt.
