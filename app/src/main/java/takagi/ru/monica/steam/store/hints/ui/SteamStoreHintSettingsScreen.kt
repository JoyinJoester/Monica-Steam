package takagi.ru.monica.steam.store.hints.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.store.hints.data.SteamStoreHintPreferences
import takagi.ru.monica.steam.store.hints.domain.SteamStoreHintSettings
import takagi.ru.monica.steam.store.interest.data.SteamStoreInterestPreferences
import takagi.ru.monica.ui.screens.SettingsItem
import takagi.ru.monica.ui.screens.SettingsItemWithSwitch
import takagi.ru.monica.ui.screens.SettingsSection

@Composable
fun SteamStoreHintSettingsEntry(onClick: () -> Unit) {
    val context = LocalContext.current
    SettingsItem(
        icon = Icons.Default.TipsAndUpdates,
        title = context.getString(R.string.steam_store_hint_settings_title),
        subtitle = context.getString(R.string.steam_store_hint_settings_description),
        onClick = onClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamStoreHintSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { SteamStoreHintPreferences(context) }
    val interestPreferences = remember(context) { SteamStoreInterestPreferences(context) }
    val settings by preferences.settings.collectAsState(SteamStoreHintSettings())
    var syncIgnoredWithSteam by remember {
        mutableStateOf(interestPreferences.syncWithSteam)
    }
    val dockClearance = LocalSteamDockContentClearance.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.steam_store_hint_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            context.getString(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = dockClearance + 24.dp)
        ) {
            item {
                SettingsSection(
                    title = context.getString(R.string.steam_store_ignore_settings_section)
                ) {
                    SettingsItemWithSwitch(
                        icon = Icons.Default.CloudSync,
                        title = context.getString(R.string.steam_store_ignore_sync_title),
                        subtitle = context.getString(R.string.steam_store_ignore_sync_description),
                        checked = syncIgnoredWithSteam,
                        onCheckedChange = { enabled ->
                            syncIgnoredWithSteam = enabled
                            interestPreferences.setSyncWithSteam(enabled)
                        }
                    )
                }
            }
            item {
                SettingsSection(
                    title = context.getString(R.string.steam_store_hint_status_section)
                ) {
                    SettingsItemWithSwitch(
                        icon = Icons.Default.CheckCircle,
                        title = context.getString(R.string.steam_store_hint_owned_title),
                        subtitle = context.getString(R.string.steam_store_hint_owned_description),
                        checked = settings.ownershipHintsEnabled,
                        onCheckedChange = {
                            scope.launch { preferences.setOwnershipHintsEnabled(it) }
                        }
                    )
                    SettingsItemWithSwitch(
                        icon = Icons.Default.FamilyRestroom,
                        title = context.getString(R.string.steam_store_hint_family_title),
                        subtitle = context.getString(R.string.steam_store_hint_family_description),
                        checked = settings.familySharingHintsEnabled,
                        onCheckedChange = {
                            scope.launch { preferences.setFamilySharingHintsEnabled(it) }
                        }
                    )
                    SettingsItemWithSwitch(
                        icon = Icons.Default.Favorite,
                        title = context.getString(R.string.steam_store_hint_wishlist_title),
                        subtitle = context.getString(R.string.steam_store_hint_wishlist_description),
                        checked = settings.wishlistHintsEnabled,
                        onCheckedChange = {
                            scope.launch { preferences.setWishlistHintsEnabled(it) }
                        }
                    )
                }
            }
            item {
                SettingsSection(
                    title = context.getString(R.string.steam_store_hint_content_section)
                ) {
                    SettingsItemWithSwitch(
                        icon = Icons.AutoMirrored.Filled.Label,
                        title = context.getString(R.string.steam_store_hint_tags_title),
                        subtitle = context.getString(R.string.steam_store_hint_tags_description),
                        checked = settings.storeTagsEnabled,
                        onCheckedChange = {
                            scope.launch { preferences.setStoreTagsEnabled(it) }
                        }
                    )
                }
            }
        }
    }
}
