# Store wallet shortcuts

Date: 2026-10-14. Base: `f816e62`.

The store drawer now groups product activation, Steam gift-card redemption and
wallet balance together, between browsing destinations and app utilities. It
uses the existing M3E connected rows and scrollable drawer.

The new actions open these official pages through `openAuthenticatedStoreWeb`:

- Gift cards: `https://store.steampowered.com/account/redeemwalletcode?l=schinese`
- Wallet balance/funding: `https://store.steampowered.com/steamaccount/addfunds?l=schinese`

Both use the selected account's existing authenticated WebView session policy,
including identity matching and missing-session handling. Wallet balance is a
page shortcut; it is not a cached or inferred number in the native drawer.
The app does not collect gift-card codes or implement a redemption request.

Designed in the local M3E Canvas and inspected native light/dark drawer renders.
Editable JSON, the canonical local Canvas share link and screenshots remain in
the ignored `docs/design/store-wallet-shortcuts/` folder. The localhost alias
was used for previewing the same local Canvas service without taking another
tab's editor lock.

Device verification reuses `Monica_Issue136_API_32` with the isolated recovery-test
app UID. Nine cases passed, covering each shortcut callback, navigation exactly
once after drawer dismissal, scrollable settings and the existing interrupted
open/close, scrim and swipe regressions. Tests use callback fixtures and do not
redeem a real code, inspect a real balance or initiate a payment. Both official
URLs also returned HTTP 200 to anonymous header requests.

The wallet shortcut case also passed with the device system font scale explicitly
set to 1.5; the prior setting was restored afterward. Native captures confirmed
the labels and icons remain complete and actions stay reachable. The public AVD
started for this task was stopped after verification.

All 18 selected JVM tests passed: product activation, drawer menu wiring,
WebView security guards and account-session/navigation policy. Standard
`:app:assembleDebug` passed for both ARM ABIs with R8/resource shrinking; the
existing R8 Kotlin-metadata warnings remain. Version configuration was unchanged.
