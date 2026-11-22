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
        val friendCards = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendCards.kt"
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
        assertTrue(friendsScreen.contains("easyNotesScreenEnter(reduceAnimations)"))
        assertTrue(friendsList.contains("TelegramContactsFilter("))
        assertFalse(friendsList.contains("horizontalScroll("))
        assertFalse(friendsList.contains("friends-summary"))
        assertFalse(friendsList.contains("FriendsSummaryCard("))
        assertFalse(friendCards.contains("fun FriendsSummaryCard("))
        assertTrue(friendsScreen.contains("SteamFriendDetailScreen("))
        assertTrue(friendsList.contains("FriendLoadingCard()"))
    }

    @Test
    fun friendDetailUsesOneHoistedTopBarAndKeepsItsExitContentStable() {
        val tokenScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val friendsScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendsScreen.kt"
        ).readText()
        val friendDetail = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendDetailScreen.kt"
        ).readText()
        val steamTopBar = tokenScreen
            .substringAfter("topBar = {")
            .substringBefore("floatingActionButton = {")

        assertTrue(steamTopBar.contains("SteamTopBarMode.FriendDetail"))
        assertTrue(steamTopBar.contains("R.string.steam_friend_details_title"))
        assertTrue(friendsScreen.contains("selectedFriendId: String?"))
        assertTrue(friendsScreen.contains("onSelectedFriendIdChange: (String?) -> Unit"))
        assertFalse(friendsScreen.contains("var selectedFriendId by rememberSaveable"))
        assertTrue(friendsScreen.contains("SteamFriendsDestination.Detail"))
        assertTrue(friendsScreen.contains("SteamFriendsDestination.Profile"))
        assertTrue(
            friendsScreen.contains(
                "val animatedFriend = friendsById[animatedDestination.steamId]"
            )
        )
        assertTrue(friendsScreen.contains("friend = animatedFriend"))
        assertTrue(friendsScreen.contains("BackHandler(enabled = profileSteamId != null)"))
        assertTrue(friendsScreen.contains("onNavigateBack = { profileSteamId = null }"))
        assertFalse(friendDetail.contains("onNavigateBack: () -> Unit"))
        assertFalse(friendDetail.contains("Icons.AutoMirrored.Filled.ArrowBack"))
    }

    @Test
    fun addFriendUsesTheFriendsFabAndTheHoistedSteamTopBar() {
        val tokenScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val friendsScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendsScreen.kt"
        ).readText()
        val addFriendScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamAddFriendScreen.kt"
        ).readText()
        val officialDialog = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamOfficialAddFriendDialog.kt"
        ).readText()

        assertTrue(tokenScreen.contains("SteamTopBarMode.AddFriend"))
        assertTrue(tokenScreen.contains("R.string.steam_friend_add_title"))
        assertTrue(tokenScreen.contains("compactTitle = true"))
        assertTrue(tokenScreen.contains("onOpenOfficialAddFriend"))
        assertTrue(tokenScreen.contains("SteamOfficialAddFriendDialog("))
        assertTrue(officialDialog.contains("https://steamcommunity.com/my/friends/add"))
        assertTrue(officialDialog.contains("requireAuthenticatedSession = true"))
        assertTrue(friendsScreen.contains("addFriendOpen: Boolean"))
        assertTrue(friendsScreen.contains("FloatingActionButton("))
        assertTrue(friendsScreen.contains("SteamAddFriendScreen("))
        assertTrue(addFriendScreen.contains("trailingIcon ="))
        assertFalse(addFriendScreen.contains("FilledIconButton("))
        assertTrue(addFriendScreen.contains("incomingRequests"))
        assertTrue(addFriendScreen.contains("steam_friend_requests_empty"))
        assertTrue(addFriendScreen.contains("SteamFriendSearchResultCard("))
        assertFalse(addFriendScreen.contains("Scaffold("))
        assertFalse(addFriendScreen.contains("TopAppBar("))
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
        assertTrue(viewModel.contains("SteamAccountSessionResolver"))
        assertTrue(viewModel.contains("sessionResolver.resolveOrKeep"))
        assertFalse(viewModel.contains("SteamSessionRefreshService"))
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
