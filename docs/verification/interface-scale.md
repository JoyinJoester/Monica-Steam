# Monica Android page-scale reuse — 2026-10-17

## Existing behavior and change

Steam already had the shared `InterfaceScaleSettingsItem`, `InterfaceScaleSelectionSheet` and `InterfaceScale` calculations, exposed through Settings → Appearance. The existing preference store persisted percentages from 80 to 120. However, the launcher wrapped page content in `ProvideSteamContentDensity`, which capped the content multiplier at 1.0 while navigation retained the selected larger density. A 120% preference therefore enlarged navigation but left page content at 100%.

The launcher now inherits one scale for content and navigation, without that nested cap. `ProvideSteamUiScale` delegates to `ProvideMonicaInterfaceScale`, copied unchanged from the current Monica Android component at `Monica-main/Monica for Android/app/src/main/java/takagi/ru/monica/ui/scale/InterfaceScaleProvider.kt`. Steam still uses its own `monica_steam_ui_scale` DataStore, so existing preferences are retained and the build remains independent of the Android checkout. The root menu-density and avatar-shape providers remain connected.

The existing Android slider/sheet is reused: 1% increments over 80%–120%, persist on release, percentage/effective-DPI display, accessible state description and reset to 100%. The sheet intentionally uses system density so its controls stay reachable while changing page scale. System font scale is preserved. No duplicate settings entry or second scale preference was added.

## Design and verification

The local M3E Canvas appearance and sheet sketches are retained in ignored `docs/design/interface-scale/` as JSON, URL text and screenshots. Their links use separate `?project=monica-steam-interface-scale-appearance` and `?project=monica-steam-interface-scale-sheet` identifiers. Actual preview contents were loaded and screenshots inspected, including the grouped settings list, native slider, 80%/120% labels and reset action. These are design previews, not native Android runtime evidence.

Per the user's resource-sharing constraint, **no build, Kotlin/JVM test run or emulator was started**. The existing scale regression suite was updated to check Android-provider delegation and removal of the content cap. Its launcher source check now accepts the existing labeled lambda. The previous assertion that 120% page content should stay at 100% was removed because that was the defect.

Static verification passed: the copied provider matches Monica Android after normalizing line endings; the launcher and Steam provider use the shared root scale; no production references to the removed content-density provider, calculation or scale local remain. `git diff --check` passed. These source checks do not establish compilation or runtime behavior.

Pending when build resources are available:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'takagi.ru.monica.steam.ui.SteamUiScaleSettingsTest' :app:assembleDebug --max-workers=2 --console=plain
```

Native follow-up should cover 80%, 100%, 120% and an intermediate percentage across store, chat, library, token and settings pages; compare content with both Dock styles, verify restart persistence, preserve increased system font settings and confirm the slider sheet/reset remain usable. This change scales Compose page layout; it does not implement a separate web-page zoom setting.
