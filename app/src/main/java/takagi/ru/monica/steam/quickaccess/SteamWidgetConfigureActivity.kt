package takagi.ru.monica.steam.quickaccess

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle
import takagi.ru.monica.ui.theme.MonicaTheme

class SteamWidgetConfigureActivity : ComponentActivity() {
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !SteamWidgetPinReceiver.isWidgetProvider(intent.getStringExtra(SteamWidgetPinReceiver.EXTRA_PROVIDER))) {
            finish()
            return
        }

        setContent {
            MonicaTheme {
                SteamWidgetConfigureScreen(
                    loadAccounts = ::loadAccounts,
                    onSelect = ::completeConfiguration
                )
            }
        }
    }

    private suspend fun loadAccounts(): List<SteamAccountSessionHandle> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            SteamAccountSourceRepository.get(applicationContext).loadAllSessionHandles()
        }

    private fun completeConfiguration(handle: SteamAccountSessionHandle) {
        val databaseId = (handle.origin.source as? SteamStorageSource.Mdbx)?.databaseId
        val provider = intent.getStringExtra(SteamWidgetPinReceiver.EXTRA_PROVIDER)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID && SteamWidgetPinReceiver.isWidgetProvider(provider)) {
            AppWidgetManager.getInstance(this).requestPinAppWidget(
                ComponentName(this, requireNotNull(provider)), null,
                SteamWidgetPinReceiver.callback(this, provider, handle.account.id, databaseId)
            )
            finish()
            return
        }
        SteamWidgetPreferences.setAccountId(applicationContext, widgetId, handle.account.id, databaseId)
        SteamWidgetUpdater.refresh(applicationContext, widgetId)
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        )
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteamWidgetConfigureScreen(
    loadAccounts: suspend () -> List<SteamAccountSessionHandle>,
    onSelect: (SteamAccountSessionHandle) -> Unit
) {
    var failed by remember { mutableStateOf(false) }
    var accounts by remember { mutableStateOf<List<SteamAccountSessionHandle>?>(null) }
    LaunchedEffect(Unit) { accounts = runCatching { loadAccounts() }.onFailure { failed = true }.getOrDefault(emptyList()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.steam_widget_choose_account)) }) }
    ) { padding ->
        val current = accounts
        when {
            current == null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.steam_widget_loading_accounts))
            }
            current.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(if (failed) R.string.steam_widget_unavailable else R.string.steam_widget_no_accounts),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(current, key = { it.stableKey }) { handle ->
                    val account = handle.account
                    Card(
                        onClick = { onSelect(handle) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.displayName.ifBlank { account.accountName },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = account.accountName + " · " + if (handle.origin.source is SteamStorageSource.Mdbx) "MDBX" else stringResource(R.string.steam_widget_local_account),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
