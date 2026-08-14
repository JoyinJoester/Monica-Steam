package takagi.ru.monica.steam.friends.chat.richmedia.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatRichMediaModelsTest {
    @Test
    fun usesOfficialHighResolutionEmoticonsAndAnimatedStickerEndpoint() {
        assertEquals(
            "https://steamcommunity.com/economy/emoticonlarge/steamthumbsup",
            SteamChatEmoticon("steamthumbsup").imageUrl
        )
        assertEquals(
            "https://steamcommunity.com/economy/sticker/steamhappy",
            SteamChatSticker("steamhappy").imageUrl
        )
        assertEquals(
            "https://steamcommunity.com/economy/sticker/steamhappy",
            SteamChatRichContent.Sticker("steamhappy").imageUrl
        )
    }

    @Test
    fun parsesOfficialStickerSlashCommand() {
        val content = SteamChatRichContentParser.parse("/sticker Mesmer spin")

        assertTrue(content is SteamChatRichContent.Sticker)
        assertEquals("Mesmer spin", (content as SteamChatRichContent.Sticker).name)
        assertEquals("/sticker Mesmer spin", SteamChatSticker("Mesmer spin", "Mesmer").messageCode)
    }

    @Test
    fun parsesOfficialStickerBbcodeTag() {
        val content = SteamChatRichContentParser.parse(
            "[sticker type=Mesmer%20spin][/sticker]"
        )

        assertTrue(content is SteamChatRichContent.Sticker)
        assertEquals("Mesmer spin", (content as SteamChatRichContent.Sticker).name)
    }

    @Test
    fun parsesOfficialRoomEffectBbcodeTag() {
        val content = SteamChatRichContentParser.parse(
            "[roomeffect type=confetti][/roomeffect]"
        )

        assertTrue(content is SteamChatRichContent.OfficialMessage)
        val effect = (content as SteamChatRichContent.OfficialMessage).message
        assertEquals(SteamChatOfficialMessageKind.ROOM_EFFECT, effect.kind)
        assertEquals("confetti", effect.description)
    }

    @Test
    fun normalizesOfficialEmoticonBbcodeTagForInlineRendering() {
        val content = SteamChatRichContentParser.parse(
            "[emoticon]steamthumbsup[/emoticon]"
        )

        assertEquals(":steamthumbsup:", (content as SteamChatRichContent.Text).body)
    }

    @Test
    fun normalizesMultipleOfficialEmoticonTagsWithoutLeakingBbcode() {
        val content = SteamChatRichContentParser.parse(
            "[emoticon]steammocking[/emoticon]" +
                "[emoticon]steammocking[/emoticon]" +
                "[emoticon type=steamthumbsup][/emoticon]"
        ) as SteamChatRichContent.Text

        assertEquals(":steammocking: :steammocking: :steamthumbsup:", content.body)
    }

    @Test
    fun parsesOfficialSelfClosingStickerAndRoomEffectTags() {
        val sticker = SteamChatRichContentParser.parse(
            "[sticker type=Mesmer%20spin]"
        ) as SteamChatRichContent.Sticker
        assertEquals("Mesmer spin", sticker.name)

        val effect = SteamChatRichContentParser.parse(
            "[roomeffect type=confetti]"
        ) as SteamChatRichContent.OfficialMessage
        assertEquals(SteamChatOfficialMessageKind.ROOM_EFFECT, effect.message.kind)
        assertEquals("confetti", effect.message.description)
    }

    @Test
    fun richMediaCommandsUseSteamOfficialSyntax() {
        assertEquals("/sticker Mesmer spin", SteamChatSticker("Mesmer spin").messageCode)
        assertEquals("/roomeffect confetti", SteamChatEffect("confetti").messageCode)
    }

    @Test
    fun parsesSteamImageAndLinkedArchiveBbcode() {
        val image = SteamChatRichContentParser.parse(
            "[img]https://steamusercontent.com/chat/photo.png[/img]"
        ) as SteamChatRichContent.Attachment
        val archive = SteamChatRichContentParser.parse(
            "[url=https://steamusercontent.com/chat/files/export.zip]export.zip[/url]"
        ) as SteamChatRichContent.Attachment

        assertEquals(SteamChatAttachmentKind.IMAGE, image.kind)
        assertEquals("photo.png", image.label)
        assertEquals(SteamChatAttachmentKind.ARCHIVE, archive.kind)
        assertEquals("export.zip", archive.label)
    }

    @Test
    fun convertsSteamBbcodeLinksIntoInlineTextLinks() {
        val content = SteamChatRichContentParser.parse(
            "See [url=https://store.steampowered.com/app/730/?a=1&amp;b=2]Counter-Strike 2[/url] now"
        ) as SteamChatRichContent.Text

        assertEquals("See Counter-Strike 2 now", content.body)
        assertEquals(
            listOf(
                SteamChatTextLink(
                    start = 4,
                    endExclusive = 20,
                    url = "https://store.steampowered.com/app/730/?a=1&b=2"
                )
            ),
            content.links
        )
    }

    @Test
    fun keepsMultipleBbcodeLinksAndPlainUrlsInOneMessage() {
        val content = SteamChatRichContentParser.parse(
            "[url=https://steamcommunity.com/id/monica]Profile[/url] " +
                "and https://store.steampowered.com/app/570/"
        ) as SteamChatRichContent.Text

        assertEquals(
            "Profile and https://store.steampowered.com/app/570/",
            content.body
        )
        assertEquals(2, content.links.size)
        assertEquals("https://steamcommunity.com/id/monica", content.links[0].url)
        assertEquals("https://store.steampowered.com/app/570/", content.links[1].url)
    }

    @Test
    fun parsesSteamSelfLabeledUrlTag() {
        val content = SteamChatRichContentParser.parse(
            "[url]https://steamcommunity.com/groups/monica[/url]"
        ) as SteamChatRichContent.Text

        assertEquals("https://steamcommunity.com/groups/monica", content.body)
        assertEquals(content.body, content.links.single().url)
    }

    @Test
    fun keepsUnknownBbcodeSchemesReadableWithoutMakingThemClickable() {
        val content = SteamChatRichContentParser.parse(
            "[url=javascript:alert(1)]unsafe link[/url]"
        ) as SteamChatRichContent.Text

        assertEquals("unsafe link", content.body)
        assertTrue(content.links.isEmpty())
    }

    @Test
    fun preservesSteamSpoilerImagesAndDecodesEscapedQueryParameters() {
        val image = SteamChatRichContentParser.parse(
            "[spoiler][img]https://steamusercontent.com/chat/photo.png?x=1&amp;y=2[/img][/spoiler]"
        ) as SteamChatRichContent.Attachment

        assertEquals(SteamChatAttachmentKind.IMAGE, image.kind)
        assertEquals("https://steamusercontent.com/chat/photo.png?x=1&y=2", image.url)
        assertTrue(image.spoiler)
    }

    @Test
    fun recognizesExtensionlessSteamUgcImageLinks() {
        val url = "https://images.steamusercontent.com/ugc/124316100000000000/ABCDEF1234567890/"
        val image = SteamChatRichContentParser.parse(
            "[url=$url]$url[/url]"
        ) as SteamChatRichContent.Attachment
        val plainImage = SteamChatRichContentParser.parse(url) as SteamChatRichContent.Attachment
        val genericLink = SteamChatRichContentParser.parse(
            "[url=https://steamusercontent.com/ugc/archive]archive[/url]"
        ) as SteamChatRichContent.Text

        assertEquals(SteamChatAttachmentKind.IMAGE, image.kind)
        assertEquals(SteamChatAttachmentKind.IMAGE, plainImage.kind)
        assertEquals("archive", genericLink.body)
        assertEquals("https://steamusercontent.com/ugc/archive", genericLink.links.single().url)
    }

    @Test
    fun parsesCurrentSteamImageTagWithResponsiveAttributes() {
        val original =
            "https://images.steamusercontent.com/ugc/12431610482965499658/" +
                "34654771B312467FA7E577C5564F2FCB0580F69A/"
        val content = SteamChatRichContentParser.parse(
            """
            [img src=$original thumbnail_src=$original?imw=512&amp;ima=fit srcset="$original?imw=1024&amp;ima=fit 1024w" width=1260 height=2800]$original[/img]
            """.trimIndent()
        ) as SteamChatRichContent.Attachment

        assertEquals(SteamChatAttachmentKind.IMAGE, content.kind)
        assertEquals(original, content.url)
        assertEquals("Steam attachment", content.label)
    }

    @Test
    fun parsesCurrentSteamImageTagWhenClosingTagIsOmitted() {
        val original =
            "https://images.steamusercontent.com/ugc/12431610482965499658/" +
                "34654771B312467FA7E577C5564F2FCB0580F69A/"
        val content = SteamChatRichContentParser.parse(
            "[img src=$original thumbnail_src=$original?imw=512&amp;ima=fit width=1260 height=2800]$original"
        ) as SteamChatRichContent.Attachment

        assertEquals(original, content.url)
    }

    @Test
    fun keepsOrdinaryTextUntouched() {
        val body = "hello :steamthumbsup:"
        assertEquals(SteamChatRichContent.Text(body), SteamChatRichContentParser.parse(body))
    }

    @Test
    fun parsesSteamStoreLinksAsNativeGameShares() {
        val plain = SteamChatRichContentParser.parse(
            "Cyberpunk 2077\nhttps://store.steampowered.com/app/1091500/"
        ) as SteamChatRichContent.StoreGameShare
        val bbcode = SteamChatRichContentParser.parse(
            "[url=https://store.steampowered.com/app/730/]Counter-Strike 2[/url]"
        ) as SteamChatRichContent.StoreGameShare
        val captioned = SteamChatRichContentParser.parse(
            "今晚一起玩吗？\n\nCyberpunk 2077\nhttps://store.steampowered.com/app/1091500/"
        ) as SteamChatRichContent.StoreGameShare
        val steamConverted = SteamChatRichContentParser.parse(
            "黑神话：悟空\n[steamstore app=\"2358720\"]" +
                "https://store.steampowered.com/app/2358720/[/steamstore]"
        ) as SteamChatRichContent.StoreGameShare

        assertEquals(1091500, plain.appId)
        assertEquals("Cyberpunk 2077", plain.label)
        assertEquals("https://store.steampowered.com/app/1091500/", plain.url)
        assertEquals(730, bbcode.appId)
        assertEquals("Counter-Strike 2", bbcode.label)
        assertEquals("今晚一起玩吗？", captioned.caption)
        assertEquals("Cyberpunk 2077", captioned.label)
        assertEquals(2358720, steamConverted.appId)
        assertEquals("黑神话：悟空", steamConverted.label)
    }

    @Test
    fun keepsNonStoreLinksAsOrdinaryTextLinks() {
        val content = SteamChatRichContentParser.parse(
            "https://steamcommunity.com/profiles/76561198000000000/"
        ) as SteamChatRichContent.Text

        assertEquals(1, content.links.size)
    }

    @Test
    fun parsesSteamMeActionsAsAFirstClassMessage() {
        assertEquals(
            SteamChatRichContent.Action("waves hello"),
            SteamChatRichContentParser.parse("/me waves hello")
        )
    }

    @Test
    fun parsesSteamJoinLobbyInviteBeforeGenericLinks() {
        val content = SteamChatRichContentParser.parse(
            "[url=steam://joinlobby/730/123456789/76561198000000001]Join game[/url]"
        )

        assertTrue(content is SteamChatRichContent.GameInvite)
        val invite = content as SteamChatRichContent.GameInvite
        assertEquals(730, invite.appId)
        assertEquals("123456789", invite.lobbyId)
        assertEquals("76561198000000001", invite.inviterSteamId)
        assertEquals("steam://joinlobby/730/123456789/76561198000000001", invite.url)
    }

    @Test
    fun keepsUnknownSteamSpecialMessageReadable() {
        val body = "[steam_unknown type=42]payload[/steam_unknown]"
        val unknown = SteamChatRichContentParser.parse(body) as SteamChatRichContent.OfficialMessage
        assertEquals(SteamChatOfficialMessageKind.UNKNOWN, unknown.message.kind)
        assertEquals(body, unknown.message.rawBody)
    }

    @Test
    fun keepsUnknownSelfClosingSteamSystemMessagesReadable() {
        val body = "[steam_new_feature type=42]"
        val unknown = SteamChatRichContentParser.parse(body) as SteamChatRichContent.OfficialMessage

        assertEquals(SteamChatOfficialMessageKind.UNKNOWN, unknown.message.kind)
        assertEquals("42", unknown.message.attributes["type"])
        assertEquals(body, unknown.message.rawBody)
    }

    @Test
    fun parsesPlainSteamTradeOfferLinksAsTypedMessages() {
        val body = "Trade: https://steamcommunity.com/tradeoffer/new/?partner=123&token=abc"
        val trade = SteamChatRichContentParser.parse(body) as SteamChatRichContent.OfficialMessage

        assertEquals(SteamChatOfficialMessageKind.TRADE_OFFER, trade.message.kind)
        assertEquals(
            "https://steamcommunity.com/tradeoffer/new/?partner=123&token=abc",
            trade.message.url
        )
    }

    @Test
    fun parsesSteamBbcodeGameInviteArgumentsAndOtherSystemTags() {
        val invite = SteamChatRichContentParser.parse(
            "[gameinvite appid=440 lobbyid=123456789]Team Fortress 2[/gameinvite]"
        ) as SteamChatRichContent.GameInvite
        assertEquals(440, invite.appId)
        assertEquals("123456789", invite.lobbyId)
        assertEquals("steam://joinlobby/440/123456789", invite.url)

        val trade = SteamChatRichContentParser.parse(
            "[tradeoffer]https://steamcommunity.com/tradeoffer/123[/tradeoffer]"
        ) as SteamChatRichContent.OfficialMessage
        assertEquals(SteamChatOfficialMessageKind.TRADE_OFFER, trade.message.kind)
        assertEquals("123", trade.message.tradeOfferId)
        assertEquals("https://steamcommunity.com/tradeoffer/123", trade.message.url)
    }

    @Test
    fun buildsOfficialGameInviteUrlsForConnectAndRemotePlayArguments() {
        val connect = SteamChatRichContentParser.parse(
            "[gameinvite appid=440 steamid=76561198000000001 connect=+connect][/gameinvite]"
        ) as SteamChatRichContent.GameInvite
        assertEquals(
            "steam://rungame/440/76561198000000001/%2Bconnect",
            connect.url
        )

        val remotePlay = SteamChatRichContentParser.parse(
            "[gameinvite appid=440 steamid=76561198000000001 remoteplay=restricted_countries=CN][/gameinvite]"
        ) as SteamChatRichContent.GameInvite
        assertEquals(
            "steam://remoteplay/connect/76561198000000001?appid=440&restricted_countries=CN",
            remotePlay.url
        )
    }

    @Test
    fun parsesGiftInventoryRemotePlayAndBroadcastNotificationsAsTypedMessages() {
        val samples = mapOf(
            "[gift appid=730]A gift is waiting[/gift]" to SteamChatOfficialMessageKind.GIFT,
            "[inventoryitem appid=440 name=Hat]New item[/inventoryitem]" to
                SteamChatOfficialMessageKind.INVENTORY_ITEM,
            "[remoteplayinvite appid=570 steamid=76561198000000001]Join[/remoteplayinvite]" to
                SteamChatOfficialMessageKind.REMOTE_PLAY_INVITE,
            "[broadcastinvite steamid=76561198000000001]Watch now[/broadcastinvite]" to
                SteamChatOfficialMessageKind.BROADCAST_INVITE
        )

        samples.forEach { (body, expectedKind) ->
            val parsed = SteamChatRichContentParser.parse(body) as SteamChatRichContent.OfficialMessage
            assertEquals(expectedKind, parsed.message.kind)
            assertEquals(body, parsed.message.rawBody)
        }
    }

    @Test
    fun parsesSelfClosingTradeAndAdditionalSteamNotificationFamilies() {
        val trade = SteamChatRichContentParser.parse(
            "[incomingtradeoffer tradeofferid=987654 steamid=76561198000000001]"
        ) as SteamChatRichContent.OfficialMessage
        assertEquals(SteamChatOfficialMessageKind.TRADE_OFFER, trade.message.kind)
        assertEquals("987654", trade.message.tradeOfferId)

        val samples = mapOf(
            "[groupinvite]Community[/groupinvite]" to SteamChatOfficialMessageKind.GROUP_INVITE,
            "[eventnotification]Weekend event[/eventnotification]" to SteamChatOfficialMessageKind.EVENT,
            "[commentnotification]New comment[/commentnotification]" to SteamChatOfficialMessageKind.COMMENT,
            "[marketnotification]Item sold[/marketnotification]" to SteamChatOfficialMessageKind.MARKET
        )
        samples.forEach { (body, kind) ->
            val parsed = SteamChatRichContentParser.parse(body) as SteamChatRichContent.OfficialMessage
            assertEquals(kind, parsed.message.kind)
        }
    }
}
