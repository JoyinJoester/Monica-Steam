package takagi.ru.monica.steam.security

import android.content.Context
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.lock.MainAppAccessState
import takagi.ru.monica.security.lock.MainAppLockPolicy
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger

/**
 * Steam requires first-time password setup, then delegates authentication to
 * Monica's shared session/keystore policy. Lock scope is selected by the caller.
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
            isFirstTime = true,
            bypassEnabled = false,
            canRestoreSession = false,
            reason = "steam_master_password_setup_required"
        )
    }

    /**
     * Keystore/EncryptedSharedPreferences can fail while the process is
     * starting (for example after a device restore or a transient keystore
     * unlock failure).  Startup authentication must degrade to a locked UI,
     * not throw out of a Compose effect and terminate the process.
     */
    fun resolveAccessStateOrLocked(
        securityManager: SecurityManager,
        context: Context,
        autoLockMinutes: Int,
        disablePasswordVerification: Boolean
    ): MainAppAccessState {
        return try {
            resolveAccessState(
                securityManager = securityManager,
                context = context,
                autoLockMinutes = autoLockMinutes,
                disablePasswordVerification = disablePasswordVerification
            )
        } catch (error: Throwable) {
            SteamDiagLogger.append(
                "app_lock_policy failed type=${error::class.java.simpleName}"
            )
            MainAppAccessState(
                isFirstTime = false,
                bypassEnabled = false,
                canRestoreSession = false,
                reason = "steam_lock_policy_error"
            )
        }
    }
}
