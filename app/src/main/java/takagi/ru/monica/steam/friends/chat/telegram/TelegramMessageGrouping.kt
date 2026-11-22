package takagi.ru.monica.steam.friends.chat.telegram

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

internal fun telegramMessageDay(
    timestampSeconds: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): LocalDate = Instant.ofEpochSecond(timestampSeconds).atZone(zoneId).toLocalDate()

/**
 * Steam adaptation of ChatActivity's pinnedTop/pinnedBottom predicates:
 * Telegram 13.0.3 ChatActivity.java:37714 / 37737, same sender within 5 minutes.
 * A date separator or Steam service event starts a new visual group.
 */
internal fun telegramMessagesShareGroup(
    senderId: String,
    timestampSeconds: Long,
    otherSenderId: String?,
    otherTimestampSeconds: Long?,
    serviceMessage: Boolean = false,
    otherServiceMessage: Boolean = false,
    zoneId: ZoneId = ZoneId.systemDefault()
): Boolean = !serviceMessage && !otherServiceMessage && senderId == otherSenderId &&
    otherTimestampSeconds != null && abs(timestampSeconds - otherTimestampSeconds) <= 300 &&
    telegramMessageDay(timestampSeconds, zoneId) == telegramMessageDay(otherTimestampSeconds, zoneId)
