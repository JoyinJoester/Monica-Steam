# Temporary Steam sign-in

Implements [issue #13](https://github.com/JoyinJoester/Monica-Steam/issues/13).

Password and QR sign-in offer a separate **Temporary login** switch in **Sign in only** mode. It never imports or migrates an authenticator. The choice survives verification-code and QR approval steps. Password login requests Steam's ephemeral persistence mode; QR persistence is controlled by the approving client because the QR request protocol has no persistence field.

Temporary accounts have independent process-local IDs and remain in memory. Logging in temporarily with the same SteamID does not overwrite an existing Room or MDBX account. Token rotation updates memory. Ending the temporary login from the account switcher, finishing the main activity, or ending the process removes the session; switching to the background retains it. A cancelled sign-in request cannot save its late result.

Session resolution checks that the temporary account is still active before and after refresh. It does not keep temporary tokens in the persistent-account session cache. IDs include process randomness to avoid reusing a prior temporary account's game-cache key.

Account backup and ZIP export omit temporary accounts, direct maFile serialization rejects them, and MDBX transfer rejects them. Widgets and background account enumeration exclude them. The embedded WebView refuses temporary authenticated sessions before creating or loading the browser, because Android's shared CookieManager can write credentials to disk. Native store, library and chat remain available. This is a credential-persistence option; ordinary game/image caches are not an incognito browsing feature.

## Design

The editable local M3E Canvas document is [canvas.json](design/temporary-login/canvas.json); its local share URL is preserved in [canvas-url.txt](design/temporary-login/canvas-url.txt).

The native dialog uses grouped Material surfaces: 20dp exterior corners and 4dp adjacent corners, a switch with whole-row accessibility semantics, and a scrollable explanation. [Canvas preview](design/temporary-login/canvas-preview.png) and [native dialog capture](design/temporary-login/native-dialog.png) use synthetic account data.

## Verification

Regression coverage includes ephemeral/persistent password requests, QR approval and verification-code propagation, same-SteamID isolation in a real in-memory Room database, token rotation, logout during refresh, non-resurrection, export filtering, WebView refusal, and the actual password-dialog switch and submission callback. No real account credentials are used in tests.

The submission is assembled against upstream main `be3722e` in a separate source snapshot, preserving already-published Workshop and launcher changes. Unrelated local scanner, vault-recovery, theme and dock work remains outside this submission.

- 310 targeted JVM checks passed across store, SteamDB, widgets, sessions, confirmations, login, web policy and affected navigation guards.
- Public API 32 AVD: 17 device checks passed; one opt-in live-image network smoke check was skipped. Tests use the isolated `takagi.ru.monica.steam.recoverytest` package with widget binding permission. Stale key-loss-test data was cleared before the final run.
- Ordinary ARM debug packaging passed with R8/resource shrinking; the existing Kotlin metadata warnings remain.
- Full-suite exploration also found 13 unrelated source-text guard failures reproduced against pristine upstream `be3722e`: module enumeration/layout, shared settings/MDBX routing, session-source text matching, chat/library host wiring, payment insets, settings scroll state, density and old navigation expressions. The full suite is not reported as green.
