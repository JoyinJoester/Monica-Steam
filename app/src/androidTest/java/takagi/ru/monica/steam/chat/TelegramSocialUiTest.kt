package takagi.ru.monica.steam.chat

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.steam.TelegramSocialFixtureActivity
import java.io.File

@RunWith(AndroidJUnit4::class)
class TelegramSocialUiTest {
    private fun clickFilter(label: String) {
        await {
            var clicked = false
            instrumentation.runOnMainSync {
                var node = visibleSteamChatText(activity!!.window.decorView, label)
                while (node != null && node.config.getOrNull(SemanticsActions.OnClick) == null) node = node.parent
                clicked = node?.config?.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true
            }
            clicked
        }
    }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var activity: TelegramSocialFixtureActivity? = null
    private val media = mutableListOf<android.net.Uri>()
    @After fun close() {
        instrumentation.runOnMainSync { activity?.finish() }
        media.forEach { instrumentation.targetContext.contentResolver.delete(it, null, null) }
    }

    @Test fun officialEmojiPackDecodesAndNativePickerReturnsUnicode() {
        val context = instrumentation.targetContext
        com.monica.telegramchat.TelegramUi.initialize(context)
        val pack = com.monica.telegramchat.upstream.EmojiPack.getInstance()
        val bitmap = pack.getEmoji(0, 0)
        assertNotNull("Official packaged emoji decodes", bitmap)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("Official glyph has visible pixels", pixels.any { android.graphics.Color.alpha(it) > 0 })
        launch("emoji")
        click("😀")
        await { activity!!.selectedEmoji == "😀" }
        screenshot("official-emoji-dark")
    }

