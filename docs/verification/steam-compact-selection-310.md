# Compact collection selection controls · 1.0.310 (unreleased)

The collection selection toolbar now measures its button content instead of stretching across the available row. The final layout follows the user's left-alignment request: its left edge matches the page's existing 12dp margin, and the member-save button follows it with the existing 8dp gap. The count still yields space to the action buttons when constrained. Button sizes, selection callbacks, glass surface, save gating and reserved Dock clearance remain unchanged.

Only SteamCollectionsScreen.kt and the Steam folder presentation's bottom-bar alignment changed. SelectionActionBar.kt, collection cloud parsing/service/ViewModel, achievement icons and review author/profile behavior were not edited. Chinese and English unreleased notes describe the final left alignment. The initial centered draft was superseded before final verification.

## Verification

- Initial centered draft: Debug main/test packaging passed in1m41s and all28 existing collection UI methods passed in30.854s. The user then requested left alignment.
- Final left-aligned main/test packaging passed in2m4s; all28 unchanged synthetic collection UI methods passed again, runner35.619s. No new test or unrelated JVM suite was added for this layout-only change. Existing tests exercise selection, clear/select all, saving, confirmation, hidden choices, long names, navigation and large-text operation.
- Final normal/light and large/dark member-page screenshots were inspected. The capsule has no trailing stretch, aligns with the left12dp margin, and its save button follows at8dp. All four controls remain visible. Large text uses Activity fontScale1.5 at360dp; this is not a system-font popup or full production Dock screenshot claim.
- Installed main/test APK hashes and final scoped source hashes were recorded. Prior achievement/author files and collection cloud files were checked for preservation. Added-line whitespace and scoped Git diff checks pass; final design screenshots are ignored.

Independent .compactselection/.compactselection.test packages use synthetic collection state on the existing MuMu1, Android35/x86_64/Debug. No real account, maFile or live Steam write is used. There is no claim of physical/OEM, Release/R8 or full production Dock integration testing. Screenshots remain only in ignored docs/design; tracked documentation contains text evidence.

## Cleanup

Only .compactselection and .compactselection.test were removed. Original users0/10/11/12, current user0, size1080x1920, density280 and font1.0 match baseline. All original packages remain; the package set differs because three unrelated Monica/Monica F-Droid packages were added during the session and were preserved. The first exact-inventory cleanup assertion stopped at that difference after removing the task packages; it did not shut down the device or remove unrelated packages. The final check explicitly verifies original-package retention and records the additions.

Task-started MuMu1 was stopped; configuration and data disk are retained. Pre-existing MuMu0 remains running with PID15892. No AVD, clone, data clearing, commit, push, tag, release or APK delivery. Private inventories and raw APK copies stay only in the task directory. Complete build/native logs, receipts, source and cleanup summaries accompany this report.
