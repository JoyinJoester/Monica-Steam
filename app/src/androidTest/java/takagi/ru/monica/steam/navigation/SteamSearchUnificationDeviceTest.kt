package takagi.ru.monica.steam.navigation

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.abs
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.foundation.ui.SteamListSearchField
import takagi.ru.monica.steam.friends.chat.ui.SteamFriendsTopBar
import takagi.ru.monica.steam.friends.chat.ui.SteamInboxTopBar
import takagi.ru.monica.steam.navigation.liquidglass.ui.*
import takagi.ru.monica.steam.store.filters.domain.*
import takagi.ru.monica.steam.store.filters.ui.SteamStoreAdvancedFilterSheet
import takagi.ru.monica.ui.LocalReduceAnimations

/** Real shared headers, native editor and store sheet; no account or network operations. */
class SteamSearchUnificationDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val metadata = SteamStoreFilterMetadata(
        priceOptions = listOf(SteamStoreFilterOption("free", "免费"), SteamStoreFilterOption("60", "低于 ¥60")),
        languages = listOf(SteamStoreFilterOption("schinese", "简体中文"), SteamStoreFilterOption("tchinese", "繁体中文"),
            SteamStoreFilterOption("long", "A language name that must remain completely readable")),
        tags = listOf(SteamStoreTagOption(1, "冒险"), SteamStoreTagOption(2, "策略"), SteamStoreTagOption(3, "角色扮演")))
    private fun label(id: Int) = rule.activity.getString(id)
    private fun mount(large: Boolean = false, dark: Boolean = false, body: @Composable () -> Unit) {
        rule.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) maxOf(1.5f, density.fontScale) else density.fontScale),
                    LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS, LocalReduceAnimations provides true) {
                    Surface(Modifier.fillMaxSize()) {
                        SteamGlassHost(Modifier.fillMaxSize(), content = { Box(Modifier.fillMaxSize()) }, overlay = { body() })
                    }
                }
            }
        }
    }
    private fun snapshot(name: String) {
        rule.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(rule.activity.filesDir, "search-unification/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        File(file.parentFile, "$name-config.txt").writeText(
            "systemFontScale=${rule.activity.resources.configuration.fontScale}\n" +
                "densityDpi=${rule.activity.resources.displayMetrics.densityDpi}\n")
        val fields = currentEditors()
        File(file.parentFile, "$name-editor.txt").writeText(fields.joinToString("\n") { field ->
            val position = IntArray(2).also { field.getLocationOnScreen(it) }
            "pos=${position.toList()}, w=${field.width}, h=${field.height}, textSize=${field.textSize}, gravity=${field.gravity}, scrollY=${field.scrollY}, maxLines=${field.maxLines}, lines=${field.layout?.lineCount}, padTop=${field.totalPaddingTop}, padBottom=${field.totalPaddingBottom}"
        })
        bitmap.recycle()
    }
    private fun currentEditors(): List<EditText> {
        fun find(view: View): List<EditText> = if (view is EditText) listOf(view)
            else if (view is ViewGroup) (0 until view.childCount).flatMap { find(view.getChildAt(it)) } else emptyList()
        return android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap { find(it) }
    }
    private fun editor(): EditText {
        fun find(view: View): EditText? {
            if (view is EditText) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        return requireNotNull(find(rule.activity.window.decorView))
    }

    @Test fun nativeSearchPreservesComposingSelectionAndSearchAction() {
        val query = mutableStateOf("正在输入")
        val frame = mutableStateOf(0)
        var searches = 0
        mount {
            Column { Text(frame.value.toString()); SteamListSearchField(query.value, { query.value = it }, "Search",
                onSearch = { searches++ }) }
        }
        rule.runOnIdle {
            val field = editor()
            field.requestFocus(); field.setSelection(1)
            val connection = requireNotNull(field.onCreateInputConnection(EditorInfo()))
            connection.setComposingRegion(0, 2)
            frame.value++
        }
        rule.runOnIdle {
            val field = editor()
            assertEquals(1, field.selectionStart)
            assertEquals(0, BaseInputConnection.getComposingSpanStart(field.text))
            assertEquals(2, BaseInputConnection.getComposingSpanEnd(field.text))
            field.setText("new query")
            assertEquals("new query", query.value)
            field.onEditorAction(EditorInfo.IME_ACTION_SEARCH)
            assertEquals(1, searches)
            assertFalse(field.hasFocus())
        }
    }

    @Test fun inboxUsesLibrarySearchExpansionAndBackClearsQuery() {
        val query = mutableStateOf("")
        val expanded = mutableStateOf(false)
        val first = SteamAccount(904011, "76561198000904011", "avatar-one", "Moon", "", "",
            null, null, null, null, null, null, "{}", false, 0, 0, 0)
        val second = first.copy(id = 904012, steamId = "76561198000904012", displayName = "Sun")
        val current = mutableStateOf(first)
        val contacts = mutableStateOf(false)
        var accountClicks = 0
        val colors = listOf(0xFF234B6B.toInt(), 0xFF6B3423.toInt())
        val cache = File(rule.activity.cacheDir, "steam_avatars").apply { mkdirs() }
        val files = listOf(first, second).map { File(cache, "${it.steamId}.png") }
        try {
            files.forEachIndexed { index, file ->
                val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(colors[index])
                val canvas = android.graphics.Canvas(bitmap)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                paint.color = 0xFFF3D4A1.toInt()
                canvas.drawCircle(32f, 32f, 19f, paint)
                paint.color = colors[index]
                canvas.drawCircle(42f, 23f, 19f, paint)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            val switchAccount = {
                accountClicks++
                current.value = if (current.value == first) second else first
            }
            mount(dark = true) {
                if (contacts.value) SteamFriendsTopBar(current.value, query.value, { query.value = it },
                    expanded.value, { expanded.value = it }, true, {}, {}, {}, switchAccount)
                else SteamInboxTopBar(current.value, true, query.value, { query.value = it },
                    searchExpanded = expanded.value, onSearchExpandedChange = { expanded.value = it },
                    onShowAccounts = switchAccount, onOpenSpace = {})
            }
            for (showContacts in listOf(false, true)) {
                rule.runOnIdle { contacts.value = showContacts; current.value = first }
                val avatar = rule.onNodeWithContentDescription(label(R.string.steam_switch_account))
                for (index in colors.indices) {
                    rule.waitUntil(10_000) {
                        val bitmap = avatar.captureToImage().asAndroidBitmap()
                        bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) == colors[index]
                    }
                    val avatarBounds = avatar.fetchSemanticsNode().boundsInRoot
                    val searchBounds = rule.onNodeWithContentDescription(label(R.string.topbar_search_hint))
                        .fetchSemanticsNode().boundsInRoot
                    val menuBounds = rule.onNodeWithContentDescription(label(R.string.steam_chat_options))
                        .fetchSemanticsNode().boundsInRoot
                    assertTrue("Account avatar must precede search", avatarBounds.right <= searchBounds.left)
                    assertTrue("Search must precede overflow", searchBounds.right <= menuBounds.left)
                    assertEquals(avatarBounds.center.y, searchBounds.center.y, 1f)
                    snapshot("${if (showContacts) "contacts" else "inbox"}-account-$index")
                    if (index == 0) avatar.performClick()
                }
            }
            rule.runOnIdle { assertEquals(2, accountClicks); contacts.value = false }
            rule.onNodeWithContentDescription(label(R.string.topbar_search_hint)).performClick()
            rule.onNode(hasSetTextAction()).performTextInput("fixture")
            rule.runOnIdle { assertTrue(expanded.value); assertEquals("fixture", query.value) }
            snapshot("inbox-library-search")
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            rule.waitForIdle()
            if (expanded.value) InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            rule.waitUntil(5_000) { !expanded.value }
            rule.runOnIdle { assertEquals("", query.value) }
        } finally {
            files.forEach { it.delete() }
        }
    }

    @Test fun storeChoicesWrapAndTagsAreAvailableBeforeExpanding() = storeLayout(false, false)
    @Test fun storeLargeDarkChoicesWrapAndTagsRemainReadable() = storeLayout(true, true)
    private fun storeLayout(large: Boolean, dark: Boolean) {
        mount(large, dark) { SteamStoreAdvancedFilterSheet(SteamStoreFilterSelection(), metadata,
            false, null, {}, {}, {}) }
        for (text in listOf("免费", "低于 ¥60", "简体中文", "繁体中文", metadata.languages.last().label, "冒险")) {
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
            val node = rule.onNodeWithText(text)
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            val width = rule.activity.resources.displayMetrics.widthPixels
            assertTrue("Full label must remain inside the viewport: $text", bounds.left >= 0 && bounds.right <= width)
            assertChoiceCentered(text)
            if (text == "低于 ¥60") snapshot(if (large) "store-dark-large-prices" else "store-light-prices")
        }
        rule.runOnIdle {
            val field = currentEditors().single { it.isShown }
            val layout = requireNotNull(field.layout)
            val top = field.totalPaddingTop - field.scrollY
            assertTrue("Native search line must fit vertically", top >= 0 && top + layout.height <= field.height)
            assertTrue("Native search line must be vertically centered", abs(top + layout.height / 2f - field.height / 2f) <= 2f)
        }
        snapshot(if (large) "store-dark-large-tags" else "store-light-tags")
    }

    private fun assertChoiceCentered(text: String) {
        val node = rule.onNodeWithText(text, useUnmergedTree = true)
        val semantics = node.fetchSemanticsNode()
        val choice = generateSequence(semantics.parent) { it.parent }.first {
            it.config.getOrElse(SemanticsProperties.Role) { Role.Button } in listOf(Role.RadioButton, Role.Checkbox)
        }
        assertTrue("Label must be centered inside its choice: $text",
            abs(semantics.boundsInRoot.center.x - choice.boundsInRoot.center.x) <= 2f)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        for (layout in layouts) for (line in 0 until layout.lineCount) {
            // Line offsets use paragraph coordinates, which can exceed the intrinsic Text size.
            assertTrue("Each wrapped line must be centered: $text, left=${layout.getLineLeft(line)}, right=${layout.getLineRight(line)}, width=${layout.size.width}, paragraph=${layout.multiParagraph.width}",
                abs((layout.getLineLeft(line) + layout.getLineRight(line)) / 2f - layout.multiParagraph.width / 2f) <= 2f)
        }
    }

    @Test fun storeDraftAppliesTogetherAndDismissLeavesOriginalSelection() {
        val original = SteamStoreFilterSelection(tagIds = setOf(2))
        val show = mutableStateOf(true)
        var applied: SteamStoreFilterSelection? = null
        mount { if (show.value) SteamStoreAdvancedFilterSheet(original, metadata, false, null, {},
            { applied = it; show.value = false }, { show.value = false }) }
        for (text in listOf("免费", "简体中文", "冒险")) {
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
            rule.onNodeWithText(text).performClick()
            assertChoiceCentered(text)
        }
        rule.runOnIdle { assertNull(applied); assertEquals(setOf(2), original.tagIds) }
        rule.onNodeWithText(label(R.string.steam_store_filter_apply)).performClick()
        rule.runOnIdle { assertEquals(SteamStoreFilterSelection("free", setOf("schinese"), setOf(1, 2)), applied) }
        rule.runOnIdle { applied = null; show.value = true }
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("低于 ¥60"))
        rule.onNodeWithText("低于 ¥60").performClick()
        rule.onNodeWithContentDescription(label(R.string.close)).performClick()
        rule.runOnIdle { assertNull(applied); assertEquals(SteamStoreFilterSelection(tagIds = setOf(2)), original) }
    }
}
