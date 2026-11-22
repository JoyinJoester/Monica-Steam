package takagi.ru.monica.steam.chat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monica.telegramchat.TelegramMessageTextView
import com.monica.telegramchat.TelegramWallpaperDrawable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.R
import takagi.ru.monica.steam.TelegramChatFixtureActivity
import takagi.ru.monica.steam.TelegramSocialFixtureActivity
import takagi.ru.monica.steam.friends.chat.info.data.SteamChatInfoPreferencesStore
import takagi.ru.monica.steam.friends.chat.info.domain.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class TelegramWallpaperUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    /** R8 drops the enum's synthetic entries accessor from the app APK. */
    private val presets = listOf(SteamChatWallpaperPreset.TELEGRAM_PATTERN, SteamChatWallpaperPreset.TELEGRAM_SOFT,
        SteamChatWallpaperPreset.THEME_PLAIN)

    @Test fun pickerConfirmsOrCancelsAndEncryptedPreferencesSurviveReload() {
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("route", "group")) as TelegramSocialFixtureActivity
        val original = SteamChatConversationPreferences(muted = true, pinned = true)
        val id = SteamChatConversationId("wallpaper-ui-fixture", SteamChatConversationType.GROUP, "wallpaper-group", "general")
        val store = SteamChatInfoPreferencesStore(activity)
        try {
            instrumentation.runOnMainSync { activity.preferences = original }
            click(activity.getString(R.string.steam_chat_wallpaper_title))
            click(activity.getString(R.string.steam_chat_wallpaper_soft))
            screenshot("picker-soft")
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            settle()
            assertEquals("Dismiss keeps original choice", original, activity.preferences)
            click(activity.getString(R.string.steam_chat_wallpaper_title))
            click(activity.getString(R.string.steam_chat_wallpaper_plain))
            click(activity.getString(R.string.steam_chat_wallpaper_apply))
            settle()
            val selected = original.copy(wallpaper = SteamChatWallpaperPreset.THEME_PLAIN)
            assertEquals("Apply keeps mute and pin", selected, activity.preferences)
            store.save(id, activity.preferences)
            assertEquals("New store reloads saved choice", selected, SteamChatInfoPreferencesStore(activity).load(id))
            assertEquals("Other conversation is isolated", SteamChatConversationPreferences(), store.load(SteamChatConversationId("wallpaper-ui-fixture", SteamChatConversationType.GROUP, "other", "general")))
            instrumentation.runOnMainSync { activity.route = "conversations" }
            settle()
            instrumentation.runOnMainSync { activity.preferences = store.load(id); activity.route = "group" }
            click(activity.getString(R.string.steam_chat_wallpaper_title))
            val choice = find(activity.getString(R.string.steam_chat_wallpaper_plain))!!
            fun selected(node: AccessibilityNodeInfo): Boolean = node.isChecked || node.isSelected ||
                (0 until node.childCount).any { node.getChild(it)?.let(::selected) == true }
            assertTrue("Reopened picker retains saved selection", selected(choice.parent ?: choice))
            screenshot("picker-saved-plain")
        } finally {
            store.save(id, SteamChatConversationPreferences())
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun bothChatTypesRenderOriginalSoftAndPlainWithoutChangingMessageCells() {
        for (group in listOf(false, true)) {
            val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, TelegramChatFixtureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("group", group).putExtra("theme", "FOREST_GREEN")
                .putExtra("fixtureKey", "wallpaper-$group")) as TelegramChatFixtureActivity
            try {
                val renders = mutableMapOf<SteamChatWallpaperPreset, IntArray>()
                for (preset in presets) {
                    instrumentation.runOnMainSync { activity.fixtureWallpaper = preset }
                    settle()
                    instrumentation.runOnMainSync {
                        val all = views(activity.window.decorView).filter { it.width > 0 && it.height > 0 }
                        assertTrue("Native message cells retained", all.count { it is TelegramMessageTextView } >= 2)
                        val wallpaper = all.first { it.background is TelegramWallpaperDrawable && it !is ViewGroup }
                        val bitmap = Bitmap.createBitmap(wallpaper.width, wallpaper.height, Bitmap.Config.ARGB_8888)
                        wallpaper.draw(Canvas(bitmap))
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        bitmap.recycle()
                        renders[preset] = pixels
                        val background = activity.currentPalette!!.background.toArgb()
                        if (preset == SteamChatWallpaperPreset.THEME_PLAIN) {
                            assertTrue("Plain uses exact theme background", pixels.all { it == background })
                        } else assertTrue("Original SVG has visible theme tinted strokes", pixels.count { it != background } > 100)
                    }
                    screenshot("${if (group) "group" else "private"}-$preset")
                }
                assertFalse("Soft visibly differs from original", renders.getValue(SteamChatWallpaperPreset.TELEGRAM_PATTERN)
                    .contentEquals(renders.getValue(SteamChatWallpaperPreset.TELEGRAM_SOFT)))
            } finally { instrumentation.runOnMainSync { activity.finish() } }
        }
    }

    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()

    private fun find(label: String): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
            return (0 until node.childCount).firstNotNullOfOrNull { visit(node.getChild(it)) }
        }
        val automation = instrumentation.uiAutomation
        return visit(automation.rootInActiveWindow) ?: automation.windows.firstNotNullOfOrNull { visit(it.root) }
    }

    private fun click(label: String) {
        val until = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < until) {
            var node = find(label)
            while (node != null && !node.isClickable) node = node.parent
            if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { settle(); return }
            fun scroll(node: AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
                return (0 until node.childCount).any { scroll(node.getChild(it)) }
            }
            scroll(instrumentation.uiAutomation.rootInActiveWindow)
            settle()
        }
        error("Missing wallpaper action: $label")
    }

    private fun settle() { instrumentation.waitForIdleSync(); SystemClock.sleep(700) }
    private fun screenshot(name: String) {
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "telegram-wallpaper-evidence").apply { mkdirs() }
        val bitmap = captureTelegramUiEvidence(instrumentation)
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
