package takagi.ru.monica.steam.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.components.PasswordVerificationContent
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel

/** Mandatory setup precedes both the optional whole-app lock and all navigation. */
@Composable
fun SteamStartupGate(
    settings: AppSettings,
    settingsManager: SettingsManager,
    settingsViewModel: SettingsViewModel,
    passwordViewModel: PasswordViewModel,
    securityManager: SecurityManager,
    content: @Composable () -> Unit
) {
    // Deliberately not saveable: activity/process recreation must recheck storage.
    var preparation by remember { mutableStateOf(SteamStartupPreparation.CHECKING) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(securityManager, settingsManager, attempt) {
        preparation = SteamStartupPreparation.CHECKING
        preparation = withContext(Dispatchers.IO) {
            prepareSteamStartup(
                isMasterPasswordSet = securityManager::isMasterPasswordSet,
                prepareFirstPasswordDefaults = settingsManager::prepareSteamMasterPasswordSetup
            )
        }
    }

    if (preparation == SteamStartupPreparation.READY) {
        // Merely finding a password does not authenticate or restore a session.
        // The Token/backup/security gates still verify when startup is unlocked.
        SteamAppLockGate(
            enabled = !settings.steamLockTokenPageOnly,
            settings = settings,
            settingsViewModel = settingsViewModel,
            passwordViewModel = passwordViewModel,
            securityManager = securityManager,
            content = content
        )
        return
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (preparation) {
            SteamStartupPreparation.PASSWORD_REQUIRED -> BoxWithConstraints(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            ) {
                PasswordVerificationContent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight),
                    isFirstTime = true,
                    disablePasswordVerification = false,
                    biometricEnabled = false,
                    setupDescription = stringResource(R.string.steam_master_password_setup_description),
                    onVerifyPassword = { false },
                    onSetPassword = passwordViewModel::setMasterPassword,
                    onSuccess = {
                        // Re-read secure storage; do not trust the UI success callback alone.
                        preparation = SteamStartupPreparation.CHECKING
                        attempt += 1
                    }
                )
            }
            SteamStartupPreparation.UNAVAILABLE -> Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.steam_master_password_setup_unavailable),
                    textAlign = TextAlign.Center
                )
                Button(onClick = {
                    preparation = SteamStartupPreparation.CHECKING
                    attempt += 1
                }) {
                    Text(stringResource(R.string.steam_store_retry))
                }
            }
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
