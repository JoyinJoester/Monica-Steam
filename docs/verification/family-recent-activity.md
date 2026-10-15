# Family-shared games in Recently active

Verified on 2026-10-15 using synthetic account and protocol fixtures.

## Reproduction and cause

`SteamFamilyLibraryServiceTest.libraryFetchMergesFamilyGamesAndPrefersOwnedDuplicate` exercises the real library service with intercepted HTTP responses, then passes its result into the default dashboard grouping. Before the fix, a shared game with a recent `rt_last_played` produced an empty Recently active section. The regression failed at the expected app-ID assertion; the other three service tests passed.

Both the Kotlin and Rust shared-app parsers discarded protobuf field 12 (`rt_last_played`, Unix seconds). Shared apps do not provide two-week minutes in this response, so their `playtimeRecentMinutes` remains zero. Both dashboard grouping paths and the play-status filter previously required positive two-week minutes.

The shared-library request already specifies the selected account's SteamID in field 8. The service test now decodes and checks that field, and confirms owned games still win when the shared response contains the same app ID.

## Change

- Preserve `lastPlayedAt` in Kotlin and Rust. The native result format is now `MSF2`; old `MSF1` payloads are rejected and use the Kotlin fallback rather than silently losing the timestamp.
- Use one activity rule for dashboard grouping, play-status filters and filter preview counts: positive two-week minutes or a last-played timestamp within the preceding 14 days, including the boundary and excluding future timestamps.
- Keep games with date-only activity out of Unplayed. Default sorting places recent activity ahead of older activity. The recent section retains minutes-first ordering and uses last-played time to order date-only entries.
- Keep reported minutes unchanged. No two-week totals or daily activity are fabricated from a timestamp.
- Reuse the existing serialized timestamp field; no database migration is needed. Older caches gain the missing shared-game timestamps after a successful library refresh.
- Add the shared-activity regression to both native and fallback CI runs. Normalize CRLF in an existing source-layout assertion that otherwise fails on Windows checkouts.

## Verification

- Library JVM suite without JNI: **158 passed**, no failures, errors or skips.
- Library suite and required host JNI integration tests: **163 passed**, no failures, errors or skips. This includes a real JNI round trip for shared-game activity and unsigned timestamps above `Int.MAX_VALUE`.
- Rust workspace: **115 passed** (105 core, 10 Android bridge), no failures.
- ARM64 and ARMv7 Rust libraries built successfully with cargo-ndk.
- Normal `:app:assembleDebug` completed successfully for ARM64 and ARMv7. Build output retains existing R8/Kotlin metadata compatibility warnings.
- Covered recent and old shared games, the exact 14-day boundary, missing/negative/future dates, date-only play records, search with ownership filters, preview counts, deduplication, serialized cache restoration and temporary family-refresh failure.

The affected user's live Steam account has not been retested. These checks establish the parser-to-dashboard defect and its regression coverage; they do not claim a device or authenticated Steam-server reproduction.
