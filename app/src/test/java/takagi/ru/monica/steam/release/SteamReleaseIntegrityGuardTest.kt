package takagi.ru.monica.steam.release

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.foundation.release.SteamReleaseConfig

class SteamReleaseIntegrityGuardTest {
    @Test
    fun allPublicReleaseReferencesUseMonicaSteamRepository() {
        val updateChecker = projectFile(
            "app/src/main/java/takagi/ru/monica/utils/UpdateChecker.kt"
        ).readText()
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SettingsScreen.kt"
        ).readText()
        val settingsHost = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSharedSettingsHost.kt"
        ).readText()
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val filePaths = projectFile("app/src/main/res/xml/file_paths.xml").readText()
        val englishStrings = projectFile("app/src/main/res/values/strings.xml").readText()
        val chineseStrings = projectFile("app/src/main/res/values-zh/strings.xml").readText()
        val buildScript = projectFile("app/build.gradle").readText()
        val appSelector = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/AppSelector.kt"
        ).readText()

        assertTrue(updateChecker.contains("SteamReleaseConfig.latestReleaseApiUrl"))
        assertTrue(updateChecker.contains("SteamReleaseConfig.updateUserAgent"))
        assertTrue(settings.contains("UpdateChecker.checkLatestRelease(currentVersion)"))
        assertTrue(settings.contains("UpdateChecker.validateDownloadedApk(context, apkFile)"))
        assertTrue(settings.contains("Monica-Steam-\${result.latestVersion}.apk"))
        assertTrue(settingsHost.contains("showUpdateCheck = inlineAppSupportItems"))
        assertTrue(manifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
        assertTrue(filePaths.contains("<cache-path name=\"update_apk\" path=\"update_apk/\" />"))
        assertTrue(englishStrings.contains("A newer Monica Steam release is available."))
        assertTrue(chineseStrings.contains("Monica Steam 有新版本可用。"))
        assertTrue(buildScript.contains("def appVersionCode = 18"))
        assertFalse(updateChecker.contains("Monica-Android"))
        assertTrue(settings.contains("SteamReleaseConfig.repositoryUrl"))
        assertTrue(appSelector.contains("SteamReleaseConfig.issuesUrl"))
        assertFalse(updateChecker.contains("Monica-Pass/Monica"))
        assertFalse(settings.contains("Monica-Pass/Monica"))
        assertFalse(appSelector.contains("Monica-Pass/Monica-for-Android"))
        assertFalse(englishStrings.contains("Monica for Android"))
        assertFalse(chineseStrings.contains("Monica for Android"))
        assertEquals(
            "https://api.github.com/repos/JoyinJoester/Monica-Steam/releases/latest",
            SteamReleaseConfig.latestReleaseApiUrl
        )
    }

    @Test
    fun updateAssetSelectionPrefersDeviceAbiThenUniversal() {
        val assets = listOf(
            "Monica-Steam-Android-armeabi-v7a-1.APK",
            "Monica-Steam-Android-arm64-v8a-1.APK",
            "Monica-Steam-Android-universal-1.APK"
        )

        assertEquals(
            "Monica-Steam-Android-arm64-v8a-1.APK",
            SteamReleaseConfig.selectReleaseApkAssetName(assets, listOf("arm64-v8a", "armeabi-v7a"))
        )
        assertEquals(
            "Monica-Steam-Android-universal-1.APK",
            SteamReleaseConfig.selectReleaseApkAssetName(assets, listOf("x86_64"))
        )
        assertEquals(
            null,
            SteamReleaseConfig.selectReleaseApkAssetName(
                listOf("Monica-Steam-Android-armeabi-v7a-1.APK"),
                listOf("x86_64")
            )
        )
    }

    @Test
    fun releaseSigningIsExternalAndDebugSigningIsForbidden() {
        val buildScript = projectFile("app/build.gradle").readText()
        val ignoreRules = projectFile(".gitignore").readText()
        val documentation = projectFile("docs/release-signing.md").readText()

        listOf(
            "MONICA_STEAM_RELEASE_STORE_FILE",
            "MONICA_STEAM_RELEASE_STORE_PASSWORD",
            "MONICA_STEAM_RELEASE_KEY_ALIAS",
            "MONICA_STEAM_RELEASE_KEY_PASSWORD",
            "verifyReleaseSigningConfiguration",
            "signingConfig null",
            "requireReleaseSigning"
        ).forEach { required -> assertTrue(buildScript.contains(required)) }
        assertFalse(buildScript.contains("signingConfig signingConfigs.debug"))
        assertTrue(ignoreRules.contains("keystore.properties"))
        assertTrue(ignoreRules.contains("*.jks"))
        assertTrue(documentation.contains("verifyReleaseSigningConfiguration"))
        assertTrue(documentation.contains("MONICA_STEAM_RELEASE_STORE_PASSWORD"))
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
