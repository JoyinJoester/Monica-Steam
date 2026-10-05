package takagi.ru.monica.steam.store.activation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.activation.domain.SteamStoreProductActivation
import takagi.ru.monica.steam.web.domain.SteamWebNavigationPolicy

class SteamStoreProductActivationUiGuardTest {
    @Test
    fun menuEntryUsesTheOfficialRegisterKeyPageInsideAuthenticatedWebView() {
        val activation = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/activation/domain/SteamStoreProductActivation.kt"
        ).readText()
        val menu = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreNavigationDrawer.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()

        assertTrue(activation.contains("https://store.steampowered.com/account/registerkey"))
        assertTrue(
            SteamWebNavigationPolicy.isAllowed(
                SteamStoreProductActivation.REGISTER_KEY_URL
            )
        )
        assertTrue(menu.contains("actions.activateProduct"))
        assertTrue(menu.contains("steam_store_activate_product_code"))
        assertTrue(screen.contains("SteamStoreProductActivation.REGISTER_KEY_URL"))
        assertTrue(screen.contains("openAuthenticatedStoreWeb"))
        assertTrue(screen.contains("activateProduct = { viewModel.openAuthenticatedStoreWeb(SteamStoreProductActivation.REGISTER_KEY_URL) }"))
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
