package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreWebSecurityGuardTest {
    @Test
    fun officialStoreWebViewKeepsCheckoutInsideSecurityBoundary() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/SteamStoreWebScreen.kt"
        ).readText()

        assertTrue(source.contains("settings.allowFileAccess = false"))
        assertTrue(source.contains("settings.allowContentAccess = false"))
        assertTrue(source.contains("WebSettings.MIXED_CONTENT_NEVER_ALLOW"))
        assertTrue(source.contains("setAcceptThirdPartyCookies(this, false)"))
        assertTrue(source.contains("SteamStoreNavigationPolicy.isAllowed(target)"))
        assertTrue(source.contains("Intent(Intent.ACTION_VIEW, Uri.parse(target))"))
        assertFalse(source.contains("addJavascriptInterface"))
        assertFalse(source.contains("onReceivedHttpAuthRequest"))
    }

    @Test
    fun addToCartJsonResponseIsNotMistakenForTheOfficialCartPage() {
        assertFalse(isSteamCartPage("https://store.steampowered.com/cart/addtocart/"))
        assertFalse(isSteamCartPage("https://store.steampowered.com/cart/addtocart"))
        assertTrue(isSteamCartPage("https://store.steampowered.com/cart/"))
        assertTrue(isSteamCartPage("https://store.steampowered.com/cart?snr=1"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
