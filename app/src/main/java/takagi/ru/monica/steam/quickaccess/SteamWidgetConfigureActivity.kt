package takagi.ru.monica.steam.quickaccess

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
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
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
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
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
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

    private suspend fun loadAccounts(): List<SteamAccount> {
        val database = SteamDatabase.getDatabase(applicationContext)
        return SteamAccountRepository(
            database.steamAccountDao(),
            SecurityManager(applicationContext)
        ).getAccounts()
    }

    private fun completeConfiguration(accountId: Long) {
        SteamWidgetPreferences.setAccountId(applicationContext, widgetId, accountId)
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
    loadAccounts: suspend () -> List<SteamAccount>,
    onSelect: (Long) -> Unit
) {
    var accounts by remember { mutableStateOf<List<SteamAccount>?>(null) }
    LaunchedEffect(Unit) { accounts = runCatching { loadAccounts() }.getOrDefault(emptyList()) }

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
                    text = stringResource(R.string.steam_widget_no_accounts),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(current, key = SteamAccount::id) { account ->
                    Card(
                        onClick = { onSelect(account.id) },
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
                                    text = account.accountName,
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
