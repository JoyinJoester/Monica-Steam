package takagi.ru.monica.steam.friends.chat.position.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatReadingPositionIntegrationTest {
    @Test
    fun directAndGroupThreadsSharePersistentReadingPositionControls() {
        val direct = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatThread.kt"
        ).readText()
        val group = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()
        val controller = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/telegram/TelegramMessageList.kt"
        ).readText()

        listOf(direct, group).forEach { source ->
            assertTrue(source.contains("rememberTelegramChatListState("))
            assertTrue(source.contains("SteamChatJumpToLatestButton("))
            assertTrue(source.contains("TelegramMessageList("))
            assertTrue(source.contains("listState::jumpToLatest"))
        }
        assertTrue(controller.contains("OnScrollListener"))
        assertTrue(controller.contains("planTelegramListScroll("))
        assertTrue(controller.contains("SteamChatReadingPositionStore"))
        assertTrue(controller.contains("handledRequestedMessageId"))
        assertTrue(controller.contains("lastVisibleMessageId"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
