# Final source and device-result review

2026-11-09; coverage_audit. Read-only review of current files against this task's captured `before/` tree and `raw/baseline-source-hashes.json`. No Gradle, device action, production edit, test modification, or assertion change was performed by this review. Final Release was still being built by root when this review completed; this report does not grant it a passing result in advance.

## Outcome

No new production blocker or weakened test assertion was found in the reviewed delta. The three latest Workshop fixtures retain every original assertion line (24, 14 and 27 respectively), and the original persistence upgrade test remains byte-identical to its original draft. The persistence diagnosis is a separate, explicitly gated test; its pass must not replace the original exact-file-hash failure.

Residual fixture limits are recorded below: the earliest scanner entry fixture has a suffix package guard, the background-worker fixture still needs clean synthetic state when repeated after a prior failure, and its HOME observation only asserts that the app is no longer the accessibility foreground package. None of these was disguised as a new production fix.

## Scope and production delta

- Rechecked 2,275 captured files under application production/JVM sources and the two local module source roots. Only the four already reviewed notification helpers differ. Their changes remain import plus explicit Intent target `MainActivity` to `MonicaSteamActivity`; no notification content, PendingIntent flags/request codes, account routing, authentication, storage, or scanner logic changed. The Steam manifest declares the intended Activity.
- `SecurityManager.kt`, `SettingsManager.kt`, the production Workshop runner/task store, and `SteamInboxInteractionUiTest.kt` remain byte-identical to `before/`. The successful long-press retry therefore did not result from changed timing assertions or a changed method body.
- All 33 captured Rust source/Cargo manifest/lock files match `raw/native-source-hashes.json`. The older native contract receipt reports original ARM library bytes unchanged. A source enumeration that does not include ignored binaries must not label the two pre-existing ignored ARM `.so` files as newly authored production code. The x86_64 test library is injected from this task's directory.
- The remaining production-affecting edits are the reviewed Gradle/AGP/Kotlin/dependency migration and the two narrow R8 reflection construction contracts. The latter preserve public no-argument constructors on `ComponentRegistrar` implementations and `InputMerger` subclasses; no whole-library keep or strict-mode disabling was added. Existing lint exclusions were not introduced by this round.
- The isolated init script sets the disposable package, signs its Release with the disposable Debug certificate, and supplies the offline Release manifest and test JNI/assets. These are task-only overrides. Production source manifest and release signing configuration are not rewritten by those overrides.
- `raw/source-delta-current.json` predates the latest three Workshop fixture edits. This review recomputed differences instead of relying on that earlier inventory. Final handoff should regenerate its delta inventory if it is presented as complete.

## Latest fixture changes

### WorkshopStageProgressUiTest

The old positional WorkInfo constructor no longer matched WorkManager 2.12.0. The replacement names `id`, `state=RUNNING`, `tags=emptySet()`, `outputData=Data.EMPTY`, `progress=data`, `runAttemptCount=0`, and `generation=0`. These preserve the intended values and adapt the tags parameter to its current Set type. It remains a synthetic running WorkInfo for progress rendering, not evidence that this class schedules a real worker.

All 24 assertion-containing lines are identical to the baseline, including progress/stage values, retained bytes, cancellation, large-font visibility and notification state. The entire seven-method class passed in both the first and second final Work batches.

### WallpaperDownloadUiTest

Only the imported Activity class and `createAndroidComposeRule` generic changed, from unregistered generic ComponentActivity to the existing registered `SteamComposeFixtureActivity`. All 14 assertion-containing lines are identical. Its two selected methods initially failed before their bodies because the Activity could not be resolved; both subsequently passed after this fixture change. Do not describe those initial failures as storage or zstd failures.

### SteamWorkshopBackgroundTest

The reviewed setup now:

1. Checks an exact allowlist containing the original disposable recovery package and this task's disposable pilot.
2. Creates a synthetic master password only if none is configured, and then only after verifying the local account table is empty. It does not overwrite an existing password or clear an account table.
3. Calls the existing setup helper to set token-page locking and enable password verification before opening the real Activity. This is a deliberate fixture settings mutation, not a read-only action or a production authentication bypass. It prevents first-time setup from covering the Workshop notification surface.
4. Uses a normal ACTION_MAIN/CATEGORY_HOME/NEW_TASK Intent instead of instrumentation HOME key injection. It retains the same observed-background assertion and timeout. It does not mock a lifecycle event or remove the requirement that the target stop being foreground.

