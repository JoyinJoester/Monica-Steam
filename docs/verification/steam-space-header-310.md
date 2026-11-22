# Steam Space floating header and cached achievement icons · 1.0.310

Verified on 2026-11-21. Unreleased code18; no commit, push, release, or APK delivery.

## Result

Steam Space uses an independent 48dp back control and a rounded title/filter/refresh capsule. The title follows the current theme and system font scale, with two lines available on narrow layouts. Active filters expose selected semantics and a small theme-colored highlight; loading and refresh show progress and disable the original refresh action. Recent activity and publishing use a smaller hierarchy with more separation from the previous-feed notice.

A Space-scoped host records only the feed and draws the header above it. M3E uses the shared FILTER Gaussian frost; Liquid Glass uses the shared blur and refraction. Feed padding, pull-to-refresh progress and the return-to-latest chip clear the measured header, including the playing-friends region. The chip is an independent overlay, preserving its material without sampling itself. The playing header retains its single collapse progress, native gestures and clickable compact avatars.

Fresh achievement parsing already normalized retired Steam image routes. The Space rendering path did not normalize URLs retained in cached events, which could keep using an old route despite a parser update. Rendering now resolves the URL too, reads the corrected image-cache key and keys image state by the resolved address. A filename is resolved only when a single game identifies its owner; unavailable images retain the trophy. This identifies and repairs a source gap, without claiming the supplied user's device was reproduced. SteamDatabase's current webui/service_usernews.proto confirms icon is field4; the protocol field mapping was unchanged.

## Validation

- Main and device test APK build04: 90.647s, 87 Space JVM methods across five suites, zero failures/errors/skips. Build05: 29.848s after the image-query fixture correction; production and main APK unchanged, JVM result reused.
- Final device03: 23/23 native methods, runner 49.663s. Existing 18 cover inbox entry, playing-header drag/settle/reduced motion, account changes, paging, filters, publishing, images and previews. Five new methods cover both materials' callbacks and live backdrop color, busy/selected semantics, 320dp/1.5-font clearance and a cached old achievement URL loading the actual decoded fixture bitmap through its canonical cache key.
- The image test queries the Image in the card's unmerged semantics and checks its rendered RGB pixel, so a label or trophy alone cannot satisfy it.
- Native M3E, Liquid Glass, large-font, refresh, playing-header and achievement screenshots inspected. Native page/components use synthetic fixtures, not a full signed-in root Activity or a real account feed. The decoded icon fixture is synthetic.
- Local editable Canvas JSON and full docz URL retained under ignored docs/design/20261121-space-header; normal and narrow single-page Canvas previews inspected before native edits. Native screenshots remain local and ignored.
- Only existing MuMu1/API35/x86_64 was started. Original MuMu0 preserved; own suffix packages removed, user identities, configuration, display settings and original packages checked. Task-started MuMu1 stopped; existing data disk retained.

## Prior attempts retained

Build01, build02 and build03 succeeded; test inputs were adjusted for the new full-viewport feed and the new-activity chip was retained in the independent overlay. Device01 passed the original 18/22 but four new tests failed because enableEdgeToEdge ran on the instrumentation thread. The fixture now invokes it through ActivityScenario.onActivity. Device02 passed 22/23; the image node query timed out because the card merges child semantics. Device03 uses the unmerged image query, retains the bitmap pixel assertion and passes all23. Original logs and frozen APK hashes remain in the local task directory.

No real account writes, account imports, publishing, cart, wishlist, purchase or payment operations. No physical device, OEM, older Android hardware, R8 or live account reproduction. The pre-API31/effects-disabled treatment was checked through the effects gate only.

Main APK SHA256: c9d10bb294bd8684fd9378d67c584b83f65ea0f7e5f42afb27d5ab992c56d5f1

Test APK SHA256: ecce61b864e410022507b4f40350e0803c4d54a3ea9dc7719785fb26dd4ea82a
