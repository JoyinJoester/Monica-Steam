package takagi.ru.monica.steam.security

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.AppSettings

class SteamStartupPreparationTest {
    @Test
    fun existingPasswordPreservesTheUsersLockScope() = runBlocking {
        val state = prepareSteamStartup(
            isMasterPasswordSet = { true },
            prepareFirstPasswordDefaults = { error("Must not overwrite existing settings") }
        )
        assertEquals(SteamStartupPreparation.READY, state)
    }

    @Test
    fun firstUsePreparesTokenScopeButStillRequiresAPassword() = runBlocking {
        var scopePrepared = false
        val state = prepareSteamStartup(
            isMasterPasswordSet = { false },
            prepareFirstPasswordDefaults = { scopePrepared = true }
        )
        assertTrue(scopePrepared)
        assertEquals(SteamStartupPreparation.PASSWORD_REQUIRED, state)
    }

    @Test
    fun reentryRemainsBlockedUntilAPasswordActuallyExists() = runBlocking {
        var passwordSet = false
        var preparations = 0
        suspend fun enter() = prepareSteamStartup(
            isMasterPasswordSet = { passwordSet },
            prepareFirstPasswordDefaults = { preparations++ }
        )
        assertEquals(SteamStartupPreparation.PASSWORD_REQUIRED, enter())
        assertEquals(SteamStartupPreparation.PASSWORD_REQUIRED, enter())
        passwordSet = true
        assertEquals(SteamStartupPreparation.READY, enter())
        assertEquals(2, preparations)
    }

    @Test
    fun unreadableSecureStorageNeverInitializesOrResetsSettings() = runBlocking {
        val state = prepareSteamStartup(
            isMasterPasswordSet = { throw IOException("storage unavailable") },
            prepareFirstPasswordDefaults = { error("Must not treat a read failure as first use") }
        )
        assertEquals(SteamStartupPreparation.UNAVAILABLE, state)
    }

    @Test
    fun failedDefaultWriteBlocksSetupUntilRetrySucceeds() = runBlocking {
        var writeFails = true
        suspend fun enter() = prepareSteamStartup(
            isMasterPasswordSet = { false },
            prepareFirstPasswordDefaults = { if (writeFails) throw IOException("disk unavailable") }
        )
        assertEquals(SteamStartupPreparation.UNAVAILABLE, enter())
        writeFails = false
        assertEquals(SteamStartupPreparation.PASSWORD_REQUIRED, enter())
    }

    @Test
    fun cancelledPreparationDoesNotPublishAnAccessState() = runBlocking {
        val cancellation = CancellationException("activity closed")
        try {
            prepareSteamStartup(
                isMasterPasswordSet = { false },
                prepareFirstPasswordDefaults = { throw cancellation }
            )
            error("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test
    fun defaultsProtectTokensWithoutEnablingGlobalAuthenticationBypass() {
        val settings = AppSettings()
        assertTrue(settings.steamLockTokenPageOnly)
        assertFalse(settings.disablePasswordVerification)
        assertTrue(shouldProtectSteamSensitiveSurface(
            tokenPageOnly = settings.steamLockTokenPageOnly,
            startupVerificationBypass = settings.disablePasswordVerification
        ))
    }
}