    @Test fun nativePhotoGridSelectsOwnedMediaBeforeConfirming() {
        val resolver = instrumentation.targetContext.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            android.content.ContentValues().apply {
                put("_display_name", "telegram-source-fixture.png"); put("mime_type", "image/png")
                if (android.os.Build.VERSION.SDK_INT >= 29) { put("relative_path", "Pictures/MonicaSourceTest"); put("is_pending", 1) }
            })!!
        media += uri
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.CYAN) }
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        if (android.os.Build.VERSION.SDK_INT >= 29) resolver.update(uri, android.content.ContentValues().apply { put("is_pending", 0) }, null, null)
        launch("attachments")
        click("telegram-source-fixture.png")
        assertEquals("Selection waits for confirmation", "", activity!!.selectedAttachment)
        assertNull("A canceled refresh must not leave a storage failure", find("无法读取附件，请重新选择文件夹或从系统文件选择"))
        screenshot("official-gallery-selected")
        click("确认选择附件")
        await { activity!!.selectedAttachment == uri.toString() }
    }

    @Test fun originalDocumentCellsPreserveFolderAndFileCallbacks() {
        launch("files")
        click("文件")
        click("测试文件夹")
        await { activity!!.selectedFolder == "content://fixture/folder" }
        click("测试附件.zip")
        await { activity!!.selectedAttachment == "content://fixture/archive" }
        screenshot("official-files-dark")
    }

    @Test fun conversationFiltersOpenTheCorrectDetailAndMessageAction() = withFailureEvidence("conversation-filters") {
        launch("conversations")
        screenshot("conversation-dark")
        // Native rows can obscure Compose accessibility children even while the filter is
        // visibly rendered. Exercise the mounted filter's own semantic action, as below.
        clickFilter("群聊")
        // The original group is archived; the group filter shows only the live group.
        await { find("周末开黑") != null && find("Alex") == null && find("Monica 游戏小组") == null }
        clickFilter("全部")
        click("Alex")
        await { activity!!.route == "friend" }
        screenshot("friend-dark")
        click(activity!!.getString(takagi.ru.monica.R.string.steam_chat_send_message))
        await { activity!!.route == "conversations" }
    }

    @Test fun friendFiltersAndIncomingRequestKeepTheirCallbacks() {
        launch("friends")
        assertContactTextFits()
        screenshot("friends-dark")
        click("筛选联系人")
        click(activity!!.getString(takagi.ru.monica.R.string.steam_friends_filter_playing))
        await { find("Alex") != null && find("Sam") == null }
        click(activity!!.getString(takagi.ru.monica.R.string.steam_friends_filter_requests))
        click(activity!!.getString(takagi.ru.monica.R.string.steam_friend_accept))
        await { activity!!.invitesAccepted == 1 }
        click("筛选联系人")
        click(activity!!.getString(takagi.ru.monica.R.string.steam_friends_filter_all))
        click("Alex")
        await { activity!!.lastFriend == "76561198000000001" && activity!!.route == "friend" }
        screenshot("friend-from-contacts-dark")
        instrumentation.runOnMainSync { activity!!.finish() }
        launch("friends", dark = false)
        assertContactTextFits()
        screenshot("friends-light")
    }

    @Test fun inboxUnreadSearchPinsAndToolbarKeepNavigation() = withFailureEvidence("inbox-navigation") {
        launch("conversations")
        assertTrue("Inbox search starts empty", activity!!.rootQuery.isEmpty())
        await { find("明月") != null && find("周末开黑") != null }
        click("未读")
        await { find("Alex") != null && find("明月") == null && find("周末开黑") == null }
        screenshot("inbox-unread-dark")
        clickFilter("群聊")
        await { find("周末开黑") != null && find("Alex") == null && find("Monica 游戏小组") == null }
        // Sam and the original group are deliberately archived by the shared fixture.
        clickFilter("归档")
        await { find("Monica 游戏小组") != null && find("Sam") != null && find("周末开黑") == null }
        clickFilter("置顶")
        await { find("Alex") != null && find("Sam") == null && find("Monica 游戏小组") == null }
        clickFilter("全部")
        // The inbox now has an always-visible search field. The expandable search button
        // belongs to the contacts page, so type into the actual inbox editor.
        setSearchText("明月")
        await { find("明月") != null && find("Alex") == null }
        screenshot("inbox-search-dark")
        click("关闭搜索")
        await { activity!!.rootQuery.isEmpty() && !activity!!.rootSearchExpanded && find("Alex") != null }
        click(activity!!.getString(takagi.ru.monica.R.string.steam_switch_account))
        await { activity!!.accountsShown == 1 }
        click("新建聊天")
        // State changes before the old header's accessibility nodes are removed. Wait for
        // the actual contacts header and native contact row before opening its overflow.
        await { activity!!.rootShowFriends &&
            find(activity!!.getString(takagi.ru.monica.R.string.steam_friends_title)) != null &&
            find("Alex", contactOnly = true) != null }
        instrumentation.waitForIdleSync()
        click("聊天选项")
        click("新建群聊")
        await { activity!!.groupsCreated == 1 }
        click("Alex", contactOnly = true)
        await { activity!!.lastFriend == "76561198000000001" && activity!!.route == "friend" }
        instrumentation.runOnMainSync { activity!!.finish() }
        launch("conversations", dark = false)
        screenshot("inbox-light")
        click("未读")
        await { find("Alex") != null && find("明月") == null }
    }

    @Test fun embeddedInboxComposeSelectsAnExistingFriend() {
        launch("conversations", embedded = true)
        click("新建聊天")
        click("Alex", contactOnly = true)
        await { activity!!.lastFriend == "76561198000000001" && activity!!.route == "friend" }
    }

    @Test fun telegramInboxMenuSearchAndUnreadPreserveNavigation() {
        launch("conversations", hideDock = true)
        await { find("Alex") != null && find("周末开黑") != null }
        val space = activity!!.getString(takagi.ru.monica.R.string.steam_space_title)
        assertNull("Space is only available from the overflow", find(space))
        screenshot("telegram-inbox-dark")
        click("聊天选项")
        awaitNode(space)
        screenshot("telegram-inbox-menu-dark")
        click(space)
        await { activity!!.spacesOpened == 1 && find(space) == null }
        click("聊天选项")
        click("新建群聊")
        await { activity!!.groupsCreated == 1 }
        click("聊天选项")
        click(activity!!.getString(takagi.ru.monica.R.string.steam_friends_refresh))
        await { activity!!.conversationsRefreshed == 1 }
        click("未读")
        await { find("Alex") != null && find("明月") == null }
        click("群聊")
        await { find("周末开黑") != null && find("Alex") == null }
        click("未读")
        await { find("Alex") != null && find("周末开黑") == null && find("明月") == null }
        click("全部")
        setSearchText("明月")
        await { activity!!.rootQuery == "明月" && find("Alex") == null }
        click("关闭搜索")
        await { activity!!.rootQuery.isEmpty() && find("Alex") != null }
        click(activity!!.getString(takagi.ru.monica.R.string.steam_switch_account))
        await { activity!!.accountsShown == 1 }
        assertEquals("Space action is dispatched once", 1, activity!!.spacesOpened)
        assertEquals("Create action is dispatched once", 1, activity!!.groupsCreated)
        click("新建聊天")
        await { activity!!.rootShowFriends }
        click("Alex", contactOnly = true)
        await { activity!!.lastFriend == "76561198000000001" && activity!!.route == "friend" }
        instrumentation.runOnMainSync { activity!!.finish() }
        launch("conversations", dark = false, hideDock = true)
        screenshot("telegram-inbox-light")
    }

    @Test fun telegramConversationNativeMenuKeepsTargetAndDeleteConfirmation() = withFailureEvidence("conversation-menu") {
        launch("conversations", hideDock = true)
        fun openAlexMenu() {
            // The action callback runs before Compose removes the closing popup. Sending an
            // accessibility action to a row behind that popup can coalesce close and reopen
            // into one frame, retaining the already-dismissed native view. A user can reach
            // the row only after its focusable popup has relinquished the window.
            await {
                var focused = false
                instrumentation.runOnMainSync { focused = activity!!.hasWindowFocus() }
                focused && find(activity!!.getString(takagi.ru.monica.R.string.steam_chat_unpin)) == null
            }
            var node: AccessibilityNodeInfo? = awaitNode("Alex")
            while (node != null && !node.isLongClickable) node = node.parent
            assertTrue("Native row exposes its long-click action", node?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) == true)
            awaitNode(activity!!.getString(takagi.ru.monica.R.string.steam_chat_unpin))
        }
        openAlexMenu()
        screenshot("telegram-conversation-menu")
        click(activity!!.getString(takagi.ru.monica.R.string.steam_chat_unpin))
        await { activity!!.conversationPins == 1 }
        assertEquals("76561198000000001", activity!!.lastConversationTarget)
        openAlexMenu()
        click(activity!!.getString(takagi.ru.monica.R.string.steam_chat_archive))
        await { activity!!.conversationArchives == 1 }
        assertEquals("76561198000000001", activity!!.lastConversationTarget)
        openAlexMenu()
        click(activity!!.getString(takagi.ru.monica.R.string.steam_chat_delete))
        awaitNode(activity!!.getString(takagi.ru.monica.R.string.steam_chat_delete_keep_messages))
        assertEquals("Opening confirmation does not delete", 0, activity!!.conversationDeletes)
        click(activity!!.getString(takagi.ru.monica.R.string.steam_chat_delete_cancel))
        await { find(activity!!.getString(takagi.ru.monica.R.string.steam_chat_delete_keep_messages)) == null }
        assertEquals("Cancel preserves the conversation", 0, activity!!.conversationDeletes)
        click("Alex")
        await { activity!!.lastFriend == "76561198000000001" && activity!!.route == "friend" }
    }

    @Test fun telegramConversationLargeTextFitsAndAccessibilityUsesHostLocale() {
        launch("conversations", hideDock = true)
        val scale = androidx.compose.runtime.mutableFloatStateOf(1f)
        val language = androidx.compose.runtime.mutableStateOf("en")
        val title = "A very long conversation name for accessibility"
        val subtitle = "A two-line conversation preview that must remain inside the measured row at large text sizes."
        var clicks = 0
        lateinit var localizedContext: android.content.Context
        instrumentation.runOnMainSync {
            activity!!.setContent {
                val configuration = android.content.res.Configuration(activity!!.resources.configuration).apply {
                    setLocale(java.util.Locale.forLanguageTag(language.value))
                }
                val context = activity!!.createConfigurationContext(configuration)
                localizedContext = context
                val density = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalContext provides context,
                    androidx.compose.ui.platform.LocalConfiguration provides configuration,
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, scale.floatValue)
                ) {
                    takagi.ru.monica.ui.theme.MonicaTheme(darkTheme = false) {
                        takagi.ru.monica.steam.friends.chat.telegram.TelegramSocialTheme {
                            androidx.compose.foundation.layout.Column {
                                takagi.ru.monica.steam.friends.chat.telegram.TelegramNativeConversationRow(
                                    title, subtitle, "23:59", 12, true, "", { clicks++ }
                                )
                            }
                        }
                    }
                }
            }
        }
        fun row(): com.monica.telegramchat.upstream.DialogCell? {
            fun descend(view: android.view.View): com.monica.telegramchat.upstream.DialogCell? {
                if (view is com.monica.telegramchat.upstream.DialogCell && view.contentDescription?.startsWith(title) == true) return view
                return if (view is android.view.ViewGroup) (0 until view.childCount).firstNotNullOfOrNull { descend(view.getChildAt(it)) } else null
            }
            return descend(activity!!.window.decorView)
        }
        fun field(cell: Any, name: String): Any = cell.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(cell)!!
        fun integer(cell: Any, name: String): Int = cell.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(cell) as Int
        var initialHeight = 0
        var initialText = 0f
        await {
            var ready = false
            instrumentation.runOnMainSync {
                row()?.takeIf { it.height > 0 }?.let {
                    initialHeight = it.height
                    initialText = (field(it, "namePaint") as android.text.TextPaint).textSize
                    ready = true
                }
            }
            ready
        }
        for (textScale in listOf(1.5f, 2f)) {
            instrumentation.runOnMainSync { scale.floatValue = textScale }
            await {
                var changed = false
                instrumentation.runOnMainSync { row()?.let { changed = it.height > initialHeight &&
                    kotlin.math.abs((field(it, "namePaint") as android.text.TextPaint).textSize - initialText * textScale) < 1f } }
                changed
            }
            instrumentation.runOnMainSync {
                val cell = requireNotNull(row())
                val name = field(cell, "nameLayout") as android.text.StaticLayout
                val message = field(cell, "messageLayout") as android.text.StaticLayout
                val count = field(cell, "countLayout") as android.text.StaticLayout
                val messageTop = integer(cell, "messageTop")
                val badgeTop = integer(cell, "badgeTop")
                val badgeHeight = integer(cell, "badgeHeight")
                assertTrue("Name and preview do not overlap", messageTop >= com.monica.telegramchat.TelegramUi.dp(10f) + name.height)
                assertTrue("Preview glyphs stay inside the native row", messageTop + message.height <= cell.height)
                assertTrue("Unread text fits its badge", count.height <= badgeHeight)
                assertTrue("Unread badge stays inside the native row", badgeTop + badgeHeight <= cell.height)
                assertTrue("Large name remains readable beside the time", name.width >= com.monica.telegramchat.TelegramUi.dp(24f))
            }
        }
        screenshot("telegram-conversation-large-text")
        // The locale comes from the host, including rows rendered by the native component.
        for (locale in listOf("en", "zh", "ja", "ru", "vi")) {
            instrumentation.runOnMainSync { language.value = locale }
            await {
                var correct = false
                instrumentation.runOnMainSync {
                    val contextLocale = localizedContext.resources.configuration.locales[0].language
                    val unread = localizedContext.getString(takagi.ru.monica.R.string.steam_chat_unread_count, 12)
                    val pinned = localizedContext.getString(takagi.ru.monica.R.string.steam_chat_filter_pinned)
                    val description = row()?.contentDescription?.toString().orEmpty()
                    correct = contextLocale == locale && description.contains(unread) && description.contains(pinned)
                }
                correct
            }
        }
        instrumentation.runOnMainSync { requireNotNull(row()).performClick() }
        assertEquals("The expanded row retains its click target", 1, clicks)
        instrumentation.runOnMainSync { scale.floatValue = 1f }
        await {
            var restored = false
            instrumentation.runOnMainSync { row()?.let { restored = it.height == initialHeight &&
                (field(it, "namePaint") as android.text.TextPaint).textSize == initialText } }
            restored
        }
    }

    private fun setSearchText(value: String) {
        fun editor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isEditable && node.isVisibleToUser &&
                node.packageName?.toString() == instrumentation.targetContext.packageName) return node
            return (0 until node.childCount).firstNotNullOfOrNull { editor(node.getChild(it)) }
        }
        // The state callback precedes Compose's accessibility-tree update. The IME
        // can also become the active window before the search field is published.
        var input: AccessibilityNodeInfo? = null
        await {
            val automation = instrumentation.uiAutomation
            input = editor(automation.rootInActiveWindow)
                ?: automation.windows.firstNotNullOfOrNull { editor(it.root) }
            input != null
        }
        assertNotNull("Search field is editable", input)
        assertTrue(input!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
            android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }))
        instrumentation.waitForIdleSync()
    }

    private fun assertContactTextFits() {
        instrumentation.runOnMainSync {
            var checked = 0
            fun inspect(view: android.view.View, contact: Boolean = false) {
                val inContact = contact || view is com.monica.telegramchat.upstream.UserCell
                if (inContact && view is android.widget.TextView && view.text.isNotEmpty()) {
                    checked++
                    val metrics = view.paint.fontMetricsInt
                    val details = "${view.text}: height=${view.height}, baseline=${view.baseline}, ascent=${metrics.ascent}, descent=${metrics.descent}, padding=${view.paddingTop}/${view.paddingBottom}, textSize=${view.textSize}"
                    assertTrue("Contact text top clipped: $details", view.baseline + metrics.ascent >= view.paddingTop)
                    assertTrue("Contact text bottom clipped: $details", view.baseline + metrics.descent <= view.height - view.paddingBottom)
                }
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) inspect(view.getChildAt(i), inContact)
            }
            inspect(activity!!.window.decorView)
            assertTrue("Contact labels were checked", checked >= 4)
        }
    }

    @Test fun groupPreferencesAndMemberSearchPreserveNavigation() {
        launch("group")
        screenshot("group-info-dark")
        click("消息免打扰", scrollList = true)
        await { activity!!.preferences.muted }
        click("置顶聊天", scrollList = true)
        await { activity!!.preferences.pinned }
        val search = awaitNode("搜索成员", scrollList = true)
        var editor: AccessibilityNodeInfo? = search
        while (editor != null && !editor.isEditable) editor = editor.parent
        assertNotNull("Search is editable", editor)
        assertTrue(editor!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
            android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Sam") }))
        awaitNode("Sam", contactOnly = true, scrollList = true)
        instrumentation.waitForIdleSync()
        SystemClock.sleep(500)
        screenshot("group-info-search")
        click("Sam", contactOnly = true, scrollList = true)
        await { activity!!.lastFriend == "76561198000000002" }
    }

    private fun launch(route: String, dark: Boolean = true, embedded: Boolean = false, hideDock: Boolean = false) {
        val previewStyle = InstrumentationRegistry.getArguments().getString("previewDesignStyle")
        val previewDark = InstrumentationRegistry.getArguments().getString("previewDark")
            ?.toBooleanStrictOrNull() ?: dark
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("route", route).putExtra("dark", previewDark)
            .putExtra("designStyle", previewStyle)
            .putExtra("embedded", embedded).putExtra("hideDock", hideDock)) as TelegramSocialFixtureActivity
        instrumentation.waitForIdleSync()
        SystemClock.sleep(500)
        if (previewStyle != null) screenshot("fab-$previewStyle-${if (previewDark) "dark" else "light"}")
    }
    private fun find(label: String, contactOnly: Boolean = false): AccessibilityNodeInfo? {
        fun search(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isVisibleToUser && node.contentDescription?.toString()?.startsWith("$label, ") == true) return node
            if (node.isVisibleToUser && !contactOnly &&
                (node.text?.toString() == label || node.contentDescription?.toString() == label)) return node
            return (0 until node.childCount).firstNotNullOfOrNull { search(node.getChild(it)) }
        }
        val automation = instrumentation.uiAutomation
        return search(automation.rootInActiveWindow)
            ?: automation.windows.firstNotNullOfOrNull { search(it.root) }
    }
    private fun awaitNode(
        label: String,
        contactOnly: Boolean = false,
        scrollList: Boolean = false
    ): AccessibilityNodeInfo {
        val until = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < until) {
            find(label, contactOnly)?.let { return it }
            // Waiting for a popup or a replaced page must not move the underlying list.
            // A forward scroll can dispose the long-pressed row and its remembered popup
            // before Compose publishes the new accessibility window. Only the explicitly
            // off-screen group-info controls opt into list traversal.
            if (scrollList) {
                var focused = false
                instrumentation.runOnMainSync { focused = activity!!.hasWindowFocus() }
                if (focused) {
                    fun scroll(node: AccessibilityNodeInfo?): Boolean {
                        if (node == null || !node.isVisibleToUser ||
                            node.packageName?.toString() != instrumentation.targetContext.packageName) return false
                        if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
                        return (0 until node.childCount).any { scroll(node.getChild(it)) }
                    }
                    scroll(instrumentation.uiAutomation.rootInActiveWindow)
                }
            }
            SystemClock.sleep(300)
        }
        val dump = StringBuilder()
        fun describe(node: AccessibilityNodeInfo?, depth: Int = 0) {
            if (node == null) return
            dump.append(" ".repeat(depth)).append("text=").append(node.text).append(" description=").append(node.contentDescription)
                .append(" visible=").append(node.isVisibleToUser).append(" scroll=").append(node.isScrollable).append("\n")
            for (i in 0 until node.childCount) describe(node.getChild(i), depth + 1)
        }
        describe(instrumentation.uiAutomation.rootInActiveWindow)
        instrumentation.uiAutomation.windows.forEach { describe(it.root) }
        File(instrumentation.targetContext.filesDir, "social-missing-node.txt").writeText(dump.toString())
        error("Missing social action: $label")
    }
    private fun click(label: String, contactOnly: Boolean = false, scrollList: Boolean = false) {
        val until = SystemClock.uptimeMillis() + 10000
        var clicked = false
        while (!clicked && SystemClock.uptimeMillis() < until) {
            var node: AccessibilityNodeInfo? = awaitNode(label, contactOnly, scrollList)
            while (node != null && !node.isClickable) node = node.parent
            clicked = node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            if (!clicked) SystemClock.sleep(100)
        }
        assertTrue("Click $label", clicked)
        instrumentation.waitForIdleSync()
    }
    private fun await(condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < until) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        error("Social callback timed out")
    }
    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
        val base = if (InstrumentationRegistry.getArguments().getString("internalEvidence") == "true")
            instrumentation.targetContext.filesDir else instrumentation.targetContext.getExternalFilesDir(null)
        val directory = File(base, "telegram-social-evidence").apply { mkdirs() }
        // Storage location does not determine the capture surface: a Compose Popup owns a
        // separate window, so Activity-only PixelCopy omits the menu being verified.
        val bitmap = captureTelegramUiEvidence(instrumentation)
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun withFailureEvidence(name: String, block: () -> Unit) {
        try {
            block()
        } catch (failure: Throwable) {
            val evidenceName = "failure-$name-${SystemClock.uptimeMillis()}"
            runCatching { dumpWindowEvidence(evidenceName) }.exceptionOrNull()?.let(failure::addSuppressed)
            runCatching { screenshot(evidenceName) }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    private fun dumpWindowEvidence(name: String) {
        val dump = StringBuilder()
        instrumentation.runOnMainSync {
            dump.append("route=").append(activity?.route)
                .append(" fixtureFocus=").append(activity?.hasWindowFocus())
                .append(" pins=").append(activity?.conversationPins)
                .append(" archives=").append(activity?.conversationArchives)
                .append(" deletes=").append(activity?.conversationDeletes).append('\n')
        }
        fun describe(node: AccessibilityNodeInfo?, depth: Int = 0) {
            if (node == null) return
            val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
            dump.append(" ".repeat(depth)).append("class=").append(node.className)
                .append(" package=").append(node.packageName).append(" text=").append(node.text)
                .append(" description=").append(node.contentDescription)
                .append(" visible=").append(node.isVisibleToUser)
                .append(" enabled=").append(node.isEnabled).append(" click=").append(node.isClickable)
                .append(" longClick=").append(node.isLongClickable).append(" scroll=").append(node.isScrollable)
                .append(" bounds=").append(bounds).append('\n')
            repeat(node.childCount) { describe(node.getChild(it), depth + 1) }
        }
        val automation = instrumentation.uiAutomation
        dump.append("Active root\n")
        describe(automation.rootInActiveWindow)
        automation.windows.forEach { window ->
            val bounds = android.graphics.Rect().also(window::getBoundsInScreen)
            dump.append("Window id=").append(window.id).append(" type=").append(window.type)
                .append(" layer=").append(window.layer).append(" active=").append(window.isActive)
                .append(" focused=").append(window.isFocused).append(" title=").append(window.title)
                .append(" bounds=").append(bounds).append('\n')
            describe(window.root)
        }
        val base = if (InstrumentationRegistry.getArguments().getString("internalEvidence") == "true")
            instrumentation.targetContext.filesDir else instrumentation.targetContext.getExternalFilesDir(null)
        File(File(base, "telegram-social-evidence").apply { mkdirs() }, "$name-windows.txt")
            .writeText(dump.toString())
    }
}
