package takagi.ru.monica.steam.friends.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamFriendsIntegrationGuardTest {
    @Test
    fun friendsRemainASecondaryRouteWithM3ExpressiveNavigation() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val tokenScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val friendsScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendsScreen.kt"
        ).readText()
        val friendsList = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendsList.kt"
        ).readText()
        val dock = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/SteamDockSettings.kt"
        ).readText()

        assertFalse(activity.contains("MonicaSteamPage.FRIENDS"))
        assertFalse(activity.contains("SteamFriendsScreen("))
        val dockPages = activity
            .substringAfter("private fun MonicaSteamPage.isDockPage()")
            .substringBefore("private fun MonicaSteamPage.toDockTab()")
        assertFalse(dockPages.contains("MonicaSteamPage.FRIENDS"))
        assertFalse(
            dock.substringAfter("enum class SteamDockTab").substringBefore(";")
                .contains("FRIENDS")
        )
        assertTrue(tokenScreen.contains("SteamSection.FRIENDS"))
        assertTrue(tokenScreen.contains("SteamFriendsScreen("))
        assertFalse(friendsScreen.contains("ExpressiveTopBar("))
        assertFalse(friendsScreen.contains("Scaffold("))
        assertFalse(friendsScreen.contains("onNavigateBack: () -> Unit"))
        assertTrue(friendsScreen.contains("BackHandler"))
        assertTrue(friendsScreen.contains("easyNotesScreenEnter()"))
        assertTrue(friendsList.contains("FlowRow("))
        assertFalse(friendsList.contains("horizontalScroll("))
        assertTrue(friendsScreen.contains("SteamFriendDetailScreen("))
        assertTrue(friendsList.contains("FriendLoadingCard()"))
    }

    @Test
    fun friendsUseOAuthCacheAndAuthenticatedCommunityActions() {
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/data/SteamFriendsService.kt"
        ).readText()
        val cache = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/data/SteamFriendsCache.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/presentation/SteamFriendsViewModel.kt"
        ).readText()

        assertTrue(service.contains("/ISteamUserOAuth/GetFriendList/v1/"))
        assertTrue(service.contains("/ISteamUserOAuth/GetUserSummaries/v1/"))
        assertTrue(service.contains("relationship\" to \"all"))
        assertTrue(service.contains("/actions/AddFriendAjax"))
        assertTrue(service.contains("/actions/IgnoreFriendInviteAjax"))
        assertTrue(service.contains("SteamInventoryService.marketCookies"))
        assertTrue(cache.contains("steam_friends_cache"))
        assertTrue(cache.contains("SteamFriendsSnapshot.serializer()"))
        assertTrue(viewModel.contains("SteamFriendsPreferencesCache"))
        assertTrue(viewModel.contains("requestGeneration"))
        assertTrue(viewModel.contains("SteamDiagLogger.append"))
    }

    @Test
    fun friendsImplementationStaysInsideFocusedSubpackagesAndFiles() {
        val root = projectFile("app/src/main/java/takagi/ru/monica/steam/friends")
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

        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        assertFalse(activity.contains("steam.friends.ui.SteamFriendsScreen"))
        assertFalse(activity.contains("steam.friends.data"))
        assertFalse(activity.contains("steam.friends.presentation"))
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
