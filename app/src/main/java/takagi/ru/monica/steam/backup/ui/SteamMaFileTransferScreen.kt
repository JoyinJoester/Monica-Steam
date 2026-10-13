package takagi.ru.monica.steam.backup.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.backup.SteamMaFileZipCodec
import takagi.ru.monica.steam.backup.SteamMaFileZipImport
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.io.SteamSafWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamMaFileTransferScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    biometricEnabled: Boolean = false,
    transferState: SteamMaFileTransferState = rememberSteamMaFileTransferState()
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
    // The transfer page only needs a count. Decrypt credentials after export authorization.
    val accountCount by remember(context) {
        SteamDatabase.getDatabase(context.applicationContext).steamAccountDao()
            .observeAccounts().map { it.size }
    }.collectAsState(initial = 0)
    var isWorking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

    fun exportTo(uri: Uri) {
        scope.launch {
            isWorking = true
            try {
                withContext(Dispatchers.IO) {
                    val bytes = codec.encode(repository.getAccounts().filterNot { it.isTemporary })
                    try {
                        check(SteamSafWriter.writeBytes(context, uri, bytes)) {
                            "Cannot open export destination"
                        }
                    } finally {
                        bytes.fill(0)
                    }
                }
                statusMessage = context.getString(R.string.steam_mafile_exported)
                errorMessage = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showError(error)
            } finally {
                isWorking = false
            }
        }
    }

    LaunchedEffect(transferState.importSource) {
        transferState.importSource?.let { uri ->
            isWorking = true
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.readBytesLimited(SteamMaFileZipCodec.MAX_ARCHIVE_BYTES)
                    } ?: throw IOException("Cannot open mafile ZIP")
                }
                val imported = try { importZip(bytes) } finally { bytes.fill(0) }
                statusMessage = context.getString(
                    R.string.steam_mafile_imported,
                    imported.payloads.size,
                    imported.skippedEntries
                )
                errorMessage = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showError(error)
            } finally {
                transferState.importSource = null
                isWorking = false
            }
        }
    }

    transferState.exportDestination?.let { destination ->
        SteamMaFileExportAuthentication(
            securityManager = securityManager,
            biometricEnabled = biometricEnabled,
            onAuthorized = {
                transferState.exportDestination = null
                exportTo(destination)
            },
            onDismiss = { transferState.exportDestination = null },
            onUnavailable = {
                transferState.exportDestination = null
                showError(null)
            }
        )
    }

    BackHandler(onBack = onNavigateBack)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.steam_mafile_transfer_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.steam_mafile_transfer_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.steam_mafile_local_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.steam_backup_accounts, accountCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                statusMessage = null
                                errorMessage = null
                                transferState.launchExport()
                            },
                            enabled = !isWorking && transferState.exportDestination == null && accountCount > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.steam_backup_export))
                        }
                        OutlinedButton(
                            onClick = {
                                transferState.launchImport()
                            },
                            enabled = !isWorking,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.steam_backup_import))
                        }
                    }
                }
            }
            item {
                if (isWorking) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
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
