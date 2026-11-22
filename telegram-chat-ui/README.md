# Telegram chat UI source module

This module compiles and runs a portable subset of Telegram Android **13.0.3** UI source from the local `Telegram-master` archive. `UPSTREAM.json` records every original file path, original SHA-256 and adaptation. The archive has no commit metadata; the version is declared by its `gradle.properties`. `LICENSE.Telegram` is the original GPL v2 license text; Telegram source headers permit GPL v2 or later. Monica bridge code uses GPL-3.0-or-later.

The host executes Telegram's message bubble path, grouping and nine-patch cache renderer, cursor positioning/drawing and text-watcher suppression, cubic interpolation, pressed bounce, back drawable, typing dots, staggered RecyclerView item entry, native menu-row geometry and original cascading popup animation/resources, original control bitmap resources and a style-aware glass bridge. Attachment tabs execute the source geometry, original gallery/file Lottie assets and `AnimatedFloat` selection; the composer runs the extracted send-button interpolation. The imported message-selection ripple and gift assets remain unconnected. Importing assets alone does not establish runtime animation parity.

## Host API

Telegram source supplies component geometry, drawing and motion. Runtime colors
come from Monica's current Material theme, including selected/custom/dynamic
palettes, light/dark mode and OLED surfaces. `TelegramChatColors` keeps its
source presets for attribution and fallback construction, while its public
constructor accepts host color roles. Incoming and outgoing text use their
matching surface foregrounds; badges and avatar initials use primary/onPrimary.
The original wallpaper pattern is tinted with the host theme. Social screens
inherit the host scheme without a separate Telegram palette.

The wallpaper host adapter parses and rasterizes the bundled SVG on one background
worker. Page and glass-source drawables share one 1440×2960 alpha mask (4,262,400
bytes), scaled to the current width and tinted at draw time. Initial rendering
uses the theme background while the mask is prepared; plain wallpaper skips that
work. Pending drawables are weakly referenced and late completion uses their
current bounds, theme and pattern strength. The cache contains only decoration.

The app connects both conversation types through native `TelegramChatHeaderView`,
`TelegramChatComposerView`, `TelegramMessageTextView`, `TelegramRecyclerView`,
`TelegramPopupLayout`, `TelegramMenuRow` and `TelegramAttachmentTabsView`.
The header and editor use extracted source layout and original resources;
the text cell combines the imported StaticLayout routine, bubble and status
drawables with a portable Steam measurement adapter. `TelegramChatSurface` owns
the single inset boundary. Steam rich media and its attachment confirmation/reaction/voice panels retain their
existing renderers. This is not full `ChatActivity` or animation parity.

`GlassAttachTab` extracts the attachment-only geometry/width calculation/theme
blending from `GlassTabView`. Original gallery/files forward and reverse JSON
resources are rendered through Airbnb Lottie 6.6.2 rather than Telegram's native
RLottie engine. Press selection uses the extracted `AnimatedFloat` with the
original 320ms deceleration. The host supplies two large accessible action
targets. The social continuation now connects `TelegramAttachmentPickerView`:
its grid runs extracted `PhotoAttachPhotoCell` measurement and selection animation
with the original `CheckBoxBase`/`CheckBox2`; its document list runs the original
`SharedDocumentCell` picker constructor/measurement subset. Steam supplies granted
MediaStore media, album labels, SAF tree entries and URI callbacks. System documents
remain an explicit fallback. Bot services and full `ChatAttachAlert` are excluded.

The default emoji panel now executes original `EmojiView.ImageViewEmoji` cell
measurement/press drawing, complete `EmojiData`, original `EmojiPack` decoder and
the byte-identical `emoji.pack`. The host owns category navigation, recents and
selection callbacks. Steam emoticons, stickers and effects retain their Steam
catalogs; Telegram premium/custom emoji, skin-tone popup and full `EmojiView`
are not imported.

Conversation and friend destinations host native source cells through AndroidView.
`UserCell` preserves avatar/name/status constructor geometry, measurement and
divider drawing; Steam text/bitmap adapters replace its account-specific update,
ImageReceiver, stories and premium paths. `DialogCell` is explicitly a portable
CustomDialog subset: original model and time/name/count layout statements, not
the complete 16,000-line account-bound class. `TextDetailSettingsCell` retains
its full constructor, measurement and setters. `HeaderCell` retains its nonanimated
branch. Friend details execute original `ProfileActionsView` geometry, touch,
accessibility, button bounce, text layout and original icons; account action
registries, avatar RenderNode blur and loading/lottie paths are excluded.
Compose still owns page routing, search/filter controls, section containers,
Steam identity data, relationship confirmation and service state. This is source
component reuse, not a transplant of all DialogsActivity/ContactsActivity/ProfileActivity.

`ComposerActionAnimator` retains `ChatActivityEnterView.animateSendButton` alpha
and scale interpolation. Native send/more controls use the original 220ms
quintic entry and 150ms return timing. Host lifecycle cancels interrupted
animations before starting another and hides the inactive action after settling;
Telegram's custom `SendButton.appear()` glyph animation remains excluded.

