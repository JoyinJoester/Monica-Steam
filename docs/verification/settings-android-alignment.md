# Settings design alignment — 2026-10-17

## Scope and reference

The user confirmed that the request covers the Settings page as a whole. The reference is Monica Android's `ui/screens/SettingsScreen.kt`: 24 dp outer corners, 4 dp adjoining corners, 2 dp row gaps, 16 dp horizontal insets, `surfaceContainer` group backgrounds, primary section labels, bodyLarge titles, bodyMedium supporting text and 24 dp icons.

Steam's shared `SettingsSection`, `SettingsItem` and `SettingsItemWithSwitch` now use those rules. `SettingsGroup` clips the visible group at its outer edge while each row uses the small inner shape. This adapts Android's explicitly indexed corner shapes to Steam's search-filtered groups and injected appearance rows without a separate item count. A single visible row consequently retains four large outer corners. Outside a group, existing standalone card spacing is retained. Disabled switches retain their disabled interaction, icon, text and switch states while keeping the group's container color.

The shared components cover the home, appearance, data management, about, developer, notification, store-hint and widget-extension sections. Navigation and Steam experience now have explicit groups, and the store-hint entry no longer creates a nested section within the Steam experience group. Password/locking controls use the same group. Notification interval controls sit below the periodic-notification cards so they do not interfere with the last card's corners. Routing, preferences, security gates, scroll states and Dock clearance are unchanged.

## Design evidence

Editable JSON, local URLs and inspected preview PNGs are retained only in ignored `docs/design/settings-android-alignment/`. The four isolated Canvas projects are `monica-steam-settings-android-home-dark`, `monica-steam-settings-android-home-light`, `monica-steam-settings-android-appearance` and `monica-steam-settings-android-navigation`. Open their corresponding `*-url.txt` files to use the full local `#docz=` link.

The previews use real Canvas searchBar, topAppBar, switch and connected listItem components. Both light and dark home previews and the appearance/navigation previews were loaded and checked. Canvas's connected list preset uses its own outer/inner radii and gap (28/8/3); native code follows the Android reference values (24/4/2). The previews illustrate layout and grouping, and are not evidence of Android rendering. The purple Canvas palette is illustrative; native colors always come from the selected Material color scheme.

## Verification and remaining checks

Reviewed the targeted source diff, component call sites and group boundaries. `git diff --check` passed. Existing action callbacks and enabled-state handling remain unchanged.

No build, Kotlin/JVM test or emulator was started, as requested to avoid competing with the main chat. This reversible presentation change adds no implementation-mirroring unit tests. Compilation and native visual/interaction validation remain pending.

When the shared resources are available, compile/package the app and check the home and child settings at 80%, 100% and 120%, including increased system font size, long translated subtitles, one-row search results, disabled notification switches, the periodic interval selector, both Dock styles and wide-screen columns. Confirm every existing entry, toggle, security gate and back action remains usable.
