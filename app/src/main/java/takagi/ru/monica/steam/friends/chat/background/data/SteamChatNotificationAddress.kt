package takagi.ru.monica.steam.friends.chat.background.data

import java.security.MessageDigest
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationIdentity

internal data class SteamChatNotificationAddress(
    val tag: String,
    val id: Int,
    val groupKey: String
)

/** Full digests keep individual messages distinct even when integer IDs collide. */
internal fun steamChatMessageNotificationAddress(
    identity: SteamChatNotificationIdentity
): SteamChatNotificationAddress = SteamChatNotificationAddress(
    tag = steamChatConversationNotificationPrefix(identity.accountKey, identity.partnerSteamId) +
        identity.stableKey,
    id = MESSAGE_NOTIFICATION_ID_BASE,
    groupKey = steamChatNotificationAddress(identity.accountKey, identity.partnerSteamId).groupKey
)

internal fun steamChatConversationNotificationPrefix(
    accountKey: String,
    partnerSteamId: String
): String = MESSAGE_NOTIFICATION_TAG_PREFIX +
    sha256Hex(listOf(accountKey, partnerSteamId).joinToString("\u001f")) + "_"

internal const val MESSAGE_NOTIFICATION_TAG_PREFIX = "steam_chat_message_"

/** Legacy conversation address, retained to clear notifications posted before upgrading. */
internal fun steamChatNotificationAddress(
    accountKey: String,
    partnerSteamId: String
): SteamChatNotificationAddress {
    val accountDigest = sha256Hex(accountKey).take(ACCOUNT_DIGEST_LENGTH)
    val partnerDigest = sha256Bytes(partnerSteamId)
    val partnerSeed = partnerDigest.take(Int.SIZE_BYTES).fold(0) { value, byte ->
        (value shl Byte.SIZE_BITS) or (byte.toInt() and 0xff)
    }
    return SteamChatNotificationAddress(
        tag = "steam_chat_account_$accountDigest",
        id = MESSAGE_NOTIFICATION_ID_BASE +
            (partnerSeed and MESSAGE_NOTIFICATION_ID_MASK),
        groupKey = "steam_chat_group_$accountDigest"
    )
}

private fun sha256Hex(value: String): String = sha256Bytes(value)
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun sha256Bytes(value: String): ByteArray = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))

private const val ACCOUNT_DIGEST_LENGTH = 24
private const val MESSAGE_NOTIFICATION_ID_BASE = 888_000
private const val MESSAGE_NOTIFICATION_ID_MASK = 0x3fff_ffff
