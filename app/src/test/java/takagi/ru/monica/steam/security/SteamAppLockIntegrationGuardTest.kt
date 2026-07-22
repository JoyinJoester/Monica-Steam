package takagi.ru.monica.steam.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-level guard for reusing Monica's authentication surfaces in Steam. */
class SteamAppLockIntegrationGuardTest {
    @Test
    fun activityUsesOptionalMonicaStartupAuthenticationGate() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val gate = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/security/SteamAppLockGate.kt"
        ).readText()

        assertTrue(activity.contains("SteamAppLockGate("))
        assertTrue(activity.contains("PasswordViewModel"))
        assertTrue(gate.contains("SteamAppLockPolicy.resolveAccessState"))
        assertTrue(gate.contains("LoginScreen("))
        assertTrue(gate.contains("Lifecycle.Event.ON_START"))
        assertTrue(gate.contains("SessionManager.updateAutoLockTimeout"))
        assertTrue(gate.contains("passwordViewModel.logout()"))
        assertTrue(gate.contains("passwordViewModel.restoreAuthenticatedUiState()"))
    }

    @Test
    fun settingsReuseMonicaMasterPasswordAndRecoveryPages() {
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSharedSettingsHost.kt"
        ).readText()

        assertTrue(settings.contains("SteamSettingsChild.MASTER_PASSWORD_SETUP"))
        assertTrue(settings.contains("MasterPasswordLockingSettingsScreen("))
        assertTrue(settings.contains("ResetPasswordScreen("))
        assertTrue(settings.contains("SecurityQuestionsSetupScreen("))
        assertTrue(host.contains("showMasterPasswordLocking = true"))
        assertFalse(host.contains("onNavigateToMasterPasswordLocking = {}"))
    }

    @Test
    fun optionalSteamPolicyExistsAlongsideMonicaPolicy() {
        val policy = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/security/SteamAppLockPolicy.kt"
        )

        assertTrue(policy.exists())
        val source = policy.readText()
        assertTrue(source.contains("MainAppLockPolicy.resolveAccessState"))
        assertTrue(source.contains("steam_lock_not_configured"))
        assertTrue(source.contains("isFirstTime = false"))
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
