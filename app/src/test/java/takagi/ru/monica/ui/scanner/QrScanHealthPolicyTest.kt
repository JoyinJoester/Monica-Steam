package takagi.ru.monica.ui.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrScanHealthPolicyTest {
    @Test
    fun longIdleWithHealthyFramesKeepsScanningAndPeriodicallyRefocuses() {
        val policy = QrScanHealthPolicy()
        policy.onSessionStarted(nowMs = 0L)

        var refocusCount = 0
        for (now in 0L..300_000L step 100L) {
            policy.onFrameStarted(now)
            policy.onFrameCompleted(now + 20L, succeeded = true)
            if (policy.nextAction(now + 20L, previewActive = true) == QrScanHealthAction.Refocus) {
                refocusCount += 1
                policy.onRefocusRequested(now + 20L)
            }
        }

        assertTrue("long-idle scanning should refresh focus repeatedly", refocusCount >= 50)
        assertFalse(policy.restartRequested)
    }

    @Test
    fun stalledFrameRequestsAFullSessionRestart() {
        val policy = QrScanHealthPolicy()
        policy.onSessionStarted(nowMs = 0L)
        policy.onFrameStarted(nowMs = 100L)

        assertEquals(
            QrScanHealthAction.Restart(QrScanRestartReason.FrameStalled),
            policy.nextAction(nowMs = 3_000L, previewActive = true)
        )
    }

    @Test
    fun missingFramesAfterAHealthyStreamRequestsRestart() {
        val policy = QrScanHealthPolicy()
        policy.onSessionStarted(nowMs = 0L)
        policy.onFrameStarted(nowMs = 100L)
        policy.onFrameCompleted(nowMs = 120L, succeeded = true)

        assertEquals(
            QrScanHealthAction.Restart(QrScanRestartReason.FrameStreamStopped),
            policy.nextAction(nowMs = 3_000L, previewActive = true)
        )
    }

    @Test
    fun transientDecoderFailureDoesNotRestartButRepeatedFailuresDo() {
        val policy = QrScanHealthPolicy()
        policy.onSessionStarted(nowMs = 0L)

        repeat(2) { index ->
            val now = 100L + index * 100L
            policy.onFrameStarted(now)
            policy.onFrameCompleted(now + 10L, succeeded = false)
        }
        assertEquals(QrScanHealthAction.None, policy.nextAction(350L, previewActive = true))

        policy.onFrameStarted(400L)
        policy.onFrameCompleted(410L, succeeded = false)
        assertEquals(
            QrScanHealthAction.Restart(QrScanRestartReason.RepeatedDecoderFailure),
            policy.nextAction(420L, previewActive = true)
        )
    }

    @Test
    fun pausedPreviewNeverTriggersRecovery() {
        val policy = QrScanHealthPolicy()
        policy.onSessionStarted(nowMs = 0L)
        policy.onFrameStarted(nowMs = 100L)

        assertEquals(QrScanHealthAction.None, policy.nextAction(10_000L, previewActive = false))
    }
}
