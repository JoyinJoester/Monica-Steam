package takagi.ru.monica.ui.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrFrameLifecycleGateTest {
    @Test
    fun stopWithoutAnActiveFrameReleasesResourcesImmediatelyAndOnlyOnce() {
        val gate = QrFrameLifecycleGate()

        assertTrue(gate.requestStop())
        assertFalse(gate.requestStop())
        assertEquals(QrFrameAdmission.Stopped, gate.tryAcquireFrame())
    }

    @Test
    fun stopDefersResourceReleaseUntilTheActiveFrameCompletes() {
        val gate = QrFrameLifecycleGate()

        assertEquals(QrFrameAdmission.Acquired, gate.tryAcquireFrame())
        assertTrue(gate.isFrameInFlight())
        assertFalse(gate.requestStop())
        assertTrue(gate.completeFrame())
        assertFalse(gate.isFrameInFlight())
        assertFalse(gate.requestStop())
    }

    @Test
    fun onlyOneFrameCanEnterTheNativeDecoderAtATime() {
        val gate = QrFrameLifecycleGate()

        assertEquals(QrFrameAdmission.Acquired, gate.tryAcquireFrame())
        assertEquals(QrFrameAdmission.Busy, gate.tryAcquireFrame())
        assertFalse(gate.completeFrame())
        assertEquals(QrFrameAdmission.Acquired, gate.tryAcquireFrame())
    }
}
