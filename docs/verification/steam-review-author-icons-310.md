# Achievement images and review author profiles · 1.0.310 (unreleased)

Achievement rows now resolve Steam image filenames against the current game’s community CDN directory. Both the library and personal stats supply the app ID, so existing cached relative definitions also work without changing their stored format. Completed entries prefer the normal image; locked entries prefer the gray image and fall back to the normal image if missing or unavailable. Existing hidden-name behavior and locked-row opacity remain. The shared image resolver uses SteamSpaceLinks and the existing image cache. SteamGameLibraryService bytes are unchanged.

Review cards now display the author’s public name and avatar in a clickable identity header. Names/avatars reuse SteamMiniProfileDecorRepository and the profile viewer image cache; requests run off the main thread with four concurrent slots. Each author has independent keyed state, and cancelled or late results cannot replace a different card’s identity. Invalid Steam IDs do not load or navigate. Unavailable metadata uses the original Steam ID and existing avatar placeholder while retaining the correct profile action. No review-cache schema or pagination change was needed.

The store routes a valid author click to the existing native SteamProfileViewerScreen with the selected signed-in account, or to the canonical community profile URL when signed out. A detail SaveableStateProvider retains the page position/tab through the native profile route. Account/source/app changes reset the selected author. The prior folder browser and collection implementation remain byte-identical except the shared library file’s achievement-specific changes and the new release-note bullet.

## Verification

- Build01 main/test packaging and 25 selected JVM methods passed in4m9s. The initial CLI personal-stats filter missed its ui package; it was corrected in the final JVM run.
- An existing source guard referring to the old author-button resource location was updated to inspect the extracted author header and its invocation. No dummy reference or relaxed navigation requirement was added.
- Final jvm02 passed all39 scoped methods with zero failures, errors or skips in1m40s, covering image URL selection/validation, review parsing/paging/recovery, mini-profile parsing, personal-stats lifetime, profile target rules and detail interaction guards.
- Device01 passed all22 distinct synthetic methods in26.367s:6 new author/image cases,14 detail regressions and2 non-live review recovery cases. Tests verify cached public-name field/actual bitmap rendering, separate name/avatar clicks to the same Steam ID, unavailable/invalid authors, a late previous-author result, a large review card, relative achievement filenames, gray images and normal-image fallback. Existing detail filters, hidden names, independent scroll, account changes, failure/retry, workshop controls and review recovery remain operable.
- Native author, achievement and large review screenshots were inspected. These use synthetic identities and colored PNG fixtures to check decoded image pixels; they are not screenshots of real Steam users or live achievement artwork. All previews remain in ignored docs/design/20261121-steam-review-author-icons.
- Final main/test APK hashes and all11 scoped changed-file hashes were recorded. Added-line whitespace and tracked diff checks pass. Achievement service and prior folder-browser source preservation were checked.

Packages are independent .authoricons/.authoricons.test, version1.0.310/code18, API35/x86_64/Debug. The UI fixtures use360dp layout and Activity font1.0/1.5; no popup/system-font claim is made. The profile callback’s exact Steam ID is tested, and production native/signed-out route wiring was reviewed. A complete signed-in StoreScreen -> real native profile -> review round trip, real profile network responses, physical/OEM devices and Release/R8 were not exercised. No real account, maFile or Steam write operation was used.

## Cleanup

Only this task’s two packages were uninstalled. Original users0/10/11/12, current user0, package set, size1080x1920, density280 and font1.0 match baseline. Task-started existing MuMu1 was stopped with its original configuration/data disk retained. Pre-existing MuMu0 remains running with PID15892. No AVD, clone, application data clearing, commit, push, tag, release or APK delivery.

Complete build/JVM/native logs, final JVM XML, receipts, source hashes and cleanup summaries accompany this report. Private device inventories and raw APK snapshots remain in the local task directory. Screenshots are not embedded in tracked documentation.
