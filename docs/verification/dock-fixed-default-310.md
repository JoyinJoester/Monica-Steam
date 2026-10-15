# Dock defaults in 1.0.310

The Dock now offers Fixed and Liquid Glass. Fresh installations and saved `M3E` or unknown style values resolve to Fixed. Existing Liquid Glass choices remain unchanged. If a fixed order has not been saved, the previous toolbar order is carried forward and missing top-level destinations are appended; an existing fixed order takes precedence.

The legacy floating toolbar and its progressive blur are no longer composed by `MonicaSteamActivity`. Both supported styles expose Store, Game Library, Chat, Tokens and Settings, with independently saved ordering. The settings page removes the old visibility switches and third style option. Tablet navigation continues to use the corresponding complete order.

The version name is `1.0.310`; the existing project policy keeps versionCode at `18`. No tag or Release is created.

The local M3E Canvas design is saved under `docs/design/dock-fixed-default/` with an editable JSON, a local editor URL and a rendered preview. Design files and screenshots remain ignored by Git.

The user's reported repeat of the family-library issue came from building the local main checkout. At investigation time, main and the task branch both pointed to `595452e`, but the family-activity fix existed only as uncommitted changes in the task worktree. This does not establish a second failure of the parser/filter fix.

Verification: 211 JVM tests covering the game library, navigation, default-style migration and saved ordering passed with no failures or skips. The old default regression first failed against `M3E`, then passed with `FIXED`. Guards requiring the removed floating toolbar were updated to check its absence and the supported navigation paths; the token-page inset guard now checks the existing adaptive-inset helper.


Device verification: the UI test passed on the shared API 32 emulator at its existing 1.5× font scale. It renders the production Dock settings page and fixed bar, checks that Fixed is selected for a legacy M3E value, verifies that the M3E option is absent, and switches to Liquid Glass and back. The native screenshot was inspected and retained only in the ignored design directory. The emulator started for this task was stopped afterward.

Integration: 41 changed/new files were applied to the previously clean local main worktree at the same base commit, including the earlier README and session-recovery work. Source contents were checked after synchronization before preparing the direct main-branch commit. No tag or Release was created.

Final build: normal `:app:assembleDebug` succeeded in the local main checkout for ARM64 and ARMv7. APK metadata confirms application ID `takagi.ru.monica.steamapp`, version name `1.0.310` and versionCode `18`. Both packaged native libraries match the newly built/stripped libraries. Existing R8/Kotlin metadata warnings remain; no APKs were copied to a delivery directory.
