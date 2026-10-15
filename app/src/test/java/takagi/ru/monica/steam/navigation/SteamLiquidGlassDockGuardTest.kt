package takagi.ru.monica.steam.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLiquidGlassDockGuardTest {
    @Test
    fun rendererKeepsTheBiliPaiKernelSuMaterialAndMotionConstants() {
        val dock = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/liquidglass/ui/SteamLiquidGlassDock.kt"
        ).readText()
        val motion = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/liquidglass/motion/LiquidGlassDockMotion.kt"
        ).readText()

        assertTrue(dock.contains(".height(64.dp)"))
        assertTrue(dock.contains(".padding(4.dp)"))
        assertTrue(dock.contains(".height(56.dp)"))
        assertTrue(dock.contains("blur(4.dp.toPx(), 4.dp.toPx())"))
        assertTrue(dock.contains("refractionHeight = 24.dp.toPx()"))
        assertTrue(dock.contains("refractionAmount = 24.dp.toPx()"))
        assertTrue(dock.contains("refractionHeight = 10.dp.toPx() * progress"))
        assertTrue(dock.contains("refractionAmount = 14.dp.toPx() * progress"))
        assertTrue(dock.contains("chromaticAberration = 0.5f"))
        assertTrue(dock.contains("rememberDeviceTilt()"))
        assertTrue(dock.contains("INDICATOR_DRAG_SCALE_TARGET = 88f / 56f"))
        assertTrue(dock.contains("VELOCITY_SCALE_X_MULTIPLIER = 0.75f"))
        assertTrue(dock.contains("VELOCITY_SCALE_Y_MULTIPLIER = 0.25f"))
        assertTrue(dock.contains("VELOCITY_SCALE_CLAMP = 0.2f"))
        assertTrue(dock.contains("rememberSteamCombinedBackdrop("))
        assertTrue(dock.contains(".layerBackdrop(tabsBackdrop)"))
        assertTrue(dock.contains(".steamWindowBottomPadding()"))
        assertTrue(motion.contains("KERNEL_SU_PRESSED_SCALE = 78f / 56f"))
        assertTrue(motion.contains("flingProjectionTimeSeconds: Float = 0.20f"))
        assertTrue(motion.contains("maxReleaseStepCount: Int = 1"))
        assertTrue(motion.contains("overscrollLimitItems: Float = 0.5f"))
    }

    @Test
    fun activityUsesOnlyFixedAndLiquidGlassCompositionPaths() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()

        assertFalse(activity.contains("SteamStandaloneDock("))
        assertTrue(activity.contains("SteamFixedBottomBar("))
        assertTrue(activity.contains("SteamLiquidGlassDock("))
        assertTrue(activity.contains("steamLiquidGlassBackdropSource("))
        assertTrue(activity.contains("dockStyle == SteamDockStyle.FIXED"))
        assertFalse(activity.contains("steamDockProgressiveBlur("))
        assertTrue(activity.contains("dockStyle == SteamDockStyle.LIQUID_GLASS"))
        assertTrue(activity.contains("currentPage.isDockPage(dockStyle)"))
        assertTrue(activity.contains("LaunchedEffect(currentPage, dockStyle)"))
        assertTrue(settings.contains("SingleChoiceSegmentedButtonRow("))
        assertTrue(settings.contains("SteamDockStyle.entries.forEachIndexed"))
        assertTrue(settings.contains("showSwitch = false"))
        assertFalse(settings.contains("SteamDockStyle.M3E"))
        assertTrue(settings.contains("LocalSteamDockContentClearance.current"))
        assertTrue(settings.contains("bottom = dockContentClearance + 16.dp"))
        assertFalse(settings.contains("showSwitch = style == SteamDockStyle.LIQUID_GLASS"))
    }

    @Test
    fun hiddenIndicatorCaptureUsesAnIsolatedNonZeroAlphaLayer() {
        val dock = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/liquidglass/ui/SteamLiquidGlassDock.kt"
        ).readText()
        val captureLayer = dock
            .substringAfter(".height(56.dp)")
            .substringBefore("if (selectedIndex >= 0)")

        assertTrue(dock.contains("LIQUID_GLASS_CAPTURE_ALPHA = 0.001f"))
        assertTrue(dock.contains("CompositingStrategy.Offscreen"))
        assertTrue(captureLayer.contains(".liquidGlassCaptureLayer()"))
        assertTrue(captureLayer.contains(".layerBackdrop(tabsBackdrop)"))
        assertFalse(captureLayer.contains(".alpha(0f)"))
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
