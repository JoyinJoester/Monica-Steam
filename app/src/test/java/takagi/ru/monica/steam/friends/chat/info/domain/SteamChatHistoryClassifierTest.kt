package takagi.ru.monica.steam.friends.chat.info.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatHistoryClassifierTest {

    @Test
    fun plainTextIsText() {
        assertEquals(
            SteamChatHistoryCategory.TEXT,
            SteamChatHistoryClassifier.classify("今晚八点开黑吗")
        )
    }

    @Test
    fun textCarryingALinkBecomesLink() {
        assertEquals(
            SteamChatHistoryCategory.LINK,
            SteamChatHistoryClassifier.classify("看这个 https://store.steampowered.com/app/620/")
        )
    }

    @Test
    fun storeShareLineIsLink() {
        assertEquals(
            SteamChatHistoryCategory.LINK,
            SteamChatHistoryClassifier.classify("https://store.steampowered.com/app/620/Portal_2/")
        )
    }

    @Test
    fun imageAttachmentIsMedia() {
        assertEquals(
            SteamChatHistoryCategory.MEDIA,
            SteamChatHistoryClassifier.classify("[img]https://cdn.akamai.steamstatic.com/steam/apps/620/header.jpg[/img]")
        )
    }

    @Test
    fun videoAttachmentIsMedia() {
        assertEquals(
            SteamChatHistoryCategory.MEDIA,
            SteamChatHistoryClassifier.classify("[video]https://cdn.example.com/clip.mp4[/video]")
        )
    }

    @Test
    fun archiveAttachmentIsFile() {
        assertEquals(
            SteamChatHistoryCategory.FILE,
            SteamChatHistoryClassifier.classify("[url=https://cdn.example.com/pack.zip]pack.zip[/url]")
        )
    }

    @Test
    fun stickerCommandIsSticker() {
        assertEquals(
            SteamChatHistoryCategory.STICKER,
            SteamChatHistoryClassifier.classify("/sticker 1234567890")
        )
    }

    @Test
    fun lobbyInviteIsInvite() {
        assertEquals(
            SteamChatHistoryCategory.INVITE,
            SteamChatHistoryClassifier.classify("steam://joinlobby/730/109775241234567890/76561198000000001")
        )
    }

    @Test
    fun tradeOfferNotificationIsInvite() {
        assertEquals(
            SteamChatHistoryCategory.INVITE,
            SteamChatHistoryClassifier.classify("[tradeoffer]https://steamcommunity.com/tradeoffer/123456789/[/tradeoffer]")
        )
    }

    @Test
    fun itemPreviewDropsTheBbcodeEnvelope() {
        val item = SteamChatHistoryClassifier.item(
            id = "1",
            senderName = "Alex",
            body = "[img]https://cdn.example.com/shot_01.png[/img]",
            timestamp = 1_700_000_000L
        )

        assertEquals(SteamChatHistoryCategory.MEDIA, item.category)
        assertEquals("图片 · shot_01.png", item.body)
        assertEquals("https://cdn.example.com/shot_01.png", item.mediaUrl)
    }

    @Test
    fun itemPreviewDropsContentHashImageNames() {
        // Steam uploads images under their content hash; the row already shows the picture, so
        // repeating "图片 · 54b97d0998d152f01d876d03dad1fdd2" is just noise.
        val item = SteamChatHistoryClassifier.item(
            id = "1",
            senderName = "Sam",
            body = "[img]https://avatars.fastly.steamstatic.com/54b97d0998d152f01d876d03dad1fdd2fb642dd2_full.jpg[/img]",
            timestamp = 1_700_000_000L
        )

        assertEquals(SteamChatHistoryCategory.MEDIA, item.category)
        assertEquals("图片", item.body)
        assertEquals(
            "https://avatars.fastly.steamstatic.com/54b97d0998d152f01d876d03dad1fdd2fb642dd2_full.jpg",
            item.mediaUrl
        )
    }

    @Test
    fun searchMatchesSenderUrlAndPreview() {
        val item = SteamChatHistoryClassifier.item(
            id = "1",
            senderName = "Alex",
            body = "[img]https://cdn.example.com/shot_01.png[/img]",
            timestamp = 1_700_000_000L
        )

        assertTrue(item.matches("alex"))
        assertTrue(item.matches("shot_01"))
        assertTrue(item.matches("cdn.example.com"))
        assertTrue(item.matches(""))
        assertFalse(item.matches("portal"))
    }

    @Test
    fun archivesHaveNoThumbnail() {
        val item = SteamChatHistoryClassifier.item(
            id = "2",
            senderName = "Sam",
            body = "[url=https://cdn.example.com/pack.zip]pack.zip[/url]",
            timestamp = 1_700_000_000L
        )

        assertEquals(SteamChatHistoryCategory.FILE, item.category)
        assertEquals(null, item.mediaUrl)
    }

    @Test
    fun shortcutCategoriesSkipTheGenericBuckets() {
        assertFalse(SteamChatHistoryCategory.shortcuts.contains(SteamChatHistoryCategory.ALL))
        assertFalse(SteamChatHistoryCategory.shortcuts.contains(SteamChatHistoryCategory.TEXT))
        assertEquals(SteamChatHistoryCategory.ALL, SteamChatHistoryCategory.filters.first())
        assertTrue(SteamChatHistoryCategory.filters.contains(SteamChatHistoryCategory.TEXT))
    }
}
