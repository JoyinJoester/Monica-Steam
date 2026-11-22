package takagi.ru.monica.steam.friends.chat.gameinvite.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatGameInviteIntegrationGuardTest {
    @Test
    fun gameInviteFeatureKeepsDataDomainAndUiIndependent() {
        val root = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/gameinvite"
        )

        assertTrue(root.resolve("data/SteamChatGameInviteMetadataRepository.kt").isFile)
        assertTrue(root.resolve("domain/SteamChatGameInviteModels.kt").isFile)
        assertTrue(root.resolve("ui/SteamChatGameInviteCard.kt").isFile)
    }

    @Test
    fun cardUsesStoreMetadataLargeTouchTargetsAndInternalStoreNavigation() {
        val card = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/gameinvite/ui/SteamChatGameInviteCard.kt"
        ).readText()
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val directThread = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatThread.kt"
        ).readText()
        val groupThread = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()

        assertTrue(card.contains("SteamChatGameInviteMetadataRepository"))
        val shared = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/ui/SteamChatRichCard.kt"
        ).readText()
        assertTrue(card.contains("SteamChatRichCard("))
        // The whole card and its overflow retain large targets after layout extraction.
        assertTrue(shared.contains("widthIn(min = 210.dp"))
        assertTrue(shared.contains("height = 44.dp"))
        assertTrue(shared.contains("IconButton(onClick = show)"))
        assertTrue(shared.contains("surfaceContainerHigh"))
        assertTrue(shared.contains("combinedClickable("))
        assertTrue(shared.contains("onLongClick = show"))
        assertTrue(shared.contains("TelegramActionMenu("))
        assertFalse(card.contains("FilledTonalButton"))
        assertTrue(card.contains("onOpenStoreApp"))
        assertTrue(activity.contains("onOpenStoreApp = { appId ->"))
        assertTrue(directThread.contains("onOpenStoreApp = onOpenStoreApp"))
        assertTrue(groupThread.contains("onOpenStoreApp = onOpenStoreApp"))
    }

    @Test
    fun gameInviteIsStandaloneAndDoesNotOfferAnUnsupportedMobileJoinAction() {
        val bubble = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatMessageBubble.kt"
        ).readText()
        val card = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/gameinvite/ui/SteamChatGameInviteCard.kt"
        ).readText()

        assertTrue(
            bubble.contains(
                "val standaloneCard = richContent is SteamChatRichContent.GameInvite"
            )
        )
        assertTrue(bubble.contains("if (standaloneCard)"))
        assertTrue(card.contains("steam_chat_game_invite_view_store"))
        assertFalse(card.contains("steam_chat_game_invite_join"))
        assertFalse(card.contains("Intent.ACTION_VIEW"))
        assertFalse(card.contains("presentation.joinUrl"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, path)
    }
}
