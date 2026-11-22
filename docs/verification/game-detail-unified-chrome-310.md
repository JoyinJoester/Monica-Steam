# Steam game detail unified navigation — 1.0.310 (unreleased)

Date: 2026-11-20.

The game detail page now has one navigation row matching the requested sketch: a round Back button at the far left and a content-width, fully rounded Store / My stats pill. Workshop joins that pill only after the existing Steam category check confirms support. The draggable action toolbar is restored for purchase options, the official store, reviews and sharing.

## Implementation

- `SteamGameDetailChrome.kt` supplies the shared header and page host. The 48 dp targets preserve touch accessibility while removing the duplicate title row. The pill keeps unused horizontal space and scrolls when larger text needs more room.
- `SteamGameDetailTabs.kt` retains independent scroll and saved state, account/game isolation, the existing statistics gateway, and native Workshop content. Unknown Workshop support is retried on a later section visit. Workshop passes its own Back callback into the common header so its internal selection/preset navigation still works.
- `SteamGameDetailSectionHost` records the body separately from the overlay. The glass header and toolbar sample the actual page; the selection lens also records the pill shell and labels, without sampling itself. Existing press, drag, release, cancellation, reduced-motion and fallback handling are reused.
- `SteamStoreDetailActionToolbar.kt` reuses the historical toolbar implementation present in commits `924601c` and `123a77c`. It keeps dragging, edge attachment, saved position and 3.5-second automatic collapse. Actions use 48 dp targets. The same container hosts Workshop download/menu actions.
- `SteamStoreScreen.kt`, `SteamStoreDetailHeader.kt` and `SteamGamePersonalStatsContent.kt` remove the shared detail page's duplicate header/Workshop entry, add the game name to the body, and reserve the measured header height. The standalone store header remains compatible.
- `SteamWorkshopScreen.kt` replaces the root title bar with the common game header when embedded; item-detail subpages retain their existing navigation. Returning between sections keeps the current query's scroll position.

Seven production files, three test files and two release-note files were changed. All visible labels reuse translated string resources. This task does not change the reviews endpoint, authentication or storage behavior.

## Validation

Environment: existing MuMu instance 1, Android 15 / API 35, x86_64. A task-only application ID suffix `.detailchrome` isolates installation from the user's existing apps. The task init script enables debuggable Debug without minification; production Gradle settings were not changed.

- Build05 assembled main/test APKs and passed all 17 focused JVM checks in 4m09s: six detail interaction guards, six personal-statistics tests and five toolbar geometry tests.
- Build06 rebuilt only the instrumentation APK in 2m29s after correcting an old lazy-list test's scroll action. Production source hashes and the installed `main05.apk` stayed unchanged during testing. Final whitespace checking removed spaces from one empty line in `SteamWorkshopScreen.kt`; all non-whitespace content remains identical to the tested source.
- All 39 distinct device methods have passing results on the fixed main APK. **The combined batch was not consistently green.** Final combined device05 passed 38/39 in 76.531s; the complete price class then passed 6/6 in device06, 11.330s, with the exact same main and test APKs and no source/assertion changes.
- In device05, all 14 detail-tab tests and all 19 liquid-glass gesture tests passed. Coverage includes the single-row/content-width layout, unsupported/supported Workshop tabs, the real Workshop screen with a synthetic gateway, Back behavior, share/official/review actions, toolbar drag/collapse, independent scroll/filter state, stale-account rejection, dark theme, 360 dp viewport and 1.5 text scale. Gesture coverage includes held-lens pixels, cancellation, RTL, cached page return, edge scrolling and fallback behavior.

The unresolved combined-run issue is the legacy price fixture waiting five seconds for its cached header-image semantics after preceding detail tests. It passed in device02, standalone device04 and the complete price class device06, but failed in combined device03/device05. A separate cached-filter return drag failed once in device03, then passed unchanged in device04/device05. Neither issue was explained conclusively or hidden by relaxing assertions; they remain test/reproducibility limitations.

### Preserved attempts

| Attempt | Result |
| --- | --- |
| build01 | Production Kotlin passed; stopped only the task's verified daemon during slow non-debuggable D8 after sources had changed. Not a final build. |
| build02 | PowerShell split the JVM argument; failed in 14s. Corrected by quoting the whole argument. |
| build03 | Main APK built; test compilation saw a header signature changed during the build. Failed after 7m56s; source was then frozen. |
| build04 | Main/Kotlin test compilation passed; old Java Compose renderer lacked two new optional parameters. Failed after 2m07s; only the renderer adapter was fixed. |
| build05 | Main, instrumentation and 17 JVM checks passed, 4m09s. |
| device01 | ADB stalled at pre-install `pm list users`; no installation/test result. Restarted only the task-started instance. |
| device02 | 38/39, 71.022s. All new tests passed. Old scroll-retention test used `performScrollTo` on an uncomposed lazy item at 360 dp. Changed the action to parent `performScrollToNode`, preserving visibility/return-position assertions. |
| build06 | Final instrumentation APK passed, 2m29s. Main APK frozen at build05. |
| device03 | 37/39, 76.587s. All 14 detail tests passed; cached header-image timeout and cached-filter return drag failed. |
| device04 | Both failed methods passed unchanged, 2/2, 7.797s. |
| device05 | 38/39, 76.531s. Detail14 and glass19 passed; same cached header-image timeout remained. |
| device06 | Complete price class passed unchanged, 6/6, 11.330s. |

APKs used for final runs:

| Artifact | SHA-256 |
| --- | --- |
| main05.apk | `a3dfc9a22d531cc4916c44c3e16bb505432c1602a94f0d8eab835ac349d0a14b` |
| test06.apk | `239bfaeecf1238a709b643ed026c506edb25e0356b41145b792f9a5ae9184127` |

## Visual evidence and cleanup

The editable local M3E Canvas was loaded and previewed before native implementation. Its JSON, full local URL and preview remain in ignored `docs/design/20261120-detail-unified-chrome/`. Native light, dark held-glass, Workshop and dragged-toolbar screenshots were inspected; the overview is composed from actual device02 screenshots of the same fixed main APK. No screenshot or Canvas asset is added to Git.

Only the two `.detailchrome` application packages were uninstalled. The original users 0/10/11/12, current user, installed package set, resolution, density, font scale and rotation setting match the baseline. Task-started MuMu1 was shut down and its stopped state verified. MuMu0 was not modified, and no emulator configuration or data disk was deleted.

Text receipts, source hashes and JVM XML are in `game-detail-unified-chrome-310-evidence/`. Full build/failed-attempt logs, before snapshots and the task-only patch remain in `.codex-tasks/20261120-steam-detail-unified-chrome/` at the workspace root.

These are synthetic native-component tests on MuMu, not physical-device/OEM-GPU, production-account, complete Activity-route or R8 validation. No commit, push, release or APK delivery was performed.
