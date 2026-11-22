package takagi.ru.monica.steam.friends.chat.richmedia.domain

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationDecision
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationPolicy
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.info.domain.*

class SteamChatOpenGraphCardParserTest {
    private val profile = "https://steamcommunity.com/profiles/76561198000000001/"
    private val image = "https://community.steamstatic.com/chat/image/synthetic/share_image.jpg"
    private fun og(url: String = profile, title: String = "Steam Community :: Fixture", desc: String = ":fixture: Bio", img: String = image) =
        """[og url="$url" img="$img" title="$title" desc="$desc"]$url[/og]"""
    private fun card(body: String) = (SteamChatRichContentParser.parse(body) as SteamChatRichContent.OfficialMessage).message

    @Test fun serverEnrichedProfileRetainsTitleBioImageAndOriginal() {
        val body = og(desc = ":fixture: Bio https://example.com/about")
        val parsed = card(body)
        assertEquals(SteamChatOfficialMessageKind.PROFILE, parsed.kind)
        assertEquals(profile, parsed.url)
        assertEquals(image, parsed.imageUrl)
        assertEquals("Steam Community :: Fixture", parsed.displayName)
        assertEquals(":fixture: Bio https://example.com/about", parsed.description)
        assertEquals(body, parsed.rawBody)
        assertTrue(parsed.caption.isBlank())
    }

    @Test fun multilineHeaderAndBracketedBiographyAreParsedAsMetadata() {
        val body = """Before
            [OG
            url="$profile"
            img="$image"
            title="Fixture [name]"
            desc="Line one [bio]
            Line two :emote:"]$profile[/OG]
            After""".trimIndent()
        val parsed = card(body)
        assertEquals("Fixture [name]", parsed.displayName)
        assertTrue(parsed.description.contains("Line one [bio]"))
        assertTrue(parsed.description.contains("Line two :emote:"))
        assertEquals("Before\nAfter", parsed.caption)
        assertEquals(body, parsed.rawBody)
    }

    @Test fun entitiesDecodeOnceWithoutHtmlRendering() {
        val parsed = card(og(title = "A &amp; B &quot;name&quot; &#x1F600;", desc = "&lt;b&gt;Bio&lt;/b&gt; &amp;quot;"))
        assertEquals("A & B \"name\" 😀", parsed.displayName)
        assertEquals("<b>Bio</b> &quot;", parsed.description)
    }

    @Test fun singleQuotedAndEmptyMetadataKeepAnActionableProfile() {
        val parsed = card("[og url='$profile' img='' title='' desc='']$profile[/og]")
        assertEquals(profile, parsed.url)
        assertNull(parsed.imageUrl)
        assertEquals("", parsed.displayName)
    }

    @Test fun inventoryAndBroadcastEnrichmentRoundTripToTheCorrectKind() {
        mapOf(profile+"inventory/" to SteamChatOfficialMessageKind.INVENTORY_ITEM,
            "https://steamcommunity.com/broadcast/watch/76561198000000001" to SteamChatOfficialMessageKind.BROADCAST_INVITE
        ).forEach { (url, kind) ->
            val parsed = card(og(url))
            assertEquals(kind, parsed.kind)
            assertEquals(url, parsed.url)
        }
    }

    @Test fun wrappedTradeRetainsTokenAndOfferId() {
        val url = "https://steamcommunity.com/tradeoffer/new/?partner=123&token=fixture"
        val escaped = url.replace("&", "&amp;")
        assertEquals(url, card(og(escaped)).url)
        assertEquals("123456", card(og("https://steamcommunity.com/tradeoffer/123456/")).tradeOfferId)
    }

    @Test fun storeWrapperUsesExistingGameCardAndPreservesCaption() {
        val url = "https://store.steampowered.com/app/440/"
        val content = SteamChatRichContentParser.parse("A note\n"+og(url, "Synthetic game", "About game")) as SteamChatRichContent.StoreGameShare
        assertEquals(440, content.appId)
        assertEquals(url, content.url)
        assertEquals("Synthetic game", content.label)
        assertEquals("A note\nAbout game", content.caption)
    }

    @Test fun unsafePreviewImageDoesNotPreventSafeProfileFromRendering() {
        for (unsafe in listOf("https://evil.test/pixel", "http://community.steamstatic.com/chat/image/a.jpg",
            "https://community.steamstatic.com.evil.test/pixel", "https://evil@community.steamstatic.com/chat/image/a.jpg",
            "https://community.steamstatic.com:444/chat/image/a.jpg")) {
            assertNull(unsafe, card(og(img = unsafe)).imageUrl)
        }
    }

    @Test fun mismatchedInnerUrlAndMetadataCannotHideDifferentDestination() {
        val body = og().replace("]$profile[/og]", "]https://steamcommunity.com/profiles/76561198000000002/[/og]")
        assertNull(SteamChatOpenGraphCardParser.parse(body))
        assertEquals(body, (SteamChatRichContentParser.parse(body) as SteamChatRichContent.Text).body)
    }

    @Test fun unsupportedUrlCannotBecomeAnActionableOpenGraphCard() {
        for (url in listOf("https://evil.test/", "javascript:alert(1)", "https://steamcommunity.com.evil.test/profiles/76561198000000001/", "http://steamcommunity.com/profiles/76561198000000001/")) {
            assertNull(url, SteamChatOpenGraphCardParser.parse(og(url)))
        }
    }

    @Test fun duplicateAttributesMalformedHeadersAndMultipleCardsStayReadable() {
        for (body in listOf(
            "[og url=\"$profile\" URL=\"$profile\"]$profile[/og]",
            "[og url=\"$profile\" title=\"unclosed]$profile[/og]",
            "[og url=\"$profile\" bad]$profile[/og]", og()+"\n"+og(), og()+" https://example.com/",
            "[og url=\"$profile\"]missing URL[/og]", og()+"[/og]",
            og(title = "x".repeat(16_384))
        )) {
            assertNull(body.take(120), SteamChatOpenGraphCardParser.parse(body))
        }
    }

    @Test fun notificationPreviewDoesNotExposeOpenGraphMarkupOrImageUrl() {
        val message = SteamChatMessage("76561198000000002", "76561198000000002", 1800000000, 1, og())
        val decision = SteamChatNotificationPolicy.evaluate("fixture", "76561198000000001", message) as SteamChatNotificationDecision.Notify
        assertEquals("Steam Community :: Fixture", decision.preview.text)
        assertFalse(decision.preview.text.contains("[og"))
        assertFalse(decision.preview.text.contains(image))
    }
}
