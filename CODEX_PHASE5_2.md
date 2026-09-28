# Phase 5-2 implementation notes

Phase 5-2 applies the complete Phase 5-1 correction list. Items marked below as
device checks are implemented mitigations, not claims that the affected live
site has already been verified.

## Player

- Uses a 1 GiB process-wide Media3 disk cache for progressive and HLS streaming.
- Keeps up to five minutes buffered ahead and two minutes behind when the source allows it.
- Shows one seek bar only; its secondary progress represents the buffered range.
- Debounces seek-preview extraction, caches generated thumbnails, and rejects likely green/corrupt frames.
- Supports pinch zoom from 1x to 4x and one-finger pan while zoomed.
- Ctrl + horizontal slide calculates frame movement without repeated seeks. It performs one seek on release and does not open the player menu or replace the current playback image while dragging.
- Escape returns to the browser.
- Background playback remains disabled by default.
- The custom controls reserve the bottom system-bar inset.

## Browser and tabs

- Compact tabs can be closed with a short right swipe.
- Selecting a compact tab scrolls the tab list to that item.
- App settings include an explicit exit action that closes every tab in the active profile and removes its saved session.
- Multi-instance manifest properties and document launch flags are enabled for supported large-screen Android versions.
- Desktop mode injects a scalable desktop viewport and enables WebView zoom controls.
- Cleartext HTTP is restricted to `mi-glamu.com` and its subdomains; other hosts remain HTTPS-only.
- Cloudflare, reCAPTCHA, and hCaptcha authentication resources bypass content filtering.
- Authentication/interstitial pages also skip cosmetic filtering, media detection, and media-gating injection.
- The launcher and browser activities both opt into document-style multi-instance tasks; opening a profile creates a unique window session.
- Background tabs use a `play` event guard instead of a 250 ms polling timer, reducing steady WebView work.

## Downloader and media library

- Direct media size estimation uses HEAD, then a byte-range fallback.
- HLS estimation reads the playlist, chooses the highest-bandwidth variant, and estimates from byte ranges or declared bandwidth and duration.
- Stream tiles use a full-screen adaptive grid with long-press multi-selection and deletion.

## Validation

- YouTube script simulation: 11 cases passed.
- AndroidManifest.xml and network security XML parse successfully.
- A local Android compile was not possible because this source bundle does not include the Gradle wrapper. The included GitHub Actions workflow remains the build path.

## Device checks still required

- Cloudflare/reCAPTCHA completion on the affected sites.
- Login and HLS detection on `mi-glamu.com`.
- Split-screen behavior on the target Samsung/Android build; the OS may still enforce device-specific multi-instance restrictions.
- Long remote HLS seeks, resume from saved position, frame stepping, pinch zoom, and pan.
- Cloudflare/reCAPTCHA behavior must be judged on the same URLs and login state that originally failed; no local test can reproduce those server decisions.
