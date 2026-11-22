# Telegram chat source integration — 2026-10-25

The private and group conversation surfaces use the local `telegram-chat-ui`
Android library and Steam-owned protocol, session, media and action callbacks.
The target is the supplied Telegram dark-blue conversation screenshot. Editable
M3E Canvas records are saved locally under the ignored `docs/design/telegram-chat-reuse/`
directory. The editor uses the project identifier `monica-steam-telegram-chat-r2`
at `http://127.0.0.1:5186/`. The complete compressed document link is saved in
that directory's `canvas-url.txt`; the document itself is `canvas.json`.

The eight Canvas states cover the screenshot composition, Steam group chat,
private drafts, attachments, reactions, conversation actions and native attachment tabs. The preview
check found no editor lock, no JavaScript errors and no missing image assets.
Reference screenshots, sample images and device screenshots remain local and
are not embedded in committed documentation.

## What executes in the app

| Surface | Runtime implementation |
| --- | --- |
| Message bubbles | Telegram `MessageDrawable`, its original path, grouping and nine-patch renderer |
| Plain message text and time | Extracted Telegram `makeStaticLayout`, original status resources and clock, wrapped by the native `TelegramMessageTextView` |
| Toolbar | Native glass islands and extracted `ChatAvatarContainer` layout, with Steam info, refresh, invite and voice callbacks |
| Composer | Native `TelegramEditText`, extracted cursor logic, original resources/geometry and `animateSendButton` scale/fade interpolation |
| List entry | Native AndroidX RecyclerView with Telegram `RecyclerItemsEnterAnimator` |
| Control and typing motion | Telegram `ButtonBounce` in native header/composer buttons; `TypingDotsDrawable` in the private-chat subtitle |
| Background | Original Telegram `default_pattern.svg`, rendered with AndroidSVG |
| Glass | Original Telegram AGSL and stroke extraction; RenderNode source-view adapter and platform blur |
| Message/header actions | Native `ActionBarMenuSubItem` geometry and original icon/nine-patch assets, original cascading popup entry and 150ms dismiss animation |
| Group channels | Steam room tabs hosted in a native glass strip |
| Attachment actions | Source `GlassTabView` geometry and original gallery/file Lottie assets; source `PhotoAttachPhotoCell`, `SharedDocumentPickerCell` and `CheckBox2` picker cells with Steam URI/upload callbacks |
| Social lists/details | Source `DialogCell`, `UserCell`, `HeaderCell`, `TextDetailSettingsCell` and `ProfileActionsView` hosted by Compose; Steam owns data, routing and relationship actions |
| Emoji picker | Source `EmojiData`, `EmojiPack`, `ImageViewEmoji` and byte-identical `emoji.pack`; host owns categories, recents and insertion callbacks |

`UPSTREAM.json` identifies original source paths, SHA-256 hashes, licenses and
adaptations. The local Telegram archive declares version 13.0.3 and has no Git
commit metadata. `verify_upstream.py` verifies all 132 source records and byte
identity of imported bitmap, SVG, Lottie JSON and AGSL resources against that archive.

## Behavior preserved

Private and group chats retain their existing Steam send, retry, copy, reaction,
report, delete where allowed, attachment upload/preview, store-game share,
inline emoticon, sticker and voice capabilities. The host preserves Steam's
capability and account restrictions. A Telegram read check is never inferred
from a Steam send acknowledgment: Steam `SENT` uses one check.

The native list saves message IDs and pixel offsets through the existing
`SteamChatReadingPositionStore`. Explicit search targets override saved anchors.
History insertion and incoming messages preserve an older reader's anchor;
an outgoing send or a bottom-following reader follows the latest message.
The same policy handles IME and multiline-editor viewport changes.
Because the native layout manager stacks from the end, its default resize
behavior retains the lower edge. A pre-layout viewport callback explicitly
pins an older message and its pixel offset instead. Restored anchors also
survive the subsequent header/composer measurement pass.

Programmatic editor echoes do not replace equal text, preserving native
composing spans, selection and focus. Native send remains available for a game
share with no caption. The scaffold owns navigation and IME insets once.

## Adaptation boundaries

This is a portable source extraction, not the complete unmodified Telegram
`ChatActivity`, `ChatMessageCell` or `ChatActivityEnterView`. Steam rich content,
attachment confirmation, Steam sticker/emoticon catalogs, reaction picker
and voice panels continue using existing Monica components. The emoji grid and
photo/file picker cells execute extracted source implementations. Full
`EmojiView`, `ChatAttachAlert` and every original transition are not transplanted.
The imported selection-ripple helper is currently unconnected; its presence is
not counted as runtime reuse. `AnimatedFloat` now executes attachment-tab selection.
The native toolbar/composer wrappers replace Telegram's controller calls with
explicit callbacks; text-only cell measurement is adapted for Steam messages.

Telegram-only pinned messages, bots and topic aggregation are not fabricated.
Wallpaper uses the original pattern over a theme color instead of Telegram's
moving four-color gradient. Source-view glass capture uses target-sized platform
blur instead of Telegram's full process-wide downscale/cache pipeline. These
boundaries mean the work must not be described as pixel-identical full-page
Telegram reuse.

