# Monica Steam maFile handoff, protocol v1

This contract transfers one user-approved plaintext maFile from Monica Steam to the Android Steam application. It is implemented in the Monica Steam 1.0.310 development tree; a version label alone does not establish support. Clients must handle a missing activity and retain manual file import.

## Request

Start the explicit activity **for a result**, from a foreground Steam activity in the same Android user/profile. Do not add `FLAG_ACTIVITY_NEW_TASK` or send secrets in the request.

| Field | Value |
| --- | --- |
| Package | `takagi.ru.monica.steamapp` |
| Activity class | `takagi.ru.monica.steam.backup.share.SteamMaFileExportActivity` |
| Action | `takagi.ru.monica.steamapp.action.REQUEST_MAFILE` |
| `protocol_version` | Integer `1` |
| `request_id` | Fresh canonical lowercase UUID string |

Monica checks Android's actual `callingPackage` and `callingActivity`, allowing only `com.valvesoftware.android.steam.community`. The receiving UID is resolved through PackageManager; a UID shared with other packages is rejected. Extras cannot override the caller. This identifies the installed Steam package and UID, **not its injected module or signing provenance**. A rooted/instrumented device or a compromised receiving process is outside this boundary.

## Consent

Every request requires a configured master password and fresh password verification or an enabled biometric unlock, even when Monica is already unlocked or startup verification is disabled. Account loading follows authentication. No account is preselected; selecting a row does not export it. The user must press **Confirm handoff to Steam**.

Only saved accounts with a real SteamID and an authenticator secret are offered. Local accounts and currently readable supported MDBX databases are included; temporary accounts and locked/unavailable databases are excluded. The selected account is re-read from its original storage source before encoding. A missing/replaced account aborts the handoff.

The screen explains that Steam and code running inside it can retain the credentials permanently. Android cannot attest which module reads them. Cancelling, leaving the activity, screen locking, recreation or a five-minute interaction timeout cancels the request. Authentication and selection are not restored. The window blocks screenshots and rejects obscured touches; Android 12+ also hides overlay windows.

## Result

Cancellation or invalid requests return `RESULT_CANCELED` without payload. A successful result is `RESULT_OK` with:

- `protocol_version=1` and the original `request_id`.
- `data` and one `ClipData` URI: `content://takagi.ru.monica.steamapp.mafile-handoff/v1/<token>`, where `<token>` is 64 lowercase hexadecimal characters generated from 32 random bytes.
- MIME type `application/octet-stream` and only `FLAG_GRANT_READ_URI_PERMISSION`.

Check the matching request, version, nonce and exact URI form **before querying or opening the provider**. Reject other authorities, user prefixes, query strings, fragments and encoded path variants. Open in mode `r` once. Do not request a persistable grant, forward the URI or retry the same URI. Start a new consent request after a failed read.

The private provider checks both the Android URI grant and the receiving UID. It exposes only the generic display name `steam-account.maFile` and byte size. It has no filesystem paths, directory listing or write operation. Wrong UIDs cannot consume the ticket. The first valid open atomically consumes it, including when the reader later fails.

The unopened ticket expires after 60 seconds of monotonic time, independently enforced during reads. A timer wipes unopened data and revokes the grant. An open nonblocking pipe finishes or aborts within the remaining ticket lifetime, capped at 30 seconds. At most four unopened payloads and four pipe writers are allowed. Process death invalidates all tickets. Already opened descriptors and copied bytes cannot be recalled by revoking a URI grant.

## Payload and receiver behavior

The payload is one UTF-8 JSON maFile, at most 1 MiB (1,048,576 bytes). Existing maFile encoding is retained, including authenticator/confirmation secrets, device ID, revocation information, available login tokens and preserved original fields. No ZIP or encrypted database is sent; existing storage encryption is unchanged.

The transport writes no plaintext file, clipboard item, broadcast or secret-bearing Intent extra. Sender-owned byte arrays are cleared on cancellation, expiration, write completion and failure. Receivers must enforce a size limit, parse UTF-8/JSON defensively and clear working byte arrays. Managed strings and the receiver's preview/imported data are not guaranteed to be erased; this is not a zero-copy or root-resistant channel.

Millennium opens the existing preview after receipt. Its existing **Import** action is still required and retains its documented account replacement/restoration behavior. This shortcut does not automatically import, restore, subscribe or sign in.

## Compatibility checks

Exercise ordinary and already-unlocked sessions, missing master password, wrong password, cancel/back, backgrounding, screen locking and recreation. Verify selection and explicit confirmation, local/MDBX source identity, deleted accounts and repeated requests. Check exact-limit/oversized payloads, wrong UID, URI forwarding, replay, expiration, read/write modes, stalled readers and process death. Manual document import and export must remain usable when the companion app is absent or too old.
