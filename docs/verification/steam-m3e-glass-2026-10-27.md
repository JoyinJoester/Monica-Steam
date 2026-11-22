# Design style, liquid glass and M3E review — verification (2026-10-27)

## Scope

- Appearance & display › Design style (M3E / Liquid Glass), separated from Bottom navigation; Bottom navigation now orders destinations only.
- Shared glass material (`SteamGlassSurface.kt`): `LocalSteamDesignStyle`, `SteamGlassHost`, `steamGlassSurface`. Applied to the Chat list Unread pill and compose button and the Store cart button. Dock glass tint now derives from the theme.
- M3E review fixes: shared `SteamEmptyState` (Game library, Tokens), Tokens page title, ExpressiveTopBar long-title scale, Navigation entry subtitle, English Monica Plus subtitle.
- Chat background presets and Telegram theme tests carried over from the 2026-10-26 chat work.

Editable M3E Canvas drafts (six screens, light and dark) are kept locally under the ignored `docs/design/m3e-glass-20261027/`.

## Build and unit tests

- `:app:assembleDebug :app:assembleDebugAndroidTest` succeeded (JDK 17, repository-local Gradle home, in-process Kotlin compilation to stay within available memory).
- `:app:testDebugUnitTest --tests 'takagi.ru.monica.steam.*' --tests 'takagi.ru.monica.ui.*'`: 1662 tests, 12 failed in the first run.
  - 1 was caused by this change (the Bottom navigation guard expected the retired `showSwitch = false` row); the guard now asserts that rows carry only a drag handle and no switch.
  - 2 came from the earlier uncommitted Telegram integration (chat threads now inherit IME/navigation insets from `TelegramChatScaffold`; the inbox search condition changed). Both guards were updated to the current structure without relaxing their intent.
  - 9 already fail at `HEAD` and are unrelated: module layout snapshots, the version literal `1.0.309`, MDBX/OneDrive routing, account-source sessions, settings scroll state, compact-home sections, standalone theme selection, and the friend identity card.
- After the updates, the navigation, chat and safe-area guard classes pass (254 tests in the targeted rerun).

## Instrumented tests (shared AVD `Monica_Issue136_API_32`, font scale 1.5)

- `TelegramThemeUiTest` and `TelegramWallpaperUiTest`: 4/4 pass. The theme tests now wait for native views and RecyclerView redraws instead of a fixed delay, and check bubble colours on the message cell that draws them rather than on the text view (a diagnostic run confirmed the expected incoming colour `#FFE8E7F1` matches the rendered bubble).
- `TelegramSocialUiTest` and `TelegramChatUiTest`: 14/14 pass with the glass host wrapping the inbox.
- `SteamDockSettingsUiTest` cannot run in this configuration: the debug APK is R8-minified, which removes `Job.complete()` needed by `createAndroidComposeRule`. The settings pages were checked in the real app instead.

## Manual checks in the real app (API 32, font scale 1.0, restored to 1.5 afterwards)

- Appearance & display lists Design style after Color Scheme, showing the current style.
- Design style shows the preview, M3E and Liquid Glass radio options, and notes. Selecting Liquid Glass switches the Dock to the floating glass Dock immediately; selecting M3E restores the fixed bar. API 32 has no runtime shaders, so the page shows the unsupported notice and glass falls back to tonal containers.
- Bottom navigation shows one hint and a connected group (24 dp outside, 4 dp inside) with drag handles only.
- Game library and Tokens show the new centred empty states; the Tokens title reads "Tokens".
- Store cart button stays above the Dock in the content overlay.

## Android 16 glass check (existing AVD `Monica_API_36_16K`, 16 KB pages)