All 27 assertion-containing lines remain identical, including request-ID equality, `WorkshopFailure.LOGIN`, empty results, eventual WorkManager completion, stop/result preservation and notification retry checks. The synthetic worker account is not inserted into the repository; production account resolution still terminates before Steam network calls.

Residual test limits:

- The HOME assertion is `assertNotEquals(targetPackage, rootInActiveWindow?.packageName)`. A null accessibility root can also satisfy it; this is retained baseline behavior, so the evidence is an actual HOME launch plus target-background observation, not proof of a specific launcher component or full lifecycle sequence.
- The real-worker method uses fixed synthetic account/game identity. `controller.start()` enqueues asynchronously, while `state.filterNotNull().first()` can observe an older persisted state with that same target. Additionally, its `goHome()` occurs before the method's `try/finally`, so the initial failed HOME attempt can skip that method's own cleanup. The first run failed HOME, and the second run failed the unchanged request-ID equality assertion. The later clean-pilot invocation passed in 5.673 seconds. This supports a contaminated-fixture explanation, but does not establish that arbitrary dirty-state reruns are reliable. Do not present the clean retry as a same-state reproduction or delete the original failure.
- Synthetic password/settings setup persists until root's disposable-package cleanup. This is acceptable for the explicitly reserved pilot, but the test is not a general shared-user test fixture.

## Persistence diagnosis integrity

`SteamToolchainPersistenceUpgradeTest.kt` still matches `drafts/SteamToolchainPersistenceUpgradeTest.kt` byte-for-byte, SHA-256 `9a64f9d219cad339c40813244760b9524497a7579ae05f479402279a1f25cadb`. Its final secure-preferences byte-equality assertion was not removed, broadened or bypassed. That original method remains a failure in the runner summary.

The new `SteamToolchainPersistenceDiagnosticTest`:

- Requires the exact pilot package, Debug flag, explicit diagnosis phase, complete SHA-256 arguments, original seed marker, a different process, and the exact preserved first-read secure XML hash.
- Loads the original encrypted XML only from the instrumentation APK asset, binds it to the seed's original file hash, and never copies it over live preferences. The asset is injected from the task directory rather than added to production assets.
- Requires both original Android Keystore aliases before constructing SecurityManager; requires identical XML key membership and identical encrypted keysets; permits exactly the encrypted values corresponding to `mdk_ready` and `mdk_keystore_blob` to differ.
- Authenticates every old/new encrypted preference value using the current keyset. Non-wrapper serialized plaintext must be equal, and both old/new readiness values must be true. It unwraps both compatibility envelopes and the unchanged password envelope and requires the same complete 32-byte MDK, without printing the key or its hash.
- Separately verifies the original password proof and rejects an incorrect proof without calling `verifyMasterPassword`; checks original ciphertext, exact encrypted and plaintext account hashes, account count, DataStore values and both dock orders.
- Compares all three protected files and the original marker against their entry bytes on every one of six later samples. It does not merely wait for a rewritten value to stabilize.

The unchanged production `verifyMasterPassword` calls `ensureMdkInitializedWithPassword` after successful authentication; that code writes `mdk_ready` and refreshes the compatibility wrapper. This explains why successful original-password verification can change randomized ciphertext without changing the actual MDK. The diagnosis proves that specific explanation for the preserved synthetic first-read artifact; it does not turn the original byte-preservation assertion into a pass.

The `read_only=true` diagnostic result is scoped to the three protected preference/DataStore files and marker plus the asserted account values. Normal production constructors/read APIs can update diagnostic logs or in-memory caches; the test does not claim a globally write-free process, database-file byte equality, or proof that every key on the device is unchanged. Its six-sample check is bounded and not a proof against all future delayed writes. No such broader claim is required for the stated diagnosis.

The first diagnostic invocation failed its malformed/short marker-hash argument before examining the retained data. The corrected argument used the same APK and passed in 1.786 seconds. Both receipts remain in the summary.

## Other protection checks

- The Release reflection test now explicitly rejects a Debug target, resolving the earlier audit finding. Native Release and actual Activity scanner tests also retain exact package/non-Debug guards; the scanner Activity additionally requires INTERNET to be absent and CAMERA initially denied.
- The earliest `SteamScannerEntryTest` still checks `packageName.endsWith(".toolchainpilot")`, unlike the later exact-package scanner fixtures. Its only direct permission mutation is granting CAMERA; it does not clear data. Root's driver checks the precise isolated application ID before installation and targets the precise instrumentation component. Root explicitly retained this already-tested low-impact fixture during final Release work; do not claim every test has an exact internal guard.
- The migrated maFile/session fixtures retain the exact old recovery-package/new-pilot allowlists, consent/authentication assertions and Handoff opt-in/host checks. No suffix-based destructive cleanup was added.
- The scanner lifecycle/gallery suite still requires empty local accounts and no saved QR account. It uses real ML Kit decoding but a supplied ActivityResultRegistry URI; this is not system-gallery-picker coverage.
- No R8 result is inferred from the new Debug pass, and no x86_64 emulator result is represented as an ARM64 phone/OEM or API36 validation.

