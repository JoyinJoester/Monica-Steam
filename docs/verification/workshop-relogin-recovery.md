# Workshop re-login recovery — 2026-10-15

## Report and reproduction

The reported Wallpaper details page asked the user to sign in again even after
they had used the account page's sign-in action. No account credentials or
device diagnostics were provided; the exact server response on that device is
unknown.

`WorkshopReloginRecoveryTest` reproduces a matching client-side failure through
the real `SteamAccountSessionManager` and `SteamWorkshopViewModel`: prime a
previously refreshed account session, reject that session with HTTP 401, then
replace the account's credentials with a successful re-login. Before the fix,
the detail page still reported `WorkshopFailure.LOGIN` because `latestAccounts`
unconditionally took precedence over the new account value.

## Change

- Resolve persistent accounts from their original Room or MDBX source, including
  the MDBX entry identity, before using a cached refresh result. Storage read
  failures, removed entries and changed Steam identities cannot silently fall
  back to an old session.
- Share refreshes only while their credentials match. A new login is not forced
  to wait on a refresh belonging to the previous login.
- Re-read the current session after a delayed refresh, before persisting its
  result, and after waiting on another refresh. Discard stale results when a
  newer login is observed, including when the old refresh failed.
- Read the current MDBX entry when writing rotated credentials rather than
  rebuilding the entry from the account list's stale snapshot.
- If personal Workshop details fail, attempt the public detail endpoint while
  retaining the original error and leaving subscription status unknown. Public
  descriptions and the existing original-file download entry remain usable;
  this does not turn an authentication failure into subscription success.

No layouts, theme, authenticator secrets, app version, database schema or Steam
permission checks were changed.

## Validation

- The original re-login regression failed before the session fix and passed
  afterward.
- Session, Workshop and CM JVM selection: 171 tests, 169 passed, 2 opt-in tests
  skipped, no failures or errors. This covers stale handles, original-source
  isolation, delayed/failed refreshes, expiring new credentials, deleted/rebound
  accounts, temporary-session expiry, public fallback and cancellation.
- Normal `:app:assembleDebug` succeeded for arm64-v8a and armeabi-v7a. Existing
  R8 Kotlin metadata warnings remained non-fatal.
- Public AVD `Monica_Issue136_API_32` (API 32) was reused with the isolated
  `takagi.ru.monica.steam.recoverytest` UID. All 3 selected instrumented tests
  passed: account-screen replacement through the real encrypted Room repository
  and production session store; temporary-account storage isolation; original
  download action remains reachable after subscription authentication fails.
  Only synthetic account values were used and the fixture account was removed.
- The AVD was started for this verification and stopped afterward. No APK was
  copied to a delivery directory and no screenshots or credentials were added
  to the repository.

The original user's login and Steam's live authenticated Workshop response have
not been replayed. These results establish a reproducible local-session defect
and its regression coverage, not confirmation of the exact cause on that phone.
