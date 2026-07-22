package takagi.ru.monica.ui.scanner

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal class QrScannerDiagnostics(
    private val label: String,
    private val sink: (String) -> Unit
) {
    private val startedAt = SystemClock.elapsedRealtime()
    private val framesStarted = AtomicInteger(0)
    private val framesSkipped = AtomicInteger(0)
    private val framesCompleted = AtomicInteger(0)
    private val frameFailures = AtomicInteger(0)
    private val emptyResults = AtomicInteger(0)
    private val invalidResults = AtomicInteger(0)
    private val matchedResults = AtomicInteger(0)
    private val refocusRequests = AtomicInteger(0)
    private val sessionRestarts = AtomicInteger(0)
    private val lastFrameAt = AtomicLong(0L)
    private val firstFrameLogged = AtomicBoolean(false)
    private val firstEmptyLogged = AtomicBoolean(false)
    private val firstInvalidLogged = AtomicBoolean(false)

    fun logScannerStarted(requestedFormats: Int, mlKitFormats: Int) {
        log("scanner_started", "requested_formats=$requestedFormats mlkit_formats=$mlKitFormats")
    }

    fun logSessionStarted(generation: Int) {
        log("session_started", "generation=$generation")
    }

    fun logCameraProviderRequested(mlKitFormatCount: Int) {
        log("camera_controller_requested", "mlkit_formats=$mlKitFormatCount")
    }

    fun logCameraProviderFailed(error: Throwable) {
        log("camera_controller_failed", "error=${error.safeErrorName()}")
    }

    fun logCameraBindSuccess(durationMs: Long) {
        log("camera_bind_success", "duration_ms=$durationMs")
    }

    fun logCameraBindFailed(error: Throwable) {
        log("camera_bind_failed", "error=${error.safeErrorName()}")
    }

    fun logPreviewState(streaming: Boolean) {
        log("preview_state", "streaming=$streaming")
    }

    fun logRefocusRequested(reason: String) {
        val count = refocusRequests.incrementAndGet()
        log("refocus_requested", "count=$count reason=$reason")
    }

    fun logRefocusFailed(error: Throwable) {
        log("refocus_failed", "error=${error.safeErrorName()}")
    }

    fun logSessionRestartRequested(reason: QrScanRestartReason) {
        val count = sessionRestarts.incrementAndGet()
        log("session_restart_requested", "count=$count reason=${reason.name}")
    }

    fun logFrameStarted(rotationDegrees: Int) {
        val count = framesStarted.incrementAndGet()
        lastFrameAt.set(SystemClock.elapsedRealtime())
        if (firstFrameLogged.compareAndSet(false, true)) {
            log("first_frame", "rotation=$rotationDegrees")
        } else if (count % FRAME_MILESTONE_INTERVAL == 0) {
            log("frame_milestone", "frames_started=$count rotation=$rotationDegrees")
        }
    }

    fun logFrameSkipped() {
        val count = framesSkipped.incrementAndGet()
        if (count == 1 || count % SKIP_MILESTONE_INTERVAL == 0) {
            log("frame_skipped_busy", "skipped=$count")
        }
    }

    fun logFrameMissingImage() {
        frameFailures.incrementAndGet()
        log("frame_missing_image")
    }

    fun logFrameSuccess(durationMs: Long, barcodeCount: Int, candidateCount: Int, matched: Boolean) {
        framesCompleted.incrementAndGet()
        when {
            matched -> {
                val count = matchedResults.incrementAndGet()
                log("frame_match", "matches=$count duration_ms=$durationMs barcodes=$barcodeCount candidates=$candidateCount")
            }
            candidateCount > 0 -> {
                val count = invalidResults.incrementAndGet()
                if (firstInvalidLogged.compareAndSet(false, true) || count % INVALID_MILESTONE_INTERVAL == 0) {
                    log("frame_invalid_candidates", "invalid=$count duration_ms=$durationMs barcodes=$barcodeCount candidates=$candidateCount")
                }
            }
            else -> {
                val count = emptyResults.incrementAndGet()
                if (firstEmptyLogged.compareAndSet(false, true) || count % EMPTY_MILESTONE_INTERVAL == 0) {
                    log("frame_empty", "empty=$count duration_ms=$durationMs")
                }
            }
        }
    }

    fun logFrameFailure(error: Throwable) {
        val count = frameFailures.incrementAndGet()
        log("frame_scan_failed", "failures=$count error=${error.safeErrorName()}")
    }

    fun logResultAccepted() {
        log("result_accepted", snapshot(processing = false))
    }

    fun logGalleryStart() {
        log("gallery_start")
    }

    fun logGalleryDecodeFailed(error: Throwable) {
        log("gallery_decode_failed", "error=${error.safeErrorName()}")
    }

    fun logGalleryScanFailed(error: Throwable) {
        log("gallery_scan_failed", "error=${error.safeErrorName()}")
    }

    fun logGalleryResult(
        durationMs: Long,
        barcodeCount: Int,
        candidateCount: Int,
        matched: Boolean,
        invalid: Boolean
    ) {
        log(
            "gallery_result",
            "duration_ms=$durationMs barcodes=$barcodeCount candidates=$candidateCount matched=$matched invalid=$invalid"
        )
    }

    fun logHeartbeat(processing: Boolean) {
        log("heartbeat", snapshot(processing))
    }

    fun logDispose(processing: Boolean) {
        log("scanner_dispose", snapshot(processing))
    }

    private fun snapshot(processing: Boolean): String {
        val now = SystemClock.elapsedRealtime()
        val lastFrame = lastFrameAt.get()
        val lastFrameAge = if (lastFrame > 0L) now - lastFrame else -1L
        return "uptime_ms=${now - startedAt} frames_started=${framesStarted.get()} frames_completed=${framesCompleted.get()} skipped=${framesSkipped.get()} failures=${frameFailures.get()} empty=${emptyResults.get()} invalid=${invalidResults.get()} matches=${matchedResults.get()} refocus=${refocusRequests.get()} restarts=${sessionRestarts.get()} processing=$processing last_frame_age_ms=$lastFrameAge"
    }

    private fun log(event: String, detail: String = "") {
        val suffix = detail.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        sink("qr_scan label=$label event=$event$suffix")
    }

    private fun Throwable.safeErrorName(): String {
        return javaClass.simpleName.ifBlank { "Throwable" }
    }

    private companion object {
        private const val FRAME_MILESTONE_INTERVAL = 300
        private const val SKIP_MILESTONE_INTERVAL = 50
        private const val EMPTY_MILESTONE_INTERVAL = 120
        private const val INVALID_MILESTONE_INTERVAL = 20
    }
}
