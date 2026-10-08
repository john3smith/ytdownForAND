# v1.9.17 audio-only implementation and verification

## Behavior

- `음원만 추출 (M4A)` uses local SharedPreferences, default `false`.
- Every normal, shared and account task captures an immutable `audioOnly` flag.
  Account child tasks inherit the parent's flag rather than the current switch.
- Audio format: `bestaudio[ext=m4a]/bestaudio/best[acodec!=none]`.
- Existing yt-dlp and FFmpeg dependencies handle `--extract-audio --audio-format m4a`.
  Native M4A is preferred; muxed fallback is extracted; `--no-keep-video` avoids
  retaining a newly downloaded intermediate video. No existing video/photo path
  is overwritten because audio output names have a separate `-audio` suffix.
- Audio mode bypasses the social photo/direct-video saver and photo fallback.
  Photo-only or audio-free media cannot produce a successful audio result.
- Success validates JSON-escaped final `after_move` paths inside the output
  directory, non-empty final M4A files, and the audio filename suffix.
  Thumbnail images and incomplete/intermediate files cannot count as success.
- Progress output can contain standalone carriage returns; parsing supports CR,
  LF and CRLF and deduplicates final paths, including unchanged repeat downloads.

## Tests on 2026-10-09 (Seoul)

- Gradle ARM64 and x86_64 debug builds succeeded; Android lint succeeded.
- 56 JVM unit tests, zero failures/errors. Includes audio/video selector routing,
  unique output names, default video tasks, immutable queued audio flags, JSON
  paths with Unicode, CR progress output, duplicates, malformed/missing output,
  outside-directory paths, thumbnails, empty files and intermediates.
- Regular API 35 emulator: existing-data-preserving install; toggle initially OFF;
  ON disables video quality; value remains ON after stopping/relaunching app;
  switching OFF restores video-quality selection.
- Actual shared YouTube URL: official Blender Foundation Big Buck Bunny,
  `https://youtu.be/aqz-KE-bpKQ`. Initial extraction generated the M4A but exposed
  a completion-parser CR issue, fixed and regression-tested. Repeating the request
  then correctly reported one audio file and successful completion.
- Host ffprobe verified the extracted file: AAC audio stream only (no video),
  634.624580 seconds, 10,264,232 bytes. Verification files are ignored/local only.
- No login cookies, credentials, recordings or user media are included in Git.

## Remaining verification boundaries

YouTube audio output was tested end-to-end. X/Instagram authenticated account
discovery was not repeated for this change; their mode propagation is covered
by the task structure/unit checks, not a claim of fresh account download success.
Native M4A was used in the live test; the muxed/non-AAC FFmpeg fallback uses the
existing bundled library but was not exercised with a separate live site fixture.

## References

- https://github.com/yt-dlp/yt-dlp#post-processing-options
- https://github.com/JunkFood02/youtubedl-android
