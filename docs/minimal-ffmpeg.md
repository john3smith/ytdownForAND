# Minimal FFmpeg runtime (YTDown 1.9.42)

Built from FFmpeg 7.1.2, OpenSSL 3.5.4, libxml2 2.14.6, and x264 commit
0480cb05fa188d37ae87e8f4fd8f1aea3711f7ee with Linux Android NDK r27c, API 24.
The combined FFmpeg executable is GPL-3.0-or-later. No --enable-nonfree.
Upstream source archives are included unchanged in the matching source asset.

## Rebuild
Extract the source archives. Obtain Android NDK r27c from Google. Set
ANDROID_NDK_HOME, FFMPEG_SOURCE, OPENSSL_SOURCE, LIBXML2_SOURCE and X264_SOURCE
to their absolute directories. Run tools/build_minimal_ffmpeg.sh for arm64-v8a
and x86_64, each with a separate absolute output directory. Linux requires
make, cmake, pkg-config, nasm and perl. The build script contains all configure
options. Copy install/bin/{ffmpeg,ffprobe} to native-ffmpeg/<ABI>/.

Alternatively unpack the matching ffmpeg-runtime.zip at the repository root.
The AAR packaging step checks architecture and exact hashes against
minimal-ffmpeg-binaries.json. Rebuilt binaries may differ due to build paths or
tool versions; validate them before explicitly updating the manifest. Do not
bypass the check by renaming an unrelated binary.

## Scope
Keep MP4/M4A muxing, HLS/DASH input, TLS certificate validation, AAC encoding,
H264 encoding, common video/audio parsers and stream-copy support. Exclude
AV1 encoders (libaom/SVT-AV1/rav1e), libvpx/x265 encoding, libsamplerate,
unused device/filter/output formats. AV1/VP9/HEVC stream-copy does not require
those encoders. This is an app-specific runtime, not a general FFmpeg package.

## Validation
Both release ABIs compile and unit tests pass. Installed x86_64 production
engine passed actual HTTPS transfer of owned synthetic fixtures, separate
video/audio MP4 merge, Opus-to-AAC M4A conversion, HLS and DASH over HTTPS,
H264 transcode, AAC extraction and rejection of untrusted TLS certificates.
Existing FFmpeg files migrated to the new static runtime without clearing app
user data. ARM64 device execution is not tested on this x86_64 emulator.
External website availability/authentication is distinct from media processing.

Actual YouTube UI download also completed: a 19.063583-second MP4 containing
AV1 video and AAC audio (533,915 bytes). This verifies AV1 stream-copy without
shipping an AV1 encoder. A previously used test video was unavailable; that
external unavailable result was not counted as a successful download.

YouTube UI M4A extraction also completed: AAC, 19.063583 seconds,
309,156 bytes. The final signed x86_64 APK was installed preserving data.

## Source checksums
FFmpeg archive was compared with a fresh HTTPS download from ffmpeg.org/releases.
OpenSSL matched www.openssl.org/source/openssl-3.5.4.tar.gz.sha256.
libxml2 matched download.gnome.org/sources/libxml2/2.14/libxml2-2.14.6.sha256sum.
x264 was cloned from code.videolan.org/videolan/x264.git at the commit above.

- ffmpeg-7.1.2.tar.xz: 089bc60fb59d6aecc5d994ff530fd0dcb3ee39aa55867849a2bbc4e555f9c304
- openssl-3.5.4.tar.gz: 967311f84955316969bdb1d8d4b983718ef42338639c621ec4c34fddef355e99
- libxml2-2.14.6.tar.xz: 7ce458a0affeb83f0b55f1f4f9e0e55735dbfc1a9de124ee86fb4a66b597203a

## v1.9.42 optimization
Icons use lossless WebP; all decoded RGBA pixels match the PNG originals.
FFmpeg and ffprobe share six FFmpeg libraries in their existing private runtime
package; the library search path is unchanged. Copy install/lib/*.so alongside
install/bin/ffmpeg and ffprobe into native-ffmpeg/<ABI> when rebuilding.
Python archive content duplicates are already removed. Remaining OpenSSL and
SQLite libraries have different SONAME/NEEDED dependencies; they are preserved
until their consumers can be rebuilt and tested against a common ABI.