Android 12/API 32 exercises platform blur. Original AGSL requires API 33 or later;
older APIs use the documented translucent fallback. Runtime shader failures log
the reason and use platform blur. All tests use offline fixtures and do not
send actual Steam messages or access personal credentials.

## Verification

| Check | Result |
| --- | --- |
| Upstream verification | 132 records verified; imported SVG, AGSL, bitmap, nine-patch PNG, Lottie JSON and emoji.pack bytes match the source archive |
| Reproducible extraction | 114 generated files match independent extraction into an initially empty ignored directory; 8 recorded host adapters are maintained separately; the 132-record source manifest also matches |
| Android builds | App debug APKs and app/module Android-test APKs built successfully, including app R8 and resource shrinking |
| JVM regression | 343 tests in 80 suites; no failures, errors or skipped tests |
| API 32 component instrumentation | 5 passed: bubbles, Unicode input/watchers, composing-span/selection/captionless send, narrow Unicode/RTL cells, original attachment JSON decoding/native callbacks and three-theme attachment contrast |
| API 32 real-page instrumentation | 12 passed across chat/social suites: reading position, send/retry, drafts, rooms, original emoji pack decoding, native gallery/file cells, conversation/friend filters, friend actions and group member navigation |
| API 36 component baseline | Earlier 5-test run passed, including original AGSL compilation/dimension binding; new attachment controls were verified on API 32 |
| Local Canvas | Eight screens rendered; no editor lock, JavaScript errors or missing images |
| Patch hygiene | `git diff --check` passes; design/reference/device images remain ignored |

The API 32 device was the already-running shared
`Monica_Issue136_API_32` (`emulator-5554`). It remains available. The existing
`Monica_API_36_16K` AVD was started solely for the shader/component tests and
stopped afterwards; no new AVD was created. The API 36 test confirms shader
compilation, not visual or performance parity of the whole app on that API.

Actual API 32 offline screenshots were inspected for private/group pages,
light/dark themes, IME, attachments, emoji picker, message actions and restored
history. They remain in the ignored local design directory. The fixture is
debug-only and non-exported. Tests do not exercise live Steam sends, uploads,
voice calls or real-account history; those services retain their existing
adapters and are covered here by the relevant regression tests.

Runtime testing exposed and fixed an R8 boundary failure: WebDAV's transitive
Simple XML/xpp3 interfaces were renamed while AndroidSVG received Android's
platform `KXmlParser`. The library's consumer rule retains the XML interfaces.
Real-page tests run against the minified app and verify this startup path.
The build still emits existing Kotlin-metadata compatibility warnings from R8.

Source guards now follow native list/input ownership. Two unrelated stale
guards were checked against the unchanged baseline before correction: the
527-line chat screen's limit was 500 (now 550), and the settings notification
route had moved to `MonicaSteamSettingsScreen`. They are not product fixes.

The local task record retains build/instrumentation logs and earlier failed
attempts, including incorrect native-view lookup of a Compose retry button,
test-only Compose/coroutines minification incompatibility and the subsequently
fixed older-reading resize regression. No APK was copied for delivery, and
this work does not create a release or alter the configured version.

The later menu continuation additionally replaces both the native-platform
header overflow and Monica message action rows with the shared native Telegram
popup layout. It retains the original `ActionBarPopupWindow.startAnimation`
cascade, background alpha/height reveal, original menu bitmaps and nine-patch,
48dp row geometry and 150ms translation/fade dismissal. Popup anchoring remains
a Compose adapter for message cells and a native PopupWindow for the toolbar.
Disabled actions remain disabled, report/delete permission callbacks are kept,
and popup/drawable lifecycle is released with the hosting view. The minified-app
UI suite passes with both the copy and toolbar-refresh callbacks.

The attachment continuation extracts `GlassTabView` attachment icon/label
geometry, width calculation and theme blending, with unchanged gallery/file
forward/reverse JSON. Airbnb Lottie 6.6.2 replaces Telegram's native RLottie
renderer. Tab selection uses the extracted `AnimatedFloat` and original 320ms
deceleration. The later picker integration runs extracted photo-grid and document
cells with original circular selection drawing. Steam supplies granted MediaStore
media, SAF tree entries and URI callbacks. System photo/document pickers remain
explicit fallbacks. Telegram album, bot and account services remain outside this
extraction; Steam owns permission requests and attachment confirmation. Native
accessibility targets are at least 48dp.

The composer now uses independent send/more views and extracted
`ChatActivityEnterView.animateSendButton`: scale 0.1 to 1 and alpha 0 to 1,
220ms quintic entry and 150ms return. Host lifecycle cancels interrupted
transitions before restarting and removes the inactive target after settling.
The custom Telegram `SendButton.appear()` glyph effect remains excluded.

