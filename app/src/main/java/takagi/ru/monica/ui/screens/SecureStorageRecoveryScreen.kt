package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.ui.components.SettingsSubpageTopBar

/** No vault repositories are opened until recovery has been verified by the caller. */
@Composable
internal fun SecureStorageRecoveryScreen(
    onRetry: () -> Unit,
    onCopyDiagnostic: () -> Unit,
    onExit: () -> Unit,
    onRecover: (suspend (String) -> Boolean)? = null
) {
    var password by remember { mutableStateOf("") }
    var recovering by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { password = "" } }
    Scaffold(topBar = { SettingsSubpageTopBar(stringResource(R.string.secure_startup_title), onExit) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onRecover != null) {
                    Button(onClick = {
                        val attempt = password
                        password = ""
                        recovering = true
                        failed = false
                        scope.launch {
                            try { failed = !onRecover(attempt) }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { failed = true }
                            finally { recovering = false }
                        }
                    }, enabled = password.isNotEmpty() && !recovering,
                        modifier = Modifier.fillMaxWidth().testTag("secure_startup_recover")) {
                        if (recovering) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.local_recovery_action))
                    }
                }
                Button(onClick = onRetry, enabled = !recovering, modifier = Modifier.fillMaxWidth().testTag("secure_startup_retry")) {
                    Text(stringResource(R.string.secure_startup_retry))
                }
                FilledTonalButton(onClick = onCopyDiagnostic, modifier = Modifier.fillMaxWidth().testTag("secure_startup_diagnostic")) {
                    Text(stringResource(R.string.secure_startup_diagnostic))
                }
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp).padding(bottom = 16.dp).testTag("secure_startup_blocked"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = settingsSectionItemShape(0, 1), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.secure_startup_preserved), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.secure_startup_description), modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (onRecover != null) {
                Text(stringResource(R.string.local_recovery_description), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = password, onValueChange = { password = it; failed = false },
                    label = { Text(stringResource(R.string.local_recovery_password)) },
                    modifier = Modifier.fillMaxWidth().testTag("secure_startup_password"),
                    enabled = !recovering, singleLine = true, isError = failed,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = MaterialTheme.shapes.large)
                if (failed) Text(stringResource(R.string.local_recovery_failed),
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("secure_startup_recovery_error"))
                Text(stringResource(R.string.local_recovery_limits), style = MaterialTheme.typography.bodySmall)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(R.string.secure_startup_restart, R.string.secure_startup_environment, R.string.secure_startup_no_reset)
                    .forEachIndexed { index, text ->
                        Surface(shape = settingsSectionItemShape(index, 3), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Text(stringResource(text), modifier = Modifier.fillMaxWidth().padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
            }
        }
    }
}
