package takagi.ru.monica.steam.quickaccess

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class SteamWidgetImageSyncTest {
    @Test fun publishesReadyImagesBeforeSlowProfileAndSurvivesOneFailedDownload() = runBlocking {
        val slowProfile = CompletableDeferred<Boolean>()
        val published = CompletableDeferred<Unit>()
        val work = async {
            enrichWidgetImages(listOf({ true }, { slowProfile.await() }, { throw java.io.IOException("offline") })) {
                published.complete(Unit)
            }
        }
        withTimeout(1000) { published.await() }
        assertFalse("A completed image must not wait for the profile request", work.isCompleted)
        slowProfile.complete(true)
        assertFalse("Partial downloads request retry without losing the successful images", work.await())
    }
}
