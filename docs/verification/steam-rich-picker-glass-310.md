# Steam rich-media picker glass (unreleased 1.0.310/code18)

The panel previously painted opaque backgrounds in both its Compose host and native emoji grid. In Liquid Glass mode it now samples the independent native message list with 18dp Gaussian blur and a 48% dark/60% light theme tint. Native emoji and category icons remain sharp and transparent. Unsupported effects and M3 retain readable opaque backgrounds. The composer passes its source view for both private and group chat.

The footer reuses SteamGlassSelectionRow with independently recorded page, shell and labels. A sibling shell permits the held lens to rise beyond its rim; 48dp targets and a 380dp maximum width avoid edge clipping on wide screens. Holding magnifies the glass and text; horizontal dragging from any category commits on release; cancelling restores the previous selection. Reduced motion, large text and RTL remain usable. The grid reserves 68dp for this footer. Search reset rules, recent emoji, refresh, close and all four pages are preserved.

Native screenshots exposed a shared sampling defect. Miuix records into a padded layer, so its DrawScope center included the 14dp sampling margin. SteamGlassSelectionLens now subtracts that margin from the magnification pivot, keeping labels centered.

## Final validation

- Build03 produced the final main/test packages in 62s; test-only04 and test-only05 repacks took 30s and 31s. Main APK bytes stayed unchanged during repacks. Exact source/APK hashes are retained.
- 45 JVM methods passed: 29 rich-media model, 8 view-model and 8 selection-motion methods.
- Dedicated final batch: 8/8 API35/x86_64 MuMu methods passed in 14.237s, with an isolated `.richpickerglass` package and synthetic data.
- Regression batch: 29/29 passed in 55.432s: 24 inbox filter methods, 2 game-detail tab methods, 2 native card-blur methods and the group composer draft/panel/channel path.
- Fine native stripe contrast was 9 in dark and 6 in light. Changing the source from black to white changed the panel by 132.85/101.85 brightness levels. Opaque fill or tint alone cannot satisfy both checks.
- Held glyph geometry grew from 54 × 22 to 65 × 24 pixels, with center drift (−0.5, 0) pixels. Raised rims counted 727 pixels in the actual panel and 1450 in the flat fixture. The tests check glyph width/area growth, retained readable pixels, centered text, release, cancel, actual panel dragging and native callbacks.
- Dark/light Gaussian backgrounds, held/released lens screenshots, RTL/large/reduced and fallback states were inspected. Editable local M3E Canvas was saved and previewed before implementation. Canvas JSON, its full loopback share URL and screenshots remain in ignored `docs/design/20261121-rich-picker-glass`; tracked documents contain no ignored image embeds.

## Earlier failures retained

Build01 succeeded; device01 had 5/8 failures: a native search editor was queried through Compose semantics, source screenshots were one frame old, an excessively stretched footer clipped the raised lens, and a central ellipse excluded enlarged English glyphs. Build02 succeeded; device02 had 3/8 failures including actual upper-left label drift and a native-view check before recomposition. Build03 fixed the shared pivot. Device-final with test04 passed 6/8; the remaining failures counted pixels above a brightness threshold as a direct size measure. Shader resampling changes that count. Test05 measures visible glyph width and area, retained readable pixels, center drift and raised rim. Final 8/8 and regression 29/29 passed; all earlier logs and test APKs remain preserved locally.

A test edit overlapped build03 compilation. APK inspection confirmed its final center assertion was absent; test-only04 explicitly rebuilt it, and test-only05 rebuilt the final geometric checks. No production source changes followed build03.

## Scope and cleanup

Only synthetic API35/x86_64 Debug verification is claimed: no physical phone, OEM compositor, R8, pre-31 device, real Steam account or network catalog verification. Runtime fallback was exercised with effects disabled. Pixel checks used a native synthetic source view; the full group composer path was tested with offline data.

Existing MuMu1 was started after main packaging. Original users, packages, settings and data disks were preserved; only task-owned packages were removed and only the instance started by this task was stopped. Pre-existing MuMu0 was preserved. All baseline checks passed; see cleanup.json. No commit, push, release or APK delivery.
