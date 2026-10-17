package takagi.ru.monica.steam.security

import kotlinx.coroutines.CancellationException

internal enum class SteamStartupPreparation {
    CHECKING,
    PASSWORD_REQUIRED,
    READY,
    UNAVAILABLE
}

/** Password presence, never a remembered authentication/bypass flag, gates first use. */
internal suspend fun prepareSteamStartup(
    isMasterPasswordSet: () -> Boolean,
    prepareFirstPasswordDefaults: suspend () -> Unit
): SteamStartupPreparation = try {
    if (isMasterPasswordSet()) {
        SteamStartupPreparation.READY
    } else {
        // Persist the intended scope before accepting a password, including after
        // an interrupted setup or an upgrade from the optional-password version.
        prepareFirstPasswordDefaults()
        SteamStartupPreparation.PASSWORD_REQUIRED
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    // A storage read/write error must never look like a new or unlocked install.
    SteamStartupPreparation.UNAVAILABLE
}
