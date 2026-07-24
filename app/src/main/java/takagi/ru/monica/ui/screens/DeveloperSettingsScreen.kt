package takagi.ru.monica.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.BuildConfig
import takagi.ru.monica.R
import takagi.ru.monica.autofill_ng.core.AutofillLogger
import takagi.ru.monica.bitwarden.service.BitwardenDiagLogger
import takagi.ru.monica.bitwarden.service.BitwardenSyncForensicsLogger
import takagi.ru.monica.mdbx.MdbxDiagLogger
import takagi.ru.monica.passkey.PasskeyValidationDiagnostics
import takagi.ru.monica.security.SecurityDiagLogger
import takagi.ru.monica.steam.diagnostics.LogcatCommandRunner
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.diagnostics.SteamCrashDiagnostics

/**
 * 开发者设置页面
 * 只提供日志查看、清除和分享。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var showDebugLogsDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.developer_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.developer_settings_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            SettingsSection(
                title = stringResource(R.string.developer_log_debugging)
            ) {
                SettingsItem(
                    icon = Icons.Default.BugReport,
                    title = stringResource(R.string.developer_view_logs),
                    subtitle = stringResource(R.string.developer_view_logs_desc),
                    onClick = { showDebugLogsDialog = true }
                )

                SettingsItem(
                    icon = Icons.Default.DeleteSweep,
                    title = stringResource(R.string.developer_clear_log_buffer),
                    subtitle = stringResource(R.string.developer_clear_log_buffer_desc),
                    onClick = {
                        scope.launch {
                            val clearResult = DeveloperLogDebugHelper.clearLogs(context)
                            val message = if (clearResult.logcatCleared) {
                                context.getString(R.string.developer_log_buffer_cleared)
                            } else {
                                context.getString(
                                    R.string.developer_clear_failed,
                                    clearResult.reason ?: "unknown"
                                )
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                SettingsItem(
                    icon = Icons.Default.Share,
                    title = stringResource(R.string.developer_share_logs),
                    subtitle = stringResource(R.string.developer_share_logs_desc),
                    onClick = {
                        if (activity == null) {
                            showDeveloperLogShareFailure(context, "activity unavailable")
                        } else {
                            launchDeveloperLogShare(activity, context) {
                                DeveloperLogDebugHelper.collectLogs(context).report
                            }
                        }
                    }
                )
            }
        }
    }

    if (showDebugLogsDialog) {
        DebugLogsDialog(
            onDismiss = { showDebugLogsDialog = false }
        )
    }
}

/**
 * 调试日志对话框 - 分级显示关键日志
 */
