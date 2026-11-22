package takagi.ru.monica.steam.chat

import android.view.View
import android.text.StaticLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.monica.telegramchat.SocialTheme
import com.monica.telegramchat.upstream.DialogCell
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/** Revisit/remeasure pattern of the actual native inbox cell; no Steam account or protocol. */
class SteamDialogCellLayoutDeviceTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private fun onMain(body: () -> Unit) {
        val failure = AtomicReference<Throwable?>()
        instrument.runOnMainSync { try { body() } catch (error: Throwable) { failure.set(error) } }
        failure.get()?.let { throw it }
    }
    private fun cell() = DialogCell(instrument.targetContext,
        SocialTheme(0xff111111.toInt(), 0xff444444.toInt(), 0xff6750a4.toInt(),
            0xffffffff.toInt(), 0xfff7f2fa.toInt(), 0xffcccccc.toInt()))
    private fun dialog() = DialogCell.CustomDialog().apply {
        name = "Inbox"; message = "Latest message"; unread_count = 2; pinned = false
    }
    private fun measure(cell: DialogCell, widthDp: Int = 360) {
        cell.forceLayout()
        val width = (widthDp * instrument.targetContext.resources.displayMetrics.density).toInt()
        cell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        cell.layout(0, 0, width, cell.measuredHeight)
    }
    private fun layout(cell: DialogCell, field: String): StaticLayout =
        DialogCell::class.java.getDeclaredField(field).apply { isAccessible = true }.get(cell) as StaticLayout

    @Test fun unchangedRebindAndRemeasureKeepNativeTextLayouts() = onMain {
        val cell = cell(); cell.setDialog(dialog(), "12:00"); measure(cell)
        val fields = listOf("nameLayout", "messageLayout", "timeLayout", "countLayout")
        val original = fields.associateWith { layout(cell, it) }
        repeat(10) {
            // Compose supplies a new CustomDialog for the same row, then Android remeasures it.
            cell.setDialog(dialog(), "12:00"); measure(cell)
            fields.forEach { field -> assertSame("Unchanged $field must survive tab remeasure", original[field], layout(cell, field)) }
        }
    }

    @Test fun mutableInputAndUnreadPinTimeChangesRefreshRenderedValues() = onMain {
        val cell = cell(); val input = dialog(); cell.setDialog(input, "12:00"); measure(cell)
        val first = layout(cell, "nameLayout")
        input.name = "Updated"; input.message = "Changed message"; input.unread_count = 3; input.pinned = true
        cell.setDialog(input, "13:00"); measure(cell)
        assertNotSame(first, layout(cell, "nameLayout"))
        assertEquals("Updated", layout(cell, "nameLayout").text.toString())
        assertEquals("Changed message", layout(cell, "messageLayout").text.toString())
        assertEquals("3", layout(cell, "countLayout").text.toString())
        assertEquals("13:00", layout(cell, "timeLayout").text.toString())
        input.unread_count = 0; input.pinned = false
        cell.setDialog(input, ""); measure(cell)
        for (field in listOf("countLayout", "timeLayout")) {
            assertNull(DialogCell::class.java.getDeclaredField(field).apply { isAccessible = true }.get(cell))
        }
    }

    @Test fun widthAndLayoutDirectionInvalidateThenReuseLayouts() = onMain {
        val cell = cell(); cell.setDialog(dialog(), "12:00"); measure(cell)
        val initial = layout(cell, "nameLayout")
        measure(cell, 320); val narrow = layout(cell, "nameLayout")
        assertNotSame(initial, narrow)
        cell.layoutDirection = View.LAYOUT_DIRECTION_RTL; measure(cell, 320)
        val rtl = layout(cell, "nameLayout"); assertNotSame(narrow, rtl)
        measure(cell, 320); assertSame(rtl, layout(cell, "nameLayout"))
    }

    @Test fun largerTextUpdatesHeightAndReusesUnchangedLayouts() = onMain {
        val cell = cell(); val input = dialog()
        input.message = "Long synthetic message that wraps onto a second line for large text."
        cell.setDialog(input, "12:00"); measure(cell)
        val originalHeight = cell.measuredHeight; val initial = layout(cell, "nameLayout")
        cell.setTextScale(1.5f); measure(cell)
        assertNotSame(initial, layout(cell, "nameLayout"))
        assertTrue(cell.measuredHeight > originalHeight)
        val large = layout(cell, "messageLayout")
        measure(cell); assertSame(large, layout(cell, "messageLayout"))
        cell.setTextScale(Float.NaN); measure(cell)
        assertNotSame(large, layout(cell, "messageLayout"))
        assertEquals(originalHeight, cell.measuredHeight)
    }
}
