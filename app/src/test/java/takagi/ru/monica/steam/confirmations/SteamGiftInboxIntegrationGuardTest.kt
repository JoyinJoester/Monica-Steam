package takagi.ru.monica.steam.confirmations

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGiftInboxIntegrationGuardTest {
    @Test
    fun confirmationsExposeNativeNotificationsAndLoggedInGiftFallback() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt"
        ).readText()
        val web = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/SteamStoreWebScreen.kt"
        ).readText()

        assertTrue(screen.contains("SteamNotificationCenter("))
        assertTrue(screen.contains("steamGiftInboxUrl("))
        assertTrue(screen.contains("onGiftAction"))
        assertTrue(screen.contains("webSessionAvailable"))
        assertTrue(screen.contains("enabled = webSessionAvailable"))
        assertTrue(screen.contains("steamLoginSecure = account?.steamLoginSecure"))
        assertTrue(screen.contains("SteamStoreWebScreen("))
        assertTrue(web.contains("title: String"))
        assertTrue(web.contains("SteamStoreNavigationPolicy.isAllowed(target)"))
        assertTrue(web.contains("installSteamCookies"))
    }

    @Test
    fun notificationsUseIndependentListDetailAndActionPage() {
        val page = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/notifications/SteamNotificationsScreen.kt"
        ).readText()
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt"
        ).readText()

        assertTrue(page.contains("fun SteamNotificationsScreen("))
        assertTrue(page.contains("BackHandler("))
        assertTrue(page.contains("ModalBottomSheet("))
        assertTrue(page.contains("SteamGiftAction.ADD_TO_LIBRARY"))
        assertTrue(page.contains("SteamGiftAction.DECLINE"))
        assertTrue(page.contains("FilterChip("))
        assertTrue(page.contains("heightIn(min = 48.dp)"))
        assertTrue(host.contains("showNotificationsPage"))
        assertTrue(host.contains("SteamNotificationsScreen("))
    }

    @Test
    fun officialGiftInboxUrlUsesAccountCommunityInventory() {
        val giftInbox = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/gifts/SteamGiftInbox.kt"
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
