package takagi.ru.monica.steam.friends.chat.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatIntegrationGuardTest {
    @Test
    fun chatIsAnIndependentCapsuleMenuPageWithFriendEntry() {
        val tokenScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val friendsScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendsScreen.kt"
        ).readText()
        val friendDetail = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendDetailScreen.kt"
        ).readText()
        val chatScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreen.kt"
        ).readText()

        assertTrue(tokenScreen.contains("SteamSection.CHAT"))
        assertTrue(tokenScreen.contains("SteamChatScreen("))
        assertTrue(tokenScreen.contains("pendingChatCount"))
        assertTrue(friendsScreen.contains("onStartChat"))
        assertTrue(friendDetail.contains("steam_chat_send_message"))
        assertFalse(chatScreen.contains("ExpressiveTopBar("))
        assertFalse(chatScreen.contains("Scaffold("))
        assertTrue(chatScreen.contains("BackHandler"))
        assertTrue(chatScreen.contains("easyNotesScreenEnter()"))
    }

    @Test
    fun chatKeepsDataPresentationAndTelegramStyleUiSeparated() {
        val root = projectFile("app/src/main/java/takagi/ru/monica/steam/friends/chat")
        assertTrue(root.resolve("domain").isDirectory)
        assertTrue(root.resolve("data").isDirectory)
        assertTrue(root.resolve("presentation").isDirectory)
        assertTrue(root.resolve("ui").isDirectory)
        assertTrue(root.listFiles().orEmpty().none { it.extension == "kt" })

        val uiFiles = root.resolve("ui").listFiles().orEmpty().filter { it.extension == "kt" }
        assertTrue(uiFiles.size >= 5)
        uiFiles.forEach { file ->
            assertTrue("${file.name} is too large", file.readLines().size <= 400)
        }
        root.resolve("presentation").listFiles().orEmpty()
            .filter { it.extension == "kt" }
            .forEach { file ->
                assertTrue("${file.name} is too large", file.readLines().size <= 450)
            }

        val thread = root.resolve("ui/SteamChatThread.kt").readText()
        val bubble = root.resolve("ui/SteamChatMessageBubble.kt").readText()
        val composer = root.resolve("ui/SteamChatComposer.kt").readText()
        assertTrue(thread.contains("animateScrollToItem"))
        assertTrue(thread.contains("animateItem()"))
        assertTrue(bubble.contains("SteamChatDeliveryState.FAILED"))
        assertTrue(bubble.contains("RoundedCornerShape"))
        assertFalse(composer.contains("imePadding()"))
        assertTrue(composer.contains("heightIn(min = 52.dp"))
    }

    @Test
    fun openingAThreadCanHideRootSteamChrome() {
        val chatScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreen.kt"
        ).readText()
        val steamScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()

        assertTrue(chatScreen.contains("onThreadVisibilityChange"))
        assertTrue(chatScreen.contains("DisposableEffect(Unit)"))
        assertTrue(steamScreen.contains("onThreadVisibilityChange"))
        assertTrue(steamScreen.contains("isChatThreadOpen"))
        assertTrue(activity.contains("isSteamChatThreadOpen"))
        assertTrue(activity.contains("!isSteamChatThreadOpen"))
    }

    @Test
    fun cacheUsesMonicaEncryptionAndAccountThreadIsolation() {
        val cache = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/data/SteamChatCache.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/presentation/SteamChatViewModel.kt"
        ).readText()
        val guard = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/presentation/SteamChatRequestGuard.kt"
        ).readText()

        assertTrue(cache.contains("encryptDataLegacyCompat"))
        assertTrue(cache.contains("thread|\$accountSteamId|\$partnerSteamId"))
        assertTrue(viewModel.contains("SteamChatRequestGuard"))
        assertTrue(guard.contains("accountSteamId == account.steamId"))
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
