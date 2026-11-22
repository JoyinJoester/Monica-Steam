# M3 frosted filters — 1.0.310 (unreleased)

Date: 2026-11-20.

M3 filters now reuse the compact, single-row Liquid Glass capsule layout with Gaussian backdrop blur. Inactive options are plain labels; only the selected option has a soft theme-colored indicator. Large text and overflow remain reachable by horizontal scrolling rather than making a second row. The shared list-filter component and unified game-detail navigation use this treatment.

## Changes

- `SteamListFilterRow.kt`: remove the separate M3 FlowRow/chip layout and reuse the existing scrollable selection row. Keep 48 dp targets, selection semantics, unread badges and reduced-motion handling. M3 presses change label opacity; Liquid Glass retains its original label scale, radius and drag selection.
- `SteamGlassSurface.kt`: add a filter-specific material role. M3 filters use 18 dp Gaussian blur and theme tint at 48% opacity in dark mode / 60% in light mode, instead of the previous 88% opaque surface. Other M3 control/panel materials retain their existing settings. Liquid Glass keeps its existing blur, lens and highlight behavior. Missing backdrop or unsupported Android versions use the existing opaque fallback.
- `SteamGameDetailChrome.kt`: apply the same filter material to the compact detail tabs and round Back button. The previous task's unified header, conditional Workshop tab and draggable actions remain connected.

The selected M3 indicator uses the existing theme primary-container color at 72% opacity. Existing labels and translations are reused. No global design-style preference or stored enum value changes.

## Verification

Task-only Debug application suffix: `.frostfilters`; x86_64, no minification. Production Gradle settings were not changed. The existing MuMu1 instance was used on Android 15 / API35 because MuMu0 was running another task's user14. No new emulator was created.

- Build01: main and instrumentation APKs passed in 3m05s.
- Device01: 33/35 passed in 69.263s. All 14 detail tests and the 19 existing filter gesture tests passed. Two new dynamic-blur tests failed because the screenshot was taken before the changed source background had been presented. Both the filter and the uncovered control sample were identical to the earlier frame.
- Build02: instrumentation rebuild passed in 2m30s. Only the test gained Compose/instrumentation idle synchronization and a stronger assertion that the uncovered background has changed before testing its blur. Production code and the installed main01 APK stayed fixed; blur and live-color thresholds were not relaxed.
- Device02: 33/35 passed in 69.667s. Both saved images showed live blur changes. A too-strict blue-channel range prerequisite failed in dark mode despite a mean external change of127 and material change of66; the prerequisite was corrected to require external mean change above100. Blur contrast and live-material thresholds were unchanged. The light test passed its pixel checks but recorded two unexpected ALL click callbacks; their origin was not determined.
- Build03: instrumentation rebuild passed in 2m15s, with test-only callback diagnostics and the corrected prerequisite. Both new methods passed in device03 (2/2, 3.170s), followed by the full device04 batch **35/35 in70.965s** on the same fixed main01 APK. Exact callback assertions remained intact. Diagnostics were subsequently removed from test source; no executable production content or test assertions changed after the passing run. The earlier unexpected clicks remain recorded, not attributed conclusively to a product defect.

The new light/dark checks use two-dp alternating stripes. They require unblurred control contrast above180, contrast below25% of that value through the material, and a visible change after changing the source color. This distinguishes real background blur from an opaque fill or tint-only overlay. Existing tests cover selection/cancellation, RTL, edge scrolling, cached-page lifecycle and reduced motion. The large-text M3 test now checks one row and scroll access to every option, including selecting Archived. Detail tests cover the compact shared header, independent content state, actions and Workshop.

The editable local Canvas and native screenshots are stored only in ignored `docs/design/20261120-m3-frosted-filters/`; the Canvas was actually loaded and previewed. Synthetic native screenshots, rather than Canvas rendering, are used to assess the Gaussian material. Text test receipts and hashes are stored in the adjacent evidence directory; raw attempts remain in the workspace task directory.

Both task-only packages were uninstalled. Original users0/10/11/12, current user, resolution, density, font and rotation settings match baseline; every original package remains. Chrome appeared independently during testing and was preserved (its origin was not investigated). Vendor shutdown stopped Android but left the task-owned shell PID93336 stuck stopping. After the user reported a disruptive Scan popup, that process was verified by its executable/instance command line and stopped; the manager then confirmed MuMu1 fully stopped. No further emulator run was performed. MuMu0, emulator disks and configurations were preserved. Shortcut settings were only read; the popup trigger was not confirmed or changed.

Only synthetic MuMu component tests are covered. Physical devices/OEM GPUs, pre-API31 device behavior, full authenticated Activity routes and R8 were not tested. This task does not claim new chat-entry performance measurements or live Steam review-service testing. No commit, push, release or APK delivery.
