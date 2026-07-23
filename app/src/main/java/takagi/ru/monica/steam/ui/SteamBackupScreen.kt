package takagi.ru.monica.steam.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.backup.SteamMaFileRemoteBackup
import takagi.ru.monica.steam.backup.SteamMaFileWebDavService
import takagi.ru.monica.steam.backup.SteamMaFileZipCodec
import takagi.ru.monica.steam.backup.SteamMaFileZipImport
import takagi.ru.monica.steam.backup.steamRemoteBackupLazyKey
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.io.SteamSafWriter
import takagi.ru.monica.ui.components.M3IdentityVerifyDialog
import takagi.ru.monica.utils.WebDavHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamBackupScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securityManager = remember(context) { SecurityManager(context.applicationContext) }
    val repository = remember(context) {
        SteamAccountRepository(
            SteamDatabase.getDatabase(context.applicationContext).steamAccountDao(),
            securityManager
        )
    }
    val codec = remember { SteamMaFileZipCodec() }
    val webDavHelper = remember(context) { WebDavHelper(context.applicationContext) }
    val webDavService = remember { SteamMaFileWebDavService() }
    val accounts by repository.observeAccounts().collectAsState(initial = emptyList())
    val initialConfig = remember { webDavHelper.getCurrentConfig() }

    var serverUrl by rememberSaveable { mutableStateOf(initialConfig?.serverUrl.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(initialConfig?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(webDavHelper.getCurrentPasswordForEdit()) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var identityVerified by remember { mutableStateOf(!securityManager.isMasterPasswordSet()) }
    var identityPassword by rememberSaveable { mutableStateOf("") }
    var identityError by rememberSaveable { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var remoteBackups by remember { mutableStateOf<List<SteamMaFileRemoteBackup>>(emptyList()) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    var pendingRemoteImport by remember { mutableStateOf<SteamMaFileRemoteBackup?>(null) }

    fun showError(error: Throwable?) {
        errorMessage = error?.message?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.steam_mafile_operation_failed)
        statusMessage = null
    }

    suspend fun importZip(bytes: ByteArray): SteamMaFileZipImport = withContext(Dispatchers.IO) {
        val decoded = codec.decode(bytes)
        decoded.payloads.forEach { repository.upsertFromMaFile(it) }
        decoded
    }

    fun currentWebDavConfigOrError(): Triple<String, String, String>? {
        if (serverUrl.isBlank()) {
            errorMessage = context.getString(R.string.steam_webdav_server_required)
            return null
        }
        return Triple(serverUrl.trim(), username.trim(), password)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val bytes = pendingExport
        pendingExport = null
        if (uri != null && bytes != null) {
            scope.launch {
                isWorking = true
                val result = withContext(Dispatchers.IO) {
                    runCatching { SteamSafWriter.writeBytes(context, uri, bytes) }
                        .mapCatching { saved -> check(saved) { "Cannot open export destination" } }
                }
                isWorking = false
                result.onSuccess {
                    statusMessage = context.getString(R.string.steam_mafile_exported)
                    errorMessage = null
                }.onFailure(::showError)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isWorking = true
                val result = runCatching {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use {
                            it.readBytesLimited(SteamMaFileZipCodec.MAX_ARCHIVE_BYTES)
                        } ?: throw IOException("Cannot open mafile ZIP")
                    }
                    importZip(bytes)
                }
                isWorking = false
                result.onSuccess { imported ->
                    statusMessage = context.getString(
                        R.string.steam_mafile_imported,
                        imported.payloads.size,
                        imported.skippedEntries
                    )
                    errorMessage = null
                }.onFailure(::showError)
            }
        }
    }

    fun refreshRemoteBackups() {
        val config = currentWebDavConfigOrError() ?: return
        scope.launch {
            isWorking = true
            val result = runCatching {
                webDavService.list(config.first, config.second, config.third)
            }
            isWorking = false
            result.onSuccess {
                remoteBackups = it
                statusMessage = context.getString(R.string.steam_webdav_refreshed)
                errorMessage = null
            }.onFailure(::showError)
        }
    }

    LaunchedEffect(identityVerified, initialConfig?.serverUrl) {
        if (identityVerified && initialConfig != null) refreshRemoteBackups()
    }
    BackHandler(onBack = onNavigateBack)

    if (!identityVerified) {
        M3IdentityVerifyDialog(
            title = stringResource(R.string.steam_backup_identity_title),
            message = stringResource(R.string.steam_backup_identity_message),
            passwordValue = identityPassword,
            onPasswordChange = { identityPassword = it; identityError = false },
            onDismiss = onNavigateBack,
            onConfirm = {
                if (securityManager.verifyMasterPassword(identityPassword)) {
                    identityVerified = true
                    identityPassword = ""
                } else {
                    identityError = true
                }
            },
            confirmText = stringResource(R.string.continue_text),
            isPasswordError = identityError,
            passwordErrorText = stringResource(R.string.incorrect_master_password),
            showBiometricSlot = false,
            onBiometricClick = null,
            destructiveConfirm = false
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.steam_backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.steam_backup_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                BackupSectionCard(
                    title = stringResource(R.string.steam_mafile_local_title),
                    subtitle = stringResource(R.string.steam_backup_accounts, accounts.size)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                isWorking = true
                                val result = withContext(Dispatchers.Default) {
                                    runCatching { codec.encode(accounts) }
                                }
                                isWorking = false
                                result.onSuccess {
                                    pendingExport = it
                                    exportLauncher.launch("steam_mafiles_${System.currentTimeMillis()}.zip")
                                }.onFailure(::showError)
                            }
                        },
                        enabled = identityVerified && !isWorking && accounts.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.steam_backup_export))
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        enabled = identityVerified && !isWorking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.steam_backup_import))
                    }
                }
            }
            item {
                BackupSectionCard(
                    title = stringResource(R.string.steam_webdav_title),
                    subtitle = stringResource(R.string.steam_webdav_mafile_only)
                ) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text(stringResource(R.string.webdav_server_url)) },
                        singleLine = true,
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.username)) },
                        singleLine = true,
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.password)) },
                        singleLine = true,
                        enabled = !isWorking,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = stringResource(
                                        if (passwordVisible) R.string.hide_password else R.string.show_password
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val config = currentWebDavConfigOrError() ?: return@OutlinedButton
                                scope.launch {
                                    isWorking = true
                                    val result = runCatching {
                                        webDavService.testConnection(config.first, config.second, config.third)
                                    }
                                    isWorking = false
                                    result.onSuccess {
                                        webDavHelper.configure(config.first, config.second, config.third)
                                        statusMessage = context.getString(R.string.steam_webdav_connected)
                                        errorMessage = null
                                        refreshRemoteBackups()
                                    }.onFailure(::showError)
                                }
                            },
                            enabled = identityVerified && !isWorking,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.steam_webdav_test_save))
                        }
                        Button(
                            onClick = {
                                val config = currentWebDavConfigOrError() ?: return@Button
                                scope.launch {
                                    isWorking = true
                                    val result = runCatching {
                                        val bytes = withContext(Dispatchers.Default) { codec.encode(accounts) }
                                        webDavService.upload(
                                            config.first,
                                            config.second,
                                            config.third,
                                            bytes
                                        )
                                    }
                                    isWorking = false
                                    result.onSuccess {
                                        webDavHelper.configure(config.first, config.second, config.third)
                                        statusMessage = context.getString(R.string.steam_webdav_uploaded, it)
                                        errorMessage = null
                                        refreshRemoteBackups()
                                    }.onFailure(::showError)
                                }
                            },
                            enabled = identityVerified && !isWorking && accounts.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.steam_webdav_upload))
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.steam_webdav_backups),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = ::refreshRemoteBackups,
                        enabled = identityVerified && !isWorking
                    ) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
                    }
                }
            }
            if (remoteBackups.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.steam_webdav_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                itemsIndexed(remoteBackups, key = ::steamRemoteBackupLazyKey) { _, backup ->
                    Card(
                        onClick = { pendingRemoteImport = backup },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(backup.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = backup.metadata(context),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            item {
                if (isWorking) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
                statusMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary)
                }
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    pendingRemoteImport?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingRemoteImport = null },
            title = { Text(stringResource(R.string.steam_webdav_restore_title)) },
            text = { Text(stringResource(R.string.steam_webdav_restore_message, backup.name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoteImport = null
                    val config = currentWebDavConfigOrError() ?: return@TextButton
                    scope.launch {
                        isWorking = true
                        val result = runCatching {
                            val bytes = webDavService.download(
                                config.first,
                                config.second,
                                config.third,
                                backup.name
                            )
                            importZip(bytes)
                        }
                        isWorking = false
                        result.onSuccess { imported ->
                            statusMessage = context.getString(
                                R.string.steam_mafile_imported,
                                imported.payloads.size,
                                imported.skippedEntries
                            )
                            errorMessage = null
                        }.onFailure(::showError)
                    }
                }) {
                    Text(stringResource(R.string.steam_backup_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoteImport = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun BackupSectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

private fun java.io.InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw IOException("mafile ZIP is too large")
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun SteamMaFileRemoteBackup.metadata(context: Context): String {
    val sizeText = when {
        size >= 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024.0))
        size >= 1024 -> "%.1f KB".format(size / 1024.0)
        else -> "$size B"
    }
    val dateText = modifiedAt.takeIf { it > 0L }?.let {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(it))
    }
    return listOfNotNull(sizeText, dateText).joinToString(" | ")
}
