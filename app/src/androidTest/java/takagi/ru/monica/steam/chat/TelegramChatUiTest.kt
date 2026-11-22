package takagi.ru.monica.steam.chat

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.view.accessibility.AccessibilityNodeInfo
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.steam.TelegramChatFixtureActivity
import java.io.File
import java.util.UUID

/** Real private/group surfaces, using offline callbacks and the compiled native controls. */
@RunWith(AndroidJUnit4::class)
class TelegramChatUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var activity: TelegramChatFixtureActivity? = null

    @After fun close() { instrumentation.runOnMainSync { activity?.finish() } }

    @Test fun directNativeEditorSendsKeepsImeAndRetries() {
        launch()
        screenshot("private-dark")
        val retry = instrumentation.targetContext.getString(takagi.ru.monica.R.string.steam_chat_retry_send)
        clickAccessible(retry)
        await { activity!!.retries == 1 }
        onView(editor()).perform(click(), replaceText("Native Steam message"))
        await { activity!!.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
        val input = editText()
        // IME visibility changes before its insets animation settles.
        await {
            val location = IntArray(2); input.getLocationOnScreen(location)
            val ime = activity!!.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
            location[1] + input.height <= activity!!.window.decorView.height - ime + 2
        }
        instrumentation.runOnMainSync {
            val location = IntArray(2); input.getLocationOnScreen(location)
            val ime = activity!!.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
            assertTrue("Editor must stay above IME", location[1] + input.height <= activity!!.window.decorView.height - ime + 2)
        }
        screenshot("private-ime")
        onView(withContentDescription("Send")).perform(click())
        await { activity!!.sent == listOf("Native Steam message") && input.text.isEmpty() }
        assertTrue("Sending retains native focus", input.hasFocus())
        onView(withContentDescription(startsWith("Native Steam message,"))).perform(longClick())
        screenshot("private-message-actions")
        clickAccessible(instrumentation.targetContext.getString(takagi.ru.monica.R.string.steam_chat_action_copy))
        await {
            val clipboard = activity!!.getSystemService(android.content.ClipboardManager::class.java)
            clipboard.primaryClip?.getItemAt(0)?.text?.toString() == "Native Steam message"
        }
    }

    @Test fun groupNativeEditorPreservesDraftThroughPanelsAndChannelNavigation() = withFailureEvidence("group-channel") {
        launch(group = true)
        screenshot("group-dark")
        onView(editor()).perform(replaceText("Draft remains"))
        onView(withContentDescription("Emoji and stickers")).perform(click())
        screenshot("group-picker")
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        await { editText().text.toString() == "Draft remains" }
        onView(withContentDescription("Send")).perform(click())
        await { activity!!.sent == listOf("Draft remains") }
        onView(withContentDescription("Attach")).perform(click())
        screenshot("group-attachment")
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        // Native Espresso does not wait for the Compose AnimatedVisibility exit. The expanding
        // panel can cover this channel on a short landscape window until its native view detaches.
        await {
            val views = nativeViews()
            val panel = views.any { it is com.monica.telegramchat.TelegramAttachmentPickerView && it.isAttachedToWindow }
            val channel = views.filterIsInstance<android.widget.TextView>().firstOrNull { it.text.toString() == "# 联机组队" }
            val composer = views.filterIsInstance<com.monica.telegramchat.TelegramChatComposerView>().firstOrNull()
            val channelBounds = android.graphics.Rect()
            val composerBounds = android.graphics.Rect()
            !panel && channel != null && composer != null &&
                channel.getGlobalVisibleRect(channelBounds) && composer.getGlobalVisibleRect(composerBounds) &&
                channelBounds.height() == channel.height && composerBounds.top >= channelBounds.bottom
        }
        screenshot("group-channel-before-tap")
        onView(withText("# 联机组队")).perform(click())
        screenshot("group-channel-after-tap")
        await { activity!!.selectedRoom == "team" }
    }

    @Test fun lightThemeNativeTitleAndLongHistoryRemainInteractive() {
        launch(dark = false, longHistory = true)
        screenshot("private-light")
        onView(withContentDescription("小林")).perform(click())
        await { activity!!.infoOpened }
        onView(allOf(withContentDescription("More options"),
            isDescendantOfA(isAssignableFrom(com.monica.telegramchat.TelegramChatHeaderView::class.java)))).perform(click())
        screenshot("private-toolbar-menu")
        clickAccessible(instrumentation.targetContext.getString(takagi.ru.monica.R.string.refresh))
        await { activity!!.refreshes == 1 }
        onView(editor()).perform(replaceText("Tail after history"))
        onView(withContentDescription("Send")).perform(click())
        await { activity!!.sent.lastOrNull() == "Tail after history" }
        screenshot("private-light-sent")
        onView(withContentDescription("Attach")).perform(click())
        screenshot("private-light-attachment")
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
    }

    @Test fun olderReadingAnchorSurvivesImeAndReopening() {
        val fixtureKey = UUID.randomUUID().toString()
        launch(longHistory = true, fixtureKey = fixtureKey)
        fun recycler(): androidx.recyclerview.widget.RecyclerView {
            fun find(view: View): androidx.recyclerview.widget.RecyclerView? = if (view is androidx.recyclerview.widget.RecyclerView) view
                else if (view is ViewGroup) (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) } else null
            return requireNotNull(find(activity!!.window.decorView))
        }
        val list = recycler()
        val manager = list.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
        instrumentation.runOnMainSync { manager.scrollToPositionWithOffset(20, -25) }
        await { manager.findFirstVisibleItemPosition() == 20 }
        onView(editor()).perform(click())
        await { activity!!.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
        SystemClock.sleep(400)
        assertEquals("IME resize preserves the older row", 20, manager.findFirstVisibleItemPosition())
        val offset = list.paddingTop - requireNotNull(manager.findViewByPosition(20)).top
        instrumentation.runOnMainSync { activity!!.finish() }
        instrumentation.waitForIdleSync()
        launch(longHistory = true, fixtureKey = fixtureKey)
        await { (recycler().layoutManager as androidx.recyclerview.widget.LinearLayoutManager).findFirstVisibleItemPosition() == 20 }
        val restored = recycler()
        assertEquals("Reopening preserves the row's pixel offset", offset,
            restored.paddingTop - requireNotNull(restored.layoutManager?.findViewByPosition(20)).top)
        screenshot("private-restored-anchor")
    }

    @Test fun sourceAttachmentPanelAndCancelableDocumentProviderPreserveDraft() {
        launch(group = true)
        onView(editor()).perform(replaceText("Attachment draft"))
        val launches = java.util.concurrent.CopyOnWriteArrayList<Intent>()
        val monitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): android.app.Instrumentation.ActivityResult? {
                if (intent.action == Intent.ACTION_OPEN_DOCUMENT || intent.action?.contains("PICK_IMAGES") == true) {
                    launches += Intent(intent)
                    return android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null)
                }
                return null
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            onView(withContentDescription("Attach")).perform(click())
            val gallery = instrumentation.targetContext.getString(takagi.ru.monica.R.string.steam_chat_attachment_gallery)
            onView(allOf(withContentDescription(startsWith("$gallery,")), isDisplayed())).perform(click())
            assertTrue("Gallery stays inside the source panel", launches.isEmpty())
            val file = instrumentation.targetContext.getString(takagi.ru.monica.R.string.steam_chat_attachment_file)
            await { nativeViews().any { view ->
                val bounds = android.graphics.Rect()
                view.contentDescription?.toString()?.startsWith("$file,") == true &&
                    view.getGlobalVisibleRect(bounds) && bounds.height() == view.height
            } }
            onView(allOf(withContentDescription(startsWith("$file,")), isDisplayed())).perform(click())
            onView(withContentDescription("更多附件选项")).perform(click())
            clickAccessible("系统文件")
            await { launches.size == 1 && editText().isShown }
            assertEquals("Attachment draft", editText().text.toString())
            assertTrue(activity!!.sent.isEmpty())
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, launches.last().action)
            val mimeTypes = launches.last().getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
            assertNotNull("System picker declares supported media types", mimeTypes)
            assertEquals(
                "System picker permits only the Steam chat media formats and their MIME aliases",
                setOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/avif",
                    "video/webm", "video/mpeg", "video/mp4", "video/ogg",
                    "image/jpg", "image/pjpeg", "image/x-png", "video/x-webm"),
                mimeTypes!!.toSet()
            )
            assertFalse("ZIP is not a Steam chat media type", "application/zip" in mimeTypes)
            assertFalse("The picker does not broaden the media allowlist", mimeTypes.any {
                it == "application/octet-stream" || '*' in it
            })
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun rapidDraftChangesSettleOriginalSendAnimationWithoutDuplicateActions() {
        launch()
        val input = editText()
        instrumentation.runOnMainSync {
            repeat(15) { input.setText("rapid $it"); input.setText("") }
            input.setText("Settled send")
        }
        SystemClock.sleep(350)
        onView(withContentDescription("Send")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()))
        onView(withContentDescription("Send")).perform(click())
        await { activity!!.sent == listOf("Settled send") && input.text.isEmpty() }
        SystemClock.sleep(250)
        onView(allOf(withContentDescription("More options"), isDescendantOfA(
            isAssignableFrom(com.monica.telegramchat.TelegramChatComposerView::class.java))))
            .check(androidx.test.espresso.assertion.ViewAssertions.matches(org.hamcrest.Matchers.not(isDisplayed())))
        onView(withContentDescription("Attach")).perform(click())
        screenshot("private-native-attachment")
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        assertEquals(listOf("Settled send"), activity!!.sent)
    }

    private fun editor() = allOf(isAssignableFrom(EditText::class.java), isDisplayed())
    private fun clickAccessible(description: String) {
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.contentDescription?.toString() == description || node.text?.toString() == description) return node
            return (0 until node.childCount).firstNotNullOfOrNull { find(node.getChild(it)) }
        }
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            val node = find(automation.rootInActiveWindow)
                ?: automation.windows.firstNotNullOfOrNull { find(it.root) }
            if (node != null) {
                var target: AccessibilityNodeInfo? = node
                while (target != null && !target.isClickable) target = target.parent
                assertTrue("Accessibility click: $description", target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
                return
            }
            SystemClock.sleep(100)
        }
        fail("Missing accessibility action: $description")
    }
    private fun editText(): EditText {
        fun find(view: View): EditText? = if (view is EditText) view else if (view is ViewGroup)
            (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) } else null
        return find(activity!!.window.decorView) ?: error("No native editor")
    }
    private fun nativeViews(): List<View> = buildList {
        fun collect(view: View) {
            add(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) collect(view.getChildAt(index))
        }
        collect(requireNotNull(activity).window.decorView)
    }
    private fun withFailureEvidence(name: String, block: () -> Unit) {
        try {
            block()
        } catch (failure: Throwable) {
            val evidenceName = "failure-$name-${SystemClock.uptimeMillis()}"
            runCatching {
                val dump = StringBuilder()
                instrumentation.runOnMainSync {
                    dump.append("selectedRoom=").append(activity?.selectedRoom)
                        .append(" sent=").append(activity?.sent)
                        .append(" windowFocus=").append(activity?.hasWindowFocus()).append('\n')
                    nativeViews().filter { it is android.widget.TextView ||
                        it is com.monica.telegramchat.TelegramChatComposerView ||
                        it is com.monica.telegramchat.TelegramAttachmentPickerView }.forEach { view ->
                        val bounds = android.graphics.Rect()
                        view.getGlobalVisibleRect(bounds)
                        dump.append(view.javaClass.simpleName)
                            .append(" text=").append((view as? android.widget.TextView)?.text)
                            .append(" bounds=").append(bounds)
                            .append(" shown=").append(view.isShown)
                            .append(" attached=").append(view.isAttachedToWindow)
                            .append(" clickable=").append(view.isClickable)
                            .append(" focused=").append(view.hasFocus()).append('\n')
                    }
                }
                File(evidenceDirectory(), "$evidenceName.txt").writeText(dump.toString())
            }.exceptionOrNull()?.let(failure::addSuppressed)
            runCatching { screenshot(evidenceName) }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }
    private fun launch(group: Boolean = false, dark: Boolean = true, longHistory: Boolean = false, fixtureKey: String = UUID.randomUUID().toString()) {
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, TelegramChatFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("group", group).putExtra("dark", dark)
            .putExtra("long", longHistory).putExtra("fixtureKey", fixtureKey)) as TelegramChatFixtureActivity
        await { runCatching { editText().isShown }.getOrDefault(false) }
        instrumentation.waitForIdleSync()
    }
    private fun await(condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < until) {
            var success = false
            instrumentation.runOnMainSync { success = condition() }
            if (success) return
            SystemClock.sleep(100)
        }
        fail("Timed out waiting for native chat state")
    }
    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
        val bitmap = captureTelegramUiEvidence(instrumentation)
        File(evidenceDirectory(), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun evidenceDirectory(): File {
        val base = if (InstrumentationRegistry.getArguments().getString("internalEvidence") == "true")
            instrumentation.targetContext.filesDir else instrumentation.targetContext.getExternalFilesDir(null)
        return File(base, "telegram-ui-evidence").apply { mkdirs() }
    }
}
