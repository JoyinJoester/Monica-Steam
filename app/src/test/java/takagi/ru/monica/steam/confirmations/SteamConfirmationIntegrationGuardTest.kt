package takagi.ru.monica.steam.confirmations

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamConfirmationIntegrationGuardTest {
    @Test
    fun productionActionsRecordHistoryAndUseSecurityRepository() {
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamViewModel.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt"
        ).readText()

        assertTrue(viewModel.contains("SteamSecurityEventRepository"))
        assertTrue(viewModel.contains("SteamSecurityEventType.CONFIRMATION_ACTION"))
        assertTrue(viewModel.contains("recordConfirmationEvent("))
        assertTrue(viewModel.contains("fun respondConfirmations("))
        assertTrue(screen.contains("M3IdentityVerifyDialog("))
        assertTrue(screen.contains("executeProtectedConfirmationAction"))
        assertTrue(screen.contains("SteamConfirmationRiskEvaluator"))
        assertTrue(screen.contains("SteamConfirmationHistoryCard"))
        assertTrue(screen.contains("SteamConfirmationKindClassifier"))
        assertTrue(screen.contains("SteamConfirmationDetailSheet("))
        assertTrue(screen.contains("steam_confirmation_kind_gift"))
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
