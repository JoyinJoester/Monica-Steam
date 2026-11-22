# Precise baseline failure audit

2026-11-09. No Gradle, device actions, production edits, or test assertion changes were performed.

The captured 401 XML files contain **1705 tests: 1685 passed, 12 failed, 8 skipped, 0 errors**. A passing build/test claim is not appropriate.

All 10 failed-test source files are byte-identical to the captured `before/` tree and match its original SHA-256 manifest. Each of the 12 exact compiled test methods was replayed twice with a standalone Java harness: once reading `before/`, once reading the current tree. All 24 invocations raised AssertionError at the same source line as the Gradle XML. This establishes the precise baseline failures without rerunning Gradle or editing assertions.

The replay uses the current JDK and frozen compiled tests whose source bytes match baseline; it is not a rebuild with the old toolchain. It only establishes each method through its first failed assertion, not its later assertions or production UI behavior.

| Test method | First failed line | Precise reason | Baseline/current replay |
| --- | --- | --- | --- |
| `SteamModuleArchitectureGuardTest#documentedFeatureAndSharedModulesExist` | 63 | Exact root module allowlist omits existing design, itad, links, richtext, space, steamdb, web and workshop modules. | same failure line in both |
| `SteamModuleArchitectureGuardTest#storeUsesLayeredPackagesAndNamedSlicesWithoutRootImplementations` | 155 | Exact Store child-directory allowlist omits existing interest, share and wallet slices. Later source reads are never reached. | same failure line in both |
| `SteamStandaloneDataBoundaryTest#standaloneSettingsExposeFullThemeSelection` | 22 | Settings routing source lacks the asserted R.string.color_scheme literal; earlier ColorSchemeSelectionScreen and CustomColorSettingsScreen assertions pass. app/build.gradle changed, but is not the operand of the failing assertion. | same failure line in both |
| `SteamStandaloneDataBoundaryTest#standaloneMdbxRouteSupportsLocalAndWebDav` | 93 | The assertion forbids OneDrive creation, but the pre-upgrade Activity already contains its route. This is an old test expectation versus implemented surface mismatch, not proof of a runtime MDBX failure. | same failure line in both |
| `SteamAccountSourceSessionGuardTest#sessionWritesAcceptAnExplicitOriginAndReloadMdbxAfterSourceSwitch` | 24 | The substringAfter delimiter contains LF, but the checked file has CRLF. The delimiter is absent, so Kotlin substringAfter returns the whole source; substringBefore then captures an earlier selected-source overload and the forbidden when expression. This does not demonstrate that the explicit-origin overload selects the wrong source. | same failure line in both |
| `SteamLibraryIntegrationGuardTest#accountDataUsesPlainCompactCardsAndGoogleSansFlex` | 22 | The guard pins alpha18 literally; baseline and current catalog both have material3Expressive 1.5.0-rc01. The catalog has other changes this round, so whole-file identity cannot be claimed; the failed version predicate is identical. | same failure line in both |
| `SteamPlusPaymentFlowRegressionGuardTest#steamPaymentPageUsesDockSafeModifier` | 53 | The literal expects LF between Scaffold and modifier; baseline/current PaymentScreen has CRLF. The actual modifier assignment is present. This text failure does not establish a missing dock-safe modifier at runtime. | same failure line in both |
| `SteamSettingsScrollStateGuardTest#settingsRootOwnsScrollStateAcrossChildPageNavigation` | 24 | The guard requires an appSupportScrollState variable no longer present in the pre-upgrade settings router. No functional scroll-state conclusion is drawn from this name-based guard. | same failure line in both |
| `SteamSettingsVersionLabelGuardTest#settingsVersionFollowsBuildConfigInsteadOfAStaleLiteral` | 24 | The guard literally expects 1.0.309; baseline/current app version is 1.0.310 and code18. The build script changed elsewhere this round, but this version predicate was already false. Five resource paths are constructed before failure but their readText calls occur after the failed assertion and are not reached. | same failure line in both |
| `SteamStoreDetailInteractionGuardTest#screenshotsOpenTheDedicatedFullscreenViewer` | 44 | The asserted Modifier.background(Color.Black) spelling is absent; the pre-upgrade viewer passes backdropColor = Color.Black to its fullscreen dialog component. The test stops before later assertions, and this audit does not certify its rendered pixels. | same failure line in both |
| `SteamIdentityInfoCardGuardTest#friendAndProfileDetailsReuseTheSameIdentityDisclosure` | 55 | The exact one-argument invocation string is absent because the pre-upgrade call includes modifier padding and embedded=true. The shared identity card is still called; this is a brittle literal match. | same failure line in both |
| `SteamSettingsChildPagesTest#sharedSettingsSupportsCompactHomeAndDedicatedChildModes` | 31 | The pre-upgrade implementation iterates visibleCompactHomeSections.chunked(resolvedColumns).forEach, so the earlier direct-forEach spelling guard is false. | same failure line in both |

