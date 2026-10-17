# Required master-password setup — 2026-10-17

## Behavior

The launcher now uses `SteamStartupGate` before the configured app lock, Dock, page content and Workshop task overlay. Every new activity checks actual master-password presence; it does not use a saveable completion flag or the ViewModel's authentication flag to waive setup. Until a password exists, the shared Monica password component requires entry and matching confirmation, with its existing password policy and masked input. There is no skip, biometric or startup-bypass action during setup. Back may leave the activity, but cannot reveal the main UI. The form scrolls and accounts for safe drawing insets and the keyboard.

Before displaying first-time setup, a single DataStore edit enables `steamLockTokenPageOnly` and disables the separate startup-verification bypass. This ordering preserves the intended default if the process dies immediately after password creation. After success, the gate rechecks password presence. Secure-storage read or defaults-write failures produce a retry screen rather than resetting data or proceeding as an empty installation; cancellation propagates. Password-setting exceptions remain on the form and do not invoke success.

Configured users do not have their stored lock scope rewritten. An absent scope preference now defaults to Token-only in both `AppSettings` and DataStore mapping. Under that default, normal app entry does not authenticate or restore an authenticated UI flag. The Token, maFile/WebDAV and security-settings gates still validate the session, and each maFile export still requires fresh verification. Explicitly selected whole-app locking remains available.

The old no-password exception in both `SteamAppLockPolicy` and the single-export authenticator has been removed. Direct exports with no master password now report unavailable and write no bytes. The module handoff uses the same mandatory authenticator without an optional-policy flag. No crypto format, existing token data or password-reset implementation was changed.

## Design

Local M3E Canvas first-entry and confirmation sketches are retained as ignored JSON, URL text and PNGs under `docs/design/required-master-password/`. Their full URLs are in `setup-url.txt` and `confirm-url.txt`, using isolated `monica-steam-required-password-setup` and `monica-steam-required-password-confirm` projects. Both were loaded and inspected using the real textField and button components. These are design previews, not native Android test evidence.

## Verification

Completed source checks: launcher setup precedes page/overlay composition; setup cannot inherit startup bypass; password presence alone never marks a session authenticated; existing defaults remain separately configurable; the no-password export authorization branch is gone. New English and Chinese XML resources parse, and `git diff --check` passes.

Seven unit cases were added in `SteamStartupPreparationTest`: configured-user preservation, first-time defaults followed by required setup, repeated entry until a password exists, unreadable secure storage, failed defaults persistence followed by retry, cancellation propagation, and Token-only defaults without global bypass. Existing lock-policy and integration/scope guards were updated. The existing device export case now expects a missing password to leave the destination empty.

**No build, Kotlin/JVM tests or emulator was run**, per the user's request to leave these resources to the main chat. The above tests are authored, not reported as passing. Earlier build and device results in other verification documents predate this change; in particular, their historical optional-password export case no longer describes the intended behavior.

Pending when shared resources are available:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'takagi.ru.monica.steam.security.*' :app:assembleDebug --max-workers=2 --console=plain
```

Native verification should cover clean install, an existing no-password installation, wrong confirmation/short password, interrupted setup and recreation, retry after storage failure, an explicitly configured whole-app lock, cold start into store/chat/library without a password prompt, Token/backup/settings authentication, a notification/deep link during initial setup, and no-password export writing zero bytes. Do not clear a developer or user installation to perform these checks; use the existing isolated test application when resources are free.
