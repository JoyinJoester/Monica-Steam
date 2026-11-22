package takagi.ru.monica.steam.friends.chat.telegram

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatReadingPosition

class TelegramListScrollPolicyTest {
    @Test fun firstOpenRestoresStoredOffsetInsteadOfFollowingTheLatest() {
        assertEquals(
            TelegramListScrollPlan.Anchor(SteamChatReadingPosition("b", 27)),
            plan(restored = false, saved = SteamChatReadingPosition("b", 27))
        )
    }

    @Test fun explicitTargetOverridesSavedOffset() {
        assertEquals(
            TelegramListScrollPlan.Anchor(SteamChatReadingPosition("c"), requested = true),
            plan(restored = false, saved = SteamChatReadingPosition("b", 27), requested = "c")
        )
    }

    @Test fun targetThatArrivesWithOlderHistoryIsHandledAfterInitialRestore() {
        assertEquals(
            TelegramListScrollPlan.Anchor(SteamChatReadingPosition("older"), requested = true),
            plan(ids = listOf("older", "a", "b", "c"), requested = "older")
        )
    }

    @Test fun handledTargetDoesNotRepeatedlyPullTheReaderBack() {
        assertEquals(
            TelegramListScrollPlan.Anchor(SteamChatReadingPosition("b", 31)),
            plan(requested = "a", handled = "a", visible = SteamChatReadingPosition("b", 31))
        )
    }

    @Test fun prependingHistoryPreservesTheVisibleMessageAndOffset() {
        assertEquals(
            TelegramListScrollPlan.Anchor(SteamChatReadingPosition("b", 31)),
            plan(ids = listOf("older", "a", "b", "c"), visible = SteamChatReadingPosition("b", 31))
        )
    }

    @Test fun incomingMessageDoesNotInterruptReadingOlderMessages() {
        assertEquals(
            TelegramListScrollPlan.Anchor(SteamChatReadingPosition("a", 8)),
            plan(ids = listOf("a", "b", "c", "d"), below = 10, visible = SteamChatReadingPosition("a", 8))
        )
    }

    @Test fun ownSendAndIncomingNearBottomFollowTheLatestMessage() {
        assertEquals(TelegramListScrollPlan.Latest, plan(ids = listOf("a", "b", "c", "d"), below = 10, outgoing = true))
        assertEquals(TelegramListScrollPlan.Latest, plan(ids = listOf("a", "b", "c", "d"), below = 2))
    }

    @Test fun missingSavedMessageFallsBackToLatestAndEmptyThreadDoesNotScroll() {
        assertEquals(TelegramListScrollPlan.Latest, plan(restored = false, saved = SteamChatReadingPosition("missing")))
        assertEquals(TelegramListScrollPlan.None, plan(ids = emptyList(), restored = false))
    }

    @Test fun removingNewestMessageDoesNotInterruptReadingOlderMessages() {
        assertEquals(
            TelegramListScrollPlan.Anchor(SteamChatReadingPosition("a", 8)),
            plan(ids = listOf("a", "b"), outgoing = true, visible = SteamChatReadingPosition("a", 8))
        )
    }

    @Test fun aBottomFollowerRemainsAtBottomDuringContentAndHistoryUpdates() {
        assertEquals(TelegramListScrollPlan.Latest, plan(followingLatest = true))
        assertEquals(
            TelegramListScrollPlan.Latest,
            plan(ids = listOf("older", "a", "b", "c"), followingLatest = true)
        )
    }

    @Test fun explicitTargetStillOverridesFollowingTheBottom() {
        assertEquals(
            TelegramListScrollPlan.Anchor(SteamChatReadingPosition("b"), requested = true),
            plan(requested = "b", followingLatest = true)
        )
    }

    private fun plan(
        ids: List<String> = listOf("a", "b", "c"),
        requested: String? = null,
        handled: String? = null,
        saved: SteamChatReadingPosition? = null,
        visible: SteamChatReadingPosition? = SteamChatReadingPosition("a"),
        restored: Boolean = true,
        outgoing: Boolean = false,
        below: Int = 0,
        followingLatest: Boolean = false
    ) = planTelegramListScroll(
        previousIds = listOf("a", "b", "c"),
        messageIds = ids,
        requestedMessageId = requested,
        handledRequestedMessageId = handled,
        savedPosition = saved,
        visiblePosition = visible,
        restored = restored,
        latestMessageOutgoing = outgoing,
        messagesBelow = below,
        followingLatest = followingLatest
    )
}
