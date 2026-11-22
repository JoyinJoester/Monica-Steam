package takagi.ru.monica.steam.friends.chat.background.domain

import java.security.MessageDigest
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentKind
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContentParser

data class SteamChatNotificationIdentity(
    val accountKey: String,
    val partnerSteamId: String,
    val timestamp: Long,
    val ordinal: Int
) {
    val stableKey: String
        get() = sha256Hex(
            listOf(accountKey, partnerSteamId, timestamp.toString(), ordinal.toString())
                .joinToString(separator = "\u001f")
        )
}

enum class SteamChatNotificationPreviewKind {
    TEXT,
    STICKER,
    IMAGE,
    VIDEO,
    FILE,
    GAME_INVITE,
    STEAM_EVENT
}

data class SteamChatNotificationPreview(
    val kind: SteamChatNotificationPreviewKind,
    val text: String = ""
)

enum class SteamChatNotificationIgnoreReason {
    INVALID_ACCOUNT,
    OUTGOING,
    INVALID_PARTNER,
    INVALID_SERVER_MESSAGE
}

sealed interface SteamChatNotificationDecision {
    data class Notify(
        val identity: SteamChatNotificationIdentity,
        val preview: SteamChatNotificationPreview
    ) : SteamChatNotificationDecision

    data class Ignore(
        val reason: SteamChatNotificationIgnoreReason
    ) : SteamChatNotificationDecision
}

object SteamChatNotificationPolicy {
    fun evaluate(
        accountKey: String,
        accountSteamId: String,
        message: SteamChatMessage
    ): SteamChatNotificationDecision {
        if (accountKey.isBlank() || !accountSteamId.isSteamChatSteamId()) {
            return SteamChatNotificationDecision.Ignore(
                SteamChatNotificationIgnoreReason.INVALID_ACCOUNT
            )
        }
        if (message.senderSteamId == accountSteamId) {
            return SteamChatNotificationDecision.Ignore(
                SteamChatNotificationIgnoreReason.OUTGOING
            )
        }
        if (
            !message.partnerSteamId.isSteamChatSteamId() ||
            message.senderSteamId != message.partnerSteamId
        ) {
            return SteamChatNotificationDecision.Ignore(
                SteamChatNotificationIgnoreReason.INVALID_PARTNER
            )
        }
        if (
            message.timestamp <= 0L ||
            message.ordinal < 0 ||
            message.ordinal == Int.MAX_VALUE ||
            message.body.isBlank() ||
            message.clientMessageId.isNotBlank() ||
            message.deliveryState != SteamChatDeliveryState.SENT
        ) {
            return SteamChatNotificationDecision.Ignore(
                SteamChatNotificationIgnoreReason.INVALID_SERVER_MESSAGE
            )
        }
        return SteamChatNotificationDecision.Notify(
            identity = SteamChatNotificationIdentity(
                accountKey = accountKey,
                partnerSteamId = message.partnerSteamId,
                timestamp = message.timestamp,
                ordinal = message.ordinal
            ),
            preview = preview(message.body)
        )
    }

    internal fun preview(body: String): SteamChatNotificationPreview {
        val content = runCatching { SteamChatRichContentParser.parse(body) }
            .getOrElse {
                return SteamChatNotificationPreview(
                    kind = SteamChatNotificationPreviewKind.TEXT,
                    text = compactText(body)
                )
            }
        return when (content) {
            is SteamChatRichContent.Text -> SteamChatNotificationPreview(
                kind = SteamChatNotificationPreviewKind.TEXT,
                text = compactText(content.body)
            )
            is SteamChatRichContent.Action -> SteamChatNotificationPreview(
                kind = SteamChatNotificationPreviewKind.TEXT,
                text = compactText("* ${content.body}")
            )
            is SteamChatRichContent.Sticker -> SteamChatNotificationPreview(
                kind = SteamChatNotificationPreviewKind.STICKER,
                text = content.name
            )
            is SteamChatRichContent.Attachment -> SteamChatNotificationPreview(
                kind = when (content.kind) {
                    SteamChatAttachmentKind.IMAGE -> SteamChatNotificationPreviewKind.IMAGE
                    SteamChatAttachmentKind.VIDEO -> SteamChatNotificationPreviewKind.VIDEO
                    SteamChatAttachmentKind.ARCHIVE,
                    SteamChatAttachmentKind.LINK -> SteamChatNotificationPreviewKind.FILE
                },
                text = compactText(content.label)
            )
            is SteamChatRichContent.GameInvite -> SteamChatNotificationPreview(
                kind = SteamChatNotificationPreviewKind.GAME_INVITE,
                text = compactText(content.label)
            )
            is SteamChatRichContent.StoreGameShare -> SteamChatNotificationPreview(
                kind = SteamChatNotificationPreviewKind.GAME_INVITE,
                text = compactText(content.label ?: content.url)
            )
            is SteamChatRichContent.OfficialMessage -> SteamChatNotificationPreview(
                kind = SteamChatNotificationPreviewKind.STEAM_EVENT,
                text = compactText(
                    content.message.caption.ifBlank { content.message.displayName }
                        .ifBlank { content.message.description }.ifBlank { content.message.url.orEmpty() }
                )
            )
        }
    }

    private fun compactText(value: String): String {
        val compact = value.trim().replace(WHITESPACE, " ")
        return if (compact.length <= MAX_PREVIEW_LENGTH) compact
        else compact.take(MAX_PREVIEW_LENGTH - 1).trimEnd() + "…"
    }

    private val WHITESPACE = Regex("\\s+")
    private const val MAX_PREVIEW_LENGTH = 180
}

private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
