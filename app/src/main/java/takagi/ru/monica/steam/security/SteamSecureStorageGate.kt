package takagi.ru.monica.steam.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.security.LocalVaultRecovery
import takagi.ru.monica.security.SecureStartupResult
import takagi.ru.monica.security.SecureStorageStartup
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.screens.SecureStorageRecoveryScreen

/** Resolve storage before creating repositories, view models or account services. */
@Composable
internal fun rememberSteamSecureStorage(
    onRecovered: () -> Unit,
    onExit: () -> Unit
): SecurityManager? {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<SecureStartupResult?>(null) }
    LaunchedEffect(attempt) {
        result = withContext(Dispatchers.IO) { SecureStorageStartup.prepare(context.applicationContext) }
    }
    return when (val state = result) {
        is SecureStartupResult.Ready -> state.manager
        is SecureStartupResult.Blocked -> {
            val recovery = remember(context) { LocalVaultRecovery(context.applicationContext) }
            val available = remember(state) { recovery.available() }
            SecureStorageRecoveryScreen(
                onRetry = { result = null; attempt++ },
                onCopyDiagnostic = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Monica Steam", SecureStorageStartup.diagnostic(state.failure)))
                },
                onExit = onExit,
                onRecover = if (available) { password ->
                    val recovered = withContext(Dispatchers.IO) {
                        if (!recovery.recover(password)) false else {
                            // Rebuild background access before honoring Steam's optional startup lock.
                            // The recovery password was authenticated by the envelope and the MDK.
                            SecurityManager(context.applicationContext).unlockVaultWithPassword(password)
                        }
                    }
                    if (recovered) onRecovered()
                    recovered
                } else null
            )
            null
        }
        null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            null
        }
    }
}
