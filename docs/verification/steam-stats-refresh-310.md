# Steam personal statistics and refresh controls — 1.0.310

Date: 2026-11-21. Development version remains 1.0.310 / code 18. No commit, push, release or APK delivery was performed.

## Result

Personal statistics uses an artwork/Steam-avatar header, compact playtime metrics, continuous achievement rows, friend playtime, game badges/trading cards and game-specific Points Shop previews. The latest visual correction puts the player's time and friends' average on **one shared axis**, distinguished by theme colors and marker size. Readable friend times use the same scale; equal values remain at their actual position. Achievement completion uses a thicker determinate wave. Redundant separator lines and repeated achievement text are removed. Unknown/private data remains unknown, rather than being represented as zero.

Refresh feedback uses the same Material 3 morphing LoadingIndicator as Nearby Sync. Chat and library attach it to their existing overflow action. The action retains its hit target and menu access while refresh is active; only a duplicate refresh command is disabled. Native Telegram headers retain the ImageView's accessibility and interaction while replacing its visible glyph. Space, community, profile/list pages, screenshots, Points Shop, freebies, notifications and token-root refresh actions share the component. Real operation progress, paging and empty-state loading are not removed. The store homepage is byte-identical to the task baseline. Unused legacy chat-list components have no production callers and were not reworked.

Earlier shared Dock selection, card-preview expansion and persistent game-detail navigation work is preserved. The device suite includes actual intermediate-frame movement of the game-detail navigation in M3E and glass modes.

## Data and protocol checks

- Friends gameplay uses IPlayerService.GetFriendsGameplayInfo, with the authenticated player's fixed64 SteamID verified against the selected account. Player categories 2–5 are deduplicated; wishlist category 6 does not contribute to the average. Missing, out-of-range and private times are distinct from confirmed zero. Profile names/avatars use one bounded batch.
- Badge/trading-card parsing checks recognizable Steam badge markup. Ownership requires explicit owned markup and rejects unowned. Generic login/private HTML is rejected. Public badge pages were inspected, including a page with nine card entries.
- Points Shop requests optionally filter by app ID. Existing global queries retain their previous behavior. A public request for app 620 returned eResult 1 and 12 definitions, all with app ID 620. The initial local summary accidentally called definition IDs app IDs; the summary was corrected using definition field 1. The production parser already used field 1 correctly.
- Extra sections load independently. Account/game/source changes cancel previous work, and even a non-cooperative late callback cannot publish into the new selection. Existing authentication refresh/retry is reused.
- Thirteen new strings are present in all five supported resource configurations (65 values).

## Final validation

The existing MuMu 0 instance was reused, Android 35 / x86_64, serial 127.0.0.1:16384. Only isolated packages takagi.ru.monica.steamapp.statsrefresh and its test package were installed. The existing user application was not replaced.

| Check | Final result |
| --- | --- |
| Debug app, instrumentation package and selected JVM tests | build07 succeeded; Gradle 1m27s, driver 88.214s |
| JVM tests | 18 passed, 0 failed/errors/skipped: extras 7, personal statistics 7, Points Shop protocol 4 |
| MuMu instrumentation | device04: 27 passed, runner 42.089s, driver 44.656s |
| Device test classes | Statistics/refresh 8, game-detail tabs 17, existing refresh behavior 2 |
| Visual inspection | Final native light, dark/glass, large-type, collectibles and achievements screenshots; chat/native menu and shared refresh-control fixtures |

The tests verify actual avatar bitmap pixels, game-specific links, hidden achievements, menu actions, pull refresh, unchanged menu bounds, in-place shape motion, static reduced-motion rendering, and existing game-detail navigation/scroll/account behavior. Screenshots of the header and comparison show the final single axis and wave.

Public Portal 2 artwork and a public Steam avatar were used only as image fixtures; account names, friend times, badges and reward examples in native screenshots are synthetic. Windows binary stdin transfers truncated these JPEGs in device02/device03. Those images do **not** prove correct image loading. The final driver uses adb push and verifies SHA-256 after copying into the isolated package; device04 renders both images completely. Task-owned temporary device files are removed immediately after copying.

Final APK SHA-256 values:

- Main: 84268d364958912a28236b0f44afff6a928b0dd9bf651d958925ca85c6ce98f7
- Instrumentation: adc97b3d0e199bf7fea4003a6e8c4138857de79937544548c80b38005499fe14

## Earlier failures retained

build01/build02 failed due to integration variable/import/argument errors. build03 packaged successfully but had 2 of 14 JVM failures, exposing the fixed64 SteamID parsing error; this was fixed and the final 18 tests pass. device01 stopped before instrumentation because a fixture directory was absent. device02 passed 3 of 5 tests and device03 passed 25 of 27. Incorrect test menu locators and Compose's automatic infinite-animation cancellation caused the remaining failures. The final motion test sets a manual clock before composition and retains pixel-change assertions; reduced motion is separately required to remain pixel-identical. No failing assertion was waived. Earlier logs remain under the local task directory.

## Evidence and limits

Text evidence is in steam-stats-refresh-310/ alongside this report. Raw build/device receipts, source hashes and earlier failures remain in the local .codex-tasks/20261121-steam-stats-refresh directory. Editable Canvas JSON, the local import URL and all native screenshots remain only in ignored docs/design/20261121-stats-refresh. The final Canvas single-screen preview was inspected.

This verifies synthetic MuMu API35/x86_64 Debug behavior and public image/API responses. It does not establish authenticated live friend-account/badge behavior, every page on a physical device, OEM behavior or R8 builds. The shared refresh component is exercised through representative production components, not every network page end to end. Existing user packages, users, font/display settings, MuMu instance and data disk are preserved; cleanup removes only the two isolated test packages.
