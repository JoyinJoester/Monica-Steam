# Steam chat game cards and expansion · 1.0.310

Verified on 2026-11-21. Unreleased versionCode 18. No commit, push, release or APK delivery.

## Result

Game invitations and store-share cards use a wide cover, title, type and optional caption. Collapsed cards have no overflow button, trailing arrow or store-action footer. Tap or long press opens the preview; store navigation, validated original links, copying and message actions remain inside it. No new user-visible strings were added.

The featured preview measures its destination without placing that copy, then lays out one visible card at bounds interpolated from the tapped card's actual window position and size. Opening takes 360 ms and closing 300 ms with FastOutSlowIn easing. The original card is hidden while lifted and restored on disposal. Artwork decoding state is shared across the card and preview, including the first frame. Existing image URL and payload checks remain in use. Closing a scrolled preview returns its content toward the cover. Back, close and backdrop reverse the expansion; actions retain immediate dismissal before the action callback. Reduced motion skips intermediate frames. Scope/background changes retain immediate preview removal. Non-game previews retain their earlier panel implementation.

## Final validation

- Main and instrumentation build06 succeeded in 171.941s; 90 JVM methods in 9 selected game-invite/rich-media domain suites passed without failures, errors or skips. Final XML is retained separately from the initial JVM run.
- Test-only build-test08 succeeded in 25.282s. Production source and main APK are unchanged from build06/device04. The device06, device07 and isolated device08 runs used identical main/test APK hashes.
- Final batch device07 passed 25/26, runner 48.553s. Its four expansion tests and both real-thread card tests passed, including source and intermediate bounds, continuous reversal, first-frame and every-opening-frame decoded cover pixels, restored source bounds, scrolling back to the cover, reduced motion, and scope disposal.
- The sole failed batch method was the existing actualComposerSendsCardOnlyThroughExistingSendButton: its immediate callback assertion saw an empty list after a physical click. The same installed APKs, source and assertions passed the isolated device08 method in 0.960s. This gives all 26 distinct device methods passing results, but the batch was not entirely green. The intermittent composer-click failure was not independently fixed or attributed to the game-card change.
- Existing card regression also covers links, copy, store/profile/trade actions, local-delete confirmation, unrelated rich cards, and preview background blur. These are synthetic UI callbacks; they do not prove live Steam transactions, remote deletion or persistent provider behavior.
- Inspected the final native opening origin, intermediate opening/closing and restored frames, plus actual chat-thread light M3E and dark large-font glass layouts. The latter uses a 320dp/1.5-font activity fixture. The motion fixture uses the emulator's baseline density. No collapsed card action buttons remain. The 27 opening and 24 closing samples are paused Compose-clock screenshots, not a real-time frame-rate/jank measurement. The local GIF replays these sampled frames and adds endpoint pauses.
- Local editable M3E Canvas was updated and its cover-tap/close interaction inspected. JSON, full docz URL, previews, native images and GIF stay in ignored docs/design/20261121-chat-game-cards. Reports contain no image embeds.
- Reused already-running MuMu instance 0, Android 15/API35/x86_64. Only isolated .gamecards packages were installed and removed. Users, original packages, current user, display size/density and font scale match baseline; MuMu0 PID 15892 remains running and its configuration/data disk are retained. No AVD or real account was used.

## Earlier results and failures retained

- build01 succeeded; initial JVM 90/90. device01 was 21/22 because the old store-label selector matched duplicate content; the selector was scoped to the preview. build-test02 succeeded but was superseded by the user's tap-to-expand correction before a device run.
- build03 succeeded; device02 was 22/25. Two animation checks had incorrect backdrop/clock assumptions and a generic preview assertion failed. Native screenshots also exposed ghosting from the initial recorded-card cross-fade. That implementation was replaced by the single visible card layout, and the generic panel restored.
- build04 succeeded; device03 was 25/25, but native frame inspection exposed the cover reverting to a loading placeholder during initial expansion. Shared decoded artwork state was then added.
- build05 and build06 succeeded. device04 was 24/26: cover sampling did not restrict itself to the moving preview and the scroll check matched three semantic nodes. build-test07 scoped these selectors. device05 was 25/26: all cover/bounds samples passed, while its 12-action fixture fit the tall emulator viewport and therefore had not actually scrolled. build-test08 increased only that fixture to 30 actions; the genuine scroll case subsequently passed.
- device06 completed three expansion methods, then instrumentation aborted with System has crashed. MuMu system_server PID 63291 suffered SIGSEGV in TaskUtils.shouldIgnoreDesktopHomeOcclusion during Activity destruction; the shell instrumentation process also faulted during shutdown. The emulator's Android services recovered with the same host process and kernel uptime. The raw buffer stays private and a relevant timestamped excerpt is included. The failed run and partial screenshots are retained, not counted as a full pass. No emulator restart, settings change or production change was used for device07.

## Scope and artifacts

Debug/x86_64 synthetic fixtures only. No physical device/OEM, older Android, release/R8, live account/network actions, real game invitation acceptance or production frame-rate benchmark. Existing unrelated working-tree changes are preserved. Earlier failed tests and the unresolved intermittent composer-click behavior remain explicit.

Main APK SHA256: ff75a912f4e5cac1ab04764c17eb0905c70f94f626f6e9465d248a23bc3a0f79

Test APK SHA256: 13e172a8d92117dbbb6940f1e8d0bafede6637dba76d9c51bf2bfe2535859231

Text logs, final test XML, source/native hashes, test result history and cleanup receipt are in steam-chat-game-cards-310-evidence. Task-only baselines and patches remain under .codex-tasks/20261121-steam-chat-game-cards.
