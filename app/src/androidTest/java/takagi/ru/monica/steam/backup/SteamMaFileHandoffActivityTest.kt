package takagi.ru.monica.steam.backup

import android.os.Bundle
import android.os.SystemClock
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.importer.SteamMaFileParser

/** Opt-in, disposable host fixture. Fails closed outside the isolated recovery-test application. */
class SteamMaFileHandoffActivityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val automation get() = instrumentation.uiAutomation
    private var prepared = false
    private lateinit var security: SecurityManager

    @Before fun prepare() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("monicaHandoffHost") == "true")
        check(context.packageName == "takagi.ru.monica.steam.recoverytest")
        val host = context.packageManager.getApplicationInfo(STEAM, 0)
        check(context.packageManager.getApplicationLabel(host).toString() == "Monica maFile test host")
        automation.serviceInfo = automation.serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            packageNames = null
        }
        shell("input keyevent 224")
        shell("wm dismiss-keyguard")
        shell("am force-stop $STEAM")
        security = SecurityManager(context)
        prepared = true
        security.clearSecurityData()
        SteamDatabase.getDatabase(context).steamAccountDao().deleteAll()
        security.setMasterPassword(PASSWORD)
        val repository = SteamAccountRepository(SteamDatabase.getDatabase(context).steamAccountDao(), security)
        repository.upsertFromMaFile(SteamMaFileParser().parse("""
            {"steamid":"76561198000000001","account_name":"handoff-fixture",
             "device_id":"android:synthetic","shared_secret":"MTIzNDU2Nzg5MDEyMzQ1Njc4OTA="}
        """.trimIndent()))
    }

    @After fun cleanup() = runBlocking<Unit> {
        if (prepared) {
            shell("am force-stop $STEAM")
            SteamDatabase.getDatabase(context).steamAccountDao().deleteAll()
            security.clearSecurityData()
        }
    }

    @Test fun authenticatedSelectionDeliversOnceAndNextRequestRequiresFreshAuth() {
        launchHost()
        authenticate()
        clickText("handoff-fixture")
        click(R.string.steam_handoff_confirm)
        await { text("RECEIVED_ONCE_REPLAY_DENIED") }
        shell("am force-stop $STEAM")
        launchHost()
        click(R.string.steam_handoff_unlock)
        await { editable() }
        assertNull(text("handoff-fixture"))
        click(R.string.cancel)
        await { text("REQUEST_CANCELLED") }
    }

    @Test fun wrongPasswordAndCancelReturnNoPayload() {
        launchHost()
        click(R.string.steam_handoff_unlock)
        inputPassword("incorrect fixture password")
        click(R.string.start_export)
        await { text(context.getString(R.string.current_password_incorrect)) }
        shell("input keyevent 4")
        await { text("REQUEST_CANCELLED") }
    }

    @Test fun backgroundingAfterAuthenticationInvalidatesConsent() {
        launchHost()
        authenticate()
        shell("input keyevent 3")
        await { automation.rootInActiveWindow?.takeIf {
            it.packageName?.toString()?.let { name -> name != context.packageName && name != STEAM } == true
        } }
        shell("am start --activity-reorder-to-front -n $STEAM/com.monica.handofftest.HostActivity")
        await { text("REQUEST_CANCELLED") }
    }

    @Test fun missingMasterPasswordDisablesUnlock() {
        security.clearSecurityData()
        launchHost()
        await { text(context.getString(R.string.steam_handoff_password_required)) }
        val label = await { text(context.getString(R.string.steam_handoff_unlock)) }
        var node: AccessibilityNodeInfo? = label
        while (node != null && !node.isClickable) node = node.parent
        assertFalse(node?.isEnabled ?: false)
        click(R.string.cancel)
        await { text("REQUEST_CANCELLED") }
    }

    @Test fun invalidProtocolIsRejectedBeforeUnlock() {
        shell("am start -n $STEAM/com.monica.handofftest.HostActivity --ez invalidVersion true")
        await { text("REQUEST_CANCELLED") }
        assertNull(editable())
    }

    private fun launchHost() {
        shell("am start -n $STEAM/com.monica.handofftest.HostActivity")
        await { text(context.getString(R.string.steam_handoff_unlock)) }
    }

    private fun authenticate() {
        click(R.string.steam_handoff_unlock)
        inputPassword(PASSWORD)
        click(R.string.start_export)
        await { text("handoff-fixture") }
    }

    private fun inputPassword(value: String) {
        val node = await { editable() }
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
    }

    private fun click(id: Int) = clickText(context.getString(id))
    private fun clickText(value: String) {
        var node: AccessibilityNodeInfo? = await { text(value) }
        while (node != null && !node.isClickable) node = node.parent
        assertTrue(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
    }

    private fun text(value: String) = walk(automation.rootInActiveWindow) { it.text?.toString() == value }
    private fun editable() = walk(automation.rootInActiveWindow) { it.isEditable }
    private fun walk(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (index in 0 until node.childCount) walk(node.getChild(index), predicate)?.let { return it }
        return null
    }

    private fun <T : Any> await(read: () -> T?): T {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            read()?.let { return it }
            SystemClock.sleep(100)
        }
        error("Expected handoff state was not reached; root=${automation.rootInActiveWindow?.packageName}, windows=${automation.windows.size}")
    }

    private fun shell(command: String) {
        automation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }

    companion object {
        private const val STEAM = "com.valvesoftware.android.steam.community"
        private const val PASSWORD = "synthetic handoff password"
    }
}
