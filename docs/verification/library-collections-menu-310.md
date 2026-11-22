# Library collections menu — 1.0.310

2026-11-21: Move the Steam Collections entry from the library body to its top-right overflow menu. The folder icon and existing localized title are reused; a selected collection or Unfiled name is appended to keep the active scope identifiable. The action opens the existing collection destination and remains available during refresh when a library snapshot exists. Remove the now-unused overview callback and standalone row. Collection filtering, persistence, editing and return behavior are unchanged.

An editable local Canvas and its single-screen preview were inspected; JSON, import URL and preview remain only in ignored docs/design/20261121-library-collections-menu. The menu preview is a design artifact, not an Android runtime screenshot.

Debug app and instrumentation build01 succeeded (Gradle 2m03s). Reused pre-existing MuMu0, Android35/x86_64, with isolated .collectionmenu packages. Four existing device tests passed in 5.652s: collection search/browse/back hierarchy, category selection/return, system Back ordering and overflow access while refreshing. These tests cover the existing destination/shared menu components; they do not execute the complete library-to-collections route with an authenticated account. Source review verifies that the menu callback sets the existing destination flag and that no standalone Collections row remains.

Scoped git diff --check passed. No new tests were added for this small entry relocation. Evidence and APK hashes are in library-collections-menu-310/. Both isolated packages were removed; original users, application packages, display/font settings and pre-existing MuMu instance/data disk were preserved. Version remains 1.0.310/code18; no commit, push, release or APK delivery.
