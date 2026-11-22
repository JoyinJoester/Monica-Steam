package takagi.ru.monica.steam.chat

import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.SystemClock

/** UiAutomation may return null while the emulator display starts rendering. */
internal fun captureTelegramUiEvidence(instrumentation: Instrumentation): Bitmap {
    val deadline = SystemClock.uptimeMillis() + 5_000
    var bitmap = instrumentation.uiAutomation.takeScreenshot()
    while (bitmap == null && SystemClock.uptimeMillis() < deadline) {
        SystemClock.sleep(100)
        bitmap = instrumentation.uiAutomation.takeScreenshot()
    }
    return checkNotNull(bitmap) { "Device display did not produce a screenshot within 5 seconds" }
}
