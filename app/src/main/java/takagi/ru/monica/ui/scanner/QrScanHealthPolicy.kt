package takagi.ru.monica.ui.scanner

internal enum class QrScanRestartReason {
    FrameStalled,
    FrameStreamStopped,
    RepeatedDecoderFailure
}

internal sealed interface QrScanHealthAction {
    data object None : QrScanHealthAction
    data object Refocus : QrScanHealthAction
    data class Restart(val reason: QrScanRestartReason) : QrScanHealthAction
}

/**
 * Pure health policy for a live QR scan session.
 *
 * Long periods without a barcode are healthy as long as camera frames continue to complete.
 * The policy periodically refreshes focus/metering, and only rebuilds the session when the
 * frame stream stalls or ML Kit repeatedly fails to process frames.
 */
internal class QrScanHealthPolicy(
    private val refocusIntervalMs: Long = DEFAULT_REFOCUS_INTERVAL_MS,
    private val frameStallTimeoutMs: Long = DEFAULT_FRAME_STALL_TIMEOUT_MS,
    private val frameStreamTimeoutMs: Long = DEFAULT_FRAME_STREAM_TIMEOUT_MS,
    private val decoderFailureThreshold: Int = DEFAULT_DECODER_FAILURE_THRESHOLD
) {
    private var sessionStartedAtMs: Long = 0L
    private var activeFrameStartedAtMs: Long? = null
    private var lastFrameCompletedAtMs: Long? = null
    private var lastRefocusAtMs: Long = 0L
    private var consecutiveDecoderFailures: Int = 0
    private var pendingRestartReason: QrScanRestartReason? = null

    var restartRequested: Boolean = false
        private set

    @Synchronized
    fun onSessionStarted(nowMs: Long) {
        sessionStartedAtMs = nowMs
        lastRefocusAtMs = nowMs
        activeFrameStartedAtMs = null
        lastFrameCompletedAtMs = null
        consecutiveDecoderFailures = 0
        pendingRestartReason = null
        restartRequested = false
    }

    @Synchronized
    fun onFrameStarted(nowMs: Long) {
        activeFrameStartedAtMs = nowMs
    }

    @Synchronized
    fun onFrameCompleted(nowMs: Long, succeeded: Boolean) {
        activeFrameStartedAtMs = null
        lastFrameCompletedAtMs = nowMs
        consecutiveDecoderFailures = if (succeeded) {
            0
        } else {
            consecutiveDecoderFailures + 1
        }
        if (consecutiveDecoderFailures >= decoderFailureThreshold) {
            pendingRestartReason = QrScanRestartReason.RepeatedDecoderFailure
        }
    }

    @Synchronized
    fun onRefocusRequested(nowMs: Long) {
        lastRefocusAtMs = nowMs
    }

    @Synchronized
    fun nextAction(nowMs: Long, previewActive: Boolean): QrScanHealthAction {
        if (!previewActive || restartRequested) return QrScanHealthAction.None

        pendingRestartReason?.let { reason ->
            restartRequested = true
            return QrScanHealthAction.Restart(reason)
        }

        activeFrameStartedAtMs?.let { frameStartedAt ->
            if (nowMs - frameStartedAt >= frameStallTimeoutMs) {
                restartRequested = true
                return QrScanHealthAction.Restart(QrScanRestartReason.FrameStalled)
            }
        }

        lastFrameCompletedAtMs?.let { lastFrameAt ->
            if (activeFrameStartedAtMs == null && nowMs - lastFrameAt >= frameStreamTimeoutMs) {
                restartRequested = true
                return QrScanHealthAction.Restart(QrScanRestartReason.FrameStreamStopped)
            }
        }

        if (nowMs - lastRefocusAtMs >= refocusIntervalMs) {
            return QrScanHealthAction.Refocus
        }

        return QrScanHealthAction.None
    }

    companion object {
        const val DEFAULT_REFOCUS_INTERVAL_MS = 4_000L
        const val DEFAULT_FRAME_STALL_TIMEOUT_MS = 2_500L
        const val DEFAULT_FRAME_STREAM_TIMEOUT_MS = 2_500L
        const val DEFAULT_DECODER_FAILURE_THRESHOLD = 3
    }
}