- The existing API 36.1 AVD (`Monica_LiveUpdates_API_36_1_4K`) is unusable here: surfaceflinger aborts every ~15 s (`Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma`) with host GPU, SwiftShader, headless and windowed launches.
- `Monica_API_36_16K` booted stably and runs the arm64 debug build. With Liquid Glass selected, the Dock refracts Store artwork and text as intended.
- Found: the Chat Unread pill and compose button, the Store cart button and the Design style preview Dock rendered as dark grey glass. `SteamGlassHost` recorded only the content, so transparent gaps let the glass drop shadow show through. Fix: when glass is active the recording is painted on an opaque `backdropColor` (page background by default, `surfaceContainerLow` in the preview).
- Also changed: Store category tabs fade at an edge that can still scroll, instead of clipping the last label against the filter button.
- Rebuilt (`:app:assembleDebug`, Gradle heap capped at 4.5 GB, one worker) and re-checked on `Monica_API_36_16K` after a clean install (the AVD data partition was nearly full of other projects' apps; the low-storage threshold was lowered only for the install and then deleted again). In light and dark mode the Design style preview Dock, Chat Unread pill and compose button, and Store cart button now render as light or dark translucent glass consistent with the Dock, refracting the content beneath. Store category tabs fade at the scrollable edge. The AVD was stopped afterwards and system night mode left off.
- Completed on 2026-10-28: rebuilt matching app/test APKs and passed all 18 chat/social/theme/wallpaper regressions on the shared API 32 AVD. Details below.

## Resumed regression — 2026-10-28

- `:app:assembleDebug :app:assembleDebugAndroidTest` succeeded in 11m 48s using JDK 17, the repository-local Gradle home, `-Pkotlin.compiler.execution.strategy=in-process`, a 4608 MB Gradle heap, `--max-workers=1`, and `--no-daemon`. The product version remains 1.0.310 / versionCode 18; the generated build detail tag is 26102818-01.
- First run: 17/18 passed. The inbox search test waited for the state callback and then immediately inspected editable accessibility nodes. Compose had not necessarily published the search field yet, and IME focus could move the active window. `setSearchText` now waits for a visible editable node belonging to the app across interactive windows. It still performs the real accessibility text action and checks that searching for Sam filters out Alex.
- Rebuilt only `:app:assembleDebugAndroidTest` in 3m 26s, excluding `:app:generateDebugBuildConfig` to preserve the time-dependent fields and existing app/R8 mapping. App Kotlin compilation and app R8 were confirmed UP-TO-DATE.
- Second run: 16 failures, including widespread window-focus errors. Kept the failed logs locally. The final run explicitly woke/dismissed the device lock screen and temporarily enabled staying awake while plugged in. Preflight diagnostics showed the screen was awake, the keyguard was hidden, and the original screen timeout was already 2147483647 ms; sleep is therefore not established as the cause of the second run. Emulator logs also contained snapshot-load and ColorBuffer warnings.
- Final run: **18/18 passed in 181.613s**, on `Monica_Issue136_API_32` (API 32, x86_64 with arm64 translation), font scale 1.5. Coverage: theme colours 2/2, wallpaper selection/rendering/persistence 2/2, social/inbox interactions 8/8, direct/group chat interactions 6/6. Raw instrumentation output: [steam-m3e-glass-2026-10-28-ui.txt](steam-m3e-glass-2026-10-28-ui.txt).
- Retrieved 34 local screenshots under ignored `docs/design/m3e-glass-20261027/regression-20261028-3/`. Visually checked the dark inbox filtered to Sam, the private chat editor above the IME, and light contacts with complete names/status text. Editable Canvas drafts and their local project-specific URLs remain under `docs/design/m3e-glass-20261027/`, including `canvas-url.txt` and `canvas-dark-url.txt`.
- Restored font scale 1.5, screen timeout 2147483647 ms, and `stay_on_while_plugged_in=0`; night mode remained off. Stopped the public AVD started by this continuation and preserved its config/data disk. The other project's already-running emulator was left available.
- App SHA-256: `1E80936EE3AC035B9FC55A5F8B9793543CA2C358AD1D1C2527DE7F5F81C923EE`. Test APK SHA-256: `B97DB6DC7B34D39B399347D51274FBF3F0E04D0F9C58BD215F56BC834ABEC11A`.
- This continuation completes the previously pending latest-build regression. The earlier nine baseline unit-test failures and the manual-only settings checks described above remain documented limitations. No commit, push, release, product version change, or APK delivery was performed.

## Not verified
- The Tokens QR scan button remains in the Scaffold FAB slot and therefore keeps the M3E style.

## Observed, not changed

- Master-password unlock runs PBKDF2 (`SecretKeyFactory.generateSecret`) on the main thread; on the ARM-translating emulator this produced an input-dispatch ANR.
