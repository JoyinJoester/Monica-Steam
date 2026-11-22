# Direct Dock reuse · Steam 1.0.310

Verified 2026-11-21, unreleased versionCode 18. No commit, push, release or APK delivery.

## Problem and implementation

The user rejected the previous selectors as visually different from Dock. Inspection of the 409-frame / 13.634-second recording and source confirmed separate implementations: selectors used a Rect spring, 140 ms press tween, fixed 220 ms tap pulse and magnified the captured row around the moving lens. The previous travel tests proved movement, not visual equivalence. That earlier report is explicitly superseded for motion behavior.

LiquidGlassSelectionMotion is now a measured-slot/lifecycle adapter around the actual LiquidGlassDockMotionState. All normal animated position, press progress, velocity deformation and settle/release timing come from that engine. Physical slots map to fractional indices so unequal widths, RTL and edge scrolling work. Down lifts the current selection; click commits immediately and starts travel; the existing Dock release waits for arrival before lowering. Reflow, style/lifecycle change and disposal revoke old work. Reduced-animation selectors retain immediate positioning with no enlargement.

Both Dock and selectors call the same extracted steamGlassSelectionLens, resolveIndicatorTransform, rememberIndicatorDragScaleProgress and liquidGlassCaptureLayer. The lens shares depth/dispersion, fixed sampling padding, sensor highlights, surface tint and inner shadow. Selectors now capture a separate primary-tinted row whose labels each scale about their own centers, as Dock does, rather than magnifying the whole row about the moving lens. Both callers pass their transform through drawBackdrop.layerBlock so its inverse compensation keeps the capture aligned. Dock's spring and deformation constants are preserved.

A real regression found during validation was corrected: child click can retire the parent's pointer token before the parent's up/cancel is processed. selectFromClick explicitly releases the engine press, so clicking the already-selected item cannot leave the lens held indefinitely. A test covers this event ordering.

The shared caller inventory covers 11 uses in 10 files: conversation/search/history/content categories, social filters, achievement sets and status, Store filters, game-detail navigation and rich-picker categories. This is reuse of selection motion/rendering; outer shell dimensions, layouts, standalone buttons and floating-toolbar morphing remain governed by their existing components. Card expansion work from the earlier request is preserved.

## Verification

- Final build06 assembled Debug main/instrumentation packages and passed all 12 selected JVM methods with no failures/skips, in 73.074 seconds. Includes frame-by-frame equality against the actual Dock engine for press, travel, rapid reversal and drag velocity; measured geometry, cancellation, lifecycle and click-before-up regression.
- Final device03: 44/44 passed, runner 89.057 s, on already-running MuMu0/API35/x86_64. One native comparison mounts the actual production Dock and measured selector and compares normalized displacement and vertical scale for 50 consecutive Compose frames, including two reversals; tolerance .025 item / scale. Both receive semantic clicks on the same frozen clock. Four representative production selectors additionally use real injected MotionEvents for hold/click/travel/RTL/cancel/reduced motion.
- Existing device coverage includes physical dragging, offscreen edge-scroll targets, badge reflow, large font, style/lifecycle cancellation, fixed/M3E frost, live source changes, rich-picker actions/search/close/native panel, rim and centered glyph magnification, plus Dock overscroll and disposal.
- 122 final native PNG files collected and hashed. Inspected final held/rest/released light and dark filter crops, game-detail and picker movement, RTL, and comparative Dock frames. Text is centered in the held lens and returns with the resting contour. The local animated preview is assembled from native paused-clock captures with endpoint pauses; it is not a real-time frame-rate/jank measurement.
- Local M3E Canvas layout/selection preview was loaded, inspected and saved. Editable JSON, local docz URL, Canvas preview, user-video extracts and native images are only in ignored docs/design/20261121-dock-shared-selection. The Canvas is a layout reference, not evidence of native optics.
- Final source hashes remained identical after testing. Scoped Git whitespace check and explicit untracked-file whitespace checks passed. Unrelated workspace changes were preserved.

## Failed iterations and assertion changes

All failures are retained in the local task, with build receipts and device logs in the evidence folder. build01 had a callback constructor error, build02 failed JVM native-memory allocation (G1 mmap, hs_err_pid6108.log), and build04 failed after an overly broad edit anchor; all were corrected. build03 (10m41s) and build05 (1m35s) succeeded. Build retries used only task-local 4 GiB SerialGC / two-CPU flags; no unrelated Java process or MuMu was stopped.

device01 passed 7/8, but the new Dock comparison did not locate its descendant input nodes. Corrected the scoped semantic locator. Native screenshots also exposed sampling drift; that iteration was rejected visually. device02 passed 40/44. One failure revealed the genuine stuck press described above. Three optical assertions counted near-white pixels; the new Dock primary tint and shader resampling change that count. Picker glyph width (>1.15), area (>1.20), centering (<4 px), and rim checks were already passing and were retained, with an absolute readable core requirement. Filter optics now measure glyph width (>1.10) and area (>1.20) alongside the existing rim/readability checks. Earlier tests expecting independent 220 ms same-selection pulses or travel on down were intentionally changed to Dock behavior. These changes are documented, not described as unchanged tests.

## Environment and limits

Only isolated .dockshared main/test packages, absent at baseline, were installed. Both removed after evidence collection. Existing MuMu0/PID15892, users, packages, display, font, current user and configuration/data disks retained; no AVD fallback. The original instance remains running.

Synthetic Debug fixtures only, without live Steam accounts/messages or remote operations. No physical/OEM/older-API/R8 test or production performance benchmark. Shared production callers were audited; four representative selectors and the production Dock were mounted directly rather than every complete page being navigated end to end.

Main APK SHA256: 3d3e81ce2cab7f928ba451e4369eb3f505187406481510958b79ae80d7a763bc

Instrumentation APK SHA256: fc347775d1d30b159261115c9b420e0ef64a6d8bb4b4c4cef43b9f79934ed1d0

Evidence: steam-dock-shared-selection-310-evidence. Task baseline, scoped patch, failed builds/crash diagnostic and raw package snapshots: .codex-tasks/20261121-steam-dock-shared-selection. Images and APKs are not repository deliverables.
