package takagi.ru.monica.steam.confirmations

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamConfirmation

class SteamConfirmationSessionRecoveryTest {
    private val account = SteamAccount(1, "76561198000000001", "fixture", "fixture", "android:test", "secret",
        "identity", null, null, "old", "refresh", null, "{}", true, 0, 0, 0)
    private val auth = SteamApiException("expired", authenticationRequired = true)

    @Test fun successfulReadDoesNotRefresh() = runTest {
        var refreshes = 0
        assertEquals(emptyList<SteamConfirmation>(), fetchSteamConfirmationsWithSessionRecovery(account,
            { refreshes++; it }, { emptyList() }))
        assertEquals(0, refreshes)
    }

    @Test fun authenticationFailureRefreshesAndReadsOnceWithTheNewToken() = runTest {
        val tokens = mutableListOf<String?>()
        var refreshes = 0
        val result = fetchSteamConfirmationsWithSessionRecovery(account,
            { refreshes++; it.copy(accessToken = "new") },
            { tokens += it.accessToken; if (it.accessToken == "old") throw auth else emptyList() })
        assertTrue(result.isEmpty())
        assertEquals(listOf("old", "new"), tokens)
        assertEquals(1, refreshes)
    }

    @Test fun repeatedAuthenticationFailureStopsAfterOneRetry() = runTest {
        var requests = 0
        var refreshes = 0
        val failure = runCatching {
            fetchSteamConfirmationsWithSessionRecovery(account,
                { refreshes++; it.copy(accessToken = "new") }, { requests++; throw auth })
        }.exceptionOrNull()
        assertSame(auth, failure)
        assertEquals(2, requests)
        assertEquals(1, refreshes)
    }

    @Test fun unchangedMissingOrDifferentAccountCredentialsAreNotRetried() = runTest {
        for (refreshed in listOf(null, account, account.copy(id = 2, accessToken = "new"),
            account.copy(steamId = "76561198000000002", accessToken = "new"), account.copy(accessToken = ""))) {
            var requests = 0
            val failure = runCatching {
                fetchSteamConfirmationsWithSessionRecovery(account, { refreshed }, { requests++; throw auth })
            }.exceptionOrNull()
            assertSame(auth, failure)
            assertEquals(1, requests)
        }
    }

    @Test fun networkRejectionAndCancellationAreNeverRetriedAsAuthenticationFailures() = runTest {
        for (failure in listOf(IOException("offline"), SteamApiException("Oh noooooes!"), CancellationException("cancelled"))) {
            var refreshes = 0
            val actual = runCatching {
                fetchSteamConfirmationsWithSessionRecovery(account, { refreshes++; it }, { throw failure })
            }.exceptionOrNull()
            assertSame(failure, actual)
            assertEquals(0, refreshes)
        }
    }

    @Test fun accountWithoutRefreshTokenDoesNotAttemptRecovery() = runTest {
        var refreshes = 0
        val failure = runCatching {
            fetchSteamConfirmationsWithSessionRecovery(account.copy(refreshToken = null),
                { refreshes++; it }, { throw auth })
        }.exceptionOrNull()
        assertSame(auth, failure)
        assertEquals(0, refreshes)
    }
}
