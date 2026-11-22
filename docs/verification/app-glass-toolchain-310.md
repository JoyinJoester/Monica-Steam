# Application glass and toolchain — 1.0.310

Status: implemented and scoped verification completed, November 8–9, 2026.
API36 remains unverified because its emulator driver crashes. No release, push,
or APK delivery.

## Scope

Existing page layouts and callbacks are retained. Shared Scaffold slots record
their body separately from toolbars, navigation, floating buttons and snackbar
slots. Menus, confirmation dialogs, sheets and the store drawer use the separate
body source; screen coordinates account for their independent windows. The
source is recorded only while there are material consumers. WebView destinations
retain the existing glass compatibility gate.

Liquid Glass uses Kyant Backdrop 2.0.1 blur, edge refraction and highlights. M3E
uses a more opaque theme tint and restrained blur, retaining Material components.
The existing Miuix source remains shared with the liquid Dock. Native Telegram
toolbar/composer/menu views use Kyant's Apache-2.0 shader through a RenderNode
bridge and a per-view design-style setting. Text, touch targets, editors and
draft state are outside the background blur. API31–32 uses platform blur;
API33+ can apply runtime refraction; older/software rendering uses theme fills.

Conversation filters remain mutually exclusive and now animate one shared
selection background over 220ms. The reduced-motion preference uses an immediate
selection update; the Compose frame clock also honors platform animation scale.

## Build migration

| Component | Version |
| --- | --- |
| Kotlin/compiler | 2.4.21 |
| Compose BOM | 2026.09.00 |
| Material3 | 1.5.0-rc01 |
| AGP | 9.3.2 |
| Gradle | 9.5.0, distribution checksum pinned |
| KSP | 2.3.13 |
| Room | 2.8.5 |
| Kyant Backdrop | 2.0.1 |
| compileSdk | 37 |
| targetSdk / minSdk | 34 / 26, unchanged |

JDK17 is used. The explicit external Kotlin compiler and legacy APK naming DSL
remain enabled through AGP9's supported migration flags; AGP10 will require a
separate DSL migration. Metadata and compileSdk-check bypasses were removed.
The removed non-optimizing default ProGuard file was replaced with the supported
optimizing default. Five mixed SQL argument arrays now explicitly use `Any?`;
values and SQL are unchanged. Legacy fingerprint enrollment checks use AndroidX
FingerprintManagerCompat, retaining the fingerprint feature check and strong
biometric requirement.

Room's generated schema was compared with the pre-upgrade generated implementation:
all 98 CREATE TABLE/INDEX statements and both current/legacy identity hashes
match. This verifies the schema contract, not every historical migration.

## Device and JVM verification

All fixture data is synthetic and isolated in `takagi.ru.monica.steamapp.appglass`.
The tests exercise production composables and native Views without a logged-in
Steam account, outbound chat messages or existing vault data.

| Check | Result |
| --- | --- |
| JVM after toolchain migration | 103 passed, 0 failed/error/skipped; 40.170 s |
| Final main/test build, final-07 | Passed, 242.538 s; 94 chat/navigation JVM tests passed |
| Test-only fixture update, tests-08 | Passed, 113.887 s; production APK unchanged |
| API32 glass final02 | 4/4, 47.742 s |
| API32 regression01 | 27/34, 189.112 s; seven fixture failures retained |
| API32 remaining final03 | 8/8, 31.370 s, including the new sheet-handle test |
| API35 final08 glass/native suite | 8/8, 57.541 s |

API32 has passing results for 39 distinct methods across the final batches, not
an initially all-green batch. Coverage includes actual moving filter frames and
exclusive selection, live sibling/popup pixels and source disposal, both styles
and light/dark themes, toolbar/FAB/sheet/dialog callbacks, sheet handle
expand/collapse/click and dismissal veto, six drawer dismissal/cancellation
cases, conversation entry/Back and wallpaper caching, chat drafts, IME-facing
scroll anchors, group dialogs and native search/palette/system text scale.
API35 verifies both Compose materials and native RuntimeShader creation without
silent fallback, plus changing source pixels and retained editor state.

The regression failures were reviewed before updating tests. One older social
scenario still expected unread plus group intersection; its assertions now
require the independent group results and then only unread results. Six drawer
tests could not launch the generic ComponentActivity removed from the debug
manifest. They now use the declared non-exported SteamComposeFixtureActivity
and a real Liquid Glass source host. Original assertions for callback count,
window dismissal and navigation cancellation remain. No production behavior
was changed to accommodate those seven tests.

The final API32 real-list-to-80-message-thread sample completed eight opens and
returns: 1224 ms first entry, 335 ms median for the next seven. These software-GPU,
2-CPU Debug emulator observations are not physical-device frame-rate claims.
The earlier before/after wallpaper investigation remains documented in
`chat-entry-filters-310.md`; its bounded asynchronous decoration cache is retained.

The local three-screen editable Canvas and the final API32/API35 screenshot
contact sheets were inspected. All images remain under ignored
`docs/design/app-glass-20261108`. Sheet/dialog controls and material differences
are visible. Some screenshots immediately following dialog dismissal capture
Android window exit frames, so those are not presented as settled still images.
Canvas is a placement draft rather than evidence of optical rendering.

## Failures found and fixed

The task-start dirty-tree audit covers 210 source paths: 151 imports-only,
55 body changes and four new files. Pre-existing unrelated changes were retained.
The task introduced no trailing whitespace; Git diff checks pass.

