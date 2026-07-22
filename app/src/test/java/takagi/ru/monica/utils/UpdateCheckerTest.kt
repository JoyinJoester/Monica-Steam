package takagi.ru.monica.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun compareVersionTags_detectsNewerRelease() {
        assertTrue(UpdateChecker.compareVersionTags("v1.0.296", "1.0.294") > 0)
    }

    @Test
    fun compareVersionTags_treatsMatchingPrefixAsSameVersion() {
        assertEquals(0, UpdateChecker.compareVersionTags("v1.0.294", "1.0.294-preview"))
    }

    @Test
    fun compareVersionTags_ignoresReleaseLetterSuffix() {
        assertEquals(0, UpdateChecker.compareVersionTags("V1.0.294c", "1.0.294"))
        assertEquals(0, UpdateChecker.compareVersionTags("V1.0.294c", "V1.0.294b"))
    }

    @Test
    fun compareVersionTags_detectsOlderRelease() {
        assertTrue(UpdateChecker.compareVersionTags("1.0.287", "1.0.294") < 0)
    }

    @Test
    fun apkDownloadUsesLongRunningClientAndReportsProgress() {
        val source = File("src/main/java/takagi/ru/monica/utils/UpdateChecker.kt").readText()
        val downloadClientBlock = source
            .substringAfter("private val downloadClient")
            .substringBefore("suspend fun checkLatestRelease")
        val downloadApkBlock = source
            .substringAfter("suspend fun downloadApk(")
            .substringBefore("fun validateDownloadedApk")

        assertFalse(downloadClientBlock.contains("callTimeout(20, TimeUnit.SECONDS)"))
        assertTrue(downloadApkBlock.contains("onProgress: suspend (UpdateDownloadProgress) -> Unit"))
        assertTrue(downloadApkBlock.contains("downloadClient.newCall(request)"))
        assertTrue(downloadApkBlock.contains("body.contentLength()"))
        assertTrue(downloadApkBlock.contains("onProgress(UpdateDownloadProgress(bytesRead, totalBytes))"))
    }

    @Test
    fun updateDownloadProgressCalculatesFractionOnlyWhenTotalIsKnown() {
        val known = UpdateDownloadProgress(bytesRead = 25L, totalBytes = 100L)
        val unknown = UpdateDownloadProgress(bytesRead = 25L, totalBytes = -1L)

        assertTrue(known.hasTotal)
        assertEquals(0.25f, known.fraction, 0.0001f)
        assertFalse(unknown.hasTotal)
        assertEquals(0f, unknown.fraction, 0.0001f)
    }
}
