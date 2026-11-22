package takagi.ru.monica.steam.friends.chat.richmedia.domain

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatOfficialMessageKind.*

class SteamChatCardsTest {
    private val sender = "76561198000000001"
    private fun card(body: String) = (SteamChatRichContentParser.parse(body, sender) as SteamChatRichContent.OfficialMessage).message

    @Test fun officialTradeLinkKeepsTokenAndCaption() {
        val body = "Here you go\n[tradeofferlink partner=39734273 url=\"https://steamcommunity.com/tradeoffer/new/?partner=39734273&amp;token=abc\"]Trade with me[/tradeofferlink]\nThanks"
        val card = card(body)
        assertEquals(TRADE_LINK, card.kind)
        assertEquals("https://steamcommunity.com/tradeoffer/new/?partner=39734273&token=abc", card.url)
        assertTrue(card.caption.contains("Here you go"))
        assertTrue(card.caption.contains("Thanks"))
        assertEquals("Trade with me", card.description)
        assertEquals(body, card.rawBody)
    }

    @Test fun officialTradeOfferIdAndAccountIdBecomeActionable() {
        val card = card("[tradeoffer id=123456 sender=39734273]A note[/tradeoffer]")
        assertEquals(TRADE_OFFER, card.kind)
        assertEquals("123456", card.tradeOfferId)
        assertEquals(sender, card.senderSteamId)
        assertEquals("https://steamcommunity.com/tradeoffer/123456/", card.url)
    }

    @Test fun legacyTradeTagWithNewOfferUrlIsTradeLink() {
        assertEquals(TRADE_LINK, card("[tradeoffer url=https://steamcommunity.com/tradeoffer/new/?partner=123]").kind)
    }

    @Test fun displayedOfferIdAndDestinationCannotDisagree() {
        val card = card("[tradeoffer id=12 url=https://steamcommunity.com/tradeoffer/99/]")
        assertEquals("12", card.tradeOfferId)
        assertEquals("https://steamcommunity.com/tradeoffer/12/", card.url)
    }

    @Test fun plainAndBbcodeTradeLinksKeepSurroundingWords() {
        val plain = card("Trade: https://steamcommunity.com/tradeoffer/new/?partner=123&token=abc")
        assertEquals("Trade:", plain.caption)
        val linked = card("Hello [url=https://steamcommunity.com/tradeoffer/123]My offer[/url] thanks")
        assertEquals(TRADE_OFFER, linked.kind)
        assertEquals("123", linked.tradeOfferId)
        assertTrue(linked.caption.contains("Hello"))
        assertTrue(linked.caption.contains("My offer"))
        assertTrue(linked.caption.contains("thanks"))
    }

    @Test fun econItemUsesInventoryAssetAndOnlySteamImageToken() {
        val card = card("[econitem appid=730 contextid=2 assetid=123 owner=39734273 image=abc_DEF-123 name=Hat][/econitem]")
        assertEquals(INVENTORY_ITEM, card.kind)
        assertEquals("https://steamcommunity.com/profiles/$sender/inventory/#730_2_123", card.url)
        assertEquals("https://steamcommunity.com/economy/image/abc_DEF-123/256x256", card.imageUrl)
        assertEquals("Hat", card.displayName)
    }

    @Test fun publishedFilesCoverWorkshopCollectionScreenshotVideoAndGuide() {
        mapOf(0 to PUBLISHED_FILE, 2 to COLLECTION, 3 to SCREENSHOT, 5 to SCREENSHOT, 4 to VIDEO, 16 to VIDEO, 9 to GUIDE).forEach { (type, expected) ->
            val card = card("[publishedfile fileid=123 file_type=$type title=Example description=Caption][/publishedfile]")
            assertEquals(expected, card.kind)
            assertEquals("Example", card.displayName)
            assertEquals("Caption", card.description)
            assertEquals("https://steamcommunity.com/sharedfiles/filedetails/?id=123", card.url)
        }
    }

    @Test fun groupInviteCodeAndBroadcastUseActualOfficialArguments() {
        assertEquals("https://steamcommunity.com/chat/invite/Abc-123", card("[inviteurl invitecode=Abc-123]").url)
        assertEquals("https://steamcommunity.com/broadcast/watch/$sender", card("[broadcast broadcaster=$sender]").url)
    }

    @Test fun broadcastInvitationCanUseEnvelopeSenderWithoutGuessingViewer() {
        assertEquals("https://steamcommunity.com/broadcast/watch/$sender", card("[broadcastinvite broadcastid=123 appid=440]").url)
        assertNull(card("[broadcastviewrequest requestid=123]").url)
    }

    @Test fun gameInviteUsesEnvelopeSenderAndKeepsCaptionForSharedLink() {
        val tagged = SteamChatRichContentParser.parse("[gameinvite appid=440 connect=+connect]Join[/gameinvite]", sender) as SteamChatRichContent.GameInvite
        assertEquals("steam://rungame/440/$sender/%2Bconnect", tagged.url)
        val plain = SteamChatRichContentParser.parse("Join me steam://joinlobby/440/123/$sender tonight") as SteamChatRichContent.GameInvite
        assertEquals("Join me  tonight", plain.caption)
    }

