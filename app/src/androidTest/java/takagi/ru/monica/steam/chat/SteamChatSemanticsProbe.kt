package takagi.ru.monica.steam.chat

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/** Read the mounted Compose semantics on the main thread without replacing its frame clock.
 * Hybrid native rows can leave API32 accessibility children missing. This tests actual mounted
 * text and bounds; it is not an accessibility-service compatibility assertion.
 */
internal fun visibleSteamChatText(root: View, text: String): SemanticsNode? {
    fun find(node: SemanticsNode): SemanticsNode? {
        val bounds = node.boundsInWindow
        if (bounds.width > 0 && bounds.height > 0 &&
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true) return node
        return node.children.firstNotNullOfOrNull(::find)
    }
    if (root is ViewRootForTest) find(root.semanticsOwner.unmergedRootSemanticsNode)?.let { return it }
    if (root is ViewGroup) for (i in 0 until root.childCount) {
        visibleSteamChatText(root.getChildAt(i), text)?.let { return it }
    }
    return null
}

/** Count badges on floating controls expose a description rather than a separate text label. */
internal fun visibleSteamChatDescription(root: View, description: String): SemanticsNode? {
    fun find(node: SemanticsNode): SemanticsNode? {
        val bounds = node.boundsInWindow
        if (bounds.width > 0 && bounds.height > 0 &&
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(description) == true) return node
        return node.children.firstNotNullOfOrNull(::find)
    }
    if (root is ViewRootForTest) find(root.semanticsOwner.unmergedRootSemanticsNode)?.let { return it }
    if (root is ViewGroup) for (i in 0 until root.childCount) {
        visibleSteamChatDescription(root.getChildAt(i), description)?.let { return it }
    }
    return null
}
