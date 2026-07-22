package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamMarketSensitiveActionGuardTest {
    @Test
    fun steamWritesAreOnlyReachedThroughTheOneTimeProtectedExecutor() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt"
        ).readText()

        assertTrue(source.contains("private sealed interface SteamProtectedMarketAction"))
        assertTrue(source.contains("fun requestProtectedMarketAction("))
        assertTrue(source.contains("fun executeProtectedMarketAction("))
        assertTrue(source.contains("if (pendingProtectedMarketAction != action) return"))
        assertEquals(1, Regex("viewModel\\.sellInventoryBatch\\(").findAll(source).count())
        assertEquals(1, Regex("viewModel\\.cancelMarketListings\\(").findAll(source).count())
        assertTrue(source.contains("entries = entries.toList()"))
        assertTrue(source.contains("listings = listings.toList()"))
    }

    @Test
    fun protectedExecutorRequiresMasterPasswordOrStrongBiometricSuccess() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt"
        ).readText()

        assertTrue(source.contains("M3IdentityVerifyDialog("))
        assertTrue(source.contains("securityManager.verifyMasterPassword("))
        assertTrue(source.contains("biometricHelper.authenticate("))
        assertTrue(source.contains("appSettings.biometricEnabled"))
        assertTrue(source.contains("onSuccess = { executeProtectedMarketAction(action) }"))
        assertTrue(source.contains("onDismiss = { dismissProtectedMarketAction() }"))
        assertTrue(source.contains("steam_market_auth_unavailable"))
    }

    @Test
    fun accountOrSectionChangesDiscardPendingAuthorization() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt"
        ).readText()
        val lifecycleCleanup = source
            .substringAfter("LaunchedEffect(selectedSection, uiState.storageSource, detailAccountId)")
            .substringBefore("LaunchedEffect(steamIdCompletionAccount?.id")

        assertTrue(lifecycleCleanup.contains("pendingProtectedMarketAction = null"))
        assertTrue(lifecycleCleanup.contains("protectedMarketPasswordInput = \"\""))
        assertTrue(lifecycleCleanup.contains("LaunchedEffect(selectedAccount?.id)"))
    }

    private fun projectFile(relativePath: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, relativePath)
    }
}
