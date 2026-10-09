package takagi.ru.monica.steam.steamdb.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.ui.components.SettingsSubpageTopBar
import takagi.ru.monica.ui.screens.SettingsItem

@Composable
fun SteamDbSettingsEntry(onClick: () -> Unit) {
    SettingsItem(icon = Icons.Default.QueryStats, title = stringResource(R.string.steamdb_title),
        subtitle = stringResource(R.string.steamdb_settings_description), onClick = onClick)
}

@Composable
fun SteamDbSettingsScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val clearance = LocalSteamDockContentClearance.current
    Scaffold(modifier = modifier, topBar = {
        SettingsSubpageTopBar(stringResource(R.string.steamdb_title), onNavigateBack)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp).padding(bottom = clearance + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.steamdb_settings_description), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.steamdb_settings_help), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.steamdb_attribution), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
