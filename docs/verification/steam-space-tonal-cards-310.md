# Steam Space tonal activity cards · 1.0.310

Verified on 2026-11-21. Unreleased code18; no commit, push, release or APK delivery.

## Change

Space event cards remove the 1dp outline and use the existing theme surfaceContainer fill. Corners increase from22 to28dp. Horizontal/bottom padding is16dp, top padding12dp and content separation12dp. Nested game rows use surfaceContainerHigh to distinguish their content through tone. No hard-coded light/dark colors or new user-visible strings were introduced.

Avatar, event type, timestamp, achievement image, media, click/long-press preview, detail/game/profile navigation, sensitivity masking and feed/header/glass behavior retain their existing implementation. No data/parser/account/network changes. Card content stays on a readable tonal surface in both visual styles.

## Validation

- Main and device-test APK build01 succeeded in 90.789s. All87 selected Space JVM methods across five suites passed without failures, errors or skips.
- Device01 passed23/23 native methods, runner 49.436s: SteamSpaceUiTest and SteamSpaceHeaderUiTest. Existing regression covers feed state, cursor/paging, header gestures, previews, actor/game/image navigation, filters, refresh and publishing callbacks, materials, and image decoding.
- The existing cached-achievement test retains the decoded fixture-pixel assertion and additionally captures the same card in dark320dp/1.5-font composition. Normal achievement, dark large-font achievement and multi-status native screenshots were inspected. Long actor names retain the existing two-line behavior. This is in-window card font scaling, not a claim about all large-font popup windows.
- A local editable Canvas single-page preview was inspected before native styling changes. Canvas JSON, full docz URL, Canvas screenshot and native images stay local under ignored docs/design/20261121-space-tonal-cards. Tracked reports do not embed screenshots.
- Only existing test MuMu1/API35/x86_64 was started. Own .spacecards packages removed; user identities/runtime flags, existing packages/current user and display/font settings match baseline. Task-started MuMu1 stopped; original MuMu0 and all configuration/data disks retained. No AVD used.
- Source/APK hashes and scoped whitespace/ignore checks retained in the task/evidence directories. Existing unrelated working-tree changes preserved.

Synthetic UI fixtures only; no real-account import, reads or writes, publishing, live Steam verification, physical device/OEM, older Android or release/R8 validation. The yellow achievement bitmap is a synthetic decoding fixture. No build or native-test failure in this task.

Main APK SHA256: 05893f1e5dc30011aa2f63548e511c70264bb451dcd1bd32af1dcc769e6c6348

Test APK SHA256: 26b8a1bbac5af83a1e110e99c94ef0ede42a6f674c68dfa4d6dd3a8fb46ce579
