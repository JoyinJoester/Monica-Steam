package takagi.ru.monica.steam.backup.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.launch
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
fun SteamMaFileTransferScreen(
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
    val accounts by repository.observeAccounts().collectAsState(initial = emptyList())
    var isWorking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }

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
                            text = stringResource(R.string.steam_backup_accounts, accounts.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                        exportLauncher.launch(
                                            "steam_mafiles_${System.currentTimeMillis()}.zip"
                                        )
                                    }.onFailure(::showError)
                                }
                            },
                            enabled = !isWorking && accounts.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.steam_backup_export))
                        }
                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(
                                    arrayOf("application/zip", "application/octet-stream")
                                )
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
