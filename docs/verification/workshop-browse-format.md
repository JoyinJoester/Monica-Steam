# Workshop browse response compatibility

Verified on 2026-10-14 against the development branch based on `1331b1f`.

## Cause and fix

Steam's Workshop browse page now places its hydration data in
`<script type="application/json" id="valve-ssr-data">`, with the existing
`renderContext` nested inside that JSON object. The client previously only
recognized `window.SSR.renderContext=JSON.parse(...)`. A successful HTTP 200
response therefore produced `WorkshopFailure.INVALID_RESPONSE`, as reported
when browsing recently updated Left 4 Dead 2 items.

The parser reads the current JSON block and retains support for older pages
when that block is absent. Neither path executes JavaScript. An invalid current
block fails rather than silently using a stale legacy block or reporting an
empty successful list. Existing game, page and response validation is retained.

## Regression verification

- Before the fix, the current-format fixture failed with
  `WorkshopException: INVALID_RESPONSE` in `SteamWorkshopParser.browse`.
- After the fix, all 132 repository Workshop JVM tests passed. Coverage includes
  both hydration formats, equivalent parsed results, incorrect game/page
  rejection, malformed data and public browsing without account credentials.
- One additional local replay test passed against five unmodified anonymous
  HTTP 200 responses. Each response yielded 30 items, with the requested game
  and page and nonempty game-specific tag categories:

| Game | Language | Sort / filter | Page | Items | Tag categories |
| --- | --- | --- | --- | --- | --- |
| Left 4 Dead 2 (550) | Simplified Chinese | Recently updated | 1 | 30 | 50 |
| Left 4 Dead 2 (550) | English | Popular | 1 | 30 | 50 |
| Left 4 Dead 2 (550) | Simplified Chinese | Recently updated | 2 | 30 | 50 |
| Left 4 Dead 2 (550) | Simplified Chinese | Recently updated / Campaigns | 1 | 30 | 50 |
| RimWorld (294100) | English | Popular | 1 | 30 | 17 |

The same current-format response was also confirmed using the application's
HTTP headers. Public page capture and replay required no account login or
subscription changes. Complete captured responses and the replay harness remain
local; the committed regression fixture contains synthetic item data.

Repository test command:

```text
./gradlew.bat :app:testDebugUnitTest --tests 'takagi.ru.monica.steam.workshop.*' --console=plain
```

The recorded run added the local replay source through a temporary Gradle init
script: 133 tests total, zero failures and zero errors. This validates response
parsing and service behavior; it does not claim reproduction on the reporter's
device or cover every possible network failure.

## Build verification

`./gradlew.bat :app:assembleDebug --console=plain` completed successfully in
2m 6s and produced both configured ARM APKs (`arm64-v8a` and `armeabi-v7a`).
The build retained the existing Kotlin metadata compatibility warnings from R8.
No emulator was needed for this parser-only change; no device UI validation is
claimed. Application version configuration was unchanged.
