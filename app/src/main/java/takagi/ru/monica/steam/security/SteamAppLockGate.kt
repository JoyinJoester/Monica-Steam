package takagi.ru.monica.steam.security

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.security.lock.MainAppAccessState
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit
import takagi.ru.monica.ui.screens.LoginScreen
import takagi.ru.monica.ui.screens.ResetPasswordScreen
import takagi.ru.monica.ui.screens.SecurityQuestionsVerificationScreen
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel

private enum class SteamAppRecoveryRoute {
    LOGIN,
    SECURITY_QUESTIONS,
    RESET_PASSWORD
}

/**
 * Monica's startup/foreground authentication gate adapted to Steam's optional
 * master-password model. All credential, biometric and session work remains in
 * Monica's existing components; this composable only selects the active page.
 */
@Composable
fun SteamAppLockGate(
    settings: AppSettings,
    settingsViewModel: SettingsViewModel,
    passwordViewModel: PasswordViewModel,
    securityManager: SecurityManager,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isAuthenticated by passwordViewModel.isAuthenticated.collectAsState()
    var accessState by remember { mutableStateOf<MainAppAccessState?>(null) }
    var recoveryRoute by rememberSaveable { mutableStateOf(SteamAppRecoveryRoute.LOGIN) }

    fun applyAccessState(
        state: MainAppAccessState,
        currentlyAuthenticated: Boolean
    ) {
        when {
            currentlyAuthenticated && !state.canEnterMainApp -> {
                passwordViewModel.logout()
            }
            !currentlyAuthenticated && state.bypassEnabled -> {
                passwordViewModel.markAuthenticatedForBypass()
            }
            !currentlyAuthenticated && state.canRestoreSession -> {
                passwordViewModel.restoreAuthenticatedUiState()
            }
        }
        accessState = state
    }

    LaunchedEffect(
        passwordViewModel,
        securityManager,
        settings.autoLockMinutes,
        settings.disablePasswordVerification
    ) {
        SessionManager.updateAutoLockTimeout(settings.autoLockMinutes)
        val loadedState = withContext(Dispatchers.IO) {
            SteamAppLockPolicy.resolveAccessState(
                securityManager = securityManager,
                context = context.applicationContext,
                autoLockMinutes = settings.autoLockMinutes,
                disablePasswordVerification = settings.disablePasswordVerification
            )
        }
        applyAccessState(loadedState, isAuthenticated)
    }

    val currentIsAuthenticated by rememberUpdatedState(isAuthenticated)
    val currentSettings by rememberUpdatedState(settings)
    DisposableEffect(lifecycleOwner, passwordViewModel, securityManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_START) return@LifecycleEventObserver

            SessionManager.updateAutoLockTimeout(currentSettings.autoLockMinutes)
            val resumedState = SteamAppLockPolicy.resolveAccessState(
                securityManager = securityManager,
                context = context.applicationContext,
                autoLockMinutes = currentSettings.autoLockMinutes,
                disablePasswordVerification = currentSettings.disablePasswordVerification
            )
            applyAccessState(resumedState, currentIsAuthenticated)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            recoveryRoute = SteamAppRecoveryRoute.LOGIN
        }
    }

    val resolvedState = accessState
    when {
        resolvedState == null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        !isAuthenticated && !resolvedState.canEnterMainApp -> {
            BackHandler(enabled = recoveryRoute != SteamAppRecoveryRoute.LOGIN) {
                recoveryRoute = when (recoveryRoute) {
                    SteamAppRecoveryRoute.RESET_PASSWORD ->
                        SteamAppRecoveryRoute.SECURITY_QUESTIONS
                    SteamAppRecoveryRoute.SECURITY_QUESTIONS,
                    SteamAppRecoveryRoute.LOGIN -> SteamAppRecoveryRoute.LOGIN
                }
            }
            AnimatedContent(
                targetState = recoveryRoute,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    easyNotesScreenEnter().togetherWith(easyNotesScreenExit())
                },
                label = "MonicaSteamAuthentication"
            ) { route ->
                when (route) {
                    SteamAppRecoveryRoute.LOGIN -> LoginScreen(
                        viewModel = passwordViewModel,
                        settingsViewModel = settingsViewModel,
                        onForgotPassword = if (securityManager.areSecurityQuestionsSet()) {
                            { recoveryRoute = SteamAppRecoveryRoute.SECURITY_QUESTIONS }
                        } else {
                            null
                        }
                    )
                    SteamAppRecoveryRoute.SECURITY_QUESTIONS ->
                        SecurityQuestionsVerificationScreen(
                            securityManager = securityManager,
                            onNavigateBack = {
                                recoveryRoute = SteamAppRecoveryRoute.LOGIN
                            },
                            onVerificationSuccess = {
                                recoveryRoute = SteamAppRecoveryRoute.RESET_PASSWORD
                            }
                        )
                    SteamAppRecoveryRoute.RESET_PASSWORD -> ResetPasswordScreen(
                        securityManager = securityManager,
                        skipCurrentPassword = true,
                        onNavigateBack = {
                            recoveryRoute = SteamAppRecoveryRoute.SECURITY_QUESTIONS
                        },
                        onResetSuccess = {
                            passwordViewModel.logout()
                            recoveryRoute = SteamAppRecoveryRoute.LOGIN
                        }
                    )
                }
            }
        }
        else -> content()
    }
}
