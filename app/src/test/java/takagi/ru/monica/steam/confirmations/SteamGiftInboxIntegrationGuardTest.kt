package takagi.ru.monica.steam.confirmations

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGiftInboxIntegrationGuardTest {
    @Test
    fun confirmationsExposeOfficialGiftInboxWithAccountSession() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt"
        ).readText()
        val web = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/SteamStoreWebScreen.kt"
        ).readText()

        assertTrue(screen.contains("SteamGiftInboxCard("))
        assertTrue(screen.contains("STEAM_GIFT_INBOX_URL"))
        assertTrue(screen.contains("steamLoginSecure = account?.steamLoginSecure"))
        assertTrue(screen.contains("SteamStoreWebScreen("))
        assertTrue(web.contains("title: String"))
        assertTrue(web.contains("SteamStoreNavigationPolicy.isAllowed(target)"))
    }

    @Test
    fun officialGiftInboxUrlUsesSteamStoreHttpsPath() {
        val giftInbox = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/gifts/SteamGiftInbox.kt"
        ).readText()

        assertTrue(giftInbox.contains("https://store.steampowered.com/account/gifts/"))
        assertTrue(giftInbox.contains("STEAM_GIFT_INBOX_URL"))
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
