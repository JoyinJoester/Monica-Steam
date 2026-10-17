# Family-shared games in Recently active

Verified on 2026-10-15 using synthetic account and protocol fixtures.

## Reproduction and cause

`SteamFamilyLibraryServiceTest.libraryFetchMergesFamilyGamesAndPrefersOwnedDuplicate` exercises the real library service with intercepted HTTP responses, then passes its result into the default dashboard grouping. Before the fix, a shared game with a recent `rt_last_played` produced an empty Recently active section. The regression failed at the expected app-ID assertion; the other three service tests passed.

Both the Kotlin and Rust shared-app parsers discarded protobuf field 12 (`rt_last_played`, Unix seconds). Shared apps do not provide two-week minutes in this response, so their `playtimeRecentMinutes` remains zero. Both dashboard grouping paths and the play-status filter previously required positive two-week minutes.

The subsequent [real playtime query](family-recent-playtime.md) fills those minutes from the selected player's activity APIs; zero is only the shared-list parser's initial value.

The shared-library request already specifies the selected account's SteamID in field 8. The service test now decodes and checks that field, and confirms owned games still win when the shared response contains the same app ID.

## Change

- Preserve `lastPlayedAt` in Kotlin and Rust. The native result format is now `MSF2`; old `MSF1` payloads are rejected and use the Kotlin fallback rather than silently losing the timestamp.
- Use one activity rule for dashboard grouping, play-status filters and filter preview counts: positive two-week minutes or a last-played timestamp within the preceding 14 days, including the boundary and excluding future timestamps.
- Keep games with date-only activity out of Unplayed. Default sorting places recent activity ahead of older activity. The 2026-10-17 follow-up below prioritizes two-week minutes across both ownership types; last-played time breaks ties.
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

## Chronological ordering follow-up — 2026-10-16

The timestamps were preserved, but sorting still preferred two-week minutes. Shared games have zero reported two-week minutes, so owned games with positive minutes appeared ahead of more recently played shared games.

Before changing production code, the expanded `SteamFamilyRecentActivityTest` ran 10 tests with 6 expected failures. The interleaved ownership fixture expected app IDs `[12, 11, 13, 10]` (shared, owned, shared, owned) but returned `[10, 11, 12, 13]` in both the dashboard and filtered results.

- Both dashboard paths, the legacy Recent filter and the current Smart filter share one activity comparator. Recent games with valid last-played timestamps appear newest first, regardless of ownership or reported minutes.
- Missing, negative and future timestamps sort after valid dates. Equal or unavailable dates fall back to two-week minutes, total minutes, name and app ID for deterministic ordering. No dates or minutes are synthesized.
- Non-recent Smart results retain their playtime ordering. Explicit two-week playtime, total playtime and name sorts retain their existing semantics.
- Regressions cover mixed ownership and input permutations, recent filters and search, missing/invalid dates, equal-date ordering, explicit sort choices and restored cached activity after a failed family refresh.

Follow-up validation: the library JVM suite passed **163 tests across 36 suites**, including all **10** tests in `SteamFamilyRecentActivityTest`, with no failures, errors or skips. This run used the normal JVM fallback path; the parser and JNI protocol did not change in this follow-up.

Normal `:app:assembleDebug` also completed successfully for ARM64 and ARMv7 (`1.0.310`, versionCode `18`). Existing R8/Kotlin metadata compatibility warnings remain. This ordering change was verified with deterministic fixtures, not the reporting user's live account or a device UI session.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'takagi.ru.monica.steam.library.*' :app:assembleDebug --console=plain
```

## Two-week playtime ordering follow-up — 2026-10-17

The user clarified that Recently active should follow the displayed two-week playtime, highest first. The previous chronological comparator instead allowed a game played for 5.3 hours to precede one played for 19.1 hours. This supersedes the chronological ordering described above; the real shared-game playtime queries remain in place.

Before changing the comparator, a regression using the six screenshot games and their displayed hours failed: expected app IDs `[3, 5, 1, 4, 6, 2]`, actual `[1, 2, 3, 4, 5, 6]`. Last-played dates in this fixture are synthetic and deliberately conflict with the desired minutes order; the screenshot contains no timestamps. The displayed `+0.0h` is represented by one minute, which rounds to that value.

- The shared activity comparator now prioritizes two-week minutes, then valid last-played time, total minutes, name and app ID. Neither ownership nor a missing date overrides reported recent minutes.
- Default dashboard grouping, the legacy Recent filter, Smart filtering and search all use this comparator. Explicit two-week, total-playtime and name sort choices retain their semantics.
- Recent date-only games remain in Recently active after games with positive minutes. No missing minutes are fabricated; old and unplayed sections retain their existing behavior.
- Regression coverage exercises the six-game order across both dashboard paths, filter/search choices and different input orders. A snapshot-refresh regression confirms that newly fetched shared minutes move the game immediately, serialization preserves that order, and a failed supplemental refresh keeps the cached order.

The library JVM suite passed **175 tests across 37 suites**, including all **12** shared-activity tests, with no failures, errors or skips. This uses the existing JVM fallback path; neither network parsing nor the JNI protocol changed in this ordering fix.

The combined standard `:app:testDebugUnitTest --tests 'takagi.ru.monica.steam.library.*' :app:assembleDebug --max-workers=2 --console=plain` run completed successfully in **2 minutes 44 seconds**. Both ARM64 and ARMv7 APKs were generated for `1.0.310`, versionCode `18`. The local build log is retained in the parent workspace at `.codex-temp/steam-recent-minutes-green-20261017.log`. No emulator, APK delivery copy, commit, release or tag was needed for this follow-up.

This is a deterministic data-to-dashboard regression using synthetic inputs, not a retest of the reporting user's authenticated account or a device UI session.
