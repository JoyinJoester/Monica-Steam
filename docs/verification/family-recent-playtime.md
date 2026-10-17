# Family-shared recent playtime — 2026-10-16

## Cause and protocol evidence

The shared-library list contains `rt_last_played` and `rt_playtime`, but no two-week minutes. That limitation applies to this response, not to Steam's player activity APIs. The library previously hard-coded zero recent minutes for shared games and never queried another source.

Protocol definitions checked on 2026-10-16:

- [Steam player protocol](https://github.com/SteamDatabase/Protobufs/blob/master/steam/steammessages_player.steamclient.proto): `ClientGetLastPlayedTimes` returns games in field 1, with app ID (1), last playtime in Unix seconds (2), two-week minutes (3), and lifetime minutes (4). The request's `min_last_played` is set to zero to request all history for the authenticated player.
- [Web UI player service](https://github.com/SteamDatabase/Protobufs/blob/master/webui/service_player.proto): the same method is exposed as a constant player-service call with authenticated-player privilege.
- [Steam Web API definition](https://github.com/SteamDatabase/SteamTracking/blob/master/API/IPlayerService.json): `GetRecentlyPlayedGames` accepts a player SteamID and `count`; zero requests all results. Monica's community service already uses this endpoint with an access token, but previously limited its own display to ten games.

The unauthenticated endpoint probe returned HTTP 200 / `x-eresult=1` with no records. This establishes route availability only; it is not evidence of an authenticated user's shared-game playtime. No personal Steam token was read or used for this investigation.

## Implementation

- After merging owned and family licenses, query playtime when there are shared games, using the same API client that successfully loaded the owned library. The client request is scoped by the selected account's token; the recent-games fallback also sends that account's SteamID, never a lender's.
- Match results by app ID only against confirmed shared licenses. Existing owned games retain their owned-library statistics; unknown activity records do not create licenses or duplicate games.
- Prefer client activity records. Request all recent games if any shared IDs are missing, including when the client endpoint fails or returns an empty response. The recent response must contain an explicit, complete `total_count` and valid minute values; private/invalid/truncated results are not interpreted as zero activity.
- Merge reported minutes without adding overlapping sources. Preserve the family-list date when a fallback has no date, and keep the newer reported date when both provide one. Existing library totals, filters, rows, details and serialized cache consume the enriched game model.
- Track unavailable app IDs separately. On partial failure, keep successful new records and restore only the unavailable games' recent minutes from the same account's cached shared games. A successful zero or complete empty result clears previous recent minutes. Older cache files remain readable through a default empty set.
- Preserve the shared activity comparator. Its [2026-10-17 follow-up](family-recent-activity.md#two-week-playtime-ordering-follow-up--2026-10-17) prioritizes two-week minutes, with last-played time breaking ties. Real minutes are not inferred from dates or from other family members' activity.

## Regression evidence

Before the implementation, the real-library-service test supplied a client activity response containing 135 minutes for a shared game; the result still contained zero. The four-test family service suite had one expected failure at that assertion.

Coverage includes the full library request-to-snapshot path, current-account request parameters, unlimited fallback results, source overlap, unknown app IDs, partial and complete failure, malformed/incomplete JSON, unsigned timestamps, zero and out-of-range minutes, cache restoration, account isolation and compatibility with old snapshots. The new suite is included in both native and fallback Steam Network CI runs.

Library JVM verification passed **173 tests across 37 suites**, with zero failures, errors or skips. This includes all ten new playtime-service tests and the previously failing full-library-service test.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'takagi.ru.monica.steam.library.*' --console=plain
```

Normal `:app:assembleDebug` passed for ARM64 and ARMv7, version `1.0.310` / versionCode `18`. Existing R8/Kotlin metadata warnings remain. No APK delivery copy, release, tag or commit was created.

Authenticated live-account validation remains outstanding; all regression data is synthetic and requests are intercepted locally.
