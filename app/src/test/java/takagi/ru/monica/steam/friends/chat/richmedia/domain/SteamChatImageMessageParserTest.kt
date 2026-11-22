package takagi.ru.monica.steam.friends.chat.richmedia.domain

import org.junit.Assert.*
import org.junit.Test

class SteamChatImageMessageParserTest {
    private fun url(index: Int) = "https://steamusercontent.com/chat/photo-$index.png"
    private fun image(index: Int) = "[img]${url(index)}[/img]"

    @Test fun responsivePhotoWithSteamUrlWrapperDoesNotFallBackToRawBbcode() {
        val source = "https://images.steamusercontent.com/ugc/10000000000000000000/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/"
        val content = SteamChatRichContentParser.parse(
            "[img src=$source thumbnail_src=${source}?imw=512&&ima=fit&impolicy=Letterbox&imcolor=%23000000&letterbox=false " +
                "srcset=\"${source}?imw=1024&&ima=fit&impolicy=Letterbox&imcolor=%23000000&letterbox=false 1024w\" " +
                "width=1260 height=2800][url=$source]$source[/url][/img]"
        )
        assertTrue("Steam wrapped photo was rendered as text", content is SteamChatRichContent.Attachment)
        content as SteamChatRichContent.Attachment
        assertEquals(source, content.url)
        assertEquals("", content.caption)
        assertEquals(1260, content.images.single().width)
        assertEquals(2800, content.images.single().height)
    }

    @Test fun wrappedPhotoWithoutSrcAndMixedAlbumRetainOrderAndCaption() {
        val raw = "[img][url=${url(0)}]${url(0)}[/url][/img]\n${image(1)}\n" +
            "[img src=${url(2)}][url]${url(2)}[/url][/img]\nCaption"
        val content = SteamChatRichContentParser.parse(raw) as SteamChatRichContent.Attachment
        assertEquals(listOf(url(0), url(1), url(2)), content.images.map { it.url })
        assertEquals("Caption", content.caption)
    }

    @Test fun wrappedPhotoWithOmittedImageClosingTagStillUsesPhotoPath() {
        val content = SteamChatRichContentParser.parse("[img src=${url(0)}][url=${url(0)}]${url(0)}[/url]") as SteamChatRichContent.Attachment
        assertEquals(url(0), content.url)
        assertEquals("", content.caption)
    }

    @Test fun mismatchedWrapperOrMeaningfulLabelIsRetainedAsText() {
        val bodies = listOf(
            "[img src=${url(0)}][url=${url(1)}]${url(1)}[/url][/img]",
            "[img src=${url(0)}][url=${url(0)}]Important description[/url][/img]",
            "[img src=${url(0)}][url=${url(0)}]${url(0)}[/img]",
            "[img src=${url(0)}][url=${url(0)}]${url(0)}[/url]${image(1)}[/img]"
        )
        for (body in bodies) {
            val content = SteamChatRichContentParser.parse(body)
            assertTrue("Wrapper content was hidden: $body", content is SteamChatRichContent.Text)
            assertTrue((content as SteamChatRichContent.Text).body.contains("[img"))
        }
    }

    @Test fun singlePhotoRetainsCaptionAndDimensions() {
        val content = SteamChatRichContentParser.parse("Before\n[img src=${url(0)} width=800 height=1200][/img]\nAfter") as SteamChatRichContent.Attachment
        assertEquals(SteamChatAttachmentKind.IMAGE, content.kind)
        assertEquals(listOf(SteamChatImageSource(url(0), "photo-0.png", 800, 1200)), content.images)
        assertEquals("Before\n\nAfter", content.caption)
    }

    @Test fun albumsKeepEveryImageInOriginalOrder() {
        for (count in listOf(2, 3, 4, 10)) {
            val content = SteamChatRichContentParser.parse((0 until count).joinToString("\n") { image(it) }) as SteamChatRichContent.Attachment
            assertEquals((0 until count).map(::url), content.images.map { it.url })
            assertEquals(url(0), content.url)
            assertEquals("", content.caption)
        }
    }

    @Test fun mixedPairedAndUnclosedSteamTagsDoNotDuplicatePhotosOrUrls() {
        val content = SteamChatRichContentParser.parse("[img src=${url(0)}]${url(0)}\n${image(1)}\n[img src=${url(2)}]${url(2)}") as SteamChatRichContent.Attachment
        assertEquals(listOf(url(0), url(1), url(2)), content.images.map { it.url })
        assertEquals("", content.caption)
    }

    @Test fun unclosedSteamImageWithRepeatedUrlDoesNotDisplayUrlAsCaption() {
        val content = SteamChatRichContentParser.parse("[img src=${url(0)} width=640 height=360]${url(0)}") as SteamChatRichContent.Attachment
        assertEquals("", content.caption)
        assertEquals(640, content.images.single().width)
        assertEquals(360, content.images.single().height)
    }

    @Test fun spoilerAppliesToEntireAlbumAndRetainsCaption() {
        val content = SteamChatRichContentParser.parse("[spoiler]${image(0)}\n${image(1)}\nA synthetic caption[/spoiler]") as SteamChatRichContent.Attachment
        assertTrue(content.spoiler)
        assertEquals(2, content.images.size)
        assertEquals("A synthetic caption", content.caption)
    }

    @Test fun tooManyInvalidOrIncompleteImagesRetainOriginalText() {
        val bodies = listOf(
            (0..10).joinToString("\n") { image(it) },
            "${image(0)}\n[img]javascript:bad[/img]",
            "${image(0)}\n[img]${url(1)}",
            "${image(0)}\n[future]unknown[/future]"
        )
        for (body in bodies) {
            val content = SteamChatRichContentParser.parse(body)
            assertTrue("Content lost: $body", content is SteamChatRichContent.Text)
            assertEquals(body, (content as SteamChatRichContent.Text).body)
        }
    }

    @Test fun invalidDimensionsDoNotPreventViewingAndAmpersandsAreDecoded() {
        val content = SteamChatRichContentParser.parse("[img src='${url(0)}?a=1&amp;b=2' w=-1 h=99999][/img]") as SteamChatRichContent.Attachment
        assertEquals("${url(0)}?a=1&b=2", content.images.single().url)
        assertEquals(0, content.images.single().width)
        assertEquals(0, content.images.single().height)
    }

    @Test fun plainImageUrlStillUsesSinglePhotoPath() {
        val content = SteamChatRichContentParser.parse(url(0)) as SteamChatRichContent.Attachment
        assertEquals(SteamChatAttachmentKind.IMAGE, content.kind)
        assertEquals(url(0), content.url)
        assertTrue(content.images.isEmpty())
    }
}