@Composable
fun DebugLogsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    var snapshot by remember {
        mutableStateOf(
            DeveloperLogSnapshot(
                report = "",
                lines = emptyList()
            )
        )
    }
    var isLoading by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf(DeveloperLogFilter.ALL) }
    var refreshRequest by remember { mutableIntStateOf(0) }

    suspend fun refreshLogs() {
        isLoading = true
        snapshot = try {
            DeveloperLogDebugHelper.collectLogs(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            DeveloperLogSnapshot(
                report = context.getString(R.string.developer_load_failed, e.message ?: "unknown"),
                lines = emptyList()
            )
        }
        isLoading = false
    }

    LaunchedEffect(refreshRequest) {
        refreshLogs()
    }

    val allLines = snapshot.lines
    val errorCount = allLines.count { it.level == DeveloperLogLevel.ERROR }
    val warningCount = allLines.count { it.level == DeveloperLogLevel.WARN }
    val filteredLines = remember(allLines, filter) {
        when (filter) {
            DeveloperLogFilter.ALL -> allLines
            DeveloperLogFilter.ERROR -> allLines.filter { it.level == DeveloperLogLevel.ERROR }
            DeveloperLogFilter.WARNING -> allLines.filter { it.level == DeveloperLogLevel.WARN }
        }
    }

    fun copyLogs() {
        val report = snapshot.report
        if (isLoading || report.isBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.developer_no_logs),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as? ClipboardManager
                ?: error("clipboard unavailable")
            clipboard.setPrimaryClip(
                ClipData.newPlainText(
                    context.getString(R.string.developer_copy_logs),
                    report
                )
            )
        }.onSuccess {
            Toast.makeText(
                context,
                context.getString(R.string.developer_logs_copied),
                Toast.LENGTH_SHORT
            ).show()
        }.onFailure { error ->
            Toast.makeText(
                context,
                context.getString(
                    R.string.developer_copy_failed,
                    error.message ?: "unknown"
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun shareLogs() {
        if (isLoading || snapshot.report.isBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.developer_no_logs),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (activity == null) {
            showDeveloperLogShareFailure(context, "activity unavailable")
        } else {
            launchDeveloperLogShare(activity, context) { snapshot.report }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.developer_system_logs),
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { refreshRequest++ },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.developer_refresh),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        text = {
            Column {
                if (!isLoading && allLines.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filter == DeveloperLogFilter.ALL,
                            onClick = { filter = DeveloperLogFilter.ALL },
                            label = { Text("${stringResource(R.string.developer_filter_all)} (${allLines.size})") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        FilterChip(
                            selected = filter == DeveloperLogFilter.ERROR,
                            onClick = { filter = DeveloperLogFilter.ERROR },
                            label = { Text("${stringResource(R.string.developer_filter_errors)} ($errorCount)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                        FilterChip(
                            selected = filter == DeveloperLogFilter.WARNING,
                            onClick = { filter = DeveloperLogFilter.WARNING },
                            label = { Text("${stringResource(R.string.developer_filter_warnings)} ($warningCount)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        filteredLines.isEmpty() -> {
                            Text(
                                text = if (snapshot.report.isNotBlank()) {
                                    snapshot.report
                                } else {
                                    stringResource(R.string.developer_no_logs)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState()),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(filteredLines) { _, line ->
                                    DeveloperLogLineItem(line)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.developer_close))
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = ::copyLogs,
                    enabled = !isLoading && snapshot.report.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.developer_copy_logs))
                }
                TextButton(
                    onClick = ::shareLogs,
                    enabled = !isLoading && snapshot.report.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.developer_share_logs))
                }
            }
        }
    )
}

private fun launchDeveloperLogShare(
    activity: ComponentActivity,
    context: Context,
    reportProvider: suspend () -> String
) {
    activity.lifecycleScope.launch {
        try {
            val report = reportProvider()
            val shareIntent = DeveloperLogDebugHelper.createShareIntent(context, report)
            activity.startActivity(
                Intent.createChooser(
                    shareIntent,
                    context.getString(R.string.developer_share_title)
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            showDeveloperLogShareFailure(context, error.message ?: "unknown")
        }
    }
}

private fun showDeveloperLogShareFailure(context: Context, reason: String) {
    Toast.makeText(
        context,
        context.getString(R.string.developer_share_failed, reason),
        Toast.LENGTH_SHORT
    ).show()
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

@Composable
private fun DeveloperLogLineItem(line: DeveloperLogLine) {
    val textColor = when (line.level) {
        DeveloperLogLevel.ERROR -> MaterialTheme.colorScheme.error
        DeveloperLogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        DeveloperLogLevel.INFO -> MaterialTheme.colorScheme.onSurface
        DeveloperLogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        DeveloperLogLevel.VERBOSE -> MaterialTheme.colorScheme.onSurfaceVariant
        DeveloperLogLevel.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = line.text,
        color = textColor,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
}

private enum class DeveloperLogLevel {
    ERROR,
    WARN,
    INFO,
    DEBUG,
    VERBOSE,
    OTHER
}

private enum class DeveloperLogFilter {
    ALL,
    ERROR,
    WARNING
}

private data class DeveloperLogLine(
    val text: String,
    val level: DeveloperLogLevel
)

private data class DeveloperLogSnapshot(
    val report: String,
    val lines: List<DeveloperLogLine>
)

private data class DeveloperLogcatSnapshot(
    val autofillTags: String,
    val appProcess: String,
    val crash: String,
    val main: String,
    val system: String
)

private data class ClearLogsResult(
    val logcatCleared: Boolean,
    val reason: String?
)

private object DeveloperLogDebugHelper {
    private const val LOG_LINE_LIMIT = 1200
    private const val SHARE_DIR = "temp_share"
    private const val SHARE_PREFIX = "monica_logs_"
    private val AUTOFILL_LOG_TAGS = arrayOf(
        "MonicaAutofill:V",
        "AutofillPicker:V",
        "AutofillPickerV2:V",
        "EnhancedParser:V",
        "EnhancedFieldParser:V",
        "SmartFieldDetector:V",
        "*:S"
    )
    private val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    suspend fun collectLogs(context: Context): DeveloperLogSnapshot = withContext(Dispatchers.IO) {
        runCatching { AutofillLogger.initialize(context.applicationContext) }
        runCatching { BitwardenDiagLogger.initialize(context.applicationContext) }
        runCatching { BitwardenSyncForensicsLogger.initialize(context.applicationContext) }
        runCatching { MdbxDiagLogger.initialize(context.applicationContext) }
        runCatching { SecurityDiagLogger.initialize(context.applicationContext) }
        runCatching { SteamDiagLogger.initialize(context.applicationContext) }
        val persistedCrash = runCatching {
            SteamCrashDiagnostics.readLastCrash(context.applicationContext)
        }.getOrDefault("")
        val logcat = coroutineScope {
            val autofillTags = async { readAutofillTagLogs(context) }
            val appProcess = async {
                readLogcat(
                    context,
                    arrayOf(
                        "logcat", "-d", "-v", "threadtime", "--pid",
                        android.os.Process.myPid().toString(), "-t", "400", "*:V"
                    )
                )
            }
            val crash = async {
                readLogcat(
                    context,
                    arrayOf(
                        "logcat", "-d", "-b", "crash", "-v", "threadtime",
                        "-t", "300", "*:S"
                    )
                )
            }
            val main = async {
                readLogcat(
                    context,
                    arrayOf(
                        "logcat", "-d", "-b", "main", "-v", "threadtime",
                        "-t", "300", "*:V"
                    )
                )
            }
            val system = async {
                readLogcat(
                    context,
                    arrayOf(
                        "logcat", "-d", "-b", "system", "-v", "threadtime",
                        "-t", "300", "AndroidRuntime:E", "System.err:W", "libc:E", "*:S"
                    )
                )
            }
            DeveloperLogcatSnapshot(
                autofillTags = autofillTags.await(),
                appProcess = appProcess.await(),
                crash = crash.await(),
                main = main.await(),
                system = system.await()
            )
        }
        val autofillTagLogs = logcat.autofillTags
        val appProcessLogs = logcat.appProcess
        val crashLogs = logcat.crash
        val mainBufferLogs = logcat.main
        val systemBufferLogs = logcat.system
        val selectedLogs = buildString {
            if (persistedCrash.isNotBlank()) {
                appendLine("---- persisted-crash ----")
                appendLine(persistedCrash.trim())
            }
            if (autofillTagLogs.isNotBlank()) {
                appendLine("---- autofill-tags ----")
                appendLine(autofillTagLogs.trim())
            }
            if (appProcessLogs.isNotBlank()) {
                if (isNotBlank()) appendLine()
                appendLine("---- app-process ----")
                appendLine(appProcessLogs.trim())
            }
            if (crashLogs.isNotBlank()) {
                if (isNotBlank()) appendLine()
                appendLine("---- crash-buffer ----")
                appendLine(crashLogs.trim())
            }
            if (mainBufferLogs.isNotBlank()) {
                if (isNotBlank()) appendLine()
                appendLine("---- main-buffer ----")
                appendLine(mainBufferLogs.trim())
            }
            if (systemBufferLogs.isNotBlank()) {
                if (isNotBlank()) appendLine()
                appendLine("---- system-buffer ----")
                appendLine(systemBufferLogs.trim())
            }
        }.trim()

        val autofillLogs = runCatching {
            AutofillLogger.exportLogs(300)
        }.getOrElse {
            "AutofillLogger unavailable: ${it.message}"
        }
        val persistedAutofillLogs = runCatching {
            AutofillLogger.exportPersistedLogs(1200)
        }.getOrElse {
            "Autofill persisted logs unavailable: ${it.message}"
        }
        val persistedBitwardenLogs = runCatching {
            BitwardenDiagLogger.exportPersistedLogs(2000)
        }.getOrElse {
            "Bitwarden persisted logs unavailable: ${it.message}"
        }
        val persistedForensicsLogs = runCatching {
            BitwardenSyncForensicsLogger.exportPersistedLogs(context, 12)
        }.getOrElse {
            "Bitwarden sync forensics logs unavailable: ${it.message}"
        }
        val persistedMdbxLogs = runCatching {
            MdbxDiagLogger.exportPersistedLogs(2000)
        }.getOrElse {
            "MDBX persisted logs unavailable: ${it.message}"
        }
        val persistedSecurityLogs = runCatching {
            SecurityDiagLogger.exportPersistedLogs(2000)
        }.getOrElse {
            "Security persisted logs unavailable: ${it.message}"
        }
        val persistedSteamLogs = runCatching {
            SteamDiagLogger.exportPersistedLogs(2000)
        }.getOrElse {
            "Steam persisted logs unavailable: ${it.message}"
        }
        val persistedPasskeyLogs = runCatching {
            PasskeyValidationDiagnostics.buildReport(context)
        }.getOrElse {
            "Passkey diagnostics unavailable: ${it.message}"
        }

        val report = buildString {
            appendLine("=== Monica Developer Log Report ===")
            appendLine("exportedAt=${timeFormatter.format(Date())}")
            appendLine("package=${context.packageName}")
            appendLine("appVersion=${BuildConfig.FULL_VERSION_NAME}")
            appendLine("displayVersion=${BuildConfig.VERSION_NAME}")
            appendLine("android=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("=== System Logcat ===")
            if (selectedLogs.isBlank()) {
                appendLine(context.getString(R.string.developer_no_logs))
            } else {
                appendLine(selectedLogs.trim())
            }
            appendLine()
            appendLine("=== Last Persisted Crash ===")
            appendLine(
                persistedCrash.ifBlank { context.getString(R.string.developer_no_logs) }
            )
            appendLine()
            appendLine("=== Autofill Structured Logs ===")
            appendLine(autofillLogs.trim())
            appendLine()
            appendLine("=== Autofill Persisted Logs ===")
            if (persistedAutofillLogs.isBlank()) {
                appendLine(context.getString(R.string.developer_no_logs))
            } else {
                appendLine(persistedAutofillLogs.trim())
            }
            appendLine()
            appendLine("=== Bitwarden Persisted Logs ===")
            if (persistedBitwardenLogs.isBlank()) {
                appendLine(context.getString(R.string.developer_no_logs))
            } else {
                appendLine(persistedBitwardenLogs.trim())
            }
            appendLine()
            appendLine("=== Bitwarden Sync Forensics ===")
            if (persistedForensicsLogs.isBlank()) {
                appendLine(context.getString(R.string.developer_no_logs))
            } else {
                appendLine(persistedForensicsLogs.trim())
            }
            appendLine()
            appendLine("=== MDBX Persisted Logs ===")
            if (persistedMdbxLogs.isBlank()) {
                appendLine(context.getString(R.string.developer_no_logs))
            } else {
                appendLine(persistedMdbxLogs.trim())
            }
            appendLine()
            appendLine("=== Security Persisted Logs ===")
            if (persistedSecurityLogs.isBlank()) {
                appendLine(context.getString(R.string.developer_no_logs))
            } else {
                appendLine(persistedSecurityLogs.trim())
            }
            appendLine()
            appendLine("=== Steam Persisted Logs ===")
            if (persistedSteamLogs.isBlank()) {
                appendLine(context.getString(R.string.developer_no_logs))
            } else {
                appendLine(persistedSteamLogs.trim())
            }
            appendLine()
            appendLine("=== Passkey Persisted Logs ===")
            if (persistedPasskeyLogs.isBlank()) {
                appendLine(context.getString(R.string.developer_no_logs))
            } else {
                appendLine(persistedPasskeyLogs.trim())
            }
        }

        val parsedSystem = parseLines(selectedLogs)
        val parsedPersisted = parseLines(persistedAutofillLogs)
        val parsedBitwarden = parseLines(persistedBitwardenLogs)
        val parsedForensics = parseLines(persistedForensicsLogs)
        val parsedMdbx = parseLines(persistedMdbxLogs)
        val parsedSecurity = parseLines(persistedSecurityLogs)
        val parsedSteam = parseLines(persistedSteamLogs)
        val parsed = when {
            parsedSystem.isNotEmpty() -> parsedSystem
            parsedSteam.isNotEmpty() -> parsedSteam
            parsedMdbx.isNotEmpty() -> parsedMdbx
            parsedSecurity.isNotEmpty() -> parsedSecurity
            parsedForensics.isNotEmpty() -> parsedForensics
            parsedBitwarden.isNotEmpty() -> parsedBitwarden
            parsedPersisted.isNotEmpty() -> parsedPersisted
            else -> parseLines(autofillLogs)
        }
        DeveloperLogSnapshot(report = report, lines = parsed)
    }

    suspend fun clearLogs(context: Context): ClearLogsResult = withContext(Dispatchers.IO) {
        runCatching {
            AutofillLogger.clear()
        }
        runCatching {
            BitwardenDiagLogger.clear()
        }
        runCatching {
            BitwardenSyncForensicsLogger.clear(context.applicationContext)
        }
        runCatching {
            MdbxDiagLogger.clear()
        }
        runCatching {
            SecurityDiagLogger.clear()
        }
        runCatching {
            SteamDiagLogger.clear()
        }
        SteamCrashDiagnostics.clear(context.applicationContext)

        val result = LogcatCommandRunner.read(
            cacheDir = context.cacheDir,
            command = arrayOf("logcat", "-c")
        )
        if (result.succeeded) {
            ClearLogsResult(logcatCleared = true, reason = null)
        } else {
            ClearLogsResult(
                logcatCleared = false,
                reason = result.error
                    ?: result.output.takeIf(String::isNotBlank)
                    ?: "exit=${result.exitCode ?: "unknown"}"
            )
        }
    }

    suspend fun createShareIntent(context: Context, report: String): Intent = withContext(Dispatchers.IO) {
        val shareDir = File(context.cacheDir, SHARE_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        cleanupOldFiles(shareDir)

        val fileName = "${SHARE_PREFIX}${fileFormatter.format(Date())}.txt"
        val file = File(shareDir, fileName)
        file.writeText(report)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.developer_share_subject))
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, buildDeveloperLogShareFallback(report))
            clipData = ClipData.newRawUri(fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun cleanupOldFiles(dir: File) {
        val exported = dir.listFiles { file ->
            file.isFile && file.name.startsWith(SHARE_PREFIX) && file.name.endsWith(".txt")
        } ?: return
        if (exported.size <= 10) return
        exported.sortedByDescending { it.lastModified() }
            .drop(10)
            .forEach { stale ->
                runCatching { stale.delete() }
            }
    }

    private fun readLogcat(context: Context, command: Array<String>): String {
        val result = LogcatCommandRunner.read(context.cacheDir, command)
        return when {
            result.timedOut -> "[WARN] logcat timed out: ${result.error.orEmpty()}"
            result.succeeded -> result.output
            result.output.isNotBlank() -> result.output
            else -> "[WARN] logcat unavailable: ${result.error ?: "exit=${result.exitCode}"}"
        }
    }

    private fun readAutofillTagLogs(context: Context): String {
        val command = mutableListOf(
            "logcat",
            "-d",
            "-v",
            "threadtime",
            "-t",
            LOG_LINE_LIMIT.toString()
        ).apply {
            addAll(AUTOFILL_LOG_TAGS)
        }.toTypedArray()
        return readLogcat(context, command)
    }

    private fun parseLines(raw: String): List<DeveloperLogLine> {
        if (raw.isBlank()) return emptyList()
        return raw
            .lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .map { line ->
                DeveloperLogLine(
                    text = line,
                    level = detectLevel(line)
                )
            }
            .toList()
    }

    private fun detectLevel(line: String): DeveloperLogLevel {
        if (line.contains("FATAL EXCEPTION", ignoreCase = true)) return DeveloperLogLevel.ERROR
        if (line.contains("[ERROR]")) return DeveloperLogLevel.ERROR
        if (line.contains("[WARN]")) return DeveloperLogLevel.WARN
        if (line.contains("[INFO]")) return DeveloperLogLevel.INFO
        if (line.contains("[DEBUG]")) return DeveloperLogLevel.DEBUG

        val match = Regex("""\s([VDIWEAF])\s[^:]+:\s""").find(line)
        val levelChar = match?.groupValues?.getOrNull(1) ?: return DeveloperLogLevel.OTHER
        return when (levelChar) {
            "E", "F", "A" -> DeveloperLogLevel.ERROR
            "W" -> DeveloperLogLevel.WARN
            "I" -> DeveloperLogLevel.INFO
            "D" -> DeveloperLogLevel.DEBUG
            "V" -> DeveloperLogLevel.VERBOSE
            else -> DeveloperLogLevel.OTHER
        }
    }
}

private const val DEVELOPER_LOG_SHARE_TEXT_LIMIT = 48_000
private const val DEVELOPER_LOG_SHARE_HEADER_LIMIT = 4_000

internal fun buildDeveloperLogShareFallback(
    report: String,
    maxChars: Int = DEVELOPER_LOG_SHARE_TEXT_LIMIT,
): String {
    val normalized = report.trim()
    if (normalized.length <= maxChars) return normalized

    val marker = "\n\n=== Share text truncated; full report is attached ===\n\n"
    val headerLength = minOf(DEVELOPER_LOG_SHARE_HEADER_LIMIT, maxChars / 3)
    val tailLength = (maxChars - headerLength - marker.length).coerceAtLeast(0)
    return buildString(maxChars) {
        append(normalized.take(headerLength))
        append(marker)
        append(normalized.takeLast(tailLength))
    }.take(maxChars)
}
