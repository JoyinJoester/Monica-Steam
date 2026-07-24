package takagi.ru.monica.ui.scanner

internal enum class QrFrameAdmission {
    Acquired,
    Busy,
    Stopped
}

/**
 * Serializes frame admission with scanner shutdown so native decoder resources
 * cannot be released while an analyzer callback is entering or still running.
 */
internal class QrFrameLifecycleGate {
    private var acceptingFrames = true
    private var frameInFlight = false
    private var releaseClaimed = false

    @Synchronized
    fun tryAcquireFrame(): QrFrameAdmission {
        if (!acceptingFrames) return QrFrameAdmission.Stopped
        if (frameInFlight) return QrFrameAdmission.Busy
        frameInFlight = true
        return QrFrameAdmission.Acquired
    }

    @Synchronized
    fun requestStop(): Boolean {
        acceptingFrames = false
        return claimReleaseIfReady()
    }

    @Synchronized
    fun completeFrame(): Boolean {
        check(frameInFlight) { "No QR frame is in flight" }
        frameInFlight = false
        return claimReleaseIfReady()
    }

    @Synchronized
    fun isFrameInFlight(): Boolean = frameInFlight

    private fun claimReleaseIfReady(): Boolean {
        if (acceptingFrames || frameInFlight || releaseClaimed) return false
        releaseClaimed = true
        return true
    }
}