## Source identity details

There are 20 unique files read or existence-checked before failure; 18 are byte-identical to baseline. All baseline copies match their captured hashes. Every source file actually read before failure is listed with baseline, captured-manifest and current SHA-256 in `baseline-failure-audit.json`. Snapshot hashes were checked against `raw/baseline-source-hashes.json`. Exceptions to whole-file identity are reported rather than concealed:

- `standaloneSettingsExposeFullThemeSelection` reads changed files: `app/build.gradle`. The exact failed predicate is unchanged and the unmodified test method reproduces against both trees.
- `accountDataUsesPlainCompactCardsAndGoogleSansFlex` reads changed files: `gradle/libs.versions.toml`. The exact failed predicate is unchanged and the unmodified test method reproduces against both trees.
- `settingsVersionFollowsBuildConfigInsteadOfAStaleLiteral` reads changed files: `app/build.gradle`. The exact failed predicate is unchanged and the unmodified test method reproduces against both trees.

The two architecture failures enumerate directories instead of reading file contents. The observed directory names match in both roots; the JSON includes a captured Kotlin-file witness and verified baseline/current hashes for every listed directory. The root allowlist omits eight modules; the Store allowlist omits three slices. No generated/empty directory was used as the sole explanation.

The `PaymentScreen` and explicit-session guards preserve raw CRLF during replay. Python newline normalization would hide their precise failure, so byte decoding and actual Kotlin File.readText behavior were used. The session guard’s missing LF delimiter causes substringAfter to retain the entire file and accidentally include the earlier selected-source overload.

## Eight skipped methods

| Method | Exact abort condition | Count as pass |
| --- | --- | --- |
| `SteamNativeIntegrationTest#cmRoundTripUsesBothNativeEntryPoints` | The @Before assumption Boolean.getBoolean("steam.native.required") is false; each of five methods aborts before the native availability assertion or test body. | No |
| `SteamNativeIntegrationTest#ownedGamesCrossesJniAndDecodesUnicodeAndUnsignedFields` | The @Before assumption Boolean.getBoolean("steam.native.required") is false; each of five methods aborts before the native availability assertion or test body. | No |
| `SteamNativeIntegrationTest#sharedGameLastPlayedCrossesJniWithoutInventingRecentMinutes` | The @Before assumption Boolean.getBoolean("steam.native.required") is false; each of five methods aborts before the native availability assertion or test body. | No |
| `SteamNativeIntegrationTest#malformedNativeInputReturnsNullWithoutCrashingJvm` | The @Before assumption Boolean.getBoolean("steam.native.required") is false; each of five methods aborts before the native availability assertion or test body. | No |
| `SteamNativeIntegrationTest#legacySecretImportRemainsCompatibleWhenNativeLibraryIsLoaded` | The @Before assumption Boolean.getBoolean("steam.native.required") is false; each of five methods aborts before the native availability assertion or test body. | No |
| `SteamInventoryLiveTest#realMaFileEligibilityCheckReturnsInventoryOverview` | The STEAM_LIVE_MAFILE path does not point to a file in this invocation; the explicit assumption aborts before reading credentials or contacting Steam. The XML alone cannot distinguish unset from a nonexistent path. | No |
| `WorkshopLiveDownloadTest#publicWallpaperAccessProbe` | MONICA_LIVE_WORKSHOP == "1" is false; the explicit assumption aborts before constructing the live download fixture or contacting Steam. | No |
| `WorkshopSubscriptionOrderTest#nativeOrderMatchesKotlinForEverySortAndExtremeValues` | nativeAvailable() reaches assumeTrue(RustSteamCoreNative.isAvailable), which is false. This run does not establish host JNI ordering behavior. | No |

All four skipped-test source files are also byte-identical to baseline and match captured hashes. The five core JNI skips occurred at the host-native opt-in assumption; they do not show a library load failure. The separate Workshop ordering skip does show native availability false at its assumption. The two live tests were deliberately gated before their live actions; no real-account/network coverage is claimed.

## Reproducibility and retained outputs

- `audit-baseline-failures.py`: runnable audit/replay driver; no Gradle invocation.
- `raw/baseline-failure-replay/classes/`: frozen copies of the already-compiled guard classes plus the tiny Java harness.
- `raw/baseline-failure-replay/01-baseline.txt` through `12-current.txt`: all original replay stack traces, including the expected failures.
- `baseline-failure-audit.json`: individual source/XML/class/library hashes, exact commands, CWDs, observed predicates, directory witnesses and skip classifications.

These findings separate the 12 demonstrated pre-upgrade text/directory failures from this upgrade. They do not make the overall JVM run green and do not authorize weakening its guards.