```java
TelegramDrawables.initialize(context);
Drawable bubble = TelegramDrawables.message(outgoing, color, sameSenderAbove, sameSenderBelow);
Drawable back = TelegramDrawables.back(iconColor);
Drawable send = TelegramDrawables.send(context, iconColor);
Drawable attach = TelegramDrawables.attach(context, iconColor);
TelegramEditText input = new TelegramEditText(context);
input.setCursorColor(accentColor);
input.setCursorSize(TelegramUi.dp(24));

TelegramRecyclerView messages = new TelegramRecyclerView(context);
messages.getItemsEnterAnimator().showItemsAnimated(firstNewPosition);

TelegramGlassView glass = new TelegramGlassView(context);
glass.setSourceView(messages);
glass.setTintColor(0xcceff4fa);
glass.setCornerRadius(TelegramUi.dp(24));
```

The glass view is an overlay behind toolbar/composer controls. Its source must be a separate view that does not contain the glass overlay. It records real source rendering in a RenderNode. Android 13+ applies the rounded-rectangle refraction shader from Kyant AndroidLiquidGlass 2.0.1 in Liquid Glass mode; M3E uses restrained platform blur. Android 12 uses RenderEffect blur, and Android 8–11 or software Canvas uses an opaque theme fallback. The shader copyright notice is retained in `KyantGlassShader.java`; the original Telegram effect source remains in the upstream subset. Scrolls invalidate the glass automatically; call `refresh()` when replacing source content. Runtime shader failure falls back to platform blur and logs the reason.

`upstream` exposes the adapted classes for direct use. `ButtonBounce.getScale()` belongs in the host's draw/dispatchDraw; `AnimatedFloat.set()` also belongs in draw. `MessageBackgroundDrawable` uses a host View for frame invalidation. `TypingDotsDrawable` should have a Drawable.Callback and matching `start()`/`stop()` lifecycle. `TelegramRecyclerView` already wires entry animation draw and detach callbacks.

## Scope of the extraction

`MessageDrawable.generatePath`, path caching, grouping, shadows and nine-patch generation retain the upstream implementation. Account-global theme/config values become the explicit `BubblePalette` and per-instance radius. Moving wallpaper gradients are excluded; these bubbles use a supplied solid color.

`EditTextBoldCursor` retains the original cursor construction, drawing/positioning, horizontal clamping and watcher suppression methods. Android's EditText supplies IME, accessibility, hints, text selection and the contextual toolbar. Cursor customization uses the public Android 10+ API; Android 8-9 retain the platform cursor. Telegram's hidden-API reflection, animated header hints, premium actions and custom toolbar are excluded.

`RecyclerItemsEnterAnimator` retains upstream alpha/timing logic while targeting AndroidX RecyclerView. Telegram-specific loading-row detection is excluded. Typing animation uses Drawable.Callback scheduling instead of Telegram's process-wide frame/notification services.

No Telegram account, MTProto, network, database, notification, Firebase or native Telegram runtime is included. The library manifest adds no permissions or components. Steam message models, sends, attachment capability and navigation remain host responsibilities.

## Reproduce and verify

`import_upstream.py <path-to-Telegram-source-root>` regenerates only the adapted upstream files, original resources and `UPSTREAM.json`. It performs no downloads. Keep the platform bridge and test files when regenerating.

`verify_upstream.py <path-to-Telegram-source-root>` checks every recorded source
hash and verifies byte identity of imported resources. `UPSTREAM_NOTICES.md`
preserves the original source-header notices for extracted files. Modifications
to bridge controls (including captionless Steam game-share sending) are local
adaptations and are intentionally preserved when regenerating upstream methods.

`verify_regeneration.py <path-to-Telegram-source-root>` runs the importers in an
empty temporary directory and compares the source manifest and all 114 generated
files. Eight separately recorded host adapters are checked for presence, and are
excluded from the generated-file count. A missing generated file is an error;
copying the current module into the temporary directory is not part of this check.

From the Monica Steam root:

```text
gradlew.bat :telegram-chat-ui:compileDebugJavaWithJavac
gradlew.bat :telegram-chat-ui:connectedDebugAndroidTest
```

Instrumentation tests exercise bubble direction/grouping/alpha pixels, multi-line text plus IME-facing watcher behavior, and original shader compilation on Android 13+ when that API is available. Reuse the shared workspace Android test AVD according to `AGENTS.md`.

Native message and toolbar action menus use the extracted static
`ActionBarPopupWindow.startAnimation` and `AndroidUtilities.cascade`, original
menu bitmaps/nine-patch, source submenu palette and source 48dp row metrics.
The host retains popup positioning, Steam capabilities and action callbacks.
The original 150ms translation/fade exit runs before callbacks; native property
accessors are retained for R8/ObjectAnimator reflection.


## Chat-only host integration

Telegram-derived controls are confined to chat, its contact/detail/search pages,
and conversation menus. A scoped chat theme follows the host palette and system
font scale. The inbox reuses TelegramEditText, native rows and popup controls;
a stable sibling ComposeView supplies pixels for its compose-action glass without
capturing the overlay itself. Existing Steam callbacks and protocols stay with
the host. Store, Library, Tokens, Settings and global navigation retain M3E or
the existing Liquid Glass design. Telegram is not a selectable global style.
