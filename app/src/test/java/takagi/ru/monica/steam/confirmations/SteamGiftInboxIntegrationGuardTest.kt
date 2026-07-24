package takagi.ru.monica.steam.confirmations

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGiftInboxIntegrationGuardTest {
    @Test
    fun confirmationsExposeNativeNotificationsAndLoggedInGiftFallback() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val web = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreWebScreen.kt"
        ).readText()

        assertTrue(screen.contains("SteamSection.NOTIFICATIONS"))
        assertTrue(screen.contains("steamGiftInboxUrl("))
        assertTrue(screen.contains("onGiftAction"))
        assertTrue(screen.contains("selectedAccount?.steamLoginSecure"))
        assertTrue(screen.contains("selectedAccount?.accessToken"))
        assertTrue(screen.contains("SteamStoreWebScreen("))
        assertTrue(screen.contains("clientMode = SteamWebClientMode.COMMUNITY_DESKTOP"))
        assertTrue(web.contains("title: String"))
        assertTrue(web.contains("SteamStoreNavigationPolicy.isAllowed(target)"))
        assertTrue(web.contains("installSteamCookies"))
    }

    @Test
    fun notificationsUseIndependentListDetailAndActionPage() {
        val page = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/notifications/ui/SteamNotificationsScreen.kt"
        ).readText()
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val detailContent = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/notifications/ui/SteamNotificationDetailContent.kt"
        ).readText()
        val appContent = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/notifications/ui/SteamNotificationAppContentCard.kt"
        ).readText()

        assertTrue(page.contains("fun SteamNotificationsScreen("))
        assertTrue(!page.contains("BackHandler("))
        assertTrue(!page.contains("Scaffold("))
        assertTrue(!page.contains("TopAppBar("))
        assertTrue(page.contains("ModalBottomSheet("))
        assertTrue(page.contains("SteamGiftAction.ADD_TO_LIBRARY"))
        assertTrue(page.contains("SteamGiftAction.DECLINE"))
        assertTrue(page.contains("FilterChip("))
        assertTrue(page.contains("heightIn(min = 48.dp)"))
        assertTrue(page.contains("containerColor = MaterialTheme.colorScheme.surfaceContainerHigh"))
        assertTrue(page.contains("border = BorderStroke"))
        assertTrue(page.contains("SteamNotificationDetailContent(details.fields)"))
        assertTrue(page.contains("steam_notification_detail_unavailable"))
        assertTrue(!page.contains("notification_detail_body"))
        assertTrue(!page.contains("text = notification.bodyData"))
        assertTrue(detailContent.contains("internal fun SteamNotificationDetailContent("))
        assertTrue(detailContent.contains("SelectionContainer"))
        assertTrue(!detailContent.contains("steam_notification_detail_app_id"))
        assertTrue(page.contains("SteamNotificationAppContentCard("))
        assertTrue(page.contains("onOpenStoreApp(content.appId)"))
        assertTrue(appContent.contains("ContentScale.Fit"))
        assertTrue(appContent.contains("heightIn(min = 48.dp)"))
        assertTrue(appContent.contains("steam_store_open_detail"))
        assertTrue(host.contains("SteamSection.NOTIFICATIONS -> SteamNotificationsScreen("))
        assertTrue(host.contains("onOpenStoreApp = onOpenStoreApp"))
        assertTrue(!host.contains("SteamNotificationCenter("))
        assertTrue(!host.contains("showNotificationsPage"))
        assertTrue(host.contains("SteamNotificationsScreen("))
    }

    @Test
    fun notificationGameContentDeepLinksIntoStandaloneStoreDetail() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()

        assertTrue(activity.contains("pendingStoreAppId = appId"))
        assertTrue(activity.contains("navigateTo(MonicaSteamPage.STORE)"))
        assertTrue(activity.contains("initialAppId = pendingStoreAppId"))
        assertTrue(store.contains("LaunchedEffect(initialAppId)"))
        assertTrue(store.contains("viewModel.openDetail(appId)"))
        assertTrue(store.contains("onInitialAppIdConsumed()"))
    }

    @Test
    fun officialGiftInboxUrlUsesAccountCommunityInventory() {
        val giftInbox = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/gifts/domain/SteamGiftInbox.kt"
        ).readText()

        assertTrue(giftInbox.contains("https://steamcommunity.com/profiles/"))
        assertTrue(giftInbox.contains("#pending_gifts"))
        assertTrue(giftInbox.contains("steamGiftInboxUrl"))
        assertTrue(!giftInbox.contains("store.steampowered.com/account/gifts"))
    }

    private fun projectFile(path: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, path)
    }
}
