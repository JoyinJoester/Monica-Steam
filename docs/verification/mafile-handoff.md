# Consented maFile handoff — 2026-10-16–17

The companion protocol is documented in [mafile handoff v1](../interop/monica-mafile-handoff.md). Monica's existing encrypted storage and normal file export policy are unchanged. The new external entry requires a configured master password and fresh authentication, then one source-bound account selection and explicit consent. The recipient is the installed Steam package/UID; this does not authenticate an injected module or promise to recall copied credentials.

## JVM and source checks

- Monica's six new tests passed: exact request validation, UID isolation, replay prevention, expiry without a timer, cancellation wiping, bounded capacity/size and concurrent consumption.
- Millennium's four new JVM tests passed in an isolated Kotlin/JUnit harness using the actual `MonicaMaFileContract.kt`, `MaFileInput.kt` and their test sources. They cover nonce/version/exact URI validation, exact-limit/oversized input and working-buffer wiping on success and failure.
- Both manifests and string resources parse, new resource references resolve, no duplicate string names were found, and protocol constants agree between repositories.
- The wider Monica backup suite ran 25 tests, with 23 passing and the two pre-existing `SteamStandaloneDataBoundaryTest` failures already documented in [issue 15 verification](issue-15-mafile-authentication.md): the theme-label and MDBX/OneDrive-route source assertions. Those assertions were not weakened.

The upstream Millennium tree at `00d5712001f26c2987d5c0228fc719ffd885f01e` has no tracked `gradle/` directory: the wrapper JAR/properties and `libs.versions.toml` referenced by its build scripts are absent. A complete module build cannot be reproduced from that checkout; dependency versions were not invented or substituted. The isolated JVM harness checks the actual pure source files, not the module's Android/Compose packaging or Steam/LSPosed hooks.

The companion module change was committed as `54f5e90` and submitted as [Millennium PR #1](https://github.com/Good-Joe2049/Millennium/pull/1). At the user's request, it was marked ready for review on 2026-10-17; the PR description retains the outstanding complete-build and live-module verification limits. Monica Steam changes remain in the local `main` working tree; no Monica commit, release or tag was created for this handoff task.

## Final standard packaging

After the final autofill and background-cancellation changes, the standard build completed successfully on 2026-10-17 in 5 minutes 56 seconds:

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --tests 'takagi.ru.monica.steam.backup.share.*' --max-workers=2 --console=plain
```

The generated APK metadata confirms the production application ID `takagi.ru.monica.steamapp`, version `1.0.310` / code `18`, and both `arm64-v8a` and `armeabi-v7a` outputs. Both APK files exist and are nonempty. The final test XML reports six tests, zero failures and zero errors. The build emitted Kotlin deprecation/opt-in warnings and R8 Kotlin-metadata compatibility warnings; packaging still succeeded. No delivery copies, release or tag were created. The local build log is retained at `.codex-temp/steam-handoff-release-check-20261017.log` in the parent workspace.

## Device checks

Use the existing `Monica_Issue136_API_32` AVD. The app's `scripts/recovery-test.gradle` gives tests the isolated `takagi.ru.monica.steam.recoverytest` package. Only synthetic accounts are used. Do not clear data for the production app or change a device's real Steam installation.

The disposable host in `scripts/mafile-handoff-test-host/` uses Steam's package name solely to exercise the real exported activity and Android result-grant path from another UID. **Never install it over real Steam.** Check that no Steam package exists before installation and remove the host afterward. The fixture cannot target the production Monica package. Activity tests are opt-in through `-e monicaHandoffHost true`, verify the fixture label and fail closed outside the isolated app.

```powershell
./gradlew.bat -I scripts/recovery-test.gradle :app:assembleDebug :app:assembleDebugAndroidTest
# Build the fixture with scripts/mafile-handoff-test-host/build.ps1.
# Install only on the checked emulator, then run:
adb shell am instrument -w -r -e monicaHandoffHost true -e class takagi.ru.monica.steam.backup.SteamMaFileHandoffProviderTest,takagi.ru.monica.steam.backup.SteamMaFileConsentScreenTest,takagi.ru.monica.steam.backup.SteamMaFileHandoffActivityTest,takagi.ru.monica.steam.backup.SteamMaFileExportAuthenticationTest takagi.ru.monica.steam.recoverytest.test/androidx.test.runner.AndroidJUnitRunner
```

**All 16 device tests passed** (214.375 seconds). The eight new cases cover generic metadata, one read, denied replay, wrong UID, writes/altered URI rejection, selection without confirmation, missing master password, complete cross-UID export with repeated-open denial, fresh authentication on a second request, wrong-password cancellation, invalid protocol and background cancellation. The existing eight export-authentication tests also passed, including ordinary export without an optional master password, startup-bypass rejection, background/recreation cancellation and repeated-export verification.

The exported Activity cancels on both `onStop` and `onUserLeaveHint`, so rapidly returning from Home/Recents before `onStop` cannot preserve consent. The test harness subscribes to accessibility window/content events and waits for actual controls and window transitions; it does not treat a non-null Boolean as an awaited condition.

Cleanup removed the disposable Steam host after checking its fixture activity, retained Android user 0 as the active user and stopped the AVD started for this task. The public AVD registration, configuration and data disk were preserved. Test account/security cleanup ran through the instrumentation fixtures; production app data was not cleared.

## Visual checks and remaining coverage

The local M3E Canvas unlock, account-selection and module-import sketches are retained in ignored `docs/design/mafile-consent/`, with editable JSON and local URLs using separate `?project=monica-steam-mafile-<screen>` identifiers. Their actual contents were checked after loading. The Compose consent screen was rendered in the isolated test activity using synthetic data; the local screenshot shows continuous grouped corners, wrapped copy, the selected account and reachable bottom actions. Screenshots and Canvas data are not committed.

Physical biometric verification, a live Steam/LSPosed module session, real MDBX accounts and vendor-specific app-launch restrictions remain outside the synthetic tests. Opening an authorized URI may expose credentials to any code running in Steam's UID; the approval page discloses this boundary.
