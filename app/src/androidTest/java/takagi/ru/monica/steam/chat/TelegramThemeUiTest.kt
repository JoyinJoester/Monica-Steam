package takagi.ru.monica.steam.chat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monica.telegramchat.TelegramMessageTextView
import com.monica.telegramchat.TelegramEmojiPickerView
import com.monica.telegramchat.TelegramAttachmentPickerView
import com.monica.telegramchat.upstream.DialogCell
import com.monica.telegramchat.upstream.ProfileActionsView
import com.monica.telegramchat.upstream.UserCell
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.data.ColorScheme as AppScheme
import takagi.ru.monica.steam.TelegramChatFixtureActivity
import takagi.ru.monica.steam.TelegramSocialFixtureActivity
import java.io.File

/** Checks pixels and editor colors on the actual source views while the host theme changes. */
@RunWith(AndroidJUnit4::class)
class TelegramThemeUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val modes = listOf(
        Triple(AppScheme.DEFAULT, false, false),
        Triple(AppScheme.FOREST_GREEN, true, false),
        Triple(AppScheme.FOREST_GREEN, false, false),
        Triple(AppScheme.SUNSET_ORANGE, false, false),
        Triple(AppScheme.CUSTOM, true, false),
        Triple(AppScheme.FOREST_GREEN, true, true)
    )

    @Test fun socialNativeColorsFollowLiveThemeAcrossInboxContactsAndProfiles() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TelegramSocialFixtureActivity
        try {
            for ((scheme, dark, oled) in modes) {
                val previous = paletteOnMain { activity.currentPalette }
                instrumentation.runOnMainSync {
                    activity.fixtureColorScheme = scheme; activity.fixtureDark = dark; activity.fixtureOled = oled
                }
                val c = awaitPaletteChange(previous) { activity.currentPalette }
                settle()
                if (oled) assertEquals("OLED theme stays black", android.graphics.Color.BLACK, c.background.toArgb())
                if (scheme == AppScheme.FOREST_GREEN && !oled) savePalette(c, dark)
                for (route in listOf("conversations", "friends", "friend", "group", "emoji", "attachments")) {
                    instrumentation.runOnMainSync { activity.route = route }
                    settle()
                    if (route == "friends" || route == "group") awaitContactRows(activity.window.decorView)
                    val cells = views(activity.window.decorView).filter { it.width > 0 && it.height > 0 }
                    when (route) {
                        "conversations" -> {
                            val row = awaitNative<DialogCell>(activity.window.decorView, first = true)
                            assertRendered(row, c.onSurface.toArgb(), "Inbox text follows theme")
                            assertRendered(row, c.primary.toArgb(), "Unread badge and avatar follow theme")
                            assertRendered(row, c.onPrimary.toArgb(), "Badge foreground follows theme")
                        }
                        "friends", "group" -> {
                            val rows = cells.filterIsInstance<UserCell>()
                            assertTrue("$route/$scheme retains native rows", rows.isNotEmpty())
                            assertRendered(rows.first(), c.onSurface.toArgb(), "Contact text follows theme")
                            assertRendered(rows.first(), c.primary.toArgb(), "Contact avatar follows theme")
                        }
                        "friend" -> assertRendered(awaitNative<ProfileActionsView>(activity.window.decorView),
                            c.onSurface.toArgb(), "Profile action foreground follows theme")
                        "emoji" -> assertRendered(awaitNative<TelegramEmojiPickerView>(activity.window.decorView),
                            c.surfaceContainerHigh.toArgb(), "Emoji picker surface follows theme")
                        "attachments" -> assertRendered(awaitNative<TelegramAttachmentPickerView>(activity.window.decorView),
                            c.surfaceContainerHigh.toArgb(), "Attachment picker surface follows theme")
                    }
                    if (scheme == AppScheme.FOREST_GREEN && !oled) screenshot("$route-${if (dark) "dark" else "light"}")
                }
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun privateAndGroupBubblesAndComposerFollowLiveTheme() {
        for (group in listOf(false, true)) {
            val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, TelegramChatFixtureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("group", group).putExtra("fixtureKey", "theme-$group")) as TelegramChatFixtureActivity
            try {
                for ((scheme, dark, oled) in modes) {
                    val previous = paletteOnMain { activity.currentPalette }
                    instrumentation.runOnMainSync {
                        activity.fixtureColorScheme = scheme; activity.fixtureDark = dark; activity.fixtureOled = oled
                    }
                    val c = awaitPaletteChange(previous) { activity.currentPalette }
                    settle()
                    val decor = activity.window.decorView
                    val messages = { visibleOnMain(decor).filterIsInstance<TelegramMessageTextView>() }
                    eventually("Native message cells present") { messages().size >= 2 }
                    // RecyclerView rows redraw a few frames after the palette changes on this emulator.
                    eventually({ "Incoming bubble follows $scheme/$dark/$oled: expected ${hex(c.surfaceContainerHigh.toArgb())}, drawn ${messages().map(::dominantColors)}" }) {
                        messages().any { bubbleHasColor(it, c.surfaceContainerHigh.toArgb()) }
                    }
                    eventually("Outgoing bubble follows $scheme/$dark/$oled") { messages().any { bubbleHasColor(it, c.primaryContainer.toArgb()) } }
                    eventually("Outgoing foreground follows $scheme/$dark/$oled") { messages().any { hasColor(it, c.onPrimaryContainer.toArgb()) } }
                    screenshot("${if (group) "group" else "private"}-$scheme-$dark-$oled")
                    val cells = visibleOnMain(decor)
                    val editor = cells.filterIsInstance<EditText>().single()
                    assertEquals("Composer text follows theme", c.onSurface.toArgb(), editor.currentTextColor)
                    assertEquals("Composer hint follows theme", c.onSurfaceVariant.toArgb(), editor.currentHintTextColor)
                    if (oled) assertEquals("Chat keeps OLED black", android.graphics.Color.BLACK, c.background.toArgb())
                    if (scheme == AppScheme.FOREST_GREEN && !oled) screenshot("${if (group) "group-chat" else "private-chat"}-${if (dark) "dark" else "light"}")
                }
            } finally { instrumentation.runOnMainSync { activity.finish() } }
        }
    }

    private fun settle() { instrumentation.waitForIdleSync(); SystemClock.sleep(700) }
    private fun paletteOnMain(read: () -> ColorScheme?): ColorScheme? {
        var value: ColorScheme? = null
        instrumentation.runOnMainSync { value = read() }
        return value
    }
    private fun awaitPaletteChange(previous: ColorScheme?, read: () -> ColorScheme?): ColorScheme {
        // The shared API 32 image runs ARM code through translation; full-screen recomposition can exceed 10 s.
        val until = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < until) {
            val value = paletteOnMain(read)
            if (value != null && value !== previous) return value
            SystemClock.sleep(100)
        }
        error("Application theme did not finish changing")
    }
    private fun awaitContactRows(root: View) {
        val until = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < until) {
            if (views(root).any { it is UserCell && it.width > 0 && it.height > 0 }) return
            fun scroll(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                if (node.isScrollable && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
                return (0 until node.childCount).any { scroll(node.getChild(it)) }
            }
            scroll(instrumentation.uiAutomation.rootInActiveWindow)
            settle()
        }
    }
    private inline fun <reified T : View> awaitNative(root: View, first: Boolean = false): T {
        val until = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < until) {
            var found: T? = null
            instrumentation.runOnMainSync {
                val candidates = views(root).filterIsInstance<T>().filter { it.width > 0 && it.height > 0 }
                found = if (first) candidates.firstOrNull() else candidates.singleOrNull()
            }
            found?.let { return it }
            SystemClock.sleep(200)
        }
        error("Missing native ${T::class.java.simpleName}")
    }
    private fun visibleOnMain(root: View): List<View> {
        var result = emptyList<View>()
        instrumentation.runOnMainSync { result = views(root).filter { it.width > 0 && it.height > 0 } }
        return result
    }
    /** Polls an exact check until it holds; the final failure message is the check's label. */
    private fun eventually(label: String, condition: () -> Boolean) = eventually({ label }, condition)
    private fun eventually(label: () -> String, condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 20000
        while (SystemClock.uptimeMillis() < until) {
            if (condition()) return
            SystemClock.sleep(250)
        }
        if (!condition()) throw AssertionError(label())
    }
    private fun hex(color: Int) = "#%08X".format(color)
    /** The text view draws only text; its message cell (at most three parents up) draws the bubble. */
    private fun bubbleHasColor(text: View, color: Int): Boolean =
        generateSequence(text) { it.parent as? View }.take(4).any { hasColor(it, color) }
    /** The three most frequent opaque colours a view draws, for diagnosing palette mismatches. */
    private fun dominantColors(view: View): List<String> {
        var result = emptyList<String>()
        instrumentation.runOnMainSync {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle()
            result = pixels.filter { (it ushr 24) == 0xFF }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.take(3).map { "${hex(it.key)}x${it.value}" }
        }
        return result
    }
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun assertRendered(view: View, color: Int, label: String) = assertTrue(label, hasColor(view, color))
    private fun hasColor(view: View, color: Int): Boolean {
        var found = false
        instrumentation.runOnMainSync {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            found = pixels.count { it == color } >= 10
            bitmap.recycle()
        }
        return found
    }
    private fun evidence() = File(instrumentation.targetContext.getExternalFilesDir(null), "telegram-theme-evidence").apply { mkdirs() }
    private fun screenshot(name: String) {
        val bitmap = captureTelegramUiEvidence(instrumentation)
        File(evidence(), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun savePalette(c: ColorScheme, dark: Boolean) {
        val values = mapOf("primary" to c.primary, "onPrimary" to c.onPrimary, "primaryContainer" to c.primaryContainer,
            "onPrimaryContainer" to c.onPrimaryContainer, "secondary" to c.secondary, "onSecondary" to c.onSecondary,
            "secondaryContainer" to c.secondaryContainer, "onSecondaryContainer" to c.onSecondaryContainer,
            "tertiary" to c.tertiary, "onTertiary" to c.onTertiary, "tertiaryContainer" to c.tertiaryContainer, "onTertiaryContainer" to c.onTertiaryContainer,
            "background" to c.background, "onBackground" to c.onBackground, "surface" to c.surface, "onSurface" to c.onSurface,
            "surfaceContainerLow" to c.surfaceContainerLow, "surfaceContainer" to c.surfaceContainer,
            "surfaceContainerHigh" to c.surfaceContainerHigh, "surfaceContainerHighest" to c.surfaceContainerHighest,
            "onSurfaceVariant" to c.onSurfaceVariant, "outline" to c.outline, "outlineVariant" to c.outlineVariant,
            "inversePrimary" to c.inversePrimary, "inverseSurface" to c.inverseSurface, "inverseOnSurface" to c.inverseOnSurface,
            "error" to c.error, "onError" to c.onError, "errorContainer" to c.errorContainer, "onErrorContainer" to c.onErrorContainer)
        val json = org.json.JSONObject()
        values.forEach { (key, value) -> json.put(key, "#%06X".format(value.toArgb() and 0xffffff)) }
        File(evidence(), "theme-${if (dark) "dark" else "light"}.json").writeText(json.toString(2))
    }
}
