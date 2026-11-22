# Monica Steam 1.0.311 interrupted UI work — 2026-11-21

Follow-up on 2026-11-22: user screenshots exposed unresolved game names and original links re-entering Monica's community handler. The earlier tests did not cover an explicitly zero redundant appid or actual Android browser dispatch. The separate `steam-space-game-browser-311.md` report records the correction, final 95 JVM / 35 device checks and retained failures. Earlier receipts below remain historical evidence.

Completed the remaining work from chat `01a1fd4d-0342-7892-b04e-f6c924d69c8c`. Version remains 1.0.311 / code 18. Earlier completed Dock, badge, account-avatar and stats changes remain in place.

- Game invitations and shared-game previews follow Monica's version-and-links layout: Store/Open destination pills, then connected Copy/Message/Delete rows with trailing icons. Large text stacks the main destinations; original card expansion, closing, scrolling and cover continuity remain verified.
- Contact filters measure to their content and start at the left. Contacts use an explicit captured-list glass host with the filter as its overlay, reusing the Dock selection lens and motion. Existing translated All/Online/Playing strings replace the picker literals. Standalone contacts share this setup.
- Space author summaries are independently loaded in one bounded official OAuth summary batch, keyed by exact feed SteamID; absent friendship snapshots no longer determine author display. Unreadable enrichment preserves unknown state; authentication and cancellation still escape. All named activity categories can show app metadata, including achievement events. StoreItem canonical id is accepted when the redundant appid is omitted. The same avatar row remains composed during collapse, while slot width changes from 88 to 48dp and spacing from 12 to 4dp; one scroll behavior continues to own height.
- Personal stats enters the existing native points shop and native reward detail instead of opening a Steam URL. Queries and caches are scoped to the current game. Returning preserves the stats section/scroll; account changes cancel previous balance publication. Only the explicit official-link action opens a web destination.

## Validation

Debug main/test build02 completed in 111.101s; test-only build03 in approximately 41s. Final relevant JVM XML contains 90 tests, zero failures/errors/skips. Final device03 has 32/32 passing methods (runner 52.609s); device04 has 55 passing methods plus one optional skipped public-artwork preview (runner 111.015s). Across both final runs, 87 different methods passed and the main APK hash is identical. The skipped preview requires external public-image fixture files that were absent; it is not counted as a pass. Synthetic artwork and independent author/game rendering were separately inspected.

The full stats → native game shop → reward → shop → stats route passed with all HTTP requests intercepted locally. No real token/account was used. Existing Space collapse/settle/large-text/hit-target checks, actual Dock selector travel, M3E/glass detail-tab transitions, card actions and origin-based card expansion were rerun.

Initial device01 was 26/30; device02 was 6/10. Three inherited text checks compared MultiParagraph's offered container width with Text's smaller intrinsic width. Actual glyph bounds and screenshots showed complete text (e.g. right edge 168.9px inside a 170px text box); the final check tests actual line bounds, retaining one-line, vertical-fit, grouped-layout and 48dp target assertions. The remaining failure used an obsolete English native-menu description; an intermediate Compose lookup matched two message menus. Final test identifies the localized native header menu and preserves the exact trade URL assertion. All failure logs and earlier implementation snapshots remain local.

MuMu0 was already running and was reused on API35/x86_64. Cleanup removed only the inherited task-owned `.gamecardactions` main/test packages. Original package inventory, user identities/flags, current user, display/font settings, instance PID and data disk were retained; no emulator was stopped. No shared AVD fallback was needed.

This is synthetic Debug validation, not reproduction with the user's real Steam account, physical/OEM testing or new R8 validation. Steam privacy/removal/network failures may still leave information unavailable. No commit, push, release or APK delivery was performed.

Raw receipts, both initial failures, final JVM XML, 28 frozen source/document hashes, main/test APK hashes and cleanup evidence are in `.codex-tasks/20261121-steam-resume/`. Local editable Canvas and device captures are under ignored `docs/design/20261121-steam-resume/`; this tracked report intentionally contains no image embeds.
