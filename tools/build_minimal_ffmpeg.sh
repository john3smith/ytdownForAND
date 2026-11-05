#!/usr/bin/env bash
# Minimal Android runtime. Rebuilt binaries require integration/TLS validation
# and an explicit update of docs/minimal-ffmpeg-binaries.json before packaging.
set -euo pipefail
: "${ANDROID_NDK_HOME:?Set Linux Android NDK root}"
: "${FFMPEG_SOURCE:?Set extracted FFmpeg source directory}"
: "${OPENSSL_SOURCE:?Set extracted OpenSSL source directory}"
: "${LIBXML2_SOURCE:?Set extracted libxml2 source directory}"
: "${X264_SOURCE:?Set x264 source directory}"
abi=${1:?arm64-v8a or x86_64}
out=${2:?absolute output directory}
case "$abi" in
 arm64-v8a) arch=aarch64; triple=aarch64-linux-android; ssl=android-arm64;;
 x86_64) arch=x86_64; triple=x86_64-linux-android; ssl=android-x86_64;;
 *) echo 'Unsupported ABI' >&2; exit 2;;
esac
mkdir -p "$out/openssl-build" "$out/ffmpeg-build"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
export PATH="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
cd "$out/openssl-build"
"$OPENSSL_SOURCE/Configure" "$ssl" -D__ANDROID_API__=24 no-shared no-tests no-apps --prefix="$out/openssl" --libdir=lib
make -j4 >build.log 2>&1
make install_sw >>build.log 2>&1
cmake -S "$LIBXML2_SOURCE" -B "$out/xml-build" \
 -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
 -DANDROID_ABI="$abi" -DANDROID_PLATFORM=24 -DCMAKE_INSTALL_PREFIX="$out/xml" \
 -DBUILD_SHARED_LIBS=OFF -DLIBXML2_WITH_PYTHON=OFF -DLIBXML2_WITH_PROGRAMS=OFF \
 -DLIBXML2_WITH_TESTS=OFF -DLIBXML2_WITH_ICONV=OFF -DLIBXML2_WITH_ZLIB=OFF -DLIBXML2_WITH_LZMA=OFF
cmake --build "$out/xml-build" -j4 >"$out/xml-build.log" 2>&1
cmake --install "$out/xml-build" >>"$out/xml-build.log" 2>&1
mkdir -p "$out/x264-build"
cd "$out/x264-build"
CC="${triple}24-clang" AR=llvm-ar RANLIB=llvm-ranlib STRIP=llvm-strip \
 "$X264_SOURCE/configure" --host="$triple" --enable-static --enable-pic --disable-cli \
 --prefix="$out/x264" --extra-cflags=-Os >configure.log 2>&1
make -j4 >build.log 2>&1
make install >>build.log 2>&1
export PKG_CONFIG_LIBDIR="$out/xml/lib/pkgconfig:$out/x264/lib/pkgconfig:$out/openssl/lib/pkgconfig"
cd "$out/ffmpeg-build"
"$FFMPEG_SOURCE/configure" --target-os=android --arch="$arch" --enable-cross-compile \
 --cc="${triple}24-clang" --cxx="${triple}24-clang++" --ar=llvm-ar --ranlib=llvm-ranlib --strip=llvm-strip \
 --prefix="$out/install" --disable-autodetect --enable-shared --disable-static \
 --disable-doc --disable-debug --disable-ffplay --disable-avdevice --disable-postproc \
 --disable-everything --enable-ffmpeg --enable-ffprobe --enable-small \
 --enable-version3 --enable-gpl --enable-openssl --enable-libxml2 --enable-libx264 --enable-network --pkg-config-flags=--static \
 --extra-cflags="-Os -I$out/openssl/include -I$out/xml/include" --extra-ldflags="-L$out/openssl/lib -Wl,-z,max-page-size=16384" \
 --enable-protocol=file,pipe,http,https,tcp,tls,crypto,data \
 --enable-demuxer=mov,matroska,webm_dash_manifest,hls,dash,mpegts,aac,mp3,ogg,flac,wav,concat \
 --enable-muxer=mp4,ipod,mov,matroska,webm,mp3,adts,ogg,flac,wav,null \
 --enable-parser=aac,aac_latm,mpegaudio,opus,vorbis,flac,h264,hevc,av1,vp9 \
 --enable-decoder=aac,aac_latm,mp3,opus,vorbis,flac,alac,pcm_s16le,pcm_s24le,pcm_s32le,pcm_f32le,ac3,eac3,h264,hevc,av1,vp8,vp9,mpeg4 \
 --enable-encoder=aac,libx264 --enable-filter=aresample,aformat,anull,atrim,asetpts,scale,format,null \
 --enable-bsf=aac_adtstoasc,h264_mp4toannexb,hevc_mp4toannexb,extract_extradata,vp9_superframe,av1_metadata \
 >configure.log 2>&1
make -j4 >build.log 2>&1
make install >>build.log 2>&1
sha256sum "$out/install/bin/ffmpeg" "$out/install/bin/ffprobe" > "$out/SHA256SUMS"
# Never accept a silently disabled feature as a deployable build.
for feature in HTTPS_PROTOCOL TLS_PROTOCOL HLS_DEMUXER DASH_DEMUXER AAC_ENCODER MOV_DEMUXER MP4_MUXER; do
    if ! grep -q "#define CONFIG_${feature} 1" config_components.h; then
        echo "RELEASE BLOCKED: missing $feature; inspect configure.log" >&2
        exit 3
    fi
done
