# Monica Steam toolchain and scanner verification — 2026-11-09

Scope: Monica Steam only, unreleased 1.0.310/code18. Existing chat, filter and glass changes were preserved. No change to targetSdk34, minSdk26, database schemas, encryption implementation or formats. No commit, push, release or APK delivery.

## Phone scanner failure and fix

The supplied vivo V2309A Android16/API36 log identifies Release `1.0.310-26110918-24`. Its R8 map ID matches the preserved local signed ARM64 APK: `54783c22e86745ead957784d6a4f0a864dda4deaa008b8b8e3aa99313f72fe59`; APK SHA256 `0a0954dccf306cd792fcaf92c6682cc4aeb9d127ea67a3ffb91d6f80d21cfa6e`.

All three ML Kit registrar classes and `androidx.work.OverwritingInputMerger` remained in that APK, but their public no-argument constructors were absent. The phone reported matching `NoSuchMethodException`s, followed by ML Kit initialization NPE. Retracing reaches `BarcodeScanning.getClient`, `QrCameraScanSession.kt:301`, `QrScannerScreen.kt:188` and `SteamQrScannerScreen.kt:174`.

`app/proguard-rules.pro` now preserves exactly the reflected public constructor contract for `ComponentRegistrar` implementations and `InputMerger` subclasses. R8 remains enabled. Strict-mode R8 fixtures reproduce removal with class-only rules and preserve construction with explicit member rules on R8 9.3.16 and 9.5.29. The first CLI fixture without strict mode did not reproduce; that result is retained. Old Debug scanner passed, so Accompanist is not claimed as the crash cause.

Only four production Kotlin files changed during this pilot: legacy notification helpers now reference the declared `MonicaSteamActivity` instead of excluded `MainActivity`. This was required when AGP built-in Kotlin began honoring the source exclusions. Scanner production code was unchanged.

## Selected versions

| Component | Final selection |
| --- | --- |
| Android Studio | Rabbit 2 2026.2.2 Patch 1, build262.10968.63.2622.16840690 |
| AGP / Gradle | 9.5.1 / 9.7.0, official Gradle distribution SHA pinned |
| Kotlin / Compose compiler / Serialization compiler | 2.4.21 |
| KSP / Room | 2.3.13 / 2.8.5 |
| Compose BOM / resolved UI / Material3 | 2026.09.00 / 1.12.1 / 1.5.0-rc01 |
| Core / Activity / Lifecycle / Navigation | 1.19.1 / 1.13.0 / 2.11.0 / 2.10.2 |
| CameraX / Accompanist | 1.6.2 / 0.37.3 |
| Coroutines / Serialization runtime | 1.11.0 / 1.12.0 |
| DataStore / WorkManager / Security Crypto | 1.3.0 / 2.12.0 / 1.1.0 |
| Fragment / Material Views / RecyclerView | 1.9.1 / 1.14.0 / 1.4.0 |
| Splashscreen / DocumentFile / Reorderable | 1.2.0 / 1.1.0 / 3.1.0 |
| Test ext-junit / Espresso | 1.3.0 / 3.7.0 |
| Gradle JVM / compileSdk | installed Studio JBR25.0.4 / 37 |

Application and MDBX bytecode target11 and Telegram target17 remain unchanged. Legacy Kotlin Android plugin and new-DSL opt-outs were removed. APK output naming uses public `androidComponents.onVariants(selector().all())`. Global Kotlin/Serialization forcing was replaced by BOM alignment; compiler-processor dependencies can select their own supported runtime.

Gradle9.9 exceeds Kotlin2.4.21's documented upper limit9.7, so9.7 was selected. AGP9.5.1 exceeds the Kotlin matrix's listed9.3.1 upper bound: this is an empirically tested combination, not a claim of complete official-matrix coverage. Existing preview Biometric and copied legacy Credentials/Koin/etc. were not indiscriminately upgraded. Official version discovery and candidate AAR requirements were independently rechecked; resolved graphs, not just catalog declarations, were recorded. Three dependency groups have separate source backups but were validated in a combined build.

