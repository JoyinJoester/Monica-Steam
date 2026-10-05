package takagi.ru.monica.steam.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLiquidGlassWebViewCompatibilityTest {
    @Test
    fun officialSteamWebViewUsesFullscreenSurfaceAndHidesDock() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val web = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/web/ui/SteamWebBrowserScreen.kt"
        ).readText()
        val webConfig = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/web/ui/SteamWebViewConfiguration.kt"
        ).readText()
        val actionBar = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/web/ui/SteamWebBrowserActionBar.kt"
        ).readText()
        assertTrue(store.contains("onPlatformViewVisibilityChanged: (Boolean) -> Unit"))
        assertTrue(
            store.contains(
                "onPlatformViewVisibilityChanged = onPlatformViewVisibilityChanged"
            )
        )
        assertTrue(web.contains("platformViewVisibilityCallback(true)"))
        assertTrue(web.contains("platformViewVisibilityCallback(false)"))
        assertTrue(web.contains("withFrameNanos { }"))
        assertTrue(web.contains("!platformViewReady -> Surface("))
        assertTrue(actionBar.contains("SelectionActionBar("))
        assertTrue(actionBar.contains("showSelectionControls = false"))
        assertTrue(webConfig.contains("setRendererPriorityPolicy("))
        assertTrue(web.contains("backgroundColor = initialBackground.toArgb()"))
        assertTrue(web.contains("CookieManager.getInstance().flush()"))
        assertTrue(web.contains("onRendererGone"))
        assertTrue(activity.contains("isPlatformViewActive"))
        assertTrue(activity.contains("dockVisible = shouldShowSteamDock("))
        assertTrue(activity.contains("platformViewActive = isPlatformViewActive"))
        assertTrue(activity.contains("imeVisible = imeVisible"))
        assertTrue(activity.contains("val useNavigationRail = adaptiveLayout.useNavigationRail && !imeVisible"))
    }

    @Test
    fun renderingPolicyRejectsRuntimeEffectsAndHidesDockForPlatformViews() {
        assertTrue(
            shouldEnableSteamLiquidGlassRuntimeEffects(
                dockStyle = SteamDockStyle.LIQUID_GLASS,
                dockVisible = true,
                platformViewActive = false
            )
        )
        assertFalse(
            shouldEnableSteamLiquidGlassRuntimeEffects(
                dockStyle = SteamDockStyle.LIQUID_GLASS,
                dockVisible = true,
                platformViewActive = true
            )
        )
        assertTrue(
            shouldShowSteamDock(
                hasConfiguration = true,
                isDockPage = true,
                chatThreadOpen = false,
                platformViewActive = false,
                imeVisible = false
            )
        )
        assertFalse(
            shouldShowSteamDock(
                hasConfiguration = true,
                isDockPage = true,
                chatThreadOpen = false,
                platformViewActive = true,
                imeVisible = false
            )
        )
        assertFalse(
            shouldShowSteamDock(
                hasConfiguration = true,
                isDockPage = true,
                chatThreadOpen = false,
                platformViewActive = false,
                imeVisible = true
            )
        )
        assertFalse(
            shouldEnableSteamLiquidGlassRuntimeEffects(
                dockStyle = SteamDockStyle.M3E,
                dockVisible = true,
                platformViewActive = false
            )
        )
        assertFalse(
            shouldEnableSteamLiquidGlassRuntimeEffects(
                dockStyle = SteamDockStyle.LIQUID_GLASS,
                dockVisible = false,
                platformViewActive = false
            )
        )
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
