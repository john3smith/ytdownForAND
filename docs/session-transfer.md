# Beta session-transfer changes

Version: 1.9.24-beta.1 (debug build, versionCode 56).

- The masked entry's WebView cookies use AndroidX WebKit's supported `GET_COOKIE_INFO` feature.
  Preserve Chromium's canonical host-only/domain distinction, path, Secure,
  HttpOnly and expiration. Reject malformed, expired, cross-platform and
  partitioned cookies; the Netscape format cannot safely represent partitions.
- Older WebViews fall back to observed HTTPS host-only session cookies. Expiry,
  HttpOnly and original path cannot be reconstructed in that fallback.
- Other platforms retain their previous export format for compatibility with
  existing direct-media and account-discovery readers.
- Write cookie files atomically, preserving the previous file on write failure.
- Logout expires the observed cookie's exact host/domain and path without
  clearing sessions belonging to other platforms.
- The masked login entry still requires confirmation from the trusted HTTPS
  page. Guest cookies alone do not establish an authenticated account.
- Existing cookie files remain untouched on update. To regenerate their scope
  metadata, open the relevant login entry and confirm an actual logged-in page.
- The masked site's downloader uses the included Chrome transport profile
  without a conflicting WebView User-Agent override. It retains bounded network
  retries and socket timeouts; TLS certificate validation stays enabled.
- Title discovery and download share one cancellable extraction process for
  that entry. Other site extraction and account queue behavior are unchanged.
- Title events and final audio-path events coexist; enabling `--print` does not
  turn the request into a simulation. Existing artifact validation remains.

No automatic CAPTCHA, age/security interstitial confirmation, certificate
validation disabling, or regional access-control workaround is added.

## Verification scope

Pure Java tests cover cookie scope, expiration, malformed input, prefixes,
title events and request options. Build and lint cover both supported build
targets. Real emulator engine checks use a public test video and check nonzero
MP4/M4A outputs, media metadata and video decoding.

Passing public-fixture checks is not proof that an authenticated third-party
site is reachable. Site-specific network/authentication failures must be
reported separately. A successful engine exit without a completed artifact is
never treated as a successful download.

References:
- https://developer.android.com/reference/androidx/webkit/CookieManagerCompat
- https://chromium.googlesource.com/chromium/src/+/refs/heads/main/net/cookies/canonical_cookie.cc
- https://github.com/yt-dlp/yt-dlp#general-options
