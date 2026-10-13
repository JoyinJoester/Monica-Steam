# Wallpaper Engine / Workshop downloads verification

Date: 2026-10-14. Base: `0346c6e`. Steam version configuration remains 1.0.309 / 18;
the existing unreleased notes remain under 1.0.310.

## Scope

- Dedicated Wallpaper Engine (431960) store-drawer entry and a two-column native
  gallery, using each workshop item's real preview.
- Existing search, filters, pagination, subscriptions and presets are retained.
  Public details have an anonymous read path.
- Download original files to a folder selected through Android's document picker.
  WorkManager owns network execution, foreground progress and cancellation.
- Anonymous and selected-account modes are explicit choices. Account records
  contain only the originating handle key and identity references; the worker
  resolves that exact account from its original storage source. Credentials,
  depot keys and CDN tokens are not persisted in task metadata.
- HTTPS SteamPipe manifests and chunks, encrypted filenames, SHA-1 / Steam Adler
  checks, ZIP / LZMA / Zstandard compression, bounded buffers and safe relative paths.
- Download and export complete before the task is marked saved. Export uses a
  fresh temporary child directory, preserves exact filenames (including providers
  that append .bin), then renames it. Partial exports and private staging are
  cleaned on cancellation/failure. It does not overwrite existing user files.
- Completed folders are accessible through the file manager; failed/cancelled
  tasks offer a new download. This is not a pause/resume UI.

## Design and device checks

The editable local M3E Canvas document and canonical 127.0.0.1 share link are
retained under the ignored `docs/design/wallpaper-workshop/` directory.
The design was previewed locally and the actual native rendering was inspected.
No sketches, screenshots or workshop content are committed.

Public AVD `Monica_Issue136_API_32` was reused with the isolated
`takagi.ru.monica.steam.recoverytest` application ID.

13 device tests passed:

- 7 Wallpaper checks: real preview rendering and click action; folder requirement
  for anonymous downloads; download still available when subscription detail
  fails; readable access error and reachable retry at 1.5x font scale; saved
  folders offer Open instead of Retry; nested original filenames and bytes are
  preserved; bundled Android Zstandard decompression.
- 6 store-drawer dismissal regression tests.

The initial storage test exposed automatic .bin suffixes; the export implementation
was corrected and the full device selection then passed.

## JVM and live network evidence

The final Workshop and CM regression selection ran 155 tests: 154 passed and
one unrelated opt-in live test was skipped. This includes the live public
access probe and the native Android anonymous-logon identity check.

Fixtures exercise full manifest -> encrypted chunk -> verified original-file
writing, encrypted filenames, LZMA/ZIP decoding, SHA/checksum mismatch, truncated
content, unsafe paths, symlinks, wrong game/manifest identity, missing chunk
coverage, URL policy, cancellation, explicit account binding and legacy direct
files that also expose a UGC handle (the usable HTTPS file URL takes precedence).

The live public Wallpaper sample was `3815558506` (about 3.7 MB).
Public details returned a content manifest and the anonymous Steam CM logon
succeeded. Steam refused the depot key with EResult 15 (AccessDenied), including
with Android native OS identity. The probe confirmed that no file was reported
downloaded. **This is permission/error-path evidence, not a successful real
Wallpaper download.** No user's credentials were used to work around it.

The selected-account route is covered at its CM/account boundary but has not
been exercised end-to-end with a real account that owns Wallpaper Engine.
Actual Steam ownership, account/session privileges and CDN availability still
determine whether a particular item can be downloaded.

## Deliberate limits

Final normal `:app:assembleDebug` succeeded for arm64-v8a and armeabi-v7a.
The pre-existing R8 Kotlin metadata warnings remain non-fatal. No APK was copied
to a delivery folder. The AVD started for this task was stopped after testing.

This change does not execute workshop code, unpack PKG/TEX resources, render
Wallpaper Engine scenes or set Android live wallpapers. Users need a compatible
player for scene/web/PKG content. It does not add a proxy/download backend, bypass
Steam ownership checks, relax TLS or enable cleartext CDN traffic.
