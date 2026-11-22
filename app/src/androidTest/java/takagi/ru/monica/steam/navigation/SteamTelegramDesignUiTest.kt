package takagi.ru.monica.steam.navigation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.ComposeView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monica.telegramchat.TelegramEditText
import com.monica.telegramchat.TelegramGlassView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.steam.SteamTelegramDesignFixtureActivity

/** Exercises retained chat controls with offline, disposable Activity state only. */
@RunWith(AndroidJUnit4::class)
class SteamTelegramDesignUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun nativeSearchPreservesComposingSelectionAndSubmitsThroughSharedTopBar() = withFixture { activity ->
        main { activity.searchExpanded = true }
        val editor = awaitEditor(activity)
        await { mainValue { editor.hasFocus() } }
        // Wait for the real IME connection before injecting a synthetic composing region.
        // A late IME startInput legitimately clears spans created before that connection.
        await { mainValue {
            editor.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true &&
                (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as
                    android.view.inputmethod.InputMethodManager).isActive(editor)
        } }
        instrumentation.waitForIdleSync()
        main {
            val connection = requireNotNull(editor.onCreateInputConnection(EditorInfo()))
            connection.setComposingText("草稿 composing", 1)
            editor.setSelection(2, 7)
            assertEquals(0, BaseInputConnection.getComposingSpanStart(editor.text))
            assertEquals(editor.length(), BaseInputConnection.getComposingSpanEnd(editor.text))
            activity.redrawVersion++
        }
        await { mainValue { activity.observedRedrawVersion == 1 } }
        main {
            assertSame(editor, awaitEditorOnMain(activity))
            assertEquals("草稿 composing", editor.text.toString())
            assertEquals(2, editor.selectionStart)
            assertEquals(7, editor.selectionEnd)
            assertEquals(0, BaseInputConnection.getComposingSpanStart(editor.text))
            assertEquals(editor.length(), BaseInputConnection.getComposingSpanEnd(editor.text))
            requireNotNull(editor.onCreateInputConnection(EditorInfo())).finishComposingText()
            editor.onEditorAction(EditorInfo.IME_ACTION_SEARCH)
        }
        await { mainValue { activity.searchSubmissions == 1 } }
        main { assertEquals("草稿 composing", activity.submittedQuery) }
        screenshot(activity, "native-search-selection")
    }

    @Test fun glassBlursActualSiblingContentAndRefreshesWithoutSelfCapture() = withFixture { activity ->
        assumeTrue("Live glass requires API31+", Build.VERSION.SDK_INT >= 31)
        main { activity.probeMode = true }
        // The old dock also contains one glass view. Wait for the newly laid-out
        // probe, not just the count, before retaining a View for PixelCopy bounds.
        await { mainValue {
            val probe = glasses(activity).singleOrNull()
            val density = activity.resources.displayMetrics.density
            activity.observedProbeVariant == 0 && probe != null &&
                kotlin.math.abs(probe.width - 220f * density) <= 1f &&
                kotlin.math.abs(probe.height - 120f * density) <= 1f
        } }
        val glass = mainValue { glasses(activity).single() }
        main {
            assertTrue("Hardware window is required for live blur", glass.isHardwareAccelerated)
            val source = descendants(activity.window.decorView).filterIsInstance<ComposeView>().last()
            assertFalse("Source view must not contain its own glass overlay", descendants(source).contains(glass))
        }
        // Exact window pixels: software View.draw would exercise the fallback, not the renderer.
        val first = glassFrame(activity, glass)
        if (Build.VERSION.SDK_INT >= 33) main {
            val field = TelegramGlassView::class.java.getDeclaredField("renderer").apply { isAccessible = true }
            val renderer = requireNotNull(field.get(glass))
            val shader = renderer.javaClass.getDeclaredField("liquid").apply { isAccessible = true }
            val failed = renderer.javaClass.getDeclaredField("shaderFailed").apply { isAccessible = true }
            assertNotNull("Kyant AGSL is running on API33+", shader.get(renderer))
            assertFalse("Shader must not silently fall back", failed.getBoolean(renderer))
        }
        save(first, "glass-red-blue-before-assertions")
        screenshot(activity, "glass-window-before-assertions")
        val y = first.height / 2
        val left = first.getPixel(first.width / 4, y)
        val right = first.getPixel(first.width * 3 / 4, y)
        assertTrue("Distinct source halves remain visible through glass", colorDistance(left, right) > 20)
        assertTrue("Blur mixes pixels on the left of the source boundary",
            colorDistance(left, first.getPixel(first.width / 2 - 3, y)) > 8)
        assertTrue("Blur mixes pixels on the right of the source boundary",
            colorDistance(right, first.getPixel(first.width / 2 + 3, y)) > 8)
        save(first, "glass-red-blue")
        main { activity.probeVariant = 1 }
        await { mainValue { activity.observedProbeVariant == 1 } }
        val second = glassFrame(activity, glass)
        assertTrue("Glass updates when its source changes",
            colorDistance(left, second.getPixel(second.width / 4, second.height / 2)) > 12)
        save(second, "glass-green-yellow")
        first.recycle(); second.recycle()
        main { assertEquals(1, activity.contentMounts); assertEquals(0, activity.contentUnmounts) }
    }

    @Test fun chatSearchKeepsHostPaletteAndSystemTextScale() = withFixture { activity ->
        main { activity.fixtureFontScale = 1.5f; activity.searchExpanded = true }
        val editor = awaitEditor(activity)
        main {
            assertEquals(activity.basePalette, activity.actualPalette)
            assertEquals(24f * activity.resources.displayMetrics.density, editor.textSize, 0.1f)
            assertTrue(editor.height >= editor.lineHeight)
        }
        screenshot(activity, "chat-search-large-text")
    }

    private fun withFixture(block: (SteamTelegramDesignFixtureActivity) -> Unit) {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,
            SteamTelegramDesignFixtureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SteamTelegramDesignFixtureActivity
        try {
            await { mainValue { activity.hasWindowFocus() && activity.contentMounts == 1 } }
            block(activity)
        } finally { main { activity.finish() }; instrumentation.waitForIdleSync() }
    }

    private fun awaitEditor(activity: SteamTelegramDesignFixtureActivity): TelegramEditText {
        await { mainValue { awaitEditorOnMain(activity) != null } }
        return mainValue { requireNotNull(awaitEditorOnMain(activity)) }
    }
    private fun awaitEditorOnMain(activity: SteamTelegramDesignFixtureActivity) =
        descendants(activity.window.decorView).filterIsInstance<TelegramEditText>().singleOrNull { it.isShown && it.width > 0 }
    private fun glasses(activity: SteamTelegramDesignFixtureActivity) =
        descendants(activity.window.decorView).filterIsInstance<TelegramGlassView>().filter { it.isShown && it.width > 0 && it.height > 0 }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun <T> mainValue(block: () -> T): T {
        var result: Result<T>? = null
        main { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(80)
        }
        assertTrue("Expected fixture state within 15 seconds", condition())
    }
    private fun glassFrame(activity: SteamTelegramDesignFixtureActivity, glass: View): Bitmap {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(200)
        val rect = mainValue { check(glass.isAttachedToWindow && glass.isShown); val p = IntArray(2); glass.getLocationInWindow(p); Rect(p[0], p[1], p[0] + glass.width, p[1] + glass.height) }
        return windowCopy(activity, rect)
    }
    private fun windowCopy(activity: SteamTelegramDesignFixtureActivity, rect: Rect): Bitmap {
        val image = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        val complete = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        main { PixelCopy.request(activity.window, rect, image, { result = it; complete.countDown() }, Handler(Looper.getMainLooper())) }
        assertTrue("Window PixelCopy completed", complete.await(5, TimeUnit.SECONDS))
        assertEquals("Window PixelCopy succeeded", PixelCopy.SUCCESS, result)
        return image
    }
    private fun screenshot(activity: SteamTelegramDesignFixtureActivity, name: String) {
        instrumentation.waitForIdleSync()
        val bounds = mainValue { Rect(0, 0, activity.window.decorView.width, activity.window.decorView.height) }
        windowCopy(activity, bounds).also { save(it, name); it.recycle() }
    }
    private fun save(bitmap: Bitmap, name: String) {
        val folder = File(instrumentation.targetContext.filesDir, "telegram-design-evidence").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun colorDistance(a: Int, b: Int) = kotlin.math.abs(Color.red(a) - Color.red(b)) +
        kotlin.math.abs(Color.green(a) - Color.green(b)) + kotlin.math.abs(Color.blue(a) - Color.blue(b))
}
