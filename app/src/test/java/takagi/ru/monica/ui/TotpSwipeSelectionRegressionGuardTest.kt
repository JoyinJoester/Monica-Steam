package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpSwipeSelectionRegressionGuardTest {

    @Test
    fun authenticatorSelectionModeKeepsRightSwipeAndBlocksDeleteSwipe() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/totp/TotpListContent.kt"
        ).readText().replace("\r\n", "\n")
        val swipeActionsCall = source
            .substringAfter("takagi.ru.monica.ui.gestures.SwipeActions(")
            .substringBefore("\n                        ) {")

        assertTrue(
            "Authenticator cards must keep swipe handling enabled during selection mode so multiple cards can be selected by right-swipe.",
            swipeActionsCall.contains("enabled = !isDragging")
        )
        assertFalse(
            "Selection mode must not disable all SwipeActions; that regresses multi-card swipe selection.",
            swipeActionsCall.contains("enabled = !isDragging && !isSelectionMode")
        )
        assertTrue(
            "Delete swipe should be disabled while selecting to avoid accidental destructive actions.",
            swipeActionsCall.contains("allowSwipeLeft = !isSelectionMode")
        )
        assertTrue(
            "Right-swipe selection must remain available after the first card enters selection mode.",
            swipeActionsCall.contains("allowSwipeRight = true")
        )
    }

    @Test
    fun swipeActionsSupportsIndependentSwipeDirections() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/gestures/SwipeActions.kt"
        ).readText()

        assertTrue(
            "SwipeActions needs direction gates so callers can keep selection swipe while blocking delete swipe.",
            source.contains("allowSwipeLeft: Boolean = true") &&
                source.contains("allowSwipeRight: Boolean = true")
        )
        assertTrue(
            "Pointer input must restart when direction gates change.",
            source.contains(".pointerInput(enabled, allowSwipeLeft, allowSwipeRight)")
        )
        assertTrue(
            "Offsets from a now-disabled direction must be reset instead of leaving a card visually stuck.",
            source.contains("(!allowSwipeLeft && total < 0f)") &&
                source.contains("(!allowSwipeRight && total > 0f)")
        )
        assertTrue(
            "Swipe callbacks must only fire for directions the caller currently allows.",
            source.contains("if (allowSwipeLeft && animatableOffset.value < -dynamicThreshold)") &&
                source.contains("} else if (allowSwipeRight && animatableOffset.value > dynamicThreshold)")
        )
    }

    private fun projectFile(path: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, path)
    }
}
