# MDBX3 runtime

Monica Steam bundles the same pinned MDBX3 Android runtime as MonicaPass: version **3.0.0-alpha.1**, status **preview**, source commit `d1d3cc4fdff4e33fcb70099b3e7df36eeae43ba4` in Monica-Pass/Mdbx. The binaries and provenance come from MonicaPass commit `9285d12365a30aa84667922e32982b8471fee335`.

MDBX3 is a drop-in native engine update. Its writable format remains **MDBX-2**, library name remains `libmdbx_ffi.so`, and the UniFFI namespace remains `mdbx_ffi`. The Kotlin bindings are identical to MonicaPass. `Mdbx2Repository` and the stored `RUST_MDBX2` identifier are retained for compatibility; the UI identifies the runtime as MDBX3. Existing MDBX-2 vaults do not need a format conversion.

MDBX1 is retired from ordinary reads, writes, account selection, default selection and synchronization. Existing records and cached content are retained for deliberate recovery. New vaults always use MDBX3. The manager can open a legacy file solely to upgrade it or remove its registration; the ordinary router rejects it and stale writes roll back their Room changes. Unknown historic engine identifiers remain classified as legacy, never silently reinterpreted as MDBX3. Historical Room migrations are unchanged.

Explicit upgrades read the original via the legacy reader, create a separate MDBX3 vault, verify folders, entries and attachments, and check that the source fingerprint is unchanged. A remote legacy vault upgrades only the existing local copy; its remote file is never overwritten, and the new local vault needs separate sync configuration. Failed upgrades clean up their target artifacts and can be retried. Installation does not automatically rewrite user files.

`mdbx-engine/MDBX3_RUNTIME_PROVENANCE.json` records the source, toolchain, compatibility profile and SHA-256/build ID for arm64-v8a, armeabi-v7a and x86_64. The x86_64 runtime supports emulator verification; shipping APK architecture selection remains controlled by the app build.

Run `python .github/scripts/verify_mdbx3_bundled_runtime.py` from the repository root to check all bundled libraries. APK build CI performs this verification before building. MDBX3 Runtime CI exercises the bundled x86_64 engine with the existing write/delete/reopen smoke tests on an Android emulator.

Future runtime updates must update binaries and provenance together, verify compatibility with the checked-in bindings, run vault compatibility tests, and check final APK native payloads. Keep the preview label until the pinned upstream runtime is promoted.
