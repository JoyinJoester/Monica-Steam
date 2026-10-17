package takagi.ru.monica.steam.backup.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.components.M3IdentityVerifyDialog
import takagi.ru.monica.utils.BiometricHelper

/** Fresh, single-export authentication; never saved across navigation or recreation. */
@Composable
internal fun SteamMaFileExportAuthentication(
    securityManager: SecurityManager,
    biometricEnabled: Boolean,
    onAuthorized: () -> Unit,
    onDismiss: () -> Unit,
    onUnavailable: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val biometric = remember(context) { BiometricHelper(context) }
    var active by remember { mutableStateOf(true) }
    var requiresAuthentication by remember { mutableStateOf<Boolean?>(null) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }

    fun dismiss() {
        active = false
        password = ""
        onDismiss()
    }

    fun authorize() {
        if (!active || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        active = false
        password = ""
        onAuthorized()
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) dismiss()
        }
        lifecycle.addObserver(observer)
        onDispose {
            active = false
            lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(securityManager) {
        try {
            val required = withContext(Dispatchers.IO) { securityManager.isMasterPasswordSet() }
            lifecycle.withResumed {
                if (active) {
                    requiresAuthentication = required
                    // First-time setup is mandatory; no-password export is never authorized.
                    if (!required) {
                        active = false
                        onUnavailable()
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (active) {
                active = false
                onUnavailable()
            }
        }
    }

    if (!active || requiresAuthentication != true) return

    val biometricAction: (() -> Unit)? = if (
        biometricEnabled && activity != null && biometric.isBiometricAvailable() && !verifying
    ) {
        {
            verifying = true
            biometric.authenticate(
                activity = activity,
                title = context.getString(R.string.verify_identity),
                subtitle = context.getString(R.string.steam_mafile_export_verify_subtitle),
                negativeButtonText = context.getString(R.string.cancel),
                onSuccess = {
                    if (active) {
                        verifying = false
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                            runCatching { securityManager.unlockVaultWithBiometric() }.getOrDefault(false)
                        ) {
                            authorize()
                        } else {
                            error = context.getString(R.string.biometric_requires_password_reentry)
                        }
                    }
                },
                onError = { if (active) { verifying = false; error = it } },
                onFailed = { if (active) verifying = false }
            )
        }
    } else null

    M3IdentityVerifyDialog(
        title = stringResource(R.string.verify_identity),
        message = stringResource(R.string.steam_mafile_export_verify_message),
        passwordValue = password,
        onPasswordChange = { password = it; error = null },
        onDismiss = ::dismiss,
        onConfirm = {
            if (active && !verifying) {
                verifying = true
                val input = password
                scope.launch {
                    val verified = withContext(Dispatchers.IO) {
                        runCatching { securityManager.unlockVaultWithPassword(input) }.getOrDefault(false)
                    }
                    if (active) {
                        verifying = false
                        if (verified) authorize()
                        else error = context.getString(R.string.current_password_incorrect)
                    }
                }
            }
        },
        confirmText = stringResource(R.string.start_export),
        confirmEnabled = password.isNotBlank() && !verifying,
        destructiveConfirm = false,
        isPasswordError = error != null,
        passwordErrorText = error,
        onBiometricClick = biometricAction,
        biometricHintText = if (biometricAction == null && !verifying) {
            stringResource(R.string.biometric_not_available)
        } else null
    )
}
