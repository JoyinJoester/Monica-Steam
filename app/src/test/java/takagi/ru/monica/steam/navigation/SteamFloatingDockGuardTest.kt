package takagi.ru.monica.steam.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamFloatingDockGuardTest {
    @Test
    fun retiredToolbarIsNotComposedAndFixedBarIncludesToken() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val dock = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/ui/SteamFixedBottomBar.kt"
        ).readText()
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/SteamDockSettings.kt"
        ).readText()
        val notices = projectFile("THIRD_PARTY_NOTICES.md").readText()
        assertFalse(activity.contains("SteamEssentialsFloatingToolbar("))
        assertFalse(activity.contains("SteamStandaloneDock("))
        assertTrue(activity.contains("Box(modifier = Modifier.fillMaxSize())"))
        assertTrue(activity.contains(".align(Alignment.BottomCenter)"))
        assertTrue(dock.contains("selected = selected == tab"))
        assertTrue(activity.contains("zIndex(1f)"))
        assertFalse(activity.contains("bottomBar ="))
        assertTrue(settings.contains("DEFAULT_ORDER: List<SteamDockTab> = listOf(STORE, LIBRARY, CHAT)"))
        assertTrue(dock.contains("SteamDockTab.completeFixedOrder(order)"))
        assertTrue(dock.contains("NavigationBarItem("))
        assertTrue(dock.contains("onClick = { onSelected(tab) }"))
        assertFalse(dock.contains("FloatingActionButton("))
        assertFalse(dock.contains("SteamAvatarImage"))
        assertFalse(dock.contains("SteamAccountPickerSheet"))
        assertFalse(dock.contains("Icons.Default.QrCodeScanner"))
        assertFalse(dock.contains("WindowInsets.navigationBars"))
        assertFalse(dock.contains("offset(x = 8.dp)"))
        assertFalse(dock.contains("showProgress"))
        assertFalse(dock.contains("LinearProgressIndicator"))
        assertFalse(activity.contains("onScan = { navigateTo(MonicaSteamPage.SCANNER) }"))
        assertFalse(dock.contains("Column(modifier = Modifier.fillMaxWidth())"))
        assertFalse(settings.substringAfter("enum class SteamDockTab").substringBefore(";").contains("SCANNER"))
        assertTrue(notices.contains("Essentials"))
        assertTrue(notices.contains("Copyright (c) 2025 Sameera Sandakelum"))
    }

    @Test
    fun dockPagesDrawBehindDockWhileFixedActionsUseTheSharedClearance() {
        val toolbar = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/ui/SteamEssentialsFloatingToolbar.kt"
        ).readText()
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val token = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreHomePage.kt"
        ).readText()
        val library = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()

        assertTrue(toolbar.contains("SteamDockContentClearance = 104.dp"))
        assertTrue(activity.contains("LocalSteamDockContentClearance provides"))
        assertFalse(activity.contains("bottom = if (currentPage.isDockPage() && !isSteamChatThreadOpen)"))
        assertTrue(token.contains("steamDockActionClearance"))
        assertTrue(store.contains("steamDockActionClearance"))
        assertTrue(library.contains("LocalSteamDockContentClearance.current"))
        assertFalse(library.contains("onLoadingChange"))
    }

    @Test
    fun fixedBarDoesNotEnableTheRetiredProgressiveBlur() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val pageModifier = activity
            .substringAfter("AnimatedContent(")
            .substringBefore("targetState = currentPage")

        assertFalse(activity.contains(".steamDockProgressiveBlur("))
        assertFalse(activity.contains("dockBlurHeightPx"))
        assertTrue(pageModifier.contains(".steamLiquidGlassBackdropSource("))
        assertTrue(pageModifier.contains("enabled = liquidGlassEffectsEnabled"))
        assertFalse(pageModifier.contains("bottom ="))
        assertFalse(pageModifier.contains("SteamDockContentClearance"))
        assertTrue(activity.contains("zIndex(1f)"))
    }

    @Test
    fun dockSwipeObservesGesturesBeforeClickableChildrenConsumeThem() {
        val toolbar = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/ui/SteamEssentialsFloatingToolbar.kt"
        ).readText()

        assertTrue(toolbar.contains("awaitEachGesture"))
        assertTrue(toolbar.contains("requireUnconsumed = false"))
        assertTrue(toolbar.contains("pass = PointerEventPass.Initial"))
        assertTrue(toolbar.contains("awaitPointerEvent(pass = PointerEventPass.Initial)"))
        assertTrue(toolbar.contains("change.consume()"))
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
