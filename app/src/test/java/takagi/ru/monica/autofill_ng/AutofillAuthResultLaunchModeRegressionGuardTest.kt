package takagi.ru.monica.autofill_ng

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class AutofillAuthResultLaunchModeRegressionGuardTest {

    @Test
    fun authResultActivitiesMustNotReuseExistingInstances() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val authResultActivities = listOf(
            ".autofill_ng.BiometricAuthActivity",
            ".autofill_ng.AutofillAuthenticationActivity",
            ".autofill_ng.AutofillUnlockActivity",
            ".autofill_ng.AutofillCipherCallbackActivity",
            ".autofill_ng.AutofillPickerActivity",
            ".autofill_ng.AutofillPickerActivityV2",
            ".autofill_ng.PasswordSuggestionActivity",
        )

        authResultActivities.forEach { activityName ->
            val block = activityBlock(manifest, activityName)
            assertFalse(
                "$activityName returns AutofillManager.EXTRA_AUTHENTICATION_RESULT and must use a fresh Activity result record.",
                block.contains("android:launchMode=\"singleTop\"") ||
                    block.contains("android:launchMode=\"singleTask\"")
            )
        }
    }

    private fun activityBlock(manifest: String, activityName: String): String {
        val marker = "android:name=\"$activityName\""
        val markerIndex = manifest.indexOf(marker)
        require(markerIndex >= 0) { "Unable to find activity in manifest: $activityName" }
        val blockStart = manifest.lastIndexOf("<activity", markerIndex)
        val blockEnd = manifest.indexOf("/>", markerIndex)
        require(blockStart >= 0 && blockEnd >= 0) { "Unable to read manifest block for: $activityName" }
        return manifest.substring(blockStart, blockEnd)
    }

    private fun projectFile(relativePath: String): File {
        val candidates = mutableListOf<File>()
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            candidates += File(dir, relativePath)
            dir = dir.parentFile
        }

        return candidates.firstOrNull { it.isFile }
            ?: error("Unable to find project file: $relativePath from ${System.getProperty("user.dir")}")
    }
}