The first configuration attempt used the removed `proguard-android.txt`; the
supported optimizing default replaced it. A compiler download stalled and only
the owned build was cancelled; the official Maven artifact was fetched and
verified against its published SHA1. Subsequent compilation identified five
SQL array intersection types, the removed fingerprint SDK symbol and three
obsolete test temp-directory calls. These were migrated explicitly. A JVM worker
then failed to start under Windows OS1455/native-memory pressure; that attempt
is retained as a failure.

The first API35 application run exposed `AbstractMethodError` in
`CustomStyle.applyStyle`, followed by a detached LayoutNode exception.
Material3 alpha18 was incompatible with the selected Compose family. The
replacement rc01 official metadata targets Compose 1.12.0 and was validated
with resolved UI/foundation 1.12.1. The removed menu-anchor overload was migrated
to its former `PrimaryNotEditable` behavior, relocated menu imports were
updated, and four expressive LoadingIndicator sites received explicit opt-ins.

Unobscured API35 pixels revealed that a separate-window menu retained old source
colors. Separate-root consumers now subscribe to source draw generations;
same-root consumers retain GraphicsLayer invalidation. API32 and API35 verify
that source changes reach the popup and recording stops after dismissal.

A bottom sheet was clipped by the glass modifier before Material3 translated it.
Glass now draws inside the sheet's moving content, retaining insets, handle
click/accessibility actions and SheetState confirmation. Final tests verify
visibility, content actions, expansion/collapse and rejected/allowed dismissal.
The narrow-screen filter screenshot helper was separately corrected to translate
clipped semantics bounds from window to screen coordinates without double padding.

## Emulator and validation limits

The shared API32 AVD was reused. API35 used the existing Pixel Fold 16KiB AVD;
these UI tests do not certify all native libraries for 16KiB pages. No AVD was
created and no configuration/data disk was deleted.

The existing API36 4KiB AVD repeatedly crashed in SurfaceFlinger RegionSampling,
`mapper.ranchu`, with `hasReadColorBufferDma` assertion before app validation.
Different runtime graphics/DMA switches did not resolve it. No API36 pass is
claimed. API35 also had system_server package-scan crashes and one Windows
emulator access violation, 0xc0000005. The 12-method final05 run emitted ten
passing method results before the host crash during the entry frame sample;
it did not complete. Some image files after that host crash were zero bytes
and were excluded from visual evidence. A premature boot install failed;
a later instrumentation startup ANR occurred alongside guest low-memory kills
and a system_server restart. These results remain failures, with cause not
attributed solely to the app or solely to memory. On the same installed APKs,
the final eight-method glass/native suite completed successfully. That scoped
result does not convert the earlier interrupted 12-method run into a pass.

Verification covers synthetic x86_64 emulators and representative components,
not every page, OEM/physical device, real Steam network or every vault migration.
Target/min SDK remain 34/26; legacy solid-fill behavior is retained but was not
newly exercised on an API26–30 device in this task.

## R8 and cleanup

R8 9.3.16 with code optimization and resource shrinking completed in 919.355 s,
using one worker, two CPUs and a 4 GiB heap. The first R8 invocation stopped in
the temporary dependency-report task because Gradle 9 required a precise
artifact variant. The report now reads resolved component metadata; the next
build passed without changing application sources or suppressing shrinker errors.
Resolved versions match the table above, including UI/foundation 1.12.1.

The isolated `.appglass.r8` package is non-debuggable, code18/version1.0.310,
compileSdk37, target34 and min26. API32 smoke checks completed first-password
setup using synthetic input, the main store's offline/error state, opening and
closing the store drawer through Settings navigation, Appearance/Design style,
and switching M3E to Liquid Glass. A force-stop and new PID (2462 to 5523)
retained the selected Liquid Glass option; XML confirms M3E unchecked and Liquid
Glass checked. Cold activity launch reports were 2768 ms initially and 742 ms
after setup. The crash buffer is empty; exit-info records only the explicit
force-stop. This is a smoke test, not the full instrumentation suite on R8 or
proof of real-service availability.

Two initial rapid ADB text-entry attempts produced mismatched confirmation:
inspection showed dropped/reordered synthetic characters. Paced single-character
entry was verified before continuing and completed setup. No input code or
assertion was changed; this automation behavior is not counted as successful
rapid-input validation. R8 screenshot attempts produced zero bytes with screenshot
protection enabled, so R8 checks use actual UI hierarchy/action and process
evidence, not visual screenshots.

Work stayed in the independent Monica Steam repository. After the explicit
resource-coordination request, build/test work waited for the main Monica Android
build to complete at 23:01:21 CST on November 8. Subsequent builds use one worker
and two compiler CPUs, with simulators stopped during compilation. Other project
Java processes were preserved.

The API32 test packages were removed and users 0, 10, 11, 12 and 14 retained;
API35 users 0 and 11 retained, with current user 0. API35 letterbox education
was explicitly restored to true. API36 isolated test packages were removed
after its failed run. The three AVDs were stopped after use. All six AVD
configuration/registration hashes match their captured baselines, and data
disks remain present. The R8 smoke package and its single temporary XML were removed successfully;
API32 returned to user0 and stopped, retaining all five pre-existing users.

Raw logs and task-start source baselines remain in
`.codex-tasks/20261108-app-glass`; the accompanying results JSON summarizes
method-level outcomes, source/APK hashes and build receipts. Original failed
runs are retained. No commit, push, tag, release or APK delivery was performed.
