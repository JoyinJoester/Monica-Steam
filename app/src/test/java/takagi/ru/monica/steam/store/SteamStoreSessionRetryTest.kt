package takagi.ru.monica.steam.store

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreSessionRetryTest {
    @Test
    fun refreshesRejectedStoreSessionAndRetriesOnce() = runTest {
        val requestedCredentials = mutableListOf<SteamStoreAccountCredentials>()
        var refreshCount = 0

        val value = executeSteamStoreAccountRetry(
            initialCredentials = SteamStoreAccountCredentials(
                accessToken = "old-access",
                steamLoginSecure = "old-session"
            ),
            forceRefreshCredentials = {
                refreshCount++
                SteamStoreAccountCredentials(
                    accessToken = "new-access",
                    steamLoginSecure = "new-session"
                )
            },
            request = { credentials ->
                requestedCredentials += credentials
                if (credentials.accessToken == "old-access") {
                    throw SteamStoreAccountRegionException()
                }
                "loaded"
            }
        )

        assertEquals("loaded", value)
        assertEquals(listOf("old-access", "new-access"), requestedCredentials.map { it.accessToken })
        assertEquals(1, refreshCount)
    }

    @Test
    fun doesNotRetryUnrelatedStoreFailure() = runTest {
        var refreshCount = 0

        val failure = runCatching {
            executeSteamStoreAccountRetry(
                initialCredentials = SteamStoreAccountCredentials("access", "session"),
                forceRefreshCredentials = {
                    refreshCount++
                    SteamStoreAccountCredentials("new-access", "new-session")
                },
                request = { throw IllegalStateException("network") }
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(0, refreshCount)
    }

    @Test
    fun keepsOriginalRegionFailureWhenRefreshCannotRotateSession() = runTest {
        var requestCount = 0

        val failure = runCatching {
            executeSteamStoreAccountRetry(
                initialCredentials = SteamStoreAccountCredentials("access", "session"),
                forceRefreshCredentials = {
                    SteamStoreAccountCredentials("access", "session")
                },
                request = {
                    requestCount++
                    throw SteamStoreAccountRegionException()
                }
            )
        }.exceptionOrNull()

        assertTrue(failure is SteamStoreAccountRegionException)
        assertEquals(1, requestCount)
    }
}
