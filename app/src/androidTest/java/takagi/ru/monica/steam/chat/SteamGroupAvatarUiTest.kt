package takagi.ru.monica.steam.chat

import android.content.Intent
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monica.telegramchat.SteamAvatarView
import com.monica.telegramchat.upstream.DialogCell
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.steam.TelegramSocialFixtureActivity
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.groupchat.avatar.ui.SteamGroupAvatarImage
import takagi.ru.monica.steam.friends.groupchat.avatar.ui.rememberSteamGroupAvatarBitmap
import takagi.ru.monica.ui.theme.MonicaTheme

@RunWith(AndroidJUnit4::class)
class SteamGroupAvatarUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var activity: TelegramSocialFixtureActivity? = null
    private var originalAutomationFlags = 0
    @Before fun includeDialogWindows() {
        val info = instrumentation.uiAutomation.serviceInfo
        originalAutomationFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = info
    }
    private fun members(count: Int) = List(count) { index ->
        SteamFriend("local-$index", SteamFriendRelationship.FRIEND, personaName = listOf("小林", "明月", "国风", "游戏", "额外")[index % 5])
    }
    @After fun close() {
        instrumentation.runOnMainSync { activity?.finish() }
        val info = instrumentation.uiAutomation.serviceInfo
        info.flags = originalAutomationFlags
        instrumentation.uiAutomation.serviceInfo = info
    }
    private fun launch(dark: Boolean = true) {
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("route", "conversations").putExtra("dark", dark)) as TelegramSocialFixtureActivity
        instrumentation.waitForIdleSync()
    }
    private fun await(check: () -> Boolean) {
        val until = android.os.SystemClock.uptimeMillis() + 10_000
        while (android.os.SystemClock.uptimeMillis() < until) {
            instrumentation.waitForIdleSync()
            if (check()) return
            Thread.sleep(80)
        }
        assertTrue("Timed out waiting for avatar", check())
    }
    private fun save(name: String) {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "group-avatar-shots").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    private fun clickLabel(label: String) {
        fun find(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
            if (node.isVisibleToUser && (node.text?.toString() == label || node.contentDescription?.toString() == label)) return node
            return (0 until node.childCount).firstNotNullOfOrNull { node.getChild(it)?.let(::find) }
        }
        var target = instrumentation.uiAutomation.windows.mapNotNull { it.root }.firstNotNullOfOrNull(::find)
            ?: instrumentation.uiAutomation.rootInActiveWindow?.let(::find)
        while (target != null && !target.isClickable) target = target.parent
        assertTrue("$label is clickable", requireNotNull(target).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    @Test fun cjkAvatarsRenderAt40_56_88DpInBothThemesAndLargeType() {
        launch()
        for (dark in listOf(true, false)) {
            val ready = AtomicReference(false)
            instrumentation.runOnMainSync {
                activity!!.setContent {
                    MonicaTheme(darkTheme = dark) {
                        val density = LocalDensity.current.density
                        CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.5f)) {
                            Surface(Modifier.fillMaxSize()) {
                                Column(Modifier.statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    for (count in 1..4) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Text("$count", modifier = Modifier.width(22.dp))
                                        for (size in listOf(40, 56, 88)) SteamGroupAvatarImage("", members(count), "group-$count-$size", Modifier.size(size.dp))
                                    }
                                    SideEffect { ready.set(true) }
                                }
                            }
                        }
                    }
                }
            }
            await { ready.get() }
            Thread.sleep(300)
            save(if (dark) "cjk-matrix-dark-large" else "cjk-matrix-light-large")
        }
    }

    @Test fun memberRenameRefreshesBitmapWithoutAnAvatarUrlChange() {
        launch()
        val current = AtomicReference<Bitmap?>()
        val state = mutableStateOf(members(4))
        instrumentation.runOnMainSync {
            activity!!.setContent {
                MonicaTheme {
                    val bitmap = rememberSteamGroupAvatarBitmap(state.value, "group", 56.dp)
                    SideEffect { current.set(bitmap) }
                }
            }
        }
        await { current.get() != null }
        val before = current.get()!!
        instrumentation.runOnMainSync { state.value = state.value.map { it.copy(personaName = "W") } }
        await { current.get() !== before }
        assertFalse("A display-name-only change changes the initials", before.sameAs(current.get()!!))
    }

    @Test fun membersBeyondTheFourVisibleTilesDoNotChangeTheBitmap() {
        launch()
        val four = AtomicReference<Bitmap?>()
        val five = AtomicReference<Bitmap?>()
        instrumentation.runOnMainSync {
            activity!!.setContent {
                MonicaTheme {
                    val first = rememberSteamGroupAvatarBitmap(members(4), "group", 56.dp)
                    val second = rememberSteamGroupAvatarBitmap(members(5), "group", 56.dp)
                    SideEffect { four.set(first); five.set(second) }
                }
            }
        }
        await { four.get() != null && five.get() != null }
        assertTrue("Only the four visible members are rendered", four.get()!!.sameAs(five.get()!!))
    }

    @Test fun presenceUpdatesKeepTheExistingCompositeBitmap() {
        launch()
        val current = AtomicReference<Bitmap?>()
        val source = mutableStateOf(members(4))
        var revision = 0
        instrumentation.runOnMainSync {
            activity!!.setContent {
                MonicaTheme {
                    val bitmap = rememberSteamGroupAvatarBitmap(source.value, "group", 56.dp)
                    SideEffect { current.set(bitmap); revision++ }
                }
            }
        }
        await { current.get() != null }
        val before = current.get()
        val initialRevision = revision
        instrumentation.runOnMainSync { source.value = source.value.map { it.copy(gameName = "Portal 2") } }
        await { revision > initialRevision }
        assertSame("Online/game state is not part of avatar identity", before, current.get())
    }

    @Test fun nativeInboxAvatarUsesItsOwnSlotAndDockAndUnreadRemainVisible() {
        for (dark in listOf(true, false)) {
            launch(dark)
            Thread.sleep(500)
            save(if (dark) "inbox-final-dark" else "inbox-final-light")
            // This fixture deliberately archives the group; it is absent from the All page.
            clickLabel("归档")
            Thread.sleep(300)
            instrumentation.runOnMainSync {
                fun find(view: View): DialogCell? {
                    if (view is DialogCell && view.contentDescription?.toString()?.startsWith("Monica 游戏小组,") == true) return view
                    if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
                    return null
                }
                val row = requireNotNull(find(activity!!.window.decorView))
                val avatar = (0 until row.childCount).map(row::getChildAt).filterIsInstance<SteamAvatarView>().single()
                val density = activity!!.resources.displayMetrics.density
                assertEquals(56*density, avatar.width.toFloat(), 1f)
                assertEquals(10*density, avatar.left.toFloat(), 1f)
                assertEquals(10*density, avatar.top.toFloat(), 1f)
                val bitmap = Bitmap.createBitmap(avatar.width, avatar.height, Bitmap.Config.ARGB_8888)
                avatar.draw(Canvas(bitmap))
                assertEquals("Native avatar corner stays transparent", 0, android.graphics.Color.alpha(bitmap.getPixel(0,0)))
                assertEquals("Group disc is opaque",255, android.graphics.Color.alpha(bitmap.getPixel(avatar.width/2, avatar.height/2)))
                bitmap.recycle()
            }
            val root = requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
            fun has(view: android.view.accessibility.AccessibilityNodeInfo, label: String): Boolean =
                // The glass Dock renders a visual layer above alpha-zero input targets. Its
                // semantic target can be flagged invisible by UiAutomation despite visible ink.
                ((view.text?.toString() == label || view.contentDescription?.toString() == label) &&
                    android.graphics.Rect().let { view.getBoundsInScreen(it); !it.isEmpty }) ||
                    (0 until view.childCount).any { index -> view.getChild(index)?.let { has(it,label) } == true }
            listOf("商店", "游戏库", "聊天", "令牌", "设置", "未读", "归档").forEach { assertTrue("$it remains visible",has(root,it)) }
            save(if (dark) "inbox-archive-final-dark" else "inbox-archive-final-light")
            instrumentation.runOnMainSync { activity!!.finish() }
        }
    }
}
