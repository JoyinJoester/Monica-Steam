package takagi.ru.monica.steam.scanner

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** No application classes or R references: this class can run against the shrunk Release APK. */
class SteamScannerReleaseActivityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private lateinit var targetPackage: String
    private lateinit var resources: Resources

    private fun label(name: String): String {
        val id = resources.getIdentifier(name, "string", targetPackage)
        check(id != 0) { "Missing Release string resource: $name" }
        return resources.getString(id)
    }

    private fun descendants(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = buildList {
        // API 32 can retain the previous Compose tree across an accessibility action.
        if (node != null && node.refresh()) {
            add(node)
            for (i in 0 until node.childCount) addAll(descendants(node.getChild(i)))
        }
    }

    private fun targetNodes(): List<AccessibilityNodeInfo> = automation.windows
        .flatMap { descendants(it.root) }
        .filter { it.packageName?.toString() == targetPackage && it.isVisibleToUser }

    private fun targetLabel(value: String): AccessibilityNodeInfo? = targetNodes().firstOrNull {
        it.text?.toString() == value || it.contentDescription?.toString() == value
    }

    private fun targetText(value: String): AccessibilityNodeInfo? = targetNodes().firstOrNull { it.text?.toString() == value }

    private fun targetDescription(value: String): AccessibilityNodeInfo? = targetNodes().firstOrNull { it.contentDescription?.toString() == value }

    private fun await(what: String, timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(40)
        }
        assertTrue("Timed out: $what; visible=" + targetNodes().mapNotNull {
            it.text?.toString()?.takeIf(String::isNotEmpty) ?: it.contentDescription?.toString()
        }, condition())
    }

    private fun clickTarget(value: String, descriptionOnly: Boolean = false) {
        fun find() = if (descriptionOnly) targetDescription(value) else targetLabel(value)
        fun control(): AccessibilityNodeInfo? {
            var node = find() ?: return null
            while (!node.isClickable) {
                val parent = node.parent ?: break
                if (!parent.refresh() || parent.packageName?.toString() != targetPackage) break
                node = parent
            }
            return node
        }
        await("target control enabled: $value") { control()?.let { it.isClickable && it.isEnabled } == true }
        val node = requireNotNull(control())
        assertTrue("Target control must be enabled", node.isEnabled)
        assertTrue("Target control must handle click", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun report(message: String) {
        instrumentation.sendStatus(2, Bundle().apply { putString("stream", "$message\n") })
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    private fun allowCameraButton(): AccessibilityNodeInfo? = automation.windows
        .flatMap { descendants(it.root) }
        .firstOrNull {
            val provider = it.packageName?.toString()
            it.isVisibleToUser && provider in setOf("com.android.permissioncontroller", "com.google.android.permissioncontroller") &&
                (it.viewIdResourceName?.endsWith(":id/permission_allow_foreground_only_button") == true ||
                    it.viewIdResourceName?.endsWith(":id/permission_allow_button") == true)
        }

    @Test fun coldReleaseUnlocksTokenPageAllowsCameraAndReturnsThroughRealNavigation() {
        targetPackage = requireNotNull(InstrumentationRegistry.getArguments().getString("targetPackage"))
        check(targetPackage in setOf("takagi.ru.monica.steamapp.toolchainpilot"))
        // A standalone host keeps the test runtime out of the optimized application's process.
        check(instrumentation.targetContext.packageName in setOf(targetPackage, "$targetPackage.releasetest"))
        val pm = instrumentation.targetContext.packageManager
        val app = pm.getApplicationInfo(targetPackage, 0)
        check(app.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) { "Requires non-debuggable Release APK" }
        // The isolated Release overlay must remove INTERNET before any app Activity is opened.
        val declaredPermissions = pm.getPackageInfo(targetPackage, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        check(Manifest.permission.INTERNET !in declaredPermissions) { "Synthetic account must be tested in the offline Release overlay" }
        assertEquals(PackageManager.PERMISSION_DENIED, pm.checkPermission(Manifest.permission.CAMERA, targetPackage))
        resources = pm.getResourcesForApplication(targetPackage)
        val oldFlags = automation.serviceInfo.flags
        try {
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            val launch = shell("am start -W --user current -n $targetPackage/takagi.ru.monica.MonicaSteamActivity")
            assertTrue("Real Release Activity must launch", launch.contains("Status: ok"))
            await("startup or navigation", 30_000) {
                targetLabel(label("steam_dock_token")) != null || targetLabel(label("enter_master_password")) != null
            }
            // Startup usually opens Store; the actual token tab activates its password gate.
            if (targetLabel(label("steam_dock_token")) != null) clickTarget(label("steam_dock_token"))
            await("token password gate") { targetLabel(label("enter_master_password")) != null }
            val field = targetNodes().single { it.isEditable }
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "73194628")
            }
            assertTrue("Synthetic password must be entered via actual field", field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments))
            clickTarget(label("unlock"))
            await("seeded token scanner action", 30_000) { targetDescription(label("scan_qr_code")) != null }
            repeat(2) { cycle ->
                clickTarget(label("scan_qr_code"), descriptionOnly = true)
                if (cycle == 0) {
                    await("actual CAMERA allow dialog") { allowCameraButton() != null }
                    assertTrue(requireNotNull(allowCameraButton()).performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    await("CAMERA granted") {
                        pm.checkPermission(Manifest.permission.CAMERA, targetPackage) == PackageManager.PERMISSION_GRANTED
                    }
                }
                await("real scanner header and gallery", 20_000) {
                    targetText(label("scan_qr_code_title")) != null &&
                        targetDescription(label("steam_qr_album_select")) != null &&
                        targetDescription(label("go_back")) != null
                }
                Thread.sleep(2_000)
                assertNotNull("Scanner must stay mounted after native initialization", targetText(label("scan_qr_code_title")))
                clickTarget(label("go_back"), descriptionOnly = true)
                await("token page restored") {
                    targetDescription(label("scan_qr_code")) != null && targetText(label("scan_qr_code_title")) == null
                }
                report("SCANNER_RELEASE_ACTIVITY cycle=${cycle + 1} scanner_visible=true returned=true")
            }
            report("SCANNER_RELEASE_ACTIVITY offline=true debug=false synthetic_account=true app_pid=${shell("pidof $targetPackage").trim()}")
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = oldFlags }
        }
    }
}
