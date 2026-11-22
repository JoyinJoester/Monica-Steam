# Monica Steam 1.0.311 release package verification

Date: 2026-11-22. The user explicitly selected the already signed packages in `C:/Users/joyins/Desktop/msteamrl/release`. These are the release inputs; this task did not rebuild, resign or modify either APK.

Both packages have application ID `takagi.ru.monica.steamapp`, versionName `1.0.311`, versionCode `18`, and are not debuggable. The ARM64 package contains only ARM64 native libraries; the ARM32 package contains only armeabi-v7a native libraries. Android `apksigner verify` succeeds for both. The signer certificate SHA-256 is `c376ebd7d4997ea5a4569c76f758ecf206ba8587de33d7f12338bbb3555cab8b`, matching the downloaded official 1.0.310 ARM64 package, so the signing identity is retained for upgrades.

| Architecture | Filename | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| ARM64 | Monica-Steam-Android-arm64-v8a-1.0.311-26112218-05.APK | 31586576 | 79dbefc68d3c9684488d3202177c469b9ad094e61626823741fe097da6ddaf24 |
| ARM32 | Monica-Steam-Android-armeabi-v7a-1.0.311-26112218-05.APK | 27283230 | 253fa847f93d2375560acec22a969471dbc4f11a8f6a74dfa949e4d411a71346 |

The supplied R8 mapping records the Space name loader, library-name enrichment, native compose screen/model and post service, including inlined symbols. Both APKs contain the official post-service method and public appdetails endpoint, and all 14 new composer resource keys. Packaged Steam JNI bytes match the release stripped outputs; merged JNI bytes match the retained source JNI inputs. Resource shrink/obfuscation inspection is static evidence, not an on-device R8 runtime test.

The root release notes are used verbatim: Chinese and English each retain 57 entries, including the latest name/FAB/native-post behavior. GitHub's draft assets report exactly the expected filenames, sizes and SHA-256 digests, and its body matches the notes file. Only the two APKs are uploaded. Mapping and native debugging symbols remain in the user's directory.

The signed packages were generated before the source commit, so their embedded BuildConfig Git SHA is `d147f87`; build type is release and build detail is `26112218-05`. The release source commit collects the already present 1.0.311 working-tree changes. Raw source hashes were frozen and Git's clean-filtered hashes were checked against the staged index before committing. No production source was reformatted or changed during publishing. Existing extra EOF newlines, build-log spacing, and byte-exact Telegram upstream whitespace are retained and recorded in the local staging check. Generated x86 JNI, screenshots/design files, local handoff notes, crash logs and scratch files are not included.

Validation reuses the completed feature-specific reports, including `steam-space-names-publish-311.md` (110 passing JVM tests and 42 distinct device methods obtaining passing results across preserved runs), `steam-space-game-browser-311.md`, and `steam-ui-resume-311.md`. Their original failures and limitations remain recorded. No real Steam account was used to publish a status. There is no new physical/OEM or on-device ARM/R8 validation.

An extra all-Steam JVM run was attempted during publishing. The first release-test command found that this project has no `testReleaseUnitTest` task. The subsequent 4 GiB Debug compile saturated its heap and was stopped after checking that the JVM belonged to this task. A 6 GiB G1 retry continued a full project recompile and was also stopped when proceeding with the user-selected packages and prior accepted verification. Neither unfinished run is counted as a passing test run, and both logs/receipts are retained. Only the two task-owned compiler processes were stopped; no emulator or another project's processes were changed.

Before staging, Git was blocked by an empty index lock last written on 2026-11-13. Active processes were inspected, and this stale lock was moved into the local task directory as a recoverable backup; no index or source was deleted. The first source commit is `76185374a6ee04cf66d0fb38fcb636e7f356d4ce` (2644 paths). The final tag also includes this verification report and the previously edited SteamNova interoperability documentation.

Local receipts: `.codex-tasks/20261122-steam-publish-311/`. The final published release metadata, remote branch/tag verification and asset digests are recorded there after publication. The prior 1.0.310 release remains intact.
