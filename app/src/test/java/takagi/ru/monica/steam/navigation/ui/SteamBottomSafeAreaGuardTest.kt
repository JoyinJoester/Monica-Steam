package takagi.ru.monica.steam.navigation.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-level guard for system navigation and Steam Dock bottom insets. */
class SteamBottomSafeAreaGuardTest {
    @Test
    fun directAndGroupChatThreadsReserveSystemNavigationInsets() {
        listOf(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatThread.kt",
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).forEach { path ->
            val source = projectFile(path).readText()
            assertTrue("$path must use the shared Telegram chat scaffold", source.contains("TelegramChatScaffold("))
        }
        // The scaffold owns the insets once for both chat types.
        val scaffold = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/telegram/TelegramChatSurface.kt"
        ).readText().substringAfter("internal fun TelegramChatScaffold(")
        assertTrue("Chat scaffold must keep IME handling", scaffold.contains(".imePadding()"))
        assertTrue(
            "Chat scaffold must keep the composer above window navigation",
            scaffold.contains(".steamWindowBottomPadding(suppressWhenImeVisible = true)")
        )
    }

    @Test
    fun floatingActionsUseDockAwareClearanceInsteadOfFixedBottomOffsets() {
        val groupList = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatList.kt"
        ).readText()
        val conversations = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamConversationList.kt"
        ).readText()
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreHomePage.kt"
        ).readText()

        assertTrue(groupList.contains("LocalSteamDockContentClearance.current"))
        assertTrue(groupList.contains("steamDockActionClearance"))
        assertTrue(groupList.contains(".navigationBarsPadding()"))
        assertFalse(groupList.contains("bottom = 96.dp"))
        assertTrue(conversations.contains("steamDockActionClearance"))
        assertTrue(conversations.contains(".navigationBarsPadding()"))
        assertTrue(store.contains(".steamDockActionClearance()"))
        assertTrue(store.contains(".navigationBarsPadding()"))
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
