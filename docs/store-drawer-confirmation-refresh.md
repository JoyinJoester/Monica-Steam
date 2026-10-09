# Store drawer dismissal and confirmation refresh

2026-10-09. Changes are limited to dismissal lifecycle and confirmation requests; existing layout and appearance are retained.

## Reproduction

- The supplied recording shows the sheet sliding off-screen while its dimmed Dialog window remains and intercepts input.
- A device regression interrupted `drawer.close()` with another anchored-drag mutation. Before the fix, `store_home_drawer` remained in the composition at `left=-672px, right=0px`, reproducing the invisible sheet / retained window. Normal scrim and swipe closure passed in the same baseline.
- The original close coroutine set `closingAction=true`, then awaited an interruptible animation before removing the Dialog. Cancellation skipped removal, while the flag disabled further close attempts. The close-state observer was also sequenced after interruptible `open()`.
- Before the protocol fix, two JVM regressions failed: the actual list request signed `list` rather than `conf`, and the default request used the phone clock rather than the supplied Steam server time.

## Fix

- Observe settled closure independently from the opening animation. Complete an explicit dismissal even when a competing drawer mutation cancels its animation; do not invoke stale navigation after the entire drawer composition has been disposed.
- Use `conf` for the list tag and its HMAC, retaining `m=react`. Reuse `SteamServerTimeService` for signing time. An empty batch remains a no-op without a time request.
- Classify `needauth` / `needsauth`, HTTP 401 and community login redirects as authentication failures. Refresh the source-aware account session and retry only the list read, once, only if the access token changed for the same account. Other failures and cancellation are not retried. Confirmation approvals are not automatically replayed by this recovery path.
- Show sign-in guidance for expired sessions and a useful explanation for Steam's generic “Oh no…” response. A failed read is no longer presented as a verified empty list.

## Reference and scope

The local `参考项目/steamguard-cli-master/steamguard-cli-master/steamguard/src/confirmation.rs` uses `conf`, `m=react` and Steam server time for list requests. Protocol tests exercise real request construction through an intercepted HTTP client; the session recovery integration test verifies the rotated cookie and re-signed second read.

The affected user's authenticated online response is unavailable. The request defects and the retained Dialog are reproduced; these checks do not establish whether that user's account also has an invalid authenticator or a server-side restriction. No account credentials or private recording frames are included in this document.

## Verification

- 31 related JVM tests passed, including real HTTP request construction, explicit authentication signals, bounded recovery, unchanged/wrong-account credentials, cancellation, server time and empty batch behavior.
- 7 device tests passed on the existing public API 32 AVD. Coverage includes scrim taps, swipes, interruption during opening and closing, exactly-once action execution, and disposal without stale navigation. The full home-screen test alternates scrim taps and swipes 16 times and verifies the account button receives touch input after every dismissal.
- Visually checked the native home-screen capture after the repeated-dismissal test. Logs and the capture are retained under `app/build/reports/drawer-confirmations/`.
- An unauthenticated read of the public Steam `ITwoFactorService/QueryTime/v1/` endpoint returned HTTP 200 with a protobuf response beginning with `server_time` (field 1).
- Regular `:app:assembleDebug` passed, including R8 and resource shrinking. The existing Kotlin metadata compatibility warnings remain. No APK was copied for delivery.
