package takagi.ru.monica.steam.richtext.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamRichTextParserTest {
    @Test
    fun parsesMarkdownAndSteamBbcodeIntoStyledSafeText() {
        val parsed = SteamRichTextParser.parse(
            "**Bold** [i]italic[/i] [Steam](https://store.steampowered.com/)"
        )

        assertEquals("Bold italic Steam", parsed.text)
        assertEquals(
            SteamRichTextSpan(0, 4, SteamRichTextStyle.BOLD),
            parsed.spans.first { it.style == SteamRichTextStyle.BOLD }
        )
        assertEquals(
            SteamRichTextSpan(5, 11, SteamRichTextStyle.ITALIC),
            parsed.spans.first { it.style == SteamRichTextStyle.ITALIC }
        )
        assertEquals(
            SteamRichTextLink(12, 17, "https://store.steampowered.com/"),
            parsed.links.single()
        )
    }

    @Test
    fun remapsExistingChatLinksAfterRemovingFormattingMarkers() {
        val parsed = SteamRichTextParser.parse(
            source = "**See** Profile",
            sourceLinks = listOf(
                SteamRichTextLink(
                    start = 8,
                    endExclusive = 15,
                    url = "https://steamcommunity.com/id/monica"
                )
            )
        )

        assertEquals("See Profile", parsed.text)
        assertEquals(
            SteamRichTextLink(
                start = 4,
                endExclusive = 11,
                url = "https://steamcommunity.com/id/monica"
            ),
            parsed.links.single()
        )
    }

    @Test
    fun ignoresUnsafeLinksButKeepsTheirLabelsReadable() {
        val parsed = SteamRichTextParser.parse("[unsafe](javascript:alert(1))")

        assertEquals("unsafe", parsed.text)
        assertTrue(parsed.links.isEmpty())
    }

    @Test
    fun preservesBalancedParenthesesInSafeMarkdownLinks() {
        val parsed = SteamRichTextParser.parse(
            "[Steam](https://example.com/wiki/Game_(series))"
        )

        assertEquals("Steam", parsed.text)
        assertEquals(
            SteamRichTextLink(0, 5, "https://example.com/wiki/Game_(series)"),
            parsed.links.single()
        )
    }

    @Test
    fun parsesSteamBbcodeStylesAndLists() {
        val parsed = SteamRichTextParser.parse(
            "[quote][b]Update[/b][/quote]\n[list][*]One[*]Two[/list]"
        )

        assertEquals("Update\n• One• Two", parsed.text)
        assertTrue(parsed.spans.any { it.style == SteamRichTextStyle.BOLD })
        assertTrue(parsed.spans.any { it.style == SteamRichTextStyle.QUOTE })
    }

    @Test
    fun preservesSteamSpoilerRangesForPressToRevealUi() {
        val parsed = SteamRichTextParser.parse("Visible [spoiler]hidden text[/spoiler]")

        assertEquals("Visible hidden text", parsed.text)
        assertEquals(
            SteamRichTextSpan(8, 19, SteamRichTextStyle.SPOILER),
            parsed.spans.single { it.style == SteamRichTextStyle.SPOILER }
        )
    }
}
