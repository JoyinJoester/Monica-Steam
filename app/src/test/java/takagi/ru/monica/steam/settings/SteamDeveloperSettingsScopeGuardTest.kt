package takagi.ru.monica.steam.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamDeveloperSettingsScopeGuardTest {
    @Test
    fun developerPageContainsOnlyTheThreeLogActions() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/DeveloperSettingsScreen.kt"
        ).readText()
        val page = source
            .substringAfter("fun DeveloperSettingsScreen(")
            .substringBefore("fun DebugLogsDialog(")

        assertEquals(3, Regex("\\bSettingsItem\\(").findAll(page).count())
        assertFalse(page.contains("SettingsItemWithSwitch("))
        assertFalse(page.contains("onNavigateToMdbx"))
        assertFalse(page.contains("AutofillPickerActivityV2"))
        assertFalse(page.contains("R.string.developer_functions"))
        assertFalse(page.contains("R.string.disable_password_verification"))
    }

    @Test
    fun passwordVerificationControlBelongsToMasterPasswordAndLocking() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MasterPasswordLockingSettingsScreen.kt"
        ).readText()

        assertTrue(source.contains("R.string.disable_password_verification"))
        assertTrue(source.contains("settings.disablePasswordVerification"))
        assertTrue(source.contains("viewModel.updateDisablePasswordVerification"))
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
