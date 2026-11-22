package takagi.ru.monica.steam.scanner

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.scanner.data.readLastSteamQrAccountId
import takagi.ru.monica.steam.scanner.data.readSteamStorageSource
import takagi.ru.monica.steam.scanner.ui.SteamQrScannerScreen
import takagi.ru.monica.ui.theme.MonicaTheme

/** Only the disposable pilot package, empty local account store and synthetic images. */
private class IsolatedScannerRule : ExternalResource() {
    override fun before() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "takagi.ru.monica.steamapp.toolchainpilot")
        check(readSteamStorageSource(context) == SteamStorageSource.Local)
        check(readLastSteamQrAccountId(context) == null)
        check(runBlocking { SteamDatabase.getDatabase(context).steamAccountDao().count() } == 0)
        SteamDiagLogger.initialize(context)
    }
}

private class ScannerCameraPermissionRule : ExternalResource() {
    override fun before() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "takagi.ru.monica.steamapp.toolchainpilot")
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
}

private fun scannerViews(root: View): List<View> = buildList {
    add(root)
    if (root is ViewGroup) for (i in 0 until root.childCount) addAll(scannerViews(root.getChildAt(i)))
}

private fun diagnosticMarker(): String = "scanner_pilot_marker_${UUID.randomUUID()}".also {
    SteamDiagLogger.append(it)
}

private fun diagnosticsAfter(marker: String): String =
    SteamDiagLogger.exportPersistedLogs(4000).substringAfter(marker, "")

private fun diagnosticEvent(lines: String, event: String): Boolean = lines.lineSequence().any {
    it.startsWith("qr_scan label=steam_qr event=$event ") ||
        it == "qr_scan label=steam_qr event=$event"
}

class SteamScannerLifecycleTest {
    private val compose = createAndroidComposeRule<SteamComposeFixtureActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(IsolatedScannerRule())
        .around(ScannerCameraPermissionRule()).around(compose)

    @Test fun repeatedGrantedEntriesStreamFramesAndDetachTheirCameraOnReturn() {
        val visible = mutableStateOf(false)
        var backs = 0
        var results = 0
        compose.setContent {
            MonicaTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (visible.value) SteamQrScannerScreen(
                        initialAccountId = null,
                        onQrCodeScanned = { _, _ -> results++ },
                        onNavigateBack = { backs++; visible.value = false }
                    ) else Text("scanner pilot closed")
                }
            }
        }
        repeat(3) { cycle ->
            val marker = diagnosticMarker()
            compose.waitUntil(5_000) { SteamDiagLogger.exportPersistedLogs(4000).contains(marker) }
            compose.runOnIdle { visible.value = true }
            compose.onNodeWithText(compose.activity.getString(R.string.scan_qr_code_title)).assertIsDisplayed()
            compose.waitUntil(20_000) {
                val lines = diagnosticsAfter(marker)
                diagnosticEvent(lines, "first_frame") &&
                    lines.lineSequence().any { it.contains("event=preview_state streaming=true") }
            }
            lateinit var preview: PreviewView
            compose.runOnIdle {
                preview = scannerViews(compose.activity.window.decorView).filterIsInstance<PreviewView>().single()
                assertNotNull(preview.controller)
                assertEquals(PreviewView.StreamState.STREAMING, preview.previewStreamState.value)
            }
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.go_back)).performClick()
            compose.onNodeWithText("scanner pilot closed").assertIsDisplayed()
            compose.waitUntil(5_000) { diagnosticEvent(diagnosticsAfter(marker), "scanner_dispose") }
            compose.runOnIdle {
                assertNull("Disposed preview must release its controller", preview.controller)
                assertFalse(preview.isAttachedToWindow)
                assertTrue(scannerViews(compose.activity.window.decorView).none { it is PreviewView })
                assertEquals(cycle + 1, backs)
                assertEquals(0, results)
            }
            val lines = diagnosticsAfter(marker)
            assertFalse(lines.contains("event=camera_bind_failed"))
            assertFalse(lines.contains("event=camera_controller_failed"))
            assertFalse(lines.contains("event=frame_scan_failed"))
            assertFalse(lines.contains("event=session_restart_requested"))
            println("SCANNER_PILOT_CYCLE ${cycle + 1}\n$lines")
        }
    }

    @Test fun galleryUsesRealMlKitRejectsForeignQrAndAcceptsSteamQrOnlyOnce() {
        val marker = diagnosticMarker()
        val delivered = CopyOnWriteArrayList<Pair<String, Long?>>()
        val visible = mutableStateOf(true)
        var galleryRequestCode: Int? = null
        var requests = 0
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                check(contract is ActivityResultContracts.GetContent)
                check(input == "image/*")
                requests++
                // Store only the result request; decoding still runs in production code.
                galleryRequestCode = requestCode
            }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        val files = mutableListOf<File>()
        try {
            compose.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    MonicaTheme {
                        Surface(Modifier.fillMaxSize()) {
                            if (visible.value) SteamQrScannerScreen(
                                initialAccountId = null,
                                onQrCodeScanned = { raw, account -> delivered += raw to account },
                                onNavigateBack = { visible.value = false }
                            ) else Text("scanner pilot closed")
                        }
                    }
                }
            }
            compose.waitUntil(5_000) { SteamDiagLogger.exportPersistedLogs(4000).contains(marker) }
            fun select(payload: String) {
                val matrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512)
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                val pixels = IntArray(512 * 512) { i -> if (matrix[i % 512, i / 512]) Color.BLACK else Color.WHITE }
                bitmap.setPixels(pixels, 0, 512, 0, 0, 512, 512)
                val file = File.createTempFile("scanner-pilot-", ".png", compose.activity.cacheDir).also(files::add)
                try { file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
                finally { bitmap.recycle() }
                galleryRequestCode = null
                compose.onNodeWithContentDescription(compose.activity.getString(R.string.steam_qr_album_select)).performClick()
                compose.runOnIdle { registry.dispatchResult<Uri?>(requireNotNull(galleryRequestCode), Uri.fromFile(file)) }
            }
            select("https://example.invalid/not-a-steam-challenge")
            compose.waitUntil(15_000) {
                diagnosticsAfter(marker).lineSequence().any { it.contains("event=gallery_result") && it.contains("invalid=true") }
            }
            compose.runOnIdle { assertTrue(delivered.isEmpty()) }
            val payload = "https://s.team/q/1/123456789"
            select(payload)
            compose.waitUntil(15_000) { delivered.size == 1 }
            compose.runOnIdle { assertEquals(listOf(payload to null), delivered) }
            select(payload)
            compose.waitUntil(15_000) {
                diagnosticsAfter(marker).lineSequence().count { it.contains("event=gallery_result") && it.contains("matched=true") } == 2
            }
            compose.runOnIdle {
                assertEquals(3, requests)
                assertEquals("Consumed scan must not approve twice", listOf(payload to null), delivered)
            }
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.go_back)).performClick()
            compose.onNodeWithText("scanner pilot closed").assertIsDisplayed()
            compose.waitUntil(5_000) { diagnosticEvent(diagnosticsAfter(marker), "scanner_dispose") }
            println("SCANNER_PILOT_GALLERY\n${diagnosticsAfter(marker)}")
        } finally {
            compose.runOnIdle { visible.value = false }
            files.forEach { check(it.delete() || !it.exists()) }
        }
    }

}

