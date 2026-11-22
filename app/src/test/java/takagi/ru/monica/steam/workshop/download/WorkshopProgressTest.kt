package takagi.ru.monica.steam.workshop.download

import org.junit.Assert.*
import org.junit.Test

class WorkshopProgressTest {
    @Test fun unknownTotalsRemainIndeterminateInsteadOfLookingComplete() {
        assertNull(WorkshopDownloadProgress(WorkshopDownloadStage.CHECKING_SIZE).fraction)
        assertNull(WorkshopDownloadProgress(WorkshopDownloadStage.SAVING, 0, 0).fraction)
    }

    @Test fun savingHasItsOwnFractionAndChecksumCountsFiles() {
        assertEquals(0.25f, WorkshopDownloadProgress(WorkshopDownloadStage.SAVING, 25, 100).fraction!!, 0f)
        assertTrue(WorkshopDownloadProgress(WorkshopDownloadStage.VERIFYING, 1, 4).countsFiles)
        assertFalse(WorkshopDownloadProgress(WorkshopDownloadStage.SAVING, 1, 4).countsFiles)
    }

    @Test fun invalidProgressCannotEscapeTheIndicatorRange() {
        assertEquals(0f, WorkshopDownloadProgress(WorkshopDownloadStage.DOWNLOADING, -5, 100).fraction!!, 0f)
        assertEquals(1f, WorkshopDownloadProgress(WorkshopDownloadStage.DOWNLOADING, Long.MAX_VALUE, 100).fraction!!, 0f)
    }
}
