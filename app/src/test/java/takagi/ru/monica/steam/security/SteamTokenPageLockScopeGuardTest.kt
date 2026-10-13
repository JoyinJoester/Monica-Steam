package takagi.ru.monica.steam.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamTokenPageLockScopeGuardTest {
    @Test
    fun steamLockScopeIsPersistedWithStartupLockAsTheSafeDefault() {
        val appSettings = projectFile(
            "app/src/main/java/takagi/ru/monica/data/AppSettings.kt"
        ).readText()
        val settingsManager = projectFile(
            "app/src/main/java/takagi/ru/monica/utils/SettingsManager.kt"
        ).readText()
        val settingsViewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/SettingsViewModel.kt"
        ).readText()

        assertTrue(appSettings.contains("val steamLockTokenPageOnly: Boolean = false"))
        assertTrue(settingsManager.contains("steam_lock_token_page_only"))
        assertTrue(settingsManager.contains("steamLockTokenPageOnly ="))
        assertTrue(settingsManager.contains("updateSteamLockTokenPageOnly"))
        assertTrue(settingsViewModel.contains("updateSteamLockTokenPageOnly"))
    }

    @Test
    fun steamMasterPasswordSettingsExposeTheOptionalTokenPageScope() {
        val sharedScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MasterPasswordLockingSettingsScreen.kt"
        ).readText()
        val steamSettings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()
        val defaultStrings = projectFile("app/src/main/res/values/strings.xml").readText()
        val chineseStrings = projectFile("app/src/main/res/values-zh/strings.xml").readText()

        assertTrue(sharedScreen.contains("showSteamTokenPageLockOption: Boolean = false"))
        assertTrue(sharedScreen.contains("settings.steamLockTokenPageOnly"))
        assertTrue(sharedScreen.contains("updateSteamLockTokenPageOnly"))
        assertTrue(steamSettings.contains("showSteamTokenPageLockOption = true"))
        assertTrue(defaultStrings.contains("steam_lock_token_page_only"))
        assertTrue(chineseStrings.contains("steam_lock_token_page_only"))
    }

    @Test
    fun activityProtectsTokenAndBackupRoutesWhenStartupGateIsBypassed() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val gate = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/security/SteamAppLockGate.kt"
        ).readText()

        assertTrue(gate.contains("enabled: Boolean = true"))
        assertTrue(gate.contains("if (!enabled)"))
        assertTrue(gate.contains("allowStartupVerificationBypass: Boolean = true"))
        assertTrue(gate.contains("allowStartupVerificationBypass = allowStartupVerificationBypass"))
        val login = projectFile("app/src/main/java/takagi/ru/monica/ui/screens/LoginScreen.kt").readText()
        assertTrue(login.contains("allowStartupVerificationBypass && (settings?.disablePasswordVerification ?: false)"))
        assertTrue(activity.contains("enabled = !settings.steamLockTokenPageOnly"))
        assertTrue(activity.contains("enabled = shouldProtectSteamSensitiveSurface("))
        assertTrue(activity.contains("allowStartupVerificationBypass = false"))
        val backupRoutes = activity.substring(
            activity.indexOf("MonicaSteamPage.MAFILE_TRANSFER,"),
            activity.indexOf("MonicaSteamPage.MDBX ->")
        )
        assertTrue(backupRoutes.contains("MonicaSteamPage.WEBDAV_BACKUP ->"))
        assertTrue(backupRoutes.contains("SteamAppLockGate("))
        assertTrue(backupRoutes.contains("allowStartupVerificationBypass = false"))
        assertTrue(backupRoutes.contains("SteamMaFileTransferScreen("))
        assertTrue(backupRoutes.contains("transferState = maFileTransferState"))
        assertTrue(backupRoutes.contains("WebDavBackupScreen("))
        assertTrue(activity.indexOf("val maFileTransferState = rememberSteamMaFileTransferState(") in
            0 until activity.indexOf("SteamAppLockGate("))
        assertEquals(3, activity.windowed("SteamAppLockGate(".length)
            .count { it == "SteamAppLockGate(" })
    }

    @Test
    fun startupWaitsForPersistedLockScopeAndDockHomeBeforeRenderingAuthentication() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val normalized = activity.replace(Regex("\\s+"), " ")

        assertTrue(normalized.contains(
            "val loadedSettings by settingsManager.settingsFlow.collectAsState( initial = null )"
        ))
        assertTrue(normalized.contains(
            "val loadedDockConfiguration by dockPreferences.configuration.collectAsState( " +
                "initial = null )"
        ))
        assertTrue(normalized.contains(
            "if (settings == null || dockConfiguration == null) { " +
                "SteamStartupSurface() return@steamContent }"
        ))
        assertTrue(normalized.contains(
            "var currentPage by rememberSaveable { mutableStateOf(homePage) }"
        ))
        assertFalse(activity.contains(
            "settingsManager.settingsFlow.collectAsState(initial = AppSettings())"
        ))
        assertTrue(
            activity.indexOf("SteamStartupSurface()") in
                0 until activity.indexOf("SteamAppLockGate(")
        )
    }

    @Test
    fun masterPasswordSecurityPagesStayProtectedWhenTheOuterGateIsBypassed() {
        val steamSettings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()

        assertTrue(steamSettings.contains("fun SteamSensitiveSettingsGate("))
        assertTrue(steamSettings.contains("enabled = shouldProtectSteamSensitiveSurface("))
        assertTrue(steamSettings.contains("allowStartupVerificationBypass = false"))
        assertTrue(steamSettings.contains("SteamSettingsChild.MASTER_PASSWORD_LOCKING ->"))
        assertTrue(steamSettings.contains("SteamSettingsChild.RESET_PASSWORD ->"))
        assertTrue(steamSettings.contains("SteamSettingsChild.SECURITY_QUESTIONS ->"))
        assertTrue(steamSettings.windowed("SteamSensitiveSettingsGate(".length)
            .count { it == "SteamSensitiveSettingsGate(" } >= 4)
    }

    @Test
    fun sensitiveSurfacesAreProtectedWheneverTheOuterGateDoesNotAuthenticate() {
        assertFalse(
            shouldProtectSteamSensitiveSurface(
                tokenPageOnly = false,
                startupVerificationBypass = false
            )
        )
        assertTrue(
            shouldProtectSteamSensitiveSurface(
                tokenPageOnly = true,
                startupVerificationBypass = false
            )
        )
        assertTrue(
            shouldProtectSteamSensitiveSurface(
                tokenPageOnly = false,
                startupVerificationBypass = true
            )
        )
        assertTrue(
            shouldProtectSteamSensitiveSurface(
                tokenPageOnly = true,
                startupVerificationBypass = true
            )
        )
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