## Raw device-summary audit

Reparsed every terminal `INSTRUMENTATION_STATUS_CODE` bundle in the 17 raw runner logs listed by the current `raw/device-summary.json`, using `(class, test)` identity. Start events are excluded; repeated exception stack entries are not counted as extra test methods. Every summary passed/failed/skipped count matches its raw method statuses; no mismatch was found.

| Stage | Distinct methods | Passed | Failed | Runner seconds | Interpretation |
| --- | ---: | ---: | ---: | ---: | --- |
| final-scanner-native-chat | 12 | 12 | 0 | 37.126 | Final dependency scanner/lifecycle/ML Kit/JNI/chat batch |
| final-scanner-denied | 1 | 1 | 0 | 3.896 | Actual separate permission denial |
| final-ui | 36 | 35 | 1 | 217.950 | Long-press interaction timed out |
| final-longpress-retry | 1 | 1 | 0 | 16.815 | Same unchanged method, separate invocation |
| final-work-storage | 13 | 7 | 6 | 73.802 | Ten JUnit exception entries across six failed methods |
| final-work-storage02 | 13 | 12 | 1 | 38.251 | Fixed worker identity observed a different request ID |
| final-worker-clean | 1 | 1 | 0 | 5.673 | Same worker method after owned-pilot reset |
| final-security-storage | 16 | 16 | 0 | 63.464 | Authentication/consent/storage checks |
| session-after-upgrade | 2 | 2 | 0 | 0.924 | Session storage before pilot reset |
| persistence-seed | 1 | 1 | 0 | 2.085 | Old-library fixture setup |
| persistence-verify | 1 | 0 | 1 | 6.593 | Original exact post-read secure XML hash failed |
| persistence-diagnostic | 1 | 0 | 1 | 0.050 | Invalid marker-hash argument |
| persistence-diagnostic02 | 1 | 1 | 0 | 1.786 | Same retained MDK/data diagnosis |
| final-release-seed | 1 | 1 | 0 | 1.754 | Offline Release fixture setup only |
| scanner-before | 1 | 1 | 0 | 14.428 | Old baseline Debug, not final result |
| scanner-newtoolchain | 12 | 12 | 0 | 31.358 | Before AndroidX dependency batches |
| scanner-denied-newtoolchain | 1 | 1 | 0 | 2.447 | Before AndroidX dependency batches |

The first Work runner's `Tests run: 13, Failures: 10` enumerates four Workshop methods with both body/teardown failures (eight exception entries) plus two unresolved-Activity failures (two entries). Its actual method results are **7 passed and 6 failed**. The summary already records this correctly.

Across the final functional groups, **80 different methods have at least one passing receipt**: scanner/JNI/chat 13, UI 36, Work/export/progress 13, security/storage 16, session storage 2. This excludes both setup seeds and the separate persistence diagnosis. It is coverage assembled across batches/retries, not one all-green 80-method run. The original persistence verification still failed. The 12 JVM baseline failures and 8 JVM skips remain as separately audited in `baseline-failure-audit.md`; no source guard was weakened to convert them to passing tests.

## Reviewed fixture hashes

| File | SHA-256 |
| --- | --- |
| WorkshopStageProgressUiTest.kt | `46de489e76724e9fb2378aa2bcdde9b76b2de12f2e15c3f482a773122de3184c` |
| WallpaperDownloadUiTest.kt | `68568aa6045a65dd585b1104b5962e9471b73ba30340faaa20841a34398fc7a5` |
| SteamWorkshopBackgroundTest.kt | `f3febf7b069bc2a5a3456737aa25775239cef3e498bb85ad3da5bb3ef89ad579` |
| SteamToolchainPersistenceDiagnosticTest.kt | `9a358522c014e51efb85fb0e2dcb578b84f7f37f8c2bbc3c403d4373f04c6d68` |

Final Release compilation, shrunk reflection/JNI/real scanner navigation receipts, latest artifact hashes and cleanup remain root-owned follow-up evidence. This review made no changes to them.