    @Test fun sharedLinkFamiliesRecognizeExactSteamDestinations() {
        mapOf(
            "https://steamcommunity.com/profiles/$sender/" to PROFILE,
            "https://steamcommunity.com/id/example/inventory/" to INVENTORY_ITEM,
            "https://steamcommunity.com/groups/example" to GROUP_INVITE,
            "https://s.team/chat/Abc123" to GROUP_INVITE,
            "https://s.team/p/abc-def/token" to FRIEND_REQUEST,
            "https://steamcommunity.com/market/listings/730/Test%20Item" to MARKET,
            "https://steamcommunity.com/sharedfiles/filedetails/?id=123" to PUBLISHED_FILE,
            "https://store.steampowered.com/bundle/123/" to STORE_BUNDLE,
            "https://store.steampowered.com/sub/123/" to STORE_PACKAGE,
            "https://store.steampowered.com/news/app/440" to EVENT,
            "https://steamcommunity.com/my/inventory/#pending_gifts" to GIFT
        ).forEach { (url, kind) ->
            val card = card(url)
            assertEquals(url, card.url)
            assertEquals(kind, card.kind)
        }
    }

    @Test fun supportedOutgoingLinksRoundTripThroughRealParser() {
        listOf(
            "https://store.steampowered.com/app/440/",
            "https://steamcommunity.com/profiles/$sender/",
            "https://steamcommunity.com/tradeoffer/new/?partner=123&token=abc",
            "https://s.team/remoteplay/connect/$sender?appid=440",
            "steam://joinlobby/440/123/$sender",
            "https://steamcommunity.com/sharedfiles/filedetails/?id=123"
        ).forEach { url ->
            assertEquals(url, SteamChatCardLinks.action(url))
            assertFalse(SteamChatRichContentParser.parse("A note\n\n$url") is SteamChatRichContent.Text)
        }
    }

    @Test fun accountConversionRejectsClanAndOutOfRangeIds() {
        assertEquals("https://steamcommunity.com/tradeoffer/new/?partner=39734273", SteamChatCardLinks.tradeWith(sender))
        listOf(null, "", "123", "76561197960265728", "76561202255233024", "103582791429521412", "-1", "18446744073709551615").forEach {
            assertNull(SteamChatCardLinks.tradeWith(it))
        }
    }

    @Test fun unsafeOrUnrelatedDestinationsCannotBecomeCardActions() {
        listOf(
            "javascript:alert(1)", "intent://tradeoffer/123#Intent;end", "file:///etc/passwd",
            "steam://install/440", "https://steamcommunity.com.evil.test/tradeoffer/123",
            "https://steamcommunity.com@evil.test/tradeoffer/123", "https://user@steamcommunity.com/tradeoffer/123",
            "https://steamcommunity.com:444/tradeoffer/123", "http://steamcommunity.com/tradeoffer/123",
            "https://steamcommunity.com/tradeoffer/../login", "https://steamcommunity.com/tradeoffer/%2e%2e/login",
            "https://steamcommunity.com/tradeoffer/new/?partner=0", "https://steamcommunity.com/tradeoffer/new/?partner=4294967296",
            "https://steamcommunity.com/tradeoffer/new/?partner=1&partner=2", "https://steamcommunity.com/login/home/",
            "https://steamcommunity.com/tradeoffer/123\\evil", "steam://joinlobby/440/123/$sender/extra"
        ).forEach { assertNull(it, SteamChatCardLinks.action(it)) }
    }

    @Test fun malformedIdsAndImagesHaveNoInventedAction() {
        val parsed = card("[tradeoffer id=-123 url=https://evil.test/]")
        assertNull(parsed.url)
        assertNull(parsed.tradeOfferId)
        assertNull(card("[econitem image=https://evil.test/pixel]").imageUrl)
        assertNull(card("[publishedfile preview_url=https://evil.test/pixel]").imageUrl)
        assertNull(SteamChatCardLinks.positiveId("18446744073709551616"))
        assertNull(SteamChatCardLinks.invite("../admin"))
    }

    @Test fun cardTypeCannotDisguiseDifferentAction() {
        assertNull(card("[tradeofferlink url=https://steamcommunity.com/profiles/$sender/]").url)
        assertNull(card("[broadcast url=https://steamcommunity.com/tradeoffer/123]").url?.takeIf { it.contains("tradeoffer") })
    }

    @Test fun multipleCardsAndMultipleUrlsStayReadable() {
        listOf(
            "[tradeoffer id=123]one[/tradeoffer] [tradeoffer id=456]two[/tradeoffer]",
            "https://steamcommunity.com/tradeoffer/123 https://steamcommunity.com/tradeoffer/456",
            "steam://joinlobby/440/123/$sender https://example.com/",
            "[tradeoffer id=123][b]note[/b][/tradeoffer]"
        ).forEach { body ->
            val content = SteamChatRichContentParser.parse(body)
            assertTrue(body, content is SteamChatRichContent.Text)
            assertEquals(body, (content as SteamChatRichContent.Text).body)
        }
    }

    @Test fun unknownNotificationPreservesSourceAndDoesNotTrustAction() {
        val body = "[steam_future url=https://steamcommunity.com/tradeoffer/123]Future payload[/steam_future]"
        val card = card(body)
        assertEquals(UNKNOWN, card.kind)
        assertEquals(body, card.rawBody)
        assertEquals("Future payload", card.description)
        assertNull(card.url)
    }

    @Test fun newSharesAreIndexedAsLinksAndPublishedImagesAsMedia() {
        val classifier = takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryClassifier
        val link = SteamChatRichContentParser.parse("https://steamcommunity.com/profiles/$sender/")
        assertEquals(takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryCategory.LINK, classifier.categoryOf(link))
        assertEquals("https://steamcommunity.com/profiles/$sender/", classifier.previewOf(link))
        val image = SteamChatRichContentParser.parse("[publishedfile fileid=123 file_type=5]")
        assertEquals(takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryCategory.MEDIA, classifier.categoryOf(image))
    }
}
