package takagi.ru.monica.steam

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamSharedOverflowMenuGuardTest {
    @Test
    fun tokenStoreAndLibraryShareMonicaMenuWithCorrectDestinations() {
        val sharedStyle = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordTopActionsMenu.kt"
        ).readText()
        val pageMenu = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/ui/SteamPageRefreshControls.kt"
        ).readText()
        val token = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val library = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()

        assertTrue(sharedStyle.contains("fun MonicaTopActionsDropdownMenu("))
        assertTrue(pageMenu.contains("MonicaTopActionsDropdownMenu("))
        assertTrue(token.contains("MonicaTopActionsDropdownMenu("))
        val tokenMenu = token.substringAfter("private fun SteamTopActionsMenu(")
            .substringBefore("private fun SteamStorageSourceMenu(")
        assertTrue(tokenMenu.contains("filterNot"))
        assertTrue(tokenMenu.contains("SteamSection.FRIENDS"))
        assertTrue(tokenMenu.contains("SteamSection.CHAT"))
        assertFalse(tokenMenu.contains("pendingChatCount"))
        assertTrue(pageMenu.contains("R.string.steam_notifications_title"))
        assertTrue(pageMenu.contains("additionalActions.forEach"))
        assertTrue(pageMenu.contains("SteamPageOverflowAction"))
        assertTrue(store.contains("onOpenNotifications"))
        assertTrue(library.contains("onOpenNotifications"))
        assertTrue(activity.contains("openNotificationsOnEntry = pendingSteamNotifications"))
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