Visual inspection caught black attachment icons/labels on dark glass despite
passing behavior checks. The palette importer now preserves upstream
`Theme.getColor` legacy fallback mappings when older built-in themes omit the
new glass-tab keys. A three-palette contrast check guards this regression;
updated light/dark native screenshots were inspected after all 6 page tests
and 5 component tests passed. The first failed visual evidence remains local.
The picker test observes the actual outgoing picker intents and supplies
canceled results through an instrumentation monitor; it does not choose a
personal photo/file or exercise a real upload.

Final continuation evidence: `build-attachments-theme-fix.log`,
`library-attachments-theme-fix.log`, `ui-attachments-theme-fix.log`,
`provenance-attachments-final.json` and `regeneration-attachments-final.json`
in the local task's `raw` directory. Regeneration matched all 85 generated
files. The 280-test JVM regression passed before the palette-only correction.

## Social source continuation

Chat lists now use the portable `DialogCell.CustomDialog` source subset;
friend/contact lists and group members use `UserCell`; friend/group details use
`HeaderCell`, `TextDetailSettingsCell` and `ProfileActionsView`. The original
profile action geometry, touch/accessibility handling and pressed animation
execute with Steam callbacks. Compose owns search/filter controls, sections,
page navigation and Steam relationship/service state. Game details are unchanged.
This is component source reuse, not a complete transplant of `DialogsActivity`,
`ContactsActivity` or `ProfileActivity`.

The Unicode emoji panel executes the official `EmojiData`, `EmojiPack` decoder,
`ImageViewEmoji` drawing/press behavior and original uncompressed `emoji.pack`.
Host code provides category navigation, recents and insertion; Steam emoticons,
stickers and effects retain their own catalogs. Telegram premium/custom emoji
and the skin-tone popup are not imported.

Final continuation logs in the task's local `raw` directory are
`native-final-build.log`, `native-final-ui.log`, `native-final-provenance.json`,
`native-final-regeneration.log` and `native-focused-r4.log`. The final build
completed successfully; the combined chat/social run passed 12 tests, and the
focused group-search/private-editor rerun passed 2 tests. JVM XML reports show
343 tests in 80 suites with zero failures/errors/skips. Source verification
reports 132 records and zero errors; regeneration reports 122 files and no
differences. The 2026-10-26 independent check below corrects the earlier count,
which included eight preserved host files among the 122 unique manifest paths.
Actual offline screenshots remain local under the ignored social
design directory. No live Steam credentials, personal media or real uploads
are used by these fixtures.

## Resumption verification — 2026-10-26

Installed the latest successful app/test builds and executed the new contact
font-bounds assertions on the shared API 32 AVD. The focused contact test passed
in 19.498 seconds, including dark/light themes and both filtering/navigation
callbacks. The AVD retained its existing 1.5 font scale. Fresh screenshots show
complete Latin and Chinese names/status labels. The source adapter uses DIP
text sizes without platform font padding to match Telegram SimpleTextView's
fixed cell geometry; this extraction does not claim to transplant SimpleTextView.

The new `telegram-chat-ui/verify_regeneration.py` generates into an empty
temporary directory and compares the source manifest plus 114 generated files.
Eight source-attributed host adapters remain maintained separately. This
corrects the earlier 122-file regeneration count, which included those preserved
hosts. Source verification still passes all 132 records. Isolated negative
checks confirmed that a changed generated Java file and a missing checked file
each produce a nonzero exit with the corresponding error.

The nine active Canvas states (six social pages plus emoji, gallery and file
pickers) were reopened in the existing local editor in both palettes;
no JavaScript errors or editing lock were found.
The full compressed links and editable JSON remain saved under the ignored
`docs/design/telegram-social-design/` directory. Fresh contact screenshots are
also local and ignored. Library/store code has no diff in this work.

The first fresh combined UI run overlapped another instrumentation process on
the shared device: 5 tests passed and 7 failed with lost foreground activity,
keyboard waits and unavailable screenshots. Independent thread/device logs
confirmed concurrent test runners. After the other tests finished, an exclusive
rerun passed 11/12; its first screenshot returned null during display startup.
Both UI suites now use a shared screenshot helper that retries null captures
for up to five seconds and fails explicitly if no image is available. This
does not relax contact bounds or interaction assertions. Foreground UI tests
on the shared AVD must run sequentially.

Final app and Android-test builds succeeded (2m30s and 4m23s). The newly
installed pair passed all 12 UI tests in 104.18 seconds, including the first
conversation capture, filters/detail navigation, contact bounds, official
pickers, IME, reading-position restore and send/draft callbacks. Earlier
343-test JVM results in 80 suites remain verified; this resumption changed
test capture infrastructure and source verification tooling rather than Steam
business code. No live Steam sends, personal media or real uploads were used.

Local evidence is retained in the task's `raw` directory:
`native-ui-final-20261026.log`, `native-app-evidence-build-20261026.log`,
`native-test-evidence-build-20261026.log`, `native-provenance-20261026.json`,
`native-regeneration-20261026.json` and
`native-regeneration-negative-20261026.json`. Failed/concurrent attempts
remain alongside the passing run. The existing AVD remains running; no AVD
was created or restarted, no APK was copied for delivery, and no release,
version change, commit or push was performed.
