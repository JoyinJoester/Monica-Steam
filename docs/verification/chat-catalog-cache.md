# Chat media catalog caching — 2026-10-17

## Cause and change

`SteamChatRichMediaViewModel` previously initialized an empty catalog on selection and retained it only for the lifetime of that ViewModel. Credential changes also cleared it, and a failed refresh replaced all lists with empty ones. `SteamChatRichMediaPickerPanel` rendered a linear progress bar whenever the catalog request was running, including on the built-in Unicode emoji tab.

The catalog now uses a shared memory cache and a private, versioned SharedPreferences cache. Memory hits are available synchronously; disk reads and session/network work run on the IO dispatcher, with cached data published before resolving the network session. The serialized catalog contains asset metadata only, with no credentials or conversations. Saved catalogs are keyed by SteamID independently of rotating tokens; temporary accounts use a separate memory namespace and never read or write the persistent catalog. The memory cache holds at most eight accounts and encoded entries are bounded to 2,097,152 characters.

The ViewModel retains items during refresh, keeps usable cached data on failure, and accepts successful empty catalogs as authoritative updates. Account changes cancel old jobs and invalidate their results. Repeated refresh taps do not start parallel requests. Disk read/write failures do not prevent a successful network catalog from being displayed. An initial network failure without any cached catalog still offers the existing retry action.

The picker no longer renders a catalog progress bar. Its existing tabs, grid, controls and error/retry layout remain in use. Steam image bytes already have a disk cache in `SteamRemoteImage.kt`; this change supplies the missing owned-asset catalog layer and does not change animation decoding or image-cache policy. No new UI layout or Canvas design is involved.

## Verification status

At the user's request, **no Gradle build, Kotlin compilation, JVM test run, emulator launch or device test was performed** for this change, so it is not claimed to be compiled or runtime-verified. Existing build reports predate this change.

`git diff --check` passed. Static source checks confirmed the production cache factory connection, serialization annotations on all four catalog models, removal of the picker progress indicator, and the presence of five cache plus seven loading test methods. These checks do not replace compilation or test execution.

Added five cache tests covering cross-instance persistence, full catalog serialization, credential-independent/account-scoped keys, memory bounds, temporary accounts, corrupt/oversized/empty entries and concurrent disk-read/refresh ordering. Added seven ViewModel tests covering cache-before-network publication, immediate reopening, offline preservation, initial retry, confirmed-empty updates, account switches with late work, credential rotation, request coalescing and disk failures. The existing UI source guard also checks that the picker has no linear progress indicator.

When the shared build resources are available, run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'takagi.ru.monica.steam.friends.chat.richmedia.*' --tests 'takagi.ru.monica.steam.friends.chat.ui.SteamChatIntegrationGuardTest' :app:assembleDebug --max-workers=2 --console=plain
```

Device follow-up: load a saved account's catalog once, leave and reopen chat, then force-stop/reopen offline and check that previously used image assets and cached catalog remain available without the progress bar. Repeat with two accounts and a temporary login, and confirm an uncached first-load failure can be retried. Cached ownership is a display convenience; Steam still validates send requests.
