package takagi.ru.monica.steam.itad.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassTopAppBar as TopAppBar
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassScaffold as Scaffold
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.steam.itad.data.ItadCredentialSaveResult
import takagi.ru.monica.steam.itad.data.ItadCredentialStore
import takagi.ru.monica.steam.itad.domain.ItadApiKeyPolicy
import takagi.ru.monica.steam.itad.domain.ItadApiKeyValidationError
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.ui.screens.SettingsItem
import takagi.ru.monica.ui.screens.SettingsSection

private const val ITAD_APPLICATIONS_URL = "https://isthereanydeal.com/apps/my/"

@Composable
fun ItadSettingsEntry(onClick: () -> Unit) {
    val context = LocalContext.current
    SettingsSection(title = context.getString(R.string.itad_settings_section)) {
        SettingsItem(
            icon = Icons.Default.PriceCheck,
            title = context.getString(R.string.itad_settings_title),
            subtitle = context.getString(R.string.itad_settings_description),
            onClick = onClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItadSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val credentialStore = remember(context.applicationContext) {
        ItadCredentialStore(context.applicationContext)
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val dockClearance = LocalSteamDockContentClearance.current
    var apiKey by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var configured by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(true) }
    var validationError by remember { mutableStateOf<ItadApiKeyValidationError?>(null) }

    LaunchedEffect(credentialStore) {
        val storedKey = withContext(Dispatchers.IO) {
            runCatching { credentialStore.readApiKey() }
        }
        busy = false
        storedKey.onSuccess { key ->
            apiKey = key.orEmpty()
            configured = !key.isNullOrEmpty()
        }.onFailure {
            snackbarHostState.showSnackbar(context.getString(R.string.itad_storage_error))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.itad_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            context.getString(R.string.back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = dockClearance + 24.dp)
        ) {
            item {
                SettingsSection(title = context.getString(R.string.itad_api_key_section)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (configured) {
                                            context.getString(R.string.itad_key_configured)
                                        } else {
                                            context.getString(R.string.itad_key_not_configured)
                                        },
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = context.getString(R.string.itad_key_help),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = {
                                    apiKey = it
                                    validationError = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !busy,
                                singleLine = true,
                                label = { Text(context.getString(R.string.itad_api_key_label)) },
                                visualTransformation = if (keyVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(onClick = { keyVisible = !keyVisible }) {
                                        Icon(
                                            imageVector = if (keyVisible) {
                                                Icons.Default.VisibilityOff
                                            } else {
                                                Icons.Default.Visibility
                                            },
                                            contentDescription = context.getString(
                                                if (keyVisible) {
                                                    R.string.itad_hide_key
                                                } else {
                                                    R.string.itad_show_key
                                                }
                                            )
                                        )
                                    }
                                },
                                isError = validationError != null,
                                supportingText = validationError?.let { error ->
                                    {
                                        Text(
                                            context.getString(
                                                when (error) {
                                                    ItadApiKeyValidationError.EMPTY ->
                                                        R.string.itad_key_error_empty
                                                    ItadApiKeyValidationError.TOO_LONG ->
                                                        R.string.itad_key_error_too_long
                                                    ItadApiKeyValidationError.CONTROL_CHARACTER ->
                                                        R.string.itad_key_error_control_character
                                                }
                                            )
                                        )
                                    }
                                }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                                TextButton(
                                    enabled = !busy && (configured || apiKey.isNotEmpty()),
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            val cleared = withContext(Dispatchers.IO) {
                                                runCatching { credentialStore.clearApiKey() }
                                            }
                                            busy = false
                                            if (cleared.getOrDefault(false)) {
                                                apiKey = ""
                                                configured = false
                                                validationError = null
                                                keyVisible = false
                                                snackbarHostState.showSnackbar(
                                                    context.getString(R.string.itad_key_cleared)
                                                )
                                            } else {
                                                snackbarHostState.showSnackbar(
                                                    context.getString(R.string.itad_storage_error)
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Text(context.getString(R.string.itad_clear_key))
                                }
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        val validation = ItadApiKeyPolicy.validate(apiKey)
                                        validationError = validation.error
                                        if (!validation.isValid) return@Button
                                        scope.launch {
                                            busy = true
                                            val saveResult = withContext(Dispatchers.IO) {
                                                runCatching { credentialStore.saveApiKey(apiKey) }
                                            }
                                            busy = false
                                            when (val result = saveResult.getOrNull()) {
                                                ItadCredentialSaveResult.Saved -> {
                                                    apiKey = validation.normalizedKey.orEmpty()
                                                    configured = true
                                                    keyVisible = false
                                                    snackbarHostState.showSnackbar(
                                                        context.getString(R.string.itad_key_saved)
                                                    )
                                                }
                                                is ItadCredentialSaveResult.Invalid -> {
                                                    validationError = result.error
                                                }
                                                ItadCredentialSaveResult.WriteFailed,
                                                null -> snackbarHostState.showSnackbar(
                                                    context.getString(R.string.itad_storage_error)
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Text(context.getString(R.string.itad_save_key))
                                }
                            }
                        }
                    }
                }
            }
            item {
                SettingsSection(title = context.getString(R.string.itad_source_section)) {
                    SettingsItem(
                        icon = Icons.Default.OpenInNew,
                        title = context.getString(R.string.itad_get_key_title),
                        subtitle = context.getString(R.string.itad_get_key_description),
                        onClick = { openItadApplications(context) }
                    )
                    Text(
                        text = context.getString(R.string.itad_attribution_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

private fun openItadApplications(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ITAD_APPLICATIONS_URL))
    if (runCatching { context.startActivity(intent) }.isFailure) {
        android.widget.Toast.makeText(
            context,
            R.string.itad_open_link_failed,
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}
