package takagi.ru.monica.steam.security

import android.content.Context
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.lock.MainAppAccessState
import takagi.ru.monica.security.lock.MainAppLockPolicy

/**
 * Steam's opt-in adapter around Monica's main-app lock policy.
 *
 * Monica's full password vault requires first-time password setup. Steam is a
 * standalone companion and must remain usable without a local master password,
 * so the unconfigured state is explicitly treated as an unlocked optional
 * feature. Once a password exists, every decision is delegated to Monica's
 * shared policy and its session/keystore checks.
 */
object SteamAppLockPolicy {
    fun resolveAccessState(
        securityManager: SecurityManager,
        context: Context,
        autoLockMinutes: Int,
        disablePasswordVerification: Boolean
    ): MainAppAccessState {
        return resolveUnconfiguredState(securityManager.isMasterPasswordSet())
            ?: MainAppLockPolicy.resolveAccessState(
                securityManager = securityManager,
                context = context,
                autoLockMinutes = autoLockMinutes,
                disablePasswordVerification = disablePasswordVerification
            )
    }

    internal fun resolveUnconfiguredState(
        masterPasswordSet: Boolean
    ): MainAppAccessState? {
        if (masterPasswordSet) return null

        return MainAppAccessState(
            isFirstTime = false,
            bypassEnabled = true,
            canRestoreSession = false,
            reason = "steam_lock_not_configured"
        )
    }
}
