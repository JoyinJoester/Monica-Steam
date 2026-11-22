# Shared glass selection travel · Steam 1.0.310

Follow-up on 2026-11-21: the user rejected visual equivalence to Dock after this run. These results prove the earlier travel/callback behavior only. The separate Rect spring, 220 ms pulse and moving-pivot magnification described below are superseded by direct Dock reuse in steam-dock-shared-selection-310.md. The original results and failures remain historical evidence.

Verified 2026-11-21. Unreleased versionCode 18. No commit, push, release or APK delivery.

## Problem and change

The user's local recording compares moving Dock selection with other controls that appear to only enlarge. Source inspection found the shared LiquidGlassSelectionMotion.press(fromAnyOption=true) assigning bounds directly to the touched item before its click animation. Thus the lens teleported to its destination on physical down; semantic-click tests and final-selection assertions missed the discontinuity.

Pressing another option now starts a spring from the currently visible bounds, while the existing press enlargement/refraction runs. Cancellation settles back to the committed selection from the visible location. A drag takes over by cancelling the press-settle job, retaining the previous drag targeting/scroll semantics. Quick clicks and immediate callback recomposition continue from the current bounds; repeated selection can retarget an in-flight animation. Reduced-motion presses retain immediate positioning. No commit happens merely on down; the original click/release callbacks still decide selection. Lifecycle, reflow, external-selection and disposal cancellation remain in force.

SteamListFilterRow now defaults to a sibling glass shell so the raised lens is not clipped inside a backdrop. Its independently zooming target label was removed; magnification comes from the moving lens's captured labels. M3E appearance/press opacity remain. The shared lens has a test tag to observe actual intermediate native bounds.

The caller audit finds 11 uses across 10 files: conversation filters; thread search; history search; chat content categories; two social category rows; achievement sets and completion status; Store advanced-filter categories; game-detail navigation; and rich-picker categories. The shared fix reaches those calls. The dedicated Dock implementation was preserved. This does not claim redesigning every standalone button, Material chip, drawer or dialog into a moving selector.

## Verification

- Before the production fix, the same motion suite ran 10 methods with one intentional regression failure: physical pointer down changed Rect(0,0,80,48) immediately to Rect(190,0,290,48). Original XML and log retained.
- build01 failed only because the new device test imported enum member tabLabelRes as a top-level extension. Corrected that import; retained the failed log. No device result is attributed to build01.
- Final build02 assembled Debug main and instrumentation packages and ran 15 selected JVM methods, all passed with no errors or skips, in 73.565s. Suites include selection motion, Dock motion and existing Dock guards. Source hashes were frozen after the build and remain unchanged.
- device01: 7/7, runner 20.876s. Real Android MotionEvent down/up against production conversation, default list, game-detail and rich-picker selectors. At frozen Compose-clock frames, the lens starts at the previous position, occupies an intermediate position after 64 ms, does not reset on release, reaches the target and publishes exactly one selection. Also covers physical RTL travel, cancellation returning without callback/jump and reduced motion without intermediate travel. Native images include 24 consecutive post-release samples for the inbox and representative frames for the other controls.
- device02-regression: 36/36, runner 61.705s, same main/test APK hashes. Existing filter dragging, cancellation, reflow, large font, unread badges, edge scrolling, M3E frost/live backdrop, visible refractive rim and label magnification; rich-picker blur/pixels/actions/dragging/RTL/reduced motion; and Dock motion retirement/overscroll/disposal tests all passed. Across both batches, 43 distinct methods passed without device-test failures.
- Inspected native origin, intermediate, raised and settled inbox frames, light default list, game-detail and picker intermediate frames and RTL. The frame contact sheet and GIF contain only captured native samples; the GIF adds endpoint pauses and is not a real-time jank/frame-rate benchmark.
- The local M3E Canvas was saved and its standard-tab layout/selection preview inspected. It is a design/layout reference; actual glass optics and motion were verified natively. Source JSON, full docz URL, Canvas screenshot, user-video extracts and native visuals remain local in ignored docs/design/20261121-glass-selection-motion. No image embeds in committed-form reports.
- Reused already-running MuMu0/API35/x86_64, PID 15892. Installed only isolated .selectionmotion packages absent at baseline. Both removed; original users/packages/current user/display/font and configuration/data disks retained. MuMu0 remains running. No AVD used.

## Evidence limits and preserved failures

Synthetic Debug UI fixtures only; no real Steam accounts, messages or remote operations. No physical/OEM device, older API, release/R8 or production frame-time benchmark. Four representative production selectors were mounted directly; the remaining shared call sites were audited, not each opened end to end. Motion tests observe sampling at a paused Compose clock, while existing pointer regression uses actual injected gestures.

After successful regression, 28 filter screenshots were collected. Attempting to read the existing rich-picker regression's external screenshot directory through run-as was denied by Android storage access. Those external images were not retained before test-package cleanup and are not counted as inspected evidence. The new rich-picker travel test uses private files and its native images were collected and inspected. Regression assertions and full runner logs remain valid. No assertion was weakened; all previous JVM/build failures are retained.

Main APK SHA256: e3e1332234baa3b8028d9074e3d88b31c3919f8e91a29c200316511ec1e14485

Test APK SHA256: f9aa8f65d0d573e2437068d70953027818419626f4efaac434767852549a0ca3

Text evidence, XML, caller audit, source/native hashes and cleanup receipt are in steam-glass-selection-motion-310-evidence. Task baselines and the scoped patch stay in .codex-tasks/20261121-steam-glass-selection-motion. Existing unrelated workspace edits were preserved.
