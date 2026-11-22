package takagi.ru.monica.steam.friends.chat.position.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatImeBottomAnchorIntegrationGuardTest {
    @Test
    fun directAndGroupThreadsShareTheImeBottomAnchorEffect() {
        val effect = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/position/ui/SteamChatImeBottomAnchorEffect.kt"
        )
        val readingPositionUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/position/ui/SteamChatReadingPositionUi.kt"
        ).readText()
        val direct = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatThread.kt"
        ).readText()
        val group = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()

        assertTrue(effect.isFile)
        val source = effect.readText()
        assertTrue(source.contains("val imeInsets = WindowInsets.ime"))
        assertTrue(source.contains("imeInsets.getBottom"))
        assertTrue(source.contains("reduceSteamChatImeAnchor"))
        assertTrue(source.contains("snapshotFlow"))
        assertTrue(source.contains("viewportEndOffset"))
        assertTrue(source.contains("scrollToLatestSteamChatMessage"))
        assertTrue(source.contains("rememberUpdatedState(messagesBelow)"))
        assertTrue(readingPositionUi.contains("SteamChatImeBottomAnchorEffect("))
        val nativeList = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/telegram/TelegramMessageList.kt"
        ).readText()
        assertTrue(direct.contains("TelegramChatScaffold("))
        assertTrue(group.contains("TelegramChatScaffold("))
        assertTrue(nativeList.contains("OnLayoutChangeListener"))
        assertTrue(nativeList.contains("followBottom"))
        assertTrue(nativeList.contains("jumpToLatest(animated = false)"))
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