Final IDE sync completed at10:57:47 in5m7.230s with JBR25. Project-only `.gradle/config.properties` was backed up and pointed to that installed runtime. Global Gradle/JDK settings were not edited. The earlier08:45 sync is retained as baseline only.

## Data preservation

PasswordDatabase76 with98 DDL statements and SteamDatabase6 with11 statements retain the same Room identities. Both Debug and Release generated implementation files are byte-identical to the old baseline.

The old-storage APK used DataStore1.0.0 and SecurityCrypto1.1.0-alpha06. It created an encrypted synthetic account, password, ciphertext and non-default settings, with a marker written last. The upgraded APK was installed with `-r`, retaining data. In a different cold process, all pre-read file hashes matched, the old password/ciphertext/account and DataStore values were valid. The final exact secure-preferences byte assertion failed because successful password verification refreshes `mdk_ready` and the compatibility Keystore envelope. The original failure remains recorded.

A separate read-only diagnostic authenticated every old/new encrypted preference, required exactly those two changed ciphertext fields, verified unchanged keysets and all other values, and decrypted old/new compatibility envelopes plus the unchanged password envelope to the same actual32-byte MDK. Both DataStore files and the seed marker remained byte-identical; diagnosis did not change any protected bytes. This is preservation with an expected envelope refresh, not a claim that the whole preferences file remains unchanged. No recovery/reset was inserted into verification.

## Validation and failures retained

The final large JVM invocation ran1705 tests:1685 passed,12 failed,8 skipped. Independent execution of the same compiled failing methods against both before-snapshot and current source reproduced all12 at the same assertion lines; these check old directory/version/call strings, including two CRLF-sensitive assertions. No guard was weakened. Six host-native tests lacked their opt-in/native prerequisites; two live-network tests lacked explicit opt-in and fixture credentials. These are not passes.

Final Debug device coverage contains80 distinct functional methods with passing results:13 scanner/JNI/chat/permission,36 UI/navigation/lifecycle,13 WorkManager/file processing,16 security/export/storage, and2 session-storage. UI first batch was35/36; unchanged long-press method separately passed. Work first batch had13 methods,6 failed and10 exception entries including teardown failures. The fixture was blocked on initial password setup, HOME-key handling, and an undeclared generic ComponentActivity. Test-only changes registered the actual fixture Activity, initialized a synthetic password only in exact disposable packages with empty accounts, and launched actual HOME while retaining foreground assertions. Second Work batch was12/13; the fixed-ID worker fixture saw residual request state, and the same method passed after preserving and clearing only pilot data. No all-green initial-batch claim is made.

Scanner Debug checks require real CameraX streaming and disposal, real ML Kit decoding of a synthetic gallery URI, invalid QR rejection, single acceptance, and actual denied-camera behavior. The gallery ActivityResultRegistry is synthetic; this does not certify the system picker. Native tests require JNI availability and reject fallback. UI checks cover exclusive animated filters, native editor/IME/drafts/history, style/glass surfaces, drawer cancellation and page lifecycle. 27 representative synthetic screenshots were saved only in ignored `docs/design`; selected filter and large-text search images were inspected. Synthetic frame samples had warm median327ms before dependency batches and481ms afterward during IDE indexing; this is not a controlled performance benchmark or a phone smoothness claim.

Rust1.86/cargo-ndk4.1.2/NDK28.2 rebuilt3 ABIs in144.550s with locked Cargo dependencies. ARM outputs matched the existing libraries exactly; x86_64 was injected only for isolated testing. All20 JNI symbols matched;64-bit Steam libraries have16KiB LOAD alignment. The initial checker incorrectly demanded16KiB for32-bit ARM and its failure is preserved. MDBX was not rebuilt; this does not certify Monica Android/F-Droid vaults.

