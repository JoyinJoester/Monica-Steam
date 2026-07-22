package takagi.ru.monica.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.BackupPreferences
import takagi.ru.monica.data.PreparedSteamMaFileExport
import takagi.ru.monica.data.SteamMaFileExportCandidate
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.components.M3IdentityVerifyDialog
import takagi.ru.monica.ui.components.OutlinedTextField
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.utils.WebDavHelper
import java.io.File

/**
 * 数据导出界面 - 重新设计
 * 采用M3 Expressive设计规范
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDataScreen(
    onNavigateBack: () -> Unit,
    onExportZip: suspend (Uri, BackupPreferences) -> Result<String>,
    onPrepareZip: suspend (BackupPreferences) -> Result<Pair<File, String>> = {
        Result.failure(Exception("Not implemented"))
    },
    onWritePreparedZip: suspend (Uri, File, String) -> Result<String> = { _, _, _ ->
        Result.failure(Exception("Not implemented"))
    },
    onExportKdbx: suspend (Uri, String) -> Result<String> = { _, _ -> Result.failure(Exception("Not implemented")) },
    biometricEnabled: Boolean = false,
    onLoadSteamMaFileCandidates: suspend () -> Result<List<SteamMaFileExportCandidate>> = {
        Result.success(emptyList())
    },
    onPrepareSteamMaFileExport: suspend (Set<Long>) -> Result<PreparedSteamMaFileExport> = {
        Result.failure(Exception("Not implemented"))
    },
    onWritePreparedSteamMaFileExport: suspend (Uri, PreparedSteamMaFileExport) -> Result<String> = { _, _ ->
        Result.failure(Exception("Not implemented"))
    }
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val biometricHelper = remember { BiometricHelper(context) }
    val securityManager = remember { SecurityManager(context) }
    
    var isExporting by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf(ExportOption.ZIP_BACKUP) }
    
    // KDBX 导出密码
    var kdbxPassword by remember { mutableStateOf("") }
    var kdbxPasswordVisible by remember { mutableStateOf(false) }
    var showKdbxPasswordDialog by remember { mutableStateOf(false) }
    
    // ZIP 备份选项
    var backupPreferences by remember { mutableStateOf(BackupPreferences()) }
    var zipBackupExpanded by remember { mutableStateOf(false) }
    var pendingPreparedZipBackup by remember { mutableStateOf<Pair<File, String>?>(null) }

    // Steam maFile 导出
    var steamMaFileExpanded by remember { mutableStateOf(false) }
    var steamMaFileCandidates by remember { mutableStateOf<List<SteamMaFileExportCandidate>>(emptyList()) }
    var selectedSteamMaFileAccountIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isLoadingSteamMaFileCandidates by remember { mutableStateOf(false) }
    var hasLoadedSteamMaFileCandidates by remember { mutableStateOf(false) }
    var showSteamMaFileRiskDialog by remember { mutableStateOf(false) }
    var showSteamMaFileIdentityDialog by remember { mutableStateOf(false) }
    var steamMaFilePasswordInput by remember { mutableStateOf("") }
    var steamMaFilePasswordError by remember { mutableStateOf(false) }
    var pendingPreparedSteamMaFileExport by remember { mutableStateOf<PreparedSteamMaFileExport?>(null) }
    
    // 检测 WebDAV 是否已配置
    val webDavHelper = remember { WebDavHelper(context) }
    val isWebDavConfigured = remember { webDavHelper.isConfigured() }
    
    // 获取本地 KeePass 数据库数量
    var localKeePassCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            val database = takagi.ru.monica.data.PasswordDatabase.getDatabase(context)
            val keepassDao = database.localKeePassDatabaseDao()
            localKeePassCount = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                keepassDao.getAllDatabasesSync().size
            }
        } catch (e: Exception) {
            localKeePassCount = 0
        }
    }

    fun loadSteamMaFileCandidates() {
        if (isLoadingSteamMaFileCandidates) return
        isLoadingSteamMaFileCandidates = true
        scope.launch {
            onLoadSteamMaFileCandidates().fold(
                onSuccess = { accounts ->
                    hasLoadedSteamMaFileCandidates = true
                    steamMaFileCandidates = accounts
                    val availableIds = accounts.map { it.id }.toSet()
                    selectedSteamMaFileAccountIds = if (selectedSteamMaFileAccountIds.isEmpty()) {
                        availableIds
                    } else {
                        selectedSteamMaFileAccountIds.intersect(availableIds)
                    }
                    isLoadingSteamMaFileCandidates = false
                },
                onFailure = { error ->
                    hasLoadedSteamMaFileCandidates = false
                    isLoadingSteamMaFileCandidates = false
                    snackbarHostState.showSnackbar(
                        error.message ?: context.getString(R.string.export_data_error)
                    )
                }
            )
        }
    }

    suspend fun handleExportUri(safeUri: Uri) {
        isExporting = true
        val preparedZipBackup = pendingPreparedZipBackup
        val preparedSteamMaFileExport = pendingPreparedSteamMaFileExport
        try {
            val result = when (selectedOption) {
                ExportOption.ZIP_BACKUP -> if (preparedZipBackup != null) {
                    onWritePreparedZip(safeUri, preparedZipBackup.first, preparedZipBackup.second)
                } else {
                    onExportZip(safeUri, backupPreferences)
                }
                ExportOption.KDBX -> onExportKdbx(safeUri, kdbxPassword)
                ExportOption.STEAM_MAFILE -> preparedSteamMaFileExport?.let {
                    onWritePreparedSteamMaFileExport(safeUri, it)
                } ?: Result.failure(Exception(context.getString(R.string.export_data_error)))
            }

            isExporting = false
            result.onSuccess { message ->
                scope.launch {
                    snackbarHostState.showSnackbar(message)
                    onNavigateBack()
                }
            }.onFailure { error ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        error.message ?: context.getString(R.string.export_data_error)
                    )
                }
            }
        } catch (e: Exception) {
            isExporting = false
            snackbarHostState.showSnackbar(
                context.getString(R.string.export_data_error) + ": ${e.message}"
            )
        } finally {
            preparedZipBackup?.first?.delete()
            if (preparedZipBackup != null) {
                pendingPreparedZipBackup = null
            }
            preparedSteamMaFileExport?.file?.delete()
            if (preparedSteamMaFileExport != null) {
                pendingPreparedSteamMaFileExport = null
            }
        }
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            pendingPreparedZipBackup?.first?.delete()
            pendingPreparedZipBackup = null
            pendingPreparedSteamMaFileExport?.file?.delete()
            pendingPreparedSteamMaFileExport = null
            isExporting = false
            return@rememberLauncherForActivityResult
        }
        val safeUri = result.data?.data
        if (safeUri == null) {
            pendingPreparedZipBackup?.first?.delete()
            pendingPreparedZipBackup = null
            pendingPreparedSteamMaFileExport?.file?.delete()
            pendingPreparedSteamMaFileExport = null
            isExporting = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            handleExportUri(safeUri)
        }
    }

    fun launchSteamMaFileCreateDocument() {
        isExporting = true
        scope.launch {
            val prepared = onPrepareSteamMaFileExport(selectedSteamMaFileAccountIds)
            prepared.onSuccess { export ->
                pendingPreparedSteamMaFileExport = export
                val createDocumentIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = export.mimeType
                    putExtra(Intent.EXTRA_TITLE, export.fileName)
                }
                try {
                    filePickerLauncher.launch(createDocumentIntent)
                } catch (e: Exception) {
                    pendingPreparedSteamMaFileExport?.file?.delete()
                    pendingPreparedSteamMaFileExport = null
                    isExporting = false
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.error_launch_export, e.message ?: "unknown")
                        )
                    }
                }
            }.onFailure { error ->
                isExporting = false
                snackbarHostState.showSnackbar(
                    error.message ?: context.getString(R.string.export_data_error)
                )
            }
        }
    }

    fun launchCreateDocument() {
        val (fileName, mimeType) = exportDocumentSpec(selectedOption)

        val createDocumentIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, fileName)
        }

        try {
            filePickerLauncher.launch(createDocumentIntent)
        } catch (e: Exception) {
            pendingPreparedZipBackup?.first?.delete()
            pendingPreparedZipBackup = null
            isExporting = false
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.error_launch_export, e.message ?: "unknown")
                )
            }
        }
    }
    
    // 启动导出
    fun startExport() {
        if (selectedOption == ExportOption.STEAM_MAFILE) {
            if (isLoadingSteamMaFileCandidates) return
            if (!hasLoadedSteamMaFileCandidates) {
                loadSteamMaFileCandidates()
                return
            }
            if (steamMaFileCandidates.isEmpty()) {
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.steam_mafile_export_empty))
                }
                return
            }
            if (selectedSteamMaFileAccountIds.isEmpty()) {
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.steam_mafile_export_no_selection))
                }
                return
            }
            showSteamMaFileRiskDialog = true
            return
        }

        // KDBX 导出需要密码
        if (selectedOption == ExportOption.KDBX) {
            if (kdbxPassword.isEmpty()) {
                showKdbxPasswordDialog = true
                return
            }
        }
        
        // ZIP 备份验证：至少选择一种内容
        if (selectedOption == ExportOption.ZIP_BACKUP && !backupPreferences.hasAnyEnabled()) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.backup_validation_error))
            }
            return
        }

        if (selectedOption == ExportOption.ZIP_BACKUP) {
            isExporting = true
            scope.launch {
                val prepared = onPrepareZip(backupPreferences)
                prepared.onSuccess { backup ->
                    pendingPreparedZipBackup = backup
                    launchCreateDocument()
                }.onFailure { error ->
                    isExporting = false
                    snackbarHostState.showSnackbar(
                        error.message ?: context.getString(R.string.export_data_error)
                    )
                }
            }
            return
        }

        launchCreateDocument()
    }
    
    // KDBX 密码输入对话框
    if (showKdbxPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showKdbxPasswordDialog = false },
            title = { Text(stringResource(R.string.kdbx_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.kdbx_password_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = kdbxPassword,
                        onValueChange = { kdbxPassword = it },
                        label = { Text(stringResource(R.string.password)) },
                        visualTransformation = if (kdbxPasswordVisible) 
                            VisualTransformation.None 
                        else 
                            PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { kdbxPasswordVisible = !kdbxPasswordVisible }) {
                                Icon(
                                    if (kdbxPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showKdbxPasswordDialog = false
                        startExport()
                    },
                    enabled = kdbxPassword.isNotEmpty()
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showKdbxPasswordDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showSteamMaFileRiskDialog) {
        val selectedAccounts = steamMaFileCandidates.filter { it.id in selectedSteamMaFileAccountIds }
        AlertDialog(
            onDismissRequest = { showSteamMaFileRiskDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.steam_mafile_export_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(
                            R.string.steam_mafile_export_confirm_message,
                            selectedAccounts.size
                        )
                    )
                    Text(
                        selectedAccounts.joinToString("\n") { account ->
                            "- ${account.title} (${account.subtitle})"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSteamMaFileRiskDialog = false
                        showSteamMaFileIdentityDialog = true
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSteamMaFileRiskDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showSteamMaFileIdentityDialog) {
        val biometricAction = if (
            activity != null &&
            biometricEnabled &&
            biometricHelper.isBiometricAvailable()
        ) {
            {
                biometricHelper.authenticate(
                    activity = activity,
                    title = context.getString(R.string.verify_identity),
                    subtitle = context.getString(R.string.steam_mafile_export_verify_subtitle),
                    onSuccess = {
                        showSteamMaFileIdentityDialog = false
                        steamMaFilePasswordInput = ""
                        steamMaFilePasswordError = false
                        launchSteamMaFileCreateDocument()
                    },
                    onError = { error ->
                        scope.launch {
                            snackbarHostState.showSnackbar(error)
                        }
                    },
                    onFailed = {}
                )
            }
        } else {
            null
        }
        M3IdentityVerifyDialog(
            title = context.getString(R.string.verify_identity),
            message = context.getString(R.string.steam_mafile_export_verify_message),
            passwordValue = steamMaFilePasswordInput,
            onPasswordChange = {
                steamMaFilePasswordInput = it
                steamMaFilePasswordError = false
            },
            onDismiss = {
                showSteamMaFileIdentityDialog = false
                steamMaFilePasswordInput = ""
                steamMaFilePasswordError = false
            },
            onConfirm = {
                val verified = if (securityManager.isMasterPasswordSet()) {
                    securityManager.verifyMasterPassword(steamMaFilePasswordInput)
                } else {
                    true
                }
                if (verified) {
                    showSteamMaFileIdentityDialog = false
                    steamMaFilePasswordInput = ""
                    steamMaFilePasswordError = false
                    launchSteamMaFileCreateDocument()
                } else {
                    steamMaFilePasswordError = true
                }
            },
            confirmText = context.getString(R.string.start_export),
            confirmEnabled = !securityManager.isMasterPasswordSet() || steamMaFilePasswordInput.isNotBlank(),
            destructiveConfirm = false,
            isPasswordError = steamMaFilePasswordError,
            passwordErrorText = context.getString(R.string.current_password_incorrect),
            onBiometricClick = biometricAction,
            biometricHintText = if (biometricAction == null) {
                context.getString(R.string.biometric_not_available)
            } else {
                null
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_data_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部说明卡片 - M3 Expressive风格
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.export_data_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            stringResource(R.string.export_data_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            // 导出选项选择
            Text(
                stringResource(R.string.export_select_option),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            // 选项卡片组
            
            // 1. 完整备份 (ZIP) - 可展开选择备份内容
            ExportOptionCard(
                icon = Icons.Default.Archive,
                title = stringResource(R.string.export_option_zip),
                description = stringResource(R.string.export_option_zip_desc),
                selected = selectedOption == ExportOption.ZIP_BACKUP,
                onClick = { 
                    if (selectedOption == ExportOption.ZIP_BACKUP) {
                        // 已选中时，点击切换展开状态
                        zipBackupExpanded = !zipBackupExpanded
                    } else {
                        // 未选中时，选中但不展开
                        selectedOption = ExportOption.ZIP_BACKUP
                    }
                },
                expandable = true,
                expanded = selectedOption == ExportOption.ZIP_BACKUP && zipBackupExpanded,
                expandedContent = {
                    ZipBackupOptionsContent(
                        backupPreferences = backupPreferences,
                        onBackupPreferencesChange = { backupPreferences = it },
                        localKeePassCount = localKeePassCount,
                        isWebDavConfigured = isWebDavConfigured
                    )
                }
            )
            
            // KDBX 导出选项（KeePass 格式）
            ExportOptionCard(
                icon = Icons.Default.Key,
                title = stringResource(R.string.export_option_kdbx),
                description = stringResource(R.string.export_option_kdbx_desc),
                selected = selectedOption == ExportOption.KDBX,
                onClick = { selectedOption = ExportOption.KDBX }
            )

            ExportOptionCard(
                icon = Icons.Default.VerifiedUser,
                title = stringResource(R.string.export_option_steam_mafile),
                description = stringResource(R.string.export_option_steam_mafile_desc),
                selected = selectedOption == ExportOption.STEAM_MAFILE,
                onClick = {
                    if (selectedOption == ExportOption.STEAM_MAFILE) {
                        steamMaFileExpanded = !steamMaFileExpanded
                    } else {
                        selectedOption = ExportOption.STEAM_MAFILE
                        steamMaFileExpanded = true
                        if (!hasLoadedSteamMaFileCandidates) {
                            loadSteamMaFileCandidates()
                        }
                    }
                },
                expandable = true,
                expanded = selectedOption == ExportOption.STEAM_MAFILE && steamMaFileExpanded,
                expandedContent = {
                    SteamMaFileExportOptionsContent(
                        candidates = steamMaFileCandidates,
                        selectedIds = selectedSteamMaFileAccountIds,
                        isLoading = isLoadingSteamMaFileCandidates,
                        onToggle = { id ->
                            selectedSteamMaFileAccountIds = if (id in selectedSteamMaFileAccountIds) {
                                selectedSteamMaFileAccountIds - id
                            } else {
                                selectedSteamMaFileAccountIds + id
                            }
                        },
                        onSelectAll = {
                            selectedSteamMaFileAccountIds = steamMaFileCandidates.map { it.id }.toSet()
                        },
                        onClear = {
                            selectedSteamMaFileAccountIds = emptySet()
                        },
                        onRefresh = {
                            loadSteamMaFileCandidates()
                        }
                    )
                }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 导出按钮
            Button(
                onClick = { startExport() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isExporting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.exporting))
                } else {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.start_export),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            
            // 安全警告
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        stringResource(R.string.export_data_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SteamMaFileExportOptionsContent(
    candidates: List<SteamMaFileExportCandidate>,
    selectedIds: Set<Long>,
    isLoading: Boolean,
    onToggle: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.steam_mafile_export_selected_count, selectedIds.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSelectAll, enabled = candidates.isNotEmpty()) {
                Text(stringResource(R.string.steam_mafile_export_select_all))
            }
            TextButton(onClick = onClear, enabled = selectedIds.isNotEmpty()) {
                Text(stringResource(R.string.steam_mafile_export_clear))
            }
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
            }
        }

        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.steam_mafile_export_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        } else if (candidates.isEmpty()) {
            Text(
                text = stringResource(R.string.steam_mafile_export_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        } else {
            candidates.forEach { account ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = account.id in selectedIds,
                        onCheckedChange = { onToggle(account.id) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = account.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

