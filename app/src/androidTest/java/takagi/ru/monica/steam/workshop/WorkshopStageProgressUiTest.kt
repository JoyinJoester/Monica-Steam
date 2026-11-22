package takagi.ru.monica.steam.workshop

import android.app.Notification
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.workDataOf
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamSpaceFixtureActivity
import takagi.ru.monica.steam.workshop.download.*
import takagi.ru.monica.ui.theme.MonicaTheme

class WorkshopStageProgressUiTest {
    @get:Rule val compose = createAndroidComposeRule<SteamSpaceFixtureActivity>()
    private val id = UUID.randomUUID()
    private val record = WorkshopDownloadRecord(id.toString(), 620, "123", "合成测试文件", "", "content://test/folder")
    private fun row(data: Data) = WorkshopDownloadRow(record,
        WorkInfo(id = id, state = WorkInfo.State.RUNNING, tags = emptySet(),
            outputData = Data.EMPTY, progress = data, runAttemptCount = 0, generation = 0))
    private fun bar() = compose.onNodeWithTag("workshop_stage_bar").fetchSemanticsNode()
        .config[SemanticsProperties.ProgressBarRangeInfo]
    private fun screenshot(name: String) {
        val folder = File(compose.activity.filesDir, "latency-download-evidence").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun savingReplacesCompletedNetworkCountersWithItsOwnProgress() {
        var progress by mutableStateOf(WorkshopDownloadProgress(WorkshopDownloadStage.DOWNLOADING, 400, 400))
        var cancelled = false
        compose.setContent { MonicaTheme {
            WorkshopDownloadsScreen(listOf(row(progress.toData())), {}, { cancelled = true }, {})
        } }
        assertEquals(1f, bar().current, 0f)
        compose.runOnIdle { progress = WorkshopDownloadProgress(WorkshopDownloadStage.SAVING, 100, 400) }
        compose.onNodeWithText(compose.activity.getString(R.string.wallpaper_saving)).assertIsDisplayed()
        assertEquals(0.25f, bar().current, 0f)
        screenshot("saving")
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.workshop_cancel)).performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }

    @Test fun legacySavingDataDoesNotShowStaleNetworkPercentage() {
        compose.setContent { MonicaTheme {
            WorkshopDownloadsScreen(listOf(row(workDataOf("phase" to "saving", "done" to 500L, "total" to 500L))), {}, {}, {})
        } }
        assertEquals(ProgressBarRangeInfo.Indeterminate, bar())
        compose.onNodeWithTag("workshop_stage_counts").assertDoesNotExist()
    }

    @Test fun checksumCountsFilesAndDirectDownloadOnlyClaimsSizeCheck() {
        var progress by mutableStateOf(WorkshopDownloadProgress(WorkshopDownloadStage.VERIFYING, 1, 4))
        compose.setContent { MonicaTheme {
            WorkshopDownloadsScreen(listOf(row(progress.toData())), {}, {}, {})
        } }
        compose.onNodeWithText(compose.activity.getString(R.string.workshop_progress_files, 1L, 4L)).assertIsDisplayed()
        assertEquals(0.25f, bar().current, 0f)
        screenshot("verifying")
        compose.runOnIdle { progress = WorkshopDownloadProgress(WorkshopDownloadStage.CHECKING_SIZE) }
        compose.onNodeWithText(compose.activity.getString(R.string.wallpaper_checking_size)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.wallpaper_verifying)).assertDoesNotExist()
        assertEquals(ProgressBarRangeInfo.Indeterminate, bar())
    }

    @Test fun stageLabelsAndCancelRemainVisibleAtLargeFont() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MonicaTheme(darkTheme = true) {
                    WorkshopDownloadsScreen(listOf(row(WorkshopDownloadProgress(WorkshopDownloadStage.SAVING, 10, 40).toData())), {}, {}, {})
                }
            }
        }
        listOf(R.string.workshop_stage_prepare, R.string.workshop_stage_download,
            R.string.workshop_stage_check, R.string.workshop_stage_save).forEach {
            compose.onNodeWithText(compose.activity.getString(it)).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.workshop_cancel)).assertIsDisplayed()
        screenshot("saving-large-dark")
    }

    @Test fun notificationUsesSameStageAndStageFraction() {
        for (stage in WorkshopDownloadStage.entries) {
            val progress = WorkshopDownloadProgress(stage, 1, 4)
            val notification = workshopDownloadForeground(compose.activity, id, "Synthetic", progress).notification
            assertEquals(compose.activity.getString(stage.label), notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertEquals(25, notification.extras.getInt(Notification.EXTRA_PROGRESS))
            assertFalse(notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        }
        val unknown = workshopDownloadForeground(compose.activity, id, "Synthetic",
            WorkshopDownloadProgress(WorkshopDownloadStage.SAVING)).notification
        assertTrue(unknown.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
    }

    @Test fun exportReportsWrittenBytesAndPreservesNestedFiles() = runBlocking {
        val folder = File(compose.activity.cacheDir, "stage-export-$id").apply { mkdirs() }
        try {
            val staging = File(folder,"staging").apply { mkdirs() }
            val first = File(staging,"nested/first.bin").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(150_000) { it.toByte() }) }
            val second = File(staging,"second.txt").apply { writeText("synthetic") }
            val output = File(folder,"output").apply { mkdirs() }
            val progress = mutableListOf<Pair<Long, Long>>()
            WorkshopDownloadWorker.exportFiles(compose.activity, staging, listOf(first, second), DocumentFile.fromFile(output)) { done, total ->
                progress += done to total
            }
            val total = first.length() + second.length()
            assertEquals(0L to total, progress.first())
            assertEquals(total to total, progress.last())
            assertTrue(progress.zipWithNext().all { (a,b) -> a.first < b.first })
            assertArrayEquals(first.readBytes(), File(output,"nested/first.bin").readBytes())
            assertArrayEquals(second.readBytes(), File(output,"second.txt").readBytes())
        } finally { folder.deleteRecursively() }
    }

    @Test fun cancelledExportDoesNotContinueWritingOrReachNextFile() = runBlocking {
        val folder = File(compose.activity.cacheDir, "stage-cancel-$id").apply { mkdirs() }
        try {
            val staging = File(folder,"staging").apply { mkdirs() }
            val first = File(staging,"first.bin").apply { writeBytes(ByteArray(150_000)) }
            val second = File(staging,"second.txt").apply { writeText("synthetic") }
            val output = File(folder,"output").apply { mkdirs() }
            try {
                WorkshopDownloadWorker.exportFiles(compose.activity, staging, listOf(first,second), DocumentFile.fromFile(output)) { done, _ ->
                    if (done > 0) throw CancellationException("user cancel")
                }
                fail("Export should have stopped")
            } catch (_: CancellationException) { }
            assertTrue(File(output,"first.bin").length() in 1 until first.length())
            assertFalse(File(output,"second.txt").exists())
        } finally { folder.deleteRecursively() }
    }
}
