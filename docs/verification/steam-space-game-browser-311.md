# Steam Space game names and original web links — 2026-11-22

Version remains 1.0.311 / code 18. This follows the screenshots reporting a numeric game ID in an achievement preview and View on Steam returning to Monica's community page.

## Changes and evidence

- StoreItem's valid canonical `id` now takes precedence when redundant `appid` is explicitly zero. Conflicting positive identities, failed items and packages cannot supply an app name. The previous parser failed the explicit-zero regression before the fix. Item type 0 and success 1 were already correct; the initial suspicion about those fields was disproved by the public protobuf contract.
- Names are fetched through one official `IStoreBrowseService/GetItems/v1/` Web API batch, using the selected account token and language. The batch requests basic display data, excludes prices, has an 8-second call timeout and runs before optional media enrichment. The news feed remains on CM. Names are restricted to requested app IDs; missing data stays partial. The exact CM/network failure on the user's device was not captured or reproduced.
- A cached feed with unresolved names remains visible and triggers revalidation on reentry, even within the normal five-minute freshness interval. A complete, fresh feed retains the existing reuse behavior. Account identity, cancellation and late-result checks remain covered.
- View on Steam retains the validated original HTTPS URL and uses a browser selector. It no longer issues a plain VIEW request that Monica's own Steam app-link handler can receive. No cookie or token is forwarded to the browser. If no browser can open it, the existing localized failure message is used; no unrestricted fallback retries the self-routing request.

## Validation

Final build03 completed the main/test packaging and JVM checks in 113.327s. All 95 relevant JVM tests passed with zero failures, errors or skips. Test-only build04 added a real browser-dispatch check without changing production sources or the main APK.

Final device04 passed all 35 methods in one run (runner 59.336s), on existing MuMu2 / API35 / x86_64. The same final main APK was used throughout device01–04. Coverage includes intercepted official HTTP metadata responses through the production parser/service into the feed and expanded achievement preview, normal and 1.5 text sizes, missing metadata, validated original URLs, no-browser behavior, Space content filters, scrolling, lifecycle, account changes and encrypted cache recreation. The actual `Context.startActivity` browser-selected request was accepted, and Chromium owned the foreground afterward. URL, selector and absent extras were independently asserted at the dispatch boundary. This verifies browser dispatch; it does not certify the remote page's contents or authentication.

The two native achievement captures from device01 were inspected: Cyberpunk 2077 is readable, and the original-link action remains reachable in both sizes. News, names, actor and achievement records use synthetic fixtures. Their API requests are intercepted locally. Public game artwork may load through the existing CDN image path; no real account credentials were used or passed to the browser. There is no physical/OEM, real-account incident or new Release/R8 validation. Privacy, removal or network failures can still leave a game name unavailable.

Failures and limited probes remain in the task evidence: red01 is the expected parser failure; build01's JVM native allocation failed; build02 generated main/test APKs but the new cache test omitted a required null cursor argument; that fixture was corrected before successful build03. A direct package-manager CLI probe with a selector returned No activity found and is not counted as a successful resolution. Device01 passed 4/4 and device02 passed 30/30. Device03 recorded four passing methods, including actual browser dispatch, before MuMu2 exited and ADB disconnected (exit 255); no cause for the instance exit was established. The same unchanged final code/test package passed the complete device04 rerun. No assertions were relaxed.

## Environment and retained artifacts

MuMu0 was in use by the other active Android reference-capture task, and MuMu1 was its existing OMK instance. MuMu2 was an already configured stopped instance, so it was started and reused; no clone or AVD was created. It was suspended during compilation to reduce memory pressure. Cleanup removed only the absent-at-baseline `.spacegamebrowser` main/test packages. Original package inventory, user identities/flags, current user, display/font settings, configuration and data disk were retained. Only task-started MuMu2 was stopped; MuMu0 and MuMu1 were left running.

Receipts, the expected failure XML, final 95-test XML, all build/device failures, APK hashes, source hashes, protocol excerpt, browser probe and cleanup checks are in `.codex-tasks/20261121-space-game-browser/`. Native captures remain in ignored `docs/design/20261121-space-game-browser/`. Unrelated dirty changes are preserved. No commit, push, release or APK delivery was performed.
