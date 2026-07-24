package takagi.ru.monica.steam.friends.chat.richmedia.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatRichMediaModelsTest {
    @Test
    fun parsesOfficialStickerSlashCommand() {
        val content = SteamChatRichContentParser.parse("/sticker Mesmer spin")

        assertTrue(content is SteamChatRichContent.Sticker)
        assertEquals("Mesmer spin", (content as SteamChatRichContent.Sticker).name)
        assertEquals("/sticker Mesmer spin", SteamChatSticker("Mesmer spin", "Mesmer").messageCode)
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
    fun keepsOrdinaryTextUntouched() {
        val body = "hello :steamthumbsup:"
        assertEquals(SteamChatRichContent.Text(body), SteamChatRichContentParser.parse(body))
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
        assertEquals(body, (SteamChatRichContentParser.parse(body) as SteamChatRichContent.Text).body)
    }
}