/** Run alone after revoking CAMERA and clearing user-set/user-fixed flags for the pilot package. */
class SteamScannerDeniedPermissionTest {
    private val compose = createAndroidComposeRule<SteamComposeFixtureActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(IsolatedScannerRule()).around(compose)

    @Test fun actualPermissionDenialKeepsReturnAvailableAndNeverBindsCamera() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(PackageManager.PERMISSION_DENIED, ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA))
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val previousFlags = automation.serviceInfo.flags
        val marker = diagnosticMarker()
        var backs = 0
        var results = 0
        val visible = mutableStateOf(true)
        try {
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            compose.setContent {
                MonicaTheme {
                    Surface(Modifier.fillMaxSize()) {
                        if (visible.value) SteamQrScannerScreen(
                            initialAccountId = null,
                            onQrCodeScanned = { _, _ -> results++ },
                            onNavigateBack = { backs++; visible.value = false }
                        ) else Text("scanner pilot closed")
                    }
                }
            }
            fun denyButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.isVisibleToUser && node.viewIdResourceName?.endsWith(":id/permission_deny_button") == true) return node
                for (i in 0 until node.childCount) denyButton(node.getChild(i))?.let { return it }
                return null
            }
            val deadline = SystemClock.uptimeMillis() + 10_000
            var denial: AccessibilityNodeInfo? = null
            while (denial == null && SystemClock.uptimeMillis() < deadline) {
                denial = automation.windows.firstNotNullOfOrNull { denyButton(it.root) }
                if (denial == null) Thread.sleep(30)
            }
            assertNotNull("A real CAMERA permission dialog must be shown; clear permission flags before this test", denial)
            assertTrue(requireNotNull(denial).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            compose.onNodeWithText(context.getString(R.string.camera_permission_required)).assertIsDisplayed()
            assertEquals(PackageManager.PERMISSION_DENIED, ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA))
            compose.onNodeWithText(context.getString(R.string.go_back)).performClick()
            compose.onNodeWithText("scanner pilot closed").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(1, backs)
                assertEquals(0, results)
                assertTrue(scannerViews(compose.activity.window.decorView).none { it is PreviewView })
            }
            assertFalse(diagnosticEvent(diagnosticsAfter(marker), "camera_controller_requested"))
            println("SCANNER_PILOT_PERMISSION_DENIED\n${diagnosticsAfter(marker)}")
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = previousFlags }
        }
    }
}