The first attempt to run the Debug instrumentation APK against Release crashed in the AndroidX test runner before any tests, because Debug packaging excluded Kotlin runtime classes that R8 renamed/removed from the target. A separate test-only application harness packages its own Kotlin/AndroidX test dependencies and copies the same three Release test classes without static production references. The production Release APK and its shrink rules remain unchanged. This in-process harness passed the reflection and four JNI methods, then its Activity method failed with an ICCE. Exact DEX inheritance proves a shared-name CoroutineExceptionHandler collision between harness runtime1.8.1/unrenamed Kotlin and target runtime1.11.0/R8-renamed Kotlin interfaces. The same Release launched independently. Moving UI automation into the harness's own process removes this collision without changing production keeps. The first external attempts exposed cached accessibility trees and an asynchronous disabled Unlock button; refreshing nodes and waiting for an enabled clickable control fixed only the test driver.

A later external attempt unlocked the token page but found no seeded account. A platform SQLite read-only check confirmed zero rows and the archived database had no sequence entries. The first seed had passed before the emulator was stopped for the Release build, but no independent post-stop database snapshot was captured then, so the exact loss interval/cause cannot be established. This remains an unresolved first fixture-state observation. Only the owned pilot was archived/reset, then the same unchanged Debug seed passed again. A cold pre-update SQLite archive independently confirmed one encrypted account; installing the exact same Release in place retained it. The read-only Release diagnostic passed before UI execution, the real scanner UI passed, and another complete emulator reboot retained that encrypted row/schema. These later checks establish the tested update/reboot behavior without converting the first empty-seed attempt into a pass.

Build history includes failed missing-permission-test dependency, Groovy selector migration, four excluded-Activity references, WorkInfo positional test arguments, and diagnostic Tink compile-classpath failures before their corrections. Final test fixtures use named WorkInfo arguments and reflection only in the isolated diagnostic. A truncated marker argument failed before data access and was corrected from the saved bytes. Raw evidence remains under `.codex-tasks/20261109-toolchain-pilot/raw`.

## Release and cleanup

Real R8 Release built successfully in 683.009s. All 13 APK audit checks passed: four public reflected constructors present with matching map ID, minification/resource shrink evidence, non-debuggable offline pilot manifest, valid signature and ZIP16KiB alignment, and all three ABIs with valid64-bit ELF16KiB alignment. Six different Release functional methods obtained passing receipts across separate runs: five reflection/native methods passed in final-release02 before its Activity method encountered the documented harness collision; the external Activity method passed1/1 in 23.467s in final-release-external05. The latter exercised actual password unlock → token page → scanner → camera permission → return → repeat entry with existing permission. This is not a single all-green six-method suite. APK SHA256: `3733c0e22a5b851fc875369c8eddfa8af5da64e039384d80c0748b2d1281b140`; a pull of the actually installed APK matched this exact hash. This verifies initialization/navigation on API32; Release camera frame decoding itself was not asserted.

The isolated Release uses exact package `takagi.ru.monica.steamapp.toolchainpilot`, test signing and an INTERNET-removing manifest overlay; production source manifest/signing configuration is unchanged. Minification and resource shrinking remain enabled. Release tests distinguish real Activity unlock/navigation/permission/initialization from Debug frame/decode assertions. No real Steam login, purchase, approval, cloud account or user vault was used.

Only the three owned pilot/Debug-test/Release-harness packages and task-owned temporary device files were removed. Pre-existing normal Steam and its test package remain installed; users0/10/11/12/14 and the shared AVD registration/configuration/data disks were preserved. The emulator instance started by this task was stopped. MuMu was not changed. Project JBR25 remains selected for the successfully synchronized Steam project.

Validation is limited to the shared API32 x86_64 emulator and synthetic data. The supplied API36 vivo phone was diagnosed from its matching release log/APK; this device was not connected for final validation. API33+ glass shader, ARM64 execution, OEM camera behavior,16KiB device execution and live Steam services are not certified by these results. MuMu/KMP and Monica Android/F-Droid builds/data were not modified by this pilot.
