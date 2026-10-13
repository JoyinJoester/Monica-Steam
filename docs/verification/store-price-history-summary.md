# Game details: current price and SteamDB historical low

Date: 2026-10-14. Base: `ce5e0b6`.

## Behavior

The detail header now groups the current regional price with its SteamDB
historical low. The current price remains first and retains the regional
comparison action. The adjoining history row shows the historical price,
discount, occurrence count and last date when supplied by SteamDB, with its
source action and existing retry/cache status handling.

The group uses existing theme colors, 20dp outside corners, 4dp adjoining
corners and a 2dp gap. Both rows expand with text; price content can wrap rather
than being clipped. The source and retry actions belong to the history row,
separate from the current-price comparison action.

Only price data loads automatically. The full SteamDB panel remains available
below and uses the same repository cache. Queries use the game's actual price
country and currency, including regional USD markets. Changing the query resets
the history state. Free games, missing current prices and games unavailable in
the account region do not show a comparison with an unrelated price. History
failure leaves the current price and comparison action available.

## Design and rendering

Created an editable design with the local M3E Canvas using a connected list-item
group, game image, app bar, chip and purchase controls. The JSON, canonical
`http://127.0.0.1:5186/#docz=...` link and preview are retained locally under
`docs/design/price-history-summary/`; no screenshots or design files are
included in this commit. The existing editor held another tab's edit lock, so
visual inspection used the same local service through its localhost alias.

Inspected native screenshots of the Chinese full detail page and the price group
at 320dp with normal text and dark mode at 1.5x font scale. Both prices and the
comparison/source actions remained visible. Screenshots use synthetic test
prices and an offline cover fixture; they are not live market-price evidence.

## Verification

- 32 JVM tests passed: SteamDB data/cache behavior, regional prices and detail
  navigation/header guards. Zero failures and zero errors.
- The normal `:app:assembleDebug` build passed for `arm64-v8a` and `armeabi-v7a`,
  including R8 and resource shrinking. Existing Kotlin metadata warnings from
  R8 remain. Version configuration is unchanged.
- Device tests use the existing `Monica_Issue136_API_32` AVD and the isolated
  `takagi.ru.monica.steam.recoverytest` application via
  `scripts/recovery-test.gradle`; no real account data is required.
- All 16 device cases passed. The first run passed 15 cases and encountered an
  ambiguous test selector: both the purchase section title and the open menu
  contained the same text. Restricting that selector to the clickable menu item
  made the remaining navigation case pass on rerun; no application change was
  needed. The AVD started for this verification was stopped afterward.

The four new device cases cover adjacency/compact height and comparison action,
failure/retry without losing current price, free/missing/unavailable-price
suppression, and large text with a CNY-to-South-Asia-USD region change. Existing
regional comparison and SteamDB panel tests cover expansion, on-demand metrics,
partial failures, delayed old responses and source controls.
