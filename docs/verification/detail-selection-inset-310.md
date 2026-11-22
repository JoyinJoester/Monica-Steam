# Game detail selection inset — 1.0.310 (unreleased)

## Change

The game detail Store / Personal stats / supported Workshop selection pill now has a 4dp inset on all sides instead of a 2dp horizontal row padding. The 48dp minimum touch targets, text size, selected state, drag destinations and press scale remain intact. At the minimum row height the drawn pill is 40dp high. M3 and liquid glass use the same inset; other filter rows keep the default zero inset.

The previous toolbar shape, clipping, contact tension and lift animation fixes remain in place.

## Validation

- Debug main and instrumentation packages built successfully in 2m 12s, using the isolated `.detailinset` application suffix and x86_64 ABI, without minification.
- Existing MuMu instance 1, Android API35 / x86_64, user 0: 35/35 existing device tests passed in a single batch, runner 64.402s (14 detail tabs and 21 shared filter interactions). Runner output and package hashes are in the adjacent evidence folder.
- Reviewed real native header screenshots for light Store / Personal stats, dark 1.5 font scale, and the held glass lens. Inset pill stays centered around the labels and held magnification remains visible.
- Verified the two edited source files match the hashes frozen during this build. Existing navigation, Workshop availability, scroll restoration, drag / cancel and filter interaction checks passed.
- Editable local M3E Canvas design was loaded and previewed before the native change. Canvas JSON, full local share URL and screenshots are kept only in ignored `docs/design/20261121-detail-selection-inset/`.

## Scope

Synthetic UI fixtures only; no real Steam account, physical device, OEM or R8 verification. This is a small visual change and reuses existing regression tests; no new mirror tests were added.

Original MuMu instance 0 remains running with its original PID 15892. Instance 1 was stopped before this task, was started briefly for these tests and is confirmed stopped again. Removed only the two isolated task packages; original users 0 / 10 / 11 / 12, applications, display settings, configuration and data disks are preserved. Cleanup completion is recorded in the task result.

No commit, push, release or APK delivery.
