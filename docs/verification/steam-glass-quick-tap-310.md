# Liquid Glass quick-tap feedback · 1.0.310 (unreleased)

The supplied video shows the Dock retaining enlargement after a simple tap while chat filters and store/personal-stat tabs barely react. Their shared selector cleared pressed state on pointer release and again when invoking the click callback. The shared motion now keeps a separate visual pulse for at least 220ms of frame-clock time, allowing the existing 140ms enlargement to finish before its return. Click selection is immediate and fires once, including taps on the current selection.

Matching selection recomposition preserves the pulse. Another selection, new pointer press, target reflow, inactive lifecycle, reduced motion or disposal cancels it. Pointer drag/settle jobs retain their generation guards and edge-scroll cancellation; M3 mode does not run the pulse. No new animation is added to ordinary material buttons.

## Verification

Debug main/test packaging and 29 JVM methods passed in 6m 24s with build06. Final device03 passed all 73 different synthetic API35/x86_64 device methods in one batch (runner 103.451s): 35 collection, 24 glass-filter and 14 game-detail methods. The 29 JVM methods comprise 4 library-membership, 17 existing collection-service and 8 selection-motion methods. Main/test APK SHA256 is recorded in the device receipt. Only this verification init script freezes volatile BuildConfig fields to avoid recompiling unchanged production source on retries.

Build02 failed because the optional shape parameter shadowed graphicsLayer.shape; renamed to contentShape. Build05 compiled production Kotlin but then JVM native mmap allocation failed during dex building. The original crash log is retained locally; only task-started MuMu1 was temporarily stopped to release memory before build06. Existing MuMu0 and unrelated Java processes were preserved. No source/assertion change was needed for the memory failure.

Device01 and Device02 each passed 34/35 collection methods (48.41s and 44.14s). The initial viewport explanation was incomplete: scrolling to the row in Device02 revealed that the whole collection list had disappeared. Disallowed left swipes returned without consuming the gesture and fell through to the child's detail click. The component now consumes that horizontal gesture through release; the negative test explicitly rejects detail navigation after both short and left swipes. Vertical scrolling and normal taps retain their original route. All original failed logs, snapshots and APKs remain local. Device01 screenshot pulling originally failed on a missing parent directory, then was recovered; the driver now creates parents.

Native family-selection screenshots and quick-tap resting/enlarged/settled frames were visually inspected. Image evidence stays in ignored docs/design. The quick-tap tests inject a 10ms native pointer tap and capture a frozen animation frame after 176ms; animated lenses must change pixels outside the entire filter shell, then return inside it. The reduced-motion case must not expand outside it. Repeating an active selection and switching to unread both invoke one immediate callback. Existing actual game-detail navigation, large-font, toolbar and filter-retention methods also pass.

Only synthetic fixture state/callbacks were used. No real account, maFile, desktop Steam write, physical/OEM device or new Release/R8 verification was performed. Passing collection-service tests validate patch/readback behavior on test responses; the full authenticated ViewModel-to-server route is not claimed.

## Cleanup

Only this task's independent .sharedcollections and .sharedcollections.test packages were removed. Baseline users 0/10/11/12, current user, display density/size and font setting match the baseline; all original packages remain. Task-started existing MuMu1 was stopped after verification and its configuration/data disk retained. Pre-existing MuMu0 remains running with PID15892. No AVD, clone, data clearing, commit, push, tag, release or APK delivery. Private inventories, APKs and full crash logs remain local.
