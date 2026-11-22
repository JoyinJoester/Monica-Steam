package takagi.ru.monica.steam.workshop

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import takagi.ru.monica.R
import takagi.ru.monica.MonicaSteamActivity
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle
import takagi.ru.monica.steam.session.domain.SteamAccountSessionOrigin

@RunWith(AndroidJUnit4::class)
class SteamWorkshopBackgroundTest {
    @get:Rule val testName = TestName()
    private val prepared = mutableListOf<WorkshopBulkTask>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun allowProgressNotifications() {
        check(context.packageName in setOf("takagi.ru.monica.steam.recoverytest", "takagi.ru.monica.steamapp.toolchainpilot"))
        val security = takagi.ru.monica.security.SecurityManager(context)
        if (!security.isMasterPasswordSet()) {
            check(runBlocking { takagi.ru.monica.steam.data.SteamDatabase.getDatabase(context).steamAccountDao().count() } == 0)
            security.setMasterPassword("workshop-synthetic-73194628")
        }
        runBlocking { takagi.ru.monica.utils.SettingsManager(context).prepareSteamMasterPasswordSetup() }
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        context.startActivity(Intent(context, MonicaSteamActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val deadline = SystemClock.elapsedRealtime() + 10_000
        var focused = false
        while (!focused && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync {
                focused = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MonicaSteamActivity>().any { !it.isFinishing && it.hasWindowFocus() }
            }
            if (!focused) SystemClock.sleep(50)
        }
        assertTrue("The notification fixture must be foreground before sending its PendingIntent", focused)
    }

    @After fun closeActivity() {
        screenshot(testName.methodName)
        runBlocking {
            for (task in prepared) {
                WorkManager.getInstance(context).cancelWorkById(UUID.fromString(task.state.requestId)).result.get(5, TimeUnit.SECONDS)
                val controller = SteamWorkshopBulkTasks.get(context).controller(task.target.key)
                controller?.stop()
                if (controller != null) withTimeout(5000) { controller.state.first { it?.running != true } }
                controller?.dismissFinished()
                if (controller != null) withTimeout(5000) { controller.state.first { it == null } }
            }
        }
        goHome()
    }

    @Test fun continueAndBackHideProgressWithoutStoppingAndItCanBeReopened() {
        val task = prepareQueuedTask()
        val notification = SteamWorkshopBulkNotifications(context).foreground(task).notification
        notification.contentIntent.send()
        awaitText(R.string.workshop_background_continue)
        screenshot("background-dialog")
        click(R.string.workshop_background_continue)
        assertNoText(R.string.workshop_background_continue)
        assertQueueContinues(task)
        notification.contentIntent.send()
        awaitText(R.string.workshop_background_continue)
        Espresso.pressBack()
        assertNoText(R.string.workshop_background_continue)
        assertQueueContinues(task)
    }

    @Test fun stopButtonStopsTheQueuedTaskAndLeavesItsResultsOpen() {
        val task = prepareQueuedTask()
        SteamWorkshopBulkNotifications(context).foreground(task).notification.contentIntent.send()
        click(R.string.workshop_stop_queue)
        awaitText(R.string.workshop_done)
        runBlocking {
            val controller = requireNotNull(SteamWorkshopBulkTasks.get(context).controller(task.target.key))
            val stopped = withTimeout(5000) { controller.state.filterNotNull().first { !it.running } }
            assertTrue(stopped.stopRequested)
            assertEquals(task.state.results, stopped.results)
        }
    }

    @Test fun systemWorkerStartsWithoutAWorkshopPageAndChecksItsOriginalAccount() = runBlocking {
        // This account is never stored and cannot send Steam requests. LOGIN means the real
        // foreground Worker started and reached account validation, rather than a service failure.
        val account = SteamAccount(904001, "76561198000904001", "workshop-test", "Worker startup", "", "",
            null, null, null, "not-a-real-token", "not-a-real-refresh", null, "{}", false, 0, 0, 0)
        val handle = SteamAccountSessionHandle(account, SteamAccountSessionOrigin(SteamStorageSource.Local))
        val tasks = SteamWorkshopBulkTasks.get(context)
        val controller = tasks.controller(handle, 570, "Dota 2")
        controller.start(listOf("1", "2"), true, false)
        val started = withTimeout(20_000) { controller.state.filterNotNull().first() }
        goHome()
        try {
            val ended = withTimeout(30_000) { controller.state.filterNotNull().first { !it.running } }
            assertEquals(started.requestId, ended.requestId)
            assertEquals(WorkshopFailure.LOGIN, ended.failure)
            assertTrue(ended.results.isEmpty())
            val key = WorkshopBulkTarget(handle.stableKey, account.id, account.steamId, 570).key
            assertEquals(ended, tasks.task(key)?.state)
            val manager = WorkManager.getInstance(context)
            withTimeout(15_000) {
                while (manager.getWorkInfoById(UUID.fromString(ended.requestId)).get(5, TimeUnit.SECONDS)?.state?.isFinished != true) delay(100)
            }
        } finally {
            controller.stop()
            controller.dismissFinished()
        }
    }

    @Test fun presetNotificationRestoresItsNameAndRetriesBothOperationDirections() = runBlocking {
        val preset = WorkshopPresetSwitch("Realistic MODs", WorkshopShareCode.encode(WorkshopShare(550, listOf("2", "3"))), listOf("1", "2"))
        val task = prepareQueuedTask(preset)
        SteamWorkshopBulkNotifications(context).foreground(task).notification.contentIntent.send()
        awaitText(R.string.workshop_preset_switch)
        assertTrue(accessibilityTree().contains("Realistic MODs"))
        screenshot("preset-background-dialog")
        click(R.string.workshop_stop_queue)
        awaitText(R.string.workshop_preset_incomplete)
        val tasks = SteamWorkshopBulkTasks.get(context)
        val controller = requireNotNull(tasks.controller(task.target.key))
        val stopped = requireNotNull(controller.state.value)
        assertFalse(stopped.running)
        assertTrue(stopped.canRetry)
        assertEquals(preset, stopped.preset)
        click(R.string.workshop_retry_remaining)
        val ended = withTimeout(30_000) { controller.state.filterNotNull().first { it.requestId != stopped.requestId && !it.running } }
        // The fictional account is absent: the actual WorkManager retry must stop before any Steam request.
        assertEquals(WorkshopFailure.LOGIN, ended.failure)
        assertEquals(preset, ended.preset)
        assertEquals(listOf("2", "3", "1"), ended.ids)
        assertFalse(ended.subscribes("1"))
        // Inspect the persisted version directly; the schema getter is unused and folded away by R8.
        val persisted = File(context.noBackupFilesDir, "workshop-tasks/${task.target.key}.json").readText()
        assertEquals(2, org.json.JSONObject(persisted).getInt("schema"))
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        val directory = requireNotNull(context.getExternalFilesDir("workshop-verification"))
        File(directory, "$name.txt").writeText(accessibilityTree())
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun goHome() {
        // Instrumentation key injection may be delivered only to the target Activity.
        // Launch the actual HOME destination and retain the observed-background assertion.
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (instrumentation.uiAutomation.rootInActiveWindow?.packageName == context.packageName &&
            SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertNotEquals(context.packageName, instrumentation.uiAutomation.rootInActiveWindow?.packageName)
    }

    private fun prepareQueuedTask(preset: WorkshopPresetSwitch? = null): WorkshopBulkTask {
        val accountId = 1_000_000L + (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE) % 1_000_000_000L
        val steamId = "76561198000904001"
        val task = WorkshopBulkTask(WorkshopBulkTarget("room|$accountId|$steamId", accountId, steamId, 550),
            "Left 4 Dead 2", "Test account", WorkshopBulkState(preset?.operationIds ?: (1..56).map(Int::toString),
                true, false, queued = true, preset = preset))
        WorkshopBulkTaskStore(File(context.noBackupFilesDir, "workshop-tasks")).write(task)
        // A real persisted WorkManager request keeps the actual app UI on queued progress,
        // without contacting Steam or introducing fake Composables into a minified APK.
        val request = OneTimeWorkRequestBuilder<SteamWorkshopBulkWorker>()
            .setId(UUID.fromString(task.state.requestId))
            .setInputData(androidx.work.Data.Builder().putString(SteamWorkshopBulkTasks.KEY_TASK, task.target.key).build())
            .setInitialDelay(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueue(request).result.get(5, TimeUnit.SECONDS)
        prepared += task
        return task
    }

    private fun assertQueueContinues(task: WorkshopBulkTask) = runBlocking {
        val state = requireNotNull(SteamWorkshopBulkTasks.get(context).task(task.target.key)).state
        assertEquals(task.state.requestId, state.requestId)
        assertTrue(state.running)
        assertFalse(state.stopRequested)
    }

    // Compose exposes virtual descendants: traverse them as UiAutomator does instead
    // of relying on the platform's findAccessibilityNodeInfosByText implementation.
    private fun allNodes(): List<AccessibilityNodeInfo> {
        val automation = instrumentation.uiAutomation
        val roots = automation.windows.mapNotNull { it.root }.ifEmpty {
            listOfNotNull(automation.rootInActiveWindow)
        }
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            nodes += node
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        roots.forEach(::visit)
        return nodes
    }

    private fun nodes(resource: Int): List<AccessibilityNodeInfo> {
        val text = context.getString(resource)
        return allNodes().filter { it.isVisibleToUser &&
            (it.text?.contains(text) == true || it.contentDescription?.contains(text) == true) }
    }

    private fun accessibilityTree(): String = allNodes().joinToString("\n") {
        "${it.className} visible=${it.isVisibleToUser} clickable=${it.isClickable} text=${it.text} description=${it.contentDescription}"
    }

    private fun awaitText(resource: Int): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            nodes(resource).firstOrNull()?.let { return it }
            Thread.sleep(50)
        }
        throw AssertionError("Missing UI text: ${context.getString(resource)}\n${accessibilityTree().take(8000)}")
    }

    private fun assertNoText(resource: Int) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (nodes(resource).isEmpty()) return
            Thread.sleep(50)
        }
        throw AssertionError("Unexpected UI text: ${context.getString(resource)}")
    }

    private fun click(resource: Int) {
        var node = awaitText(resource)
        while (!node.isClickable) node = node.parent ?: throw AssertionError("Text has no clickable parent")
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
}
