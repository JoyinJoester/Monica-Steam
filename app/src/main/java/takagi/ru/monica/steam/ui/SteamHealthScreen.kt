package takagi.ru.monica.steam.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamSecurityEvent
import takagi.ru.monica.steam.health.SteamAccountHealthReport
import takagi.ru.monica.steam.health.SteamHealthCheck
import takagi.ru.monica.steam.health.SteamHealthCheckType
import takagi.ru.monica.steam.health.SteamHealthStatus
import takagi.ru.monica.steam.health.SteamHealthViewModel
import takagi.ru.monica.steam.io.SteamSafWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamHealthScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: SteamHealthViewModel = viewModel(
        factory = remember(context) { SteamHealthViewModel.factory(context) }
    )
    val state by viewModel.uiState.collectAsState()
    var detailAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    val detailAccount = state.accounts.firstOrNull { it.id == detailAccountId }
    val diagnosticExported = stringResource(R.string.steam_health_diagnostic_exported)
    val diagnosticFailed = stringResource(R.string.steam_health_diagnostic_failed)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    SteamSafWriter.writeText(
                        context = context,
                        uri = uri,
                        text = viewModel.diagnosticText()
                    )
                }
                Toast.makeText(
                    context,
                    if (saved) diagnosticExported else diagnosticFailed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    BackHandler(enabled = detailAccount != null) { detailAccountId = null }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(detailAccount?.displayName?.ifBlank { detailAccount.accountName }
                        ?: stringResource(R.string.steam_health_title))
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (detailAccount != null) detailAccountId = null else onNavigateBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (detailAccount == null) {
                        IconButton(
                            onClick = { exportLauncher.launch("monica-steam-health.txt") },
                            enabled = state.reports.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = stringResource(R.string.steam_health_export_diagnostic)
                            )
                        }
                        IconButton(onClick = viewModel::refresh, enabled = !state.isChecking) {
                            if (state.isChecking) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.refresh)
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (detailAccount == null) {
            SteamHealthOverview(
                accounts = state.accounts,
                reports = state.reports,
                networkUnavailable = state.networkUnavailable,
                lastClockOffset = state.clock.lastSuccessfulOffsetSeconds,
                lastClockAt = state.clock.lastSuccessfulAt,
                onOpenAccount = { detailAccountId = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            SteamHealthDetail(
                account = detailAccount,
                report = state.reports[detailAccount.id],
                events = state.events.filter { it.accountId == detailAccount.id },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}

@Composable
private fun SteamHealthOverview(
    accounts: List<SteamAccount>,
    reports: Map<Long, SteamAccountHealthReport>,
    networkUnavailable: Boolean,
    lastClockOffset: Long?,
    lastClockAt: Long?,
    onOpenAccount: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HealthCount(
                    label = stringResource(R.string.steam_health_status_healthy),
                    count = reports.values.count { it.status == SteamHealthStatus.HEALTHY },
                    status = SteamHealthStatus.HEALTHY,
                    modifier = Modifier.weight(1f)
                )
                HealthCount(
                    label = stringResource(R.string.steam_health_status_attention),
                    count = reports.values.count { it.status == SteamHealthStatus.ATTENTION },
                    status = SteamHealthStatus.ATTENTION,
                    modifier = Modifier.weight(1f)
                )
                HealthCount(
                    label = stringResource(R.string.steam_health_status_critical),
                    count = reports.values.count { it.status == SteamHealthStatus.CRITICAL },
                    status = SteamHealthStatus.CRITICAL,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (networkUnavailable) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.steam_health_clock_unknown),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (lastClockOffset != null && lastClockAt != null) {
                            Text(
                                stringResource(
                                    R.string.steam_health_last_clock,
                                    formatSignedSeconds(lastClockOffset),
                                    formatDateTime(lastClockAt)
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.steam_health_accounts, accounts.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        if (accounts.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.steam_health_no_accounts),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                )
            }
        }
        items(accounts, key = SteamAccount::id) { account ->
            val report = reports[account.id]
            ListItem(
                headlineContent = {
                    Text(account.displayName.ifBlank { account.accountName })
                },
                supportingContent = {
                    Text(
                        report?.let {
                            stringResource(
                                R.string.steam_health_checked_at,
                                formatDateTime(it.checkedAt)
                            )
                        } ?: stringResource(R.string.steam_health_not_checked)
                    )
                },
                leadingContent = { HealthStatusIcon(report?.status ?: SteamHealthStatus.UNKNOWN) },
                trailingContent = {
                    Text(
                        healthStatusLabel(report?.status ?: SteamHealthStatus.UNKNOWN),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                modifier = Modifier.clickable { onOpenAccount(account.id) }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
        }
    }
}

@Composable
private fun HealthCount(
    label: String,
    count: Int,
    status: SteamHealthStatus,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HealthStatusIcon(status)
            Text(count.toString(), style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SteamHealthDetail(
    account: SteamAccount,
    report: SteamAccountHealthReport?,
    events: List<SteamSecurityEvent>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HealthStatusIcon(report?.status ?: SteamHealthStatus.UNKNOWN)
                Column {
                    Text(
                        healthStatusLabel(report?.status ?: SteamHealthStatus.UNKNOWN),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        report?.let { formatDateTime(it.checkedAt) }
                            ?: stringResource(R.string.steam_health_not_checked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.steam_health_checks),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        if (report == null) {
            item {
                Text(
                    stringResource(R.string.steam_health_not_checked),
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(report.checks, key = SteamHealthCheck::type) { check ->
                ListItem(
                    headlineContent = { Text(healthCheckLabel(check.type)) },
                    supportingContent = {
                        Text(healthCheckDetail(check))
                    },
                    leadingContent = { HealthStatusIcon(check.status) },
                    trailingContent = { Text(healthStatusLabel(check.status)) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
        item {
            Text(
                stringResource(R.string.steam_health_recent_history),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }
        if (events.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.steam_health_no_history),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            items(events.take(20), key = SteamSecurityEvent::id) { event ->
                ListItem(
                    headlineContent = { Text(event.summary) },
                    supportingContent = { Text(formatDateTime(event.occurredAt)) }
                )
            }
        }
    }
}

@Composable
private fun HealthStatusIcon(status: SteamHealthStatus) {
    val icon = when (status) {
        SteamHealthStatus.HEALTHY -> Icons.Default.CheckCircle
        SteamHealthStatus.ATTENTION -> Icons.Default.Warning
        SteamHealthStatus.CRITICAL -> Icons.Default.Error
        SteamHealthStatus.UNKNOWN -> Icons.Default.Help
    }
    val color = when (status) {
        SteamHealthStatus.HEALTHY -> MaterialTheme.colorScheme.primary
        SteamHealthStatus.ATTENTION -> MaterialTheme.colorScheme.tertiary
        SteamHealthStatus.CRITICAL -> MaterialTheme.colorScheme.error
        SteamHealthStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(icon, contentDescription = healthStatusLabel(status), tint = color)
}

@Composable
private fun healthStatusLabel(status: SteamHealthStatus): String {
    return stringResource(
        when (status) {
            SteamHealthStatus.HEALTHY -> R.string.steam_health_status_healthy
            SteamHealthStatus.ATTENTION -> R.string.steam_health_status_attention
            SteamHealthStatus.CRITICAL -> R.string.steam_health_status_critical
            SteamHealthStatus.UNKNOWN -> R.string.steam_health_status_unknown
        }
    )
}

@Composable
private fun healthCheckLabel(type: SteamHealthCheckType): String {
    return stringResource(
        when (type) {
            SteamHealthCheckType.STEAM_ID -> R.string.steam_health_check_steam_id
            SteamHealthCheckType.DEVICE_ID -> R.string.steam_health_check_device_id
            SteamHealthCheckType.SHARED_SECRET -> R.string.steam_health_check_shared_secret
            SteamHealthCheckType.IDENTITY_SECRET -> R.string.steam_health_check_identity_secret
            SteamHealthCheckType.REVOCATION_CODE -> R.string.steam_health_check_recovery_code
            SteamHealthCheckType.SESSION -> R.string.steam_health_check_session
            SteamHealthCheckType.CLOCK -> R.string.steam_health_check_clock
        }
    )
}

@Composable
private fun healthCheckDetail(check: SteamHealthCheck): String {
    if (check.status == SteamHealthStatus.HEALTHY) {
        return stringResource(R.string.steam_health_check_ok)
    }
    return stringResource(
        when (check.type) {
            SteamHealthCheckType.STEAM_ID -> R.string.steam_health_fix_steam_id
            SteamHealthCheckType.DEVICE_ID -> R.string.steam_health_fix_device_id
            SteamHealthCheckType.SHARED_SECRET -> R.string.steam_health_fix_shared_secret
            SteamHealthCheckType.IDENTITY_SECRET -> R.string.steam_health_fix_identity_secret
            SteamHealthCheckType.REVOCATION_CODE -> R.string.steam_health_fix_recovery_code
            SteamHealthCheckType.SESSION -> R.string.steam_health_fix_session
            SteamHealthCheckType.CLOCK -> if (check.status == SteamHealthStatus.UNKNOWN) {
                R.string.steam_health_fix_clock_unknown
            } else {
                R.string.steam_health_fix_clock
            }
        }
    )
}

private fun formatDateTime(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
}

private fun formatSignedSeconds(seconds: Long): String {
    return if (seconds > 0L) "+${seconds}s" else "${seconds}s"
}
