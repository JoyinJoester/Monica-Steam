package takagi.ru.monica.steam.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamFloatingDockGuardTest {
    @Test
    fun dockReusesEssentialsFloatingToolbarWithGlobalAccountAvatar() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val dock = activity
            .substringAfter("private fun SteamStandaloneDock(")
            .substringBefore("private fun SteamDockTab.icon()")
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/SteamDockSettings.kt"
        ).readText()
        val notices = projectFile("THIRD_PARTY_NOTICES.md").readText()
        val accountState = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/organization/presentation/SteamGlobalAccountViewModel.kt"
        ).readText()

        assertTrue(activity.contains("HorizontalFloatingToolbar"))
        assertTrue(activity.contains("ExperimentalMaterial3ExpressiveApi"))
        assertTrue(activity.contains("animateDpAsState"))
        assertTrue(activity.contains("Box(modifier = Modifier.fillMaxSize())"))
        assertTrue(activity.contains("modifier = Modifier.align(Alignment.BottomCenter)"))
        assertFalse(activity.contains("bottomBar ="))
        assertTrue(dock.contains("SteamDockTab.sanitizeOrder(order).forEachIndexed"))
        assertTrue(dock.contains("floatingActionButton ="))
        assertFalse(dock.contains("FloatingActionButton("))
        assertTrue(dock.contains("IconButton("))
        assertTrue(dock.contains("SteamAvatarImage(account = selectedAccount, size = 48.dp)"))
        assertTrue(dock.contains(".size(56.dp)"))
        assertTrue(dock.contains("selectedAccount"))
        assertTrue(dock.contains("onAccountClick"))
        assertFalse(dock.contains("Icons.Default.QrCodeScanner"))
        assertTrue(dock.contains("WindowInsets.navigationBars"))
        assertTrue(dock.contains("isSelected && !shouldHideLabel"))
        assertTrue(dock.contains("showProgress"))
        assertTrue(activity.contains("SteamAccountPickerSheet("))
        assertTrue(activity.contains("globalAccountViewModel.selectAccount(account.id)"))
        assertFalse(activity.contains("onScan = { navigateTo(MonicaSteamPage.SCANNER) }"))
        assertFalse(dock.contains("Column(modifier = Modifier.fillMaxWidth())"))
        assertTrue(dock.contains("toolbarContainerColor = MaterialTheme.colorScheme.primary"))
        assertTrue(dock.contains("contentColor = MaterialTheme.colorScheme.primary"))
        assertTrue(dock.contains("containerColor = MaterialTheme.colorScheme.background"))
        assertTrue(accountState.contains("accountRepository.observeAccounts()"))
        assertTrue(accountState.contains("accountRepository.select(accountId)"))
        assertFalse(settings.substringAfter("enum class SteamDockTab").substringBefore(";").contains("SCANNER"))
        assertTrue(notices.contains("Essentials"))
        assertTrue(notices.contains("Copyright (c) 2025 Sameera Sandakelum"))
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
