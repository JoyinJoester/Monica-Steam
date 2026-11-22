package takagi.ru.monica.steam.friends.chat.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the overflow menu's first frame.
 *
 * <p>A PopupWindow can measure, lay out and draw its content in the same frame it is attached, which
 * happens before the posted cascade animation starts. If the popup still carries its default fully
 * expanded state at that point, the menu flashes open for one frame and then snaps back into the
 * cascade. Upstream Telegram prevents this by collapsing the layout in `ActionBarPopupWindow` before
 * the window is added; this project must keep that ordering in both entry points.
 */
class SteamChatMenuPopupGuardTest {
    @Test
    fun popupIsCollapsedBeforeTheFirstFrameCanBeDrawn() {
        val popup = projectFile(
            "telegram-chat-ui/src/main/java/com/monica/telegramchat/TelegramPopupLayout.java"
        ).readText()
        val header = projectFile(
            "telegram-chat-ui/src/main/java/com/monica/telegramchat/TelegramChatHeaderView.java"
        ).readText()

        assertTrue(popup.contains("public void prepareForAnimation()"))
        // The start state has to be transparent and zero-height, otherwise the frame that is drawn
        // before the animation runs shows the whole menu.
        assertTrue(popup.contains("backScaleY = 0f"))
        assertTrue(popup.contains("backAlpha = 0"))
        assertTrue(popup.contains("rows.getChildAt(a).setAlpha(0f)"))

        val attached = popup.substringAfter("onAttachedToWindow")
        assertTrue(
            "prepareForAnimation must run before the posted cascade",
            attached.indexOf("prepareForAnimation()") in 0 until attached.indexOf("post(")
        )
        val showActionMenu = header.substringAfter("public void showActionMenu")
            .substringBefore("public void dismissActionMenu")
        assertTrue(
            "the popup must be collapsed before showAsDropDown",
            showActionMenu.indexOf("prepareForAnimation()") in 0 until showActionMenu.indexOf("showAsDropDown")
        )
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, path)
    }
}
