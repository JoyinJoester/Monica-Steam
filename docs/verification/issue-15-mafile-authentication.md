# maFile backup authentication — issue #15

Date: 2026-10-14. Base: `8b4ac634586c57ce19017529b3634ce2695015d3`.

## Behavior

- Local maFile transfer and WebDAV backup routes use the same sensitive-surface gate as the Token page. Token-only locking and the startup bypass cannot leave these routes unprotected.
- The gate now passes its startup-bypass policy through to `LoginScreen`; previously the login control independently read the startup setting and could skip password verification.
- With an optional master password configured, every plaintext ZIP export requires fresh authentication after the document picker returns. A valid app session does not skip this export confirmation. Fingerprint authentication is offered only when enabled and available; it must also unlock the vault material.
- The transfer page observes encrypted account rows only for their count. The document callback retains only the destination URI. Account decryption, ZIP generation and writing happen after authorization, and the ZIP byte buffer is cleared after writing.
- Authentication is held only in memory for this request. Cancellation, verification failure, backgrounding or disposal cannot grant an export. Configuration recreation cannot restore a grant. The document provider may retain an empty file created by its picker; no maFile bytes are written before authorization.
- Document-picker registrations and pending URIs live above both authentication gates. Immediate foreground locking can therefore dispose the protected page without losing the destination; unlocking the page still requires fresh export verification. Leaving the transfer route clears pending locations and ignores late picker callbacks.
- Steam remains usable without a configured master password. Temporary accounts remain excluded from exports. Existing encrypted WebDAV automatic backups retain their existing behavior; this change protects entry to their settings and manual backup UI.

## Device verification

Reused `Monica_Issue136_API_32` (Android 32, x86_64). Tests ran under the isolated `takagi.ru.monica.steam.recoverytest` UID using synthetic accounts; real app data was not cleared.

Build:

```powershell
./gradlew.bat -I scripts/recovery-test.gradle :app:assembleDebug :app:assembleDebugAndroidTest
```

Instrumentation:

```powershell
adb shell am instrument -w -r -e class takagi.ru.monica.steam.backup.SteamMaFileExportAuthenticationTest takagi.ru.monica.steam.recoverytest.test/androidx.test.runner.AndroidJUnitRunner
```

Result: **8 tests passed**, including fresh authentication despite an active session, wrong password/cancel writing no bytes, successful ZIP round-trip with temporary-account exclusion, repeated-export authentication, picker cancellation, background/recreation cancellation, unconfigured optional locking, startup-bypass rejection plus foreground re-locking, and completing an export after its picker returns into an immediate app re-lock.

The tests inject document-picker results through `ActivityResultRegistry` and write actual files in the isolated application's cache. Password verification uses the real `SecurityManager`. Physical fingerprint enrollment/authentication and a live WebDAV server were not exercised.

Final standard `:app:assembleDebug` also passed for `arm64-v8a` and `armeabi-v7a`, including R8/resource shrinking. The existing Kotlin-metadata compatibility warning from R8 remains. Build version stays at `1.0.309` / versionCode `18`; no release or tag was created. The public AVD started for these tests was stopped after verification.

## JVM verification

The `takagi.ru.monica.steam.backup.*` and `takagi.ru.monica.steam.security.*` suites ran **33 tests: 31 passed, 2 failed**. Both failures are pre-existing source-text expectations in the unchanged `SteamStandaloneDataBoundaryTest`:

- `standaloneSettingsExposeFullThemeSelection`, line 22, expects `R.string.color_scheme`, which is absent from that screen in the base commit.
- `standaloneMdbxRouteSupportsLocalAndWebDav`, line 93, forbids the existing `MdbxOneDriveCreateScreen` route.

Both methods were run again against an exact source fixture exported from base `8b4ac63` using the test process's `user.dir`; both failed at the same assertions. Neither guard was weakened by this fix. Excluding only those two methods, the remaining **31 tests passed**.

The added route guard checks that both backup routes use authentication without startup bypass. The device gate test additionally exercises the real login control with startup bypass enabled: a wrong password must fail, the correct password must reveal the protected content, and ending the session must re-lock it on return.
