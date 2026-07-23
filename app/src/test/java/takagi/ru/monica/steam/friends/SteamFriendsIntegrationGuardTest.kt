package takagi.ru.monica.steam.friends

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
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt"
        ).readText()
        val friendsScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/SteamFriendsScreen.kt"
        ).readText()
        val dock = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/SteamDockSettings.kt"
        ).readText()

        assertTrue(activity.contains("MonicaSteamPage.FRIENDS"))
        assertTrue(activity.contains("navigateTo(MonicaSteamPage.FRIENDS)"))
        assertTrue(activity.contains("SteamFriendsScreen("))
        val dockPages = activity
            .substringAfter("private fun MonicaSteamPage.isDockPage()")
            .substringBefore("private fun MonicaSteamPage.toDockTab()")
        assertTrue(dockPages.contains("MonicaSteamPage.FRIENDS"))
        assertTrue(dockPages.substringAfter("MonicaSteamPage.FRIENDS").contains("false"))
        assertFalse(
            dock.substringAfter("enum class SteamDockTab").substringBefore(";")
                .contains("FRIENDS")
        )
        assertTrue(tokenScreen.contains("onOpenFriends"))
        assertTrue(tokenScreen.contains("Icons.Default.Groups"))
        assertTrue(friendsScreen.contains("ExpressiveTopBar("))
        assertTrue(friendsScreen.contains("Modifier.statusBarsPadding()"))
        assertTrue(friendsScreen.contains("BackHandler"))
        assertTrue(friendsScreen.contains("easyNotesScreenEnter()"))
        assertTrue(friendsScreen.contains("FlowRow("))
        assertFalse(friendsScreen.contains("horizontalScroll("))
        assertTrue(friendsScreen.contains("heightIn(min = 48.dp)"))
        assertTrue(friendsScreen.contains("SteamFriendDetailScreen("))
        assertTrue(friendsScreen.contains("FriendLoadingCard()"))
    }

    @Test
    fun friendsUseOAuthCacheAndAuthenticatedCommunityActions() {
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/SteamFriendsService.kt"
        ).readText()
        val cache = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/SteamFriendsCache.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/SteamFriendsViewModel.kt"
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
