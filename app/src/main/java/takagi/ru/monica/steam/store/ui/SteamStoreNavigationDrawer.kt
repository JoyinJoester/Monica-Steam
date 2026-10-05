package takagi.ru.monica.steam.store.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.SteamPageOverflowAction

/** A window-level drawer also covers the floating Dock, so it cannot leak taps. */
@Composable
internal fun SteamStoreNavigationDrawer(
    refreshing: Boolean,
    actions: SteamStoreHomeActions,
    onDismiss: () -> Unit
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var closingAction by remember { mutableStateOf(false) }
    val currentDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(drawer) {
        drawer.open()
        snapshotFlow { drawer.currentValue }.first { it == DrawerValue.Closed }
        if (!closingAction) currentDismiss()
    }
    fun close(action: () -> Unit = {}) {
        if (closingAction) return
        closingAction = true
        scope.launch { drawer.close(); currentDismiss(); action() }
    }
    val shopping = listOf(
        SteamPageOverflowAction(stringResource(R.string.store_home_wishlist), Icons.Default.FavoriteBorder, onClick = actions.wishlist),
        SteamPageOverflowAction(stringResource(R.string.steam_store_freebies), Icons.Default.Redeem, onClick = actions.freebies),
        SteamPageOverflowAction(stringResource(R.string.steam_store_points_shop), Icons.Default.CardGiftcard, onClick = actions.points),
        SteamPageOverflowAction(stringResource(R.string.steam_store_activate_product_code), Icons.Default.Key, onClick = actions.activateProduct),
        SteamPageOverflowAction(stringResource(R.string.workshop_import_share), Icons.Default.Extension, onClick = actions.workshopImport)
    )
    val utilities = listOf(
        SteamPageOverflowAction(stringResource(R.string.steam_notifications_title), Icons.Default.Notifications, onClick = actions.notifications),
        SteamPageOverflowAction(stringResource(R.string.refresh), Icons.Default.Refresh, enabled = !refreshing, onClick = actions.refresh),
        SteamPageOverflowAction(stringResource(R.string.settings_title), Icons.Default.Settings, onClick = actions.settings)
    )
    val title = stringResource(R.string.steam_store_title)
    val closeLabel = stringResource(R.string.close)
    val lightBars = MaterialTheme.colorScheme.surfaceContainerLow.luminance() > 0.5f
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view, lightBars) {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
            onDispose { }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sheetWidth = minOf(336.dp, maxWidth - 48.dp)
            ModalNavigationDrawer(
                drawerState = drawer,
                gesturesEnabled = !closingAction,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(sheetWidth).fillMaxHeight().testTag("store_home_drawer"),
                        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { close() }, modifier = Modifier.testTag("store_drawer_close")) { Icon(Icons.Default.Close, closeLabel) }
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            listOf(shopping, utilities).forEach { group ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    group.forEachIndexed { index, action ->
                                        Surface(
                                            onClick = { close(action.onClick) }, enabled = action.enabled,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            shape = RoundedCornerShape(topStart = if (index == 0) 24.dp else 5.dp, topEnd = if (index == 0) 24.dp else 5.dp,
                                                bottomStart = if (index == group.lastIndex) 24.dp else 5.dp, bottomEnd = if (index == group.lastIndex) 24.dp else 5.dp)
                                        ) {
                                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                Icon(action.icon, null, tint = if (action.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                                                Text(action.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (action.enabled) 1f else 0.38f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }, content = {}
            )
        }
    }
}
