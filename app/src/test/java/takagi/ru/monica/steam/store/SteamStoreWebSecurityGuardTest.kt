package takagi.ru.monica.steam.store

import takagi.ru.monica.steam.store.gift.data.isSteamCartPage

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreWebSecurityGuardTest {
    @Test
    fun browserClientCallbacksDoNotShadowFrameworkOverrides() {
        val clients = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/web/ui/SteamWebBrowserClients.kt"
        ).readText()

        listOf(
            "onPageStartedCallback",
            "onPageCommitVisibleCallback",
            "onPageFinishedCallback",
            "onProgressChangedCallback",
            "onTitleChangedCallback",
            "onFileChooserCallback",
            "onPermissionRequestCallback",
            "onPermissionRequestCanceledCallback",
            "onShowCustomViewCallback",
            "onHideCustomViewCallback"
        ).forEach { callbackName ->
            assertTrue(clients.contains("private val $callbackName"))
        }
        listOf(
            "onPageCommitVisibleCallback(view, url)",
            "onPageFinishedCallback(view, url)",
            "onPermissionRequestCallback(request)",
            "onPermissionRequestCanceledCallback(request)",
            "onShowCustomViewCallback(view, callback)",
            "onHideCustomViewCallback()"
        ).forEach { callbackInvocation ->
            assertTrue(clients.contains(callbackInvocation))
        }
    }

    @Test
    fun officialStoreWebViewKeepsCheckoutInsideSecurityBoundary() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/web/ui/SteamWebBrowserScreen.kt"
        ).readText()
        val clients = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/web/ui/SteamWebBrowserClients.kt"
        ).readText()
        val configuration = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/web/ui/SteamWebViewConfiguration.kt"
        ).readText()
        val installer = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/web/data/SteamWebCookieInstaller.kt"
        ).readText()
        val checkoutAutomation = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/gift/data/SteamStoreCheckoutWebAutomation.kt"
        ).readText()
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/presentation/SteamStoreViewModel.kt"
        ).readText()

        assertTrue(configuration.contains("allowFileAccess = false"))
        assertTrue(configuration.contains("allowContentAccess = false"))
        assertTrue(configuration.contains("WebSettings.MIXED_CONTENT_NEVER_ALLOW"))
        assertTrue(configuration.contains("setAcceptThirdPartyCookies(this, false)"))
        assertTrue(clients.contains("SteamWebNavigationPolicy.isAllowed(target)"))
        assertTrue(source.contains("SteamWebAccountSessionPolicy.decide("))
        assertTrue(configuration.contains("SteamWebClientPolicy.displayPolicy(clientMode)"))
        assertTrue(configuration.contains("textZoom = displayPolicy.textZoomPercent"))
        assertTrue(source.contains("clearSteamCookies()"))
        assertTrue(source.contains("replaceSteamCookies("))
        assertTrue(installer.contains("removeAllCookies"))
        assertTrue(installer.contains("pending = Request(latestGeneration"))
        assertTrue(
            store.contains(
                "requireAuthenticatedSession = state.webRequiresAuthenticatedSession"
            )
        )
        assertTrue(viewModel.contains("fun openAuthenticatedStoreWeb"))
        assertTrue(viewModel.contains("webRequiresAuthenticatedSession = true"))
        assertTrue(checkoutAutomation.contains("SteamStoreGiftCheckoutProtocol.addToCartBody("))
        assertTrue(source.contains("Intent(Intent.ACTION_VIEW, uri)"))
        assertTrue(clients.contains("handler.cancel()"))
        assertFalse(source.contains("addJavascriptInterface"))
        assertFalse(configuration.contains("addJavascriptInterface"))
    }

    @Test
    fun addToCartJsonResponseIsNotMistakenForTheOfficialCartPage() {
        assertFalse(isSteamCartPage("https://store.steampowered.com/cart/addtocart/"))
        assertFalse(isSteamCartPage("https://store.steampowered.com/cart/addtocart"))
        assertTrue(isSteamCartPage("https://store.steampowered.com/cart/"))
        assertTrue(isSteamCartPage("https://store.steampowered.com/cart?snr=1"))
    }

    @Test
    fun storeMenuScrollFixKeepsSteamDropdownInteractionTreeUntouched() {
        val clients = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/web/ui/SteamWebBrowserClients.kt"
        ).readText()
        val script = clients.substringAfter("STEAM_STORE_MENU_SCROLL_FIX_SCRIPT =")

        assertTrue(script.contains("monica-steam-store-menu-scroll-fix"))
        assertTrue(script.contains("monica-steam-store-menu-scroll-target"))
        assertTrue(script.contains("placeholder?.nextElementSibling"))
        assertTrue(script.contains("a[href*=\"/wishlist\"]"))
        assertTrue(script.contains("position: relative !important"))
        assertTrue(script.contains("height: auto !important"))
        assertFalse(script.contains(":first-child"))
        assertFalse(script.contains("position: absolute !important"))
        assertFalse(script.contains("querySelectorAll('*')"))
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
