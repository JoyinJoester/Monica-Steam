# Steam library collections · 1.0.310 (unreleased)

Implemented flat Steam collections through CloudConfigStore namespace 1: full read, create, rename, delete, and batch add/remove owned games. A game can belong to multiple ordinary collections. Library search, ownership, achievements, playtime, cloud filters, and sorting remain available. The selected collection lives in the library ViewModel, survives detail navigation, and resets on account/storage/Steam-ID changes.

Dynamic collections and unreadable ordinary entries are shown read-only. Dynamic rules are not evaluated locally. Unfiled is disabled when unreadable ordinary entries make membership uncertain. Existing members missing from the owned-game list, removed IDs, unknown JSON properties, and unrelated cloud entries are preserved. Batch edits send only IDs whose checkboxes changed; the ViewModel also verifies those IDs belong to the selected account’s owned library.

Each mutation downloads a fresh full namespace, compares the exact reviewed target, uploads only one entry, then downloads and checks the resulting value or tombstone. A lost reply is reconciled without replaying the upload. Unknown outcomes freeze writes until a successful refresh. The UI captures the reviewed entry when its editor opens; a refresh does not silently replace that baseline. Saving blocks competing writes across account switches, and generation checks prevent stale results from appearing in the next account. Namespace/entry versions are transmitted, but the available protocol does not document atomic compare-and-swap: preflight checks cannot eliminate simultaneous-device races.

## Protocol and design references

- SteamDatabase protocol: https://github.com/SteamDatabase/Protobufs/blob/master/webui/service_cloudconfigstore.proto
- GameNative interoperability reference: https://github.com/utkarshdalal/GameNative/blob/master/app/src/main/java/app/gamenative/steam/SteamCollectionParser.kt
- GameNative cloud gateway: https://github.com/utkarshdalal/GameNative/blob/master/app/src/main/java/app/gamenative/steam/CloudConfigStoreService.kt
- Desktop collection format reference: https://github.com/doZennn/steam-categories

Local editable Canvas was created and the library frame previewed before production UI changes. Manager and member-editor previews were subsequently inspected. The JSON, full local URL, and native screenshots stay in ignored docs/design/20261121-steam-collections; they are not embedded in committed documentation. Native sheets use the existing M3/glass sheet wrapper, rounded rows, resource labels, and accessible membership checkboxes. All 28 new strings cover default English, Chinese, Japanese, Russian, and Vietnamese (140 values); keys and format arguments match.

## Validation

Task records: ../.codex-tasks/20261121-steam-collections. Full main/unit build01 succeeded in 4m; build02 main/test and 29 scoped JVM checks succeeded in 2m11s. After moving selection into the ViewModel, final main/test and the same 29 JVM checks passed in build03 (3m26s). No JVM failures, errors, or skipped methods. First native component run device01 passed all 10 methods in 12.846s.

Visual review found that a composition-only density override did not reach the modal dialog. Earlier screenshots labelled large do not establish large-font rendering. The test fixture was corrected to configure actual Activity fontScale=1.5 and a 360dp width, restoring its original resources after each method; system size/density/font settings are never changed. Final test-only build04 passed in 1m16s; the corrected native screenshots were inspected at normal size and at actual 1.5 font scale with a 360dp Activity width.

The user first selected synthetic-only validation, then explicitly supplied a test-account maFile and authorized using it. Only Steam ID, account name and session tokens were transferred to an independent debug package’s private directory. Guard shared/identity/revocation secrets were not transferred. The original maFile was not modified, imported into the normal app, or copied into this repository.

Live Steam cloud test on build02 passed in 9.797s: download, temporary collection create, rename, independent service read, delete, and final preservation of all 9 original entries. The temporary collection was removed and the private session fixture was deleted. The account’s owned-games endpoint returned 0 games, so live membership add/remove was not executed; membership behavior is covered by synthetic protocol and native controls. Parser/service/sheet production bytes are unchanged between this live run and final builds. No direct desktop Steam UI, physical device, OEM, Release/R8, dynamic-rule evaluation, nested folders, or offline cloud edits were verified.

MuMu instance 1 (existing API35/x86_64 test instance) was started for this task; existing instance 0 was left alone. Original users 0/10/11/12 and device settings are preserved. Cleanup receipts and final native results are recorded below. No commit, push, tag, release, or APK delivery.

### Final native results and cleanup

Device02 (final main03/test04) completed 44/45 methods in 77.462s: all 10 collection and 14 detail methods passed, and 20/21 glass methods passed. The existing heldLensHasVisibleGlassRimOnFlatDarkChatBackground method timed out waiting for its pre-capture hardware frame-commit callback (5s); it did not reach the rim assertion. Device03 reran that exact method on identical APK hashes and passed all assertions in 1.894s. All 45 distinct synthetic methods have passing results, but the combined 45-method batch is not claimed green. The frame-callback timeout cause is not established, and no production glass code, assertion, or timeout was changed.

Only the task's .collections and .collections.test packages were uninstalled. Original installed packages, users 0/10/11/12, current user, size 1080×1920, density 280, font 1.0 and original maFile SHA-256 matched the baseline afterward. The task-started MuMu instance 1 was shut down, preserving its configuration and data disk. Pre-existing MuMu instance 0 remained running with its original PID. No AVD was used. Private test session material was already deleted by the live test and confirmed absent before uninstall.

Source/APK hashes, sanitized live receipt, both successful scoped JVM reports, all native run logs including the frame timeout, resource audit and cleanup receipt are saved with this report's evidence. Original local logs and APK snapshots remain in the task directory. Design files and screenshots remain ignored. Unrelated worktree changes were preserved; no commit, push, release or APK delivery was performed.

## Standalone-page follow-up

On 2026-11-21 the manager/member sheets were replaced by independent collection pages reusing Monica Android password-library components. Cloud parser/service/ViewModel bytes remain unchanged. The implementation and newer UI/large-system-font evidence are recorded in steam-collections-page-310.md; this report retains the earlier protocol and sheet-validation history.
