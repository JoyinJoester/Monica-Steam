package takagi.ru.monica.steam.friends.chat.telegram

import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramMessageGroupingTest {
    @Test fun sameSenderGroupsAtTheFiveMinuteBoundaryButNotBeyondIt() {
        assertTrue(group(1_000, 1_300))
        assertFalse(group(1_000, 1_301))
    }

    @Test fun groupEndsAtAnotherSenderOrServiceEvent() {
        assertFalse(group(1_000, 1_100, otherSender = "other"))
        assertFalse(group(1_000, 1_100, service = true))
        assertFalse(group(1_000, 1_100, otherService = true))
    }

    @Test fun dateSeparatorBreaksEvenAOneSecondGroup() {
        assertFalse(group(86_399, 86_400))
        assertTrue(group(86_400, 86_401))
    }

    @Test fun absentNeighborStartsOrEndsAGroup() {
        assertFalse(group(1_000, null, otherSender = null))
    }

    private fun group(
        timestamp: Long,
        otherTimestamp: Long?,
        otherSender: String? = "sender",
        service: Boolean = false,
        otherService: Boolean = false
    ) = telegramMessagesShareGroup(
        senderId = "sender",
        timestampSeconds = timestamp,
        otherSenderId = otherSender,
        otherTimestampSeconds = otherTimestamp,
        serviceMessage = service,
        otherServiceMessage = otherService,
        zoneId = ZoneOffset.UTC
    )
}
