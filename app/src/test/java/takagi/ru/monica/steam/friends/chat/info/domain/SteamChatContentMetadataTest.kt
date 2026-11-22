package takagi.ru.monica.steam.friends.chat.info.domain

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentKind

class SteamChatContentMetadataTest {
    @Test fun videoRetainsOriginalUrlWithoutTreatingVideoBytesAsAnImage() {
        val url = "https://cdn.akamai.steamstatic.com/chat/clip.mp4"
        val item = SteamChatHistoryClassifier.item("v", "Alex", "[video]$url[/video]", 10)
        assertEquals(SteamChatHistoryCategory.MEDIA, item.category)
        assertEquals(SteamChatAttachmentKind.VIDEO, item.attachmentKind)
        assertEquals(url,item.contentUrl);assertNull(item.mediaUrl)
        assertTrue(item.matches("clip.mp4"))
    }
    @Test fun hiddenMediaRetainsSpoilerStateAndMessageIdentity() {
        val item = SteamChatHistoryClassifier.item("secret", "Alex",
            "[spoiler][img]https://avatars.fastly.steamstatic.com/secret.png[/img][/spoiler]", 10)
        assertTrue(item.spoiler);assertEquals("secret",item.id)
        assertEquals(SteamChatAttachmentKind.IMAGE,item.attachmentKind)
    }
    @Test fun linksAndFilesRetainTheOriginalUrlForTypedRows() {
        val link=SteamChatHistoryClassifier.item("link","Alex","Guide https://steamcommunity.com/sharedfiles/filedetails/?id=123",10)
        assertEquals("https://steamcommunity.com/sharedfiles/filedetails/?id=123",link.contentUrl)
        val file=SteamChatHistoryClassifier.item("file","Alex","[url=https://steamcommunity.com/pack.zip]pack.zip[/url]",10)
        assertEquals(SteamChatHistoryCategory.FILE,file.category)
        assertEquals(SteamChatAttachmentKind.ARCHIVE,file.attachmentKind)
        assertNull(file.mediaUrl)
    }
}
