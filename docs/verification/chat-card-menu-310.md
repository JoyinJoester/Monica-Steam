# Chat card menu and local deletion — source-only follow-up

Date: 2026-11-10. Scope: Monica Steam, existing unreleased development version.

The user requested compact game-invitation cards and explicitly accepted local-only deletion for private chats. The earlier instruction to write code without building remains active. No Gradle, compiler, JVM test, ADB, emulator, installation, or live Steam operation was run for this follow-up.

## Changes

- The game-invitation card has no inline action rows. Tapping the body opens the existing internal Store destination. Its 48dp overflow control opens the existing themed native popup with Copy link, View Store, Open link, and Delete when eligible. Invitation link actions retain the existing validator and missing-handler handling. Long press retains message actions.
- Private-chat deletion requires a confirmation explaining its local scope. Account/conversation-specific SHA-256 identity markers persist in app-private preferences; message text and credentials are not written into this store. Identity uses server timestamp, ordinal, and sender so reloaded messages remain hidden after a local echo ID disappears. Unconfirmed and queued messages have no delete action.
- The UI filters locally hidden messages from the conversation, its search, and shared history. It waits for marker loading before showing history, reports load/save failure, and reacts to late marker changes after navigating away/back. Raw cached history and pagination stay intact; an empty filtered page still provides Load older messages. This is local hiding, not server recall or secure cache erasure, and uninstalling/clearing app data removes the markers.
- Group-card deletion reuses the existing own-message eligibility and confirmation/server callback; other members' messages do not gain deletion authority.
- Earlier code-only follow-ups are included in the static scope: Start trade is in the private-chat header menu, not the attachment sheet; it still opens the Steam web form, not a native offer editor. The conversation refresh indicator uses the measured filter height as its inset.

## Verification boundary

Tree-sitter parsed 16 affected Kotlin source/test files without syntax errors. Five locale files each contain the same 53 card string keys (265 localized values), with XML parsing, nonempty values, duplicate-name, resource reference, placeholder parity, and whitespace checks. Call sites and server-identity/pagination ownership were reviewed. These checks do not establish Kotlin type checking, rendered layout, runtime behavior, or frame performance.

Five new JVM cases cover stable server identity, pending-message exclusion, account/conversation separation, retained pagination, and hiding history until markers load. Device test sources cover card/menu dispatch, delete confirmation/cancel, and marker persistence/notification across store instances. Existing trade/theme test sources were updated for the relocated entry. None of these updated tests has been compiled or run.

Source hashes and static results are recorded separately under `.codex-tasks/20261110-chat-card-menu/static-result.json`. The earlier `.codex-tasks/20261110-chat-cards/` freeze, Debug/R8 builds, 81 JVM results, and 20 distinct passing API 32 methods describe the earlier implementation only; they are not evidence for the current changes. Existing failure records and the unrelated dirty tree are preserved. No commit, push, release, or APK delivery was performed.
