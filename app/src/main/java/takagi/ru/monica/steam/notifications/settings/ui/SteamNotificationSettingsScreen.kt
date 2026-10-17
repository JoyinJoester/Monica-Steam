package takagi.ru.monica.steam.notifications.settings.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.alerts.data.SteamAlertPreferences
import takagi.ru.monica.steam.alerts.data.SteamAlertScheduler
import takagi.ru.monica.steam.alerts.domain.SteamAlertSettings
import takagi.ru.monica.steam.friends.chat.background.data.SteamChatBackgroundPreferences
import takagi.ru.monica.steam.friends.chat.background.data.SteamChatBackgroundServiceController
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatBackgroundSettings
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.ui.screens.SettingsItem
import takagi.ru.monica.ui.screens.SettingsItemWithSwitch
import takagi.ru.monica.ui.screens.SettingsSection

@Composable
fun SteamNotificationSettingsEntry(onClick: () -> Unit) {
    SettingsSection(title = LocalContext.current.getString(R.string.steam_notification_settings_section)) {
        SettingsItem(
            icon = Icons.Default.Notifications,
            title = LocalContext.current.getString(R.string.steam_notification_settings_title),
            subtitle = LocalContext.current.getString(R.string.steam_notification_settings_description),
            onClick = onClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamNotificationSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alertPreferences = remember(context) { SteamAlertPreferences(context) }
    val chatPreferences = remember(context) { SteamChatBackgroundPreferences(context) }
    val alertSettings by alertPreferences.settings.collectAsState(SteamAlertSettings())
    val chatSettings by chatPreferences.settings.collectAsState(SteamChatBackgroundSettings())
    val dockClearance = LocalSteamDockContentClearance.current
    var enableChatAfterPermission by remember { mutableStateOf(false) }

    fun setChatEnabled(enabled: Boolean) {
        scope.launch {
            chatPreferences.setEnabled(enabled)
            if (enabled) {
                if (!SteamChatBackgroundServiceController.start(context)) {
                    chatPreferences.setEnabled(false)
                    Toast.makeText(context, R.string.steam_chat_background_start_failed, Toast.LENGTH_LONG).show()
                }
            } else SteamChatBackgroundServiceController.stop(context)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && enableChatAfterPermission) setChatEnabled(true) else if (!granted) Toast.makeText(
                context,
                R.string.steam_chat_background_permission_required,
                Toast.LENGTH_LONG
            ).show()
        enableChatAfterPermission = false
    }
    fun requestPermissionIfNeeded(enableChatAfterGrant: Boolean = false): Boolean {
        val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needed) {
            enableChatAfterPermission = enableChatAfterGrant
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        return needed
    }
    fun syncAlerts(block: suspend () -> Unit) {
        scope.launch {
            block()
            SteamAlertScheduler.sync(context)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.steam_notification_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, context.getString(R.string.back))
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
                SettingsSection(title = context.getString(R.string.steam_notification_realtime_section)) {
                    SettingsItemWithSwitch(
                        icon = Icons.Default.ChatBubble,
                        title = context.getString(R.string.steam_chat_background_title),
                        subtitle = context.getString(R.string.steam_chat_background_description),
                        checked = chatSettings.enabled,
                        onCheckedChange = { enabled ->
                            if (enabled && requestPermissionIfNeeded(enableChatAfterGrant = true)) {
                                return@SettingsItemWithSwitch
                            }
                            setChatEnabled(enabled)
                        }
                    )
                    SettingsItemWithSwitch(
                        icon = Icons.Default.Login,
                        title = context.getString(R.string.steam_notification_login_requests),
                        subtitle = context.getString(R.string.steam_notification_login_requests_description),
                        checked = alertSettings.loginRequestsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { alertPreferences.setLoginRequestsEnabled(enabled) }
                        }
                    )
                }
            }
            item {
                SettingsSection(title = context.getString(R.string.steam_notification_periodic_section)) {
                    SettingsItemWithSwitch(
                        icon = Icons.Default.Schedule,
                        title = context.getString(R.string.steam_alerts_enabled),
                        subtitle = context.getString(R.string.steam_alerts_enabled_description),
                        checked = alertSettings.enabled,
                        onCheckedChange = { enabled ->
                            if (enabled) requestPermissionIfNeeded()
                            syncAlerts { alertPreferences.setEnabled(enabled) }
                        }
                    )
                    SettingsItemWithSwitch(
                        icon = Icons.Default.Redeem,
                        title = context.getString(R.string.steam_alerts_notifications),
                        subtitle = context.getString(R.string.steam_alerts_notifications_description),
                        checked = alertSettings.notificationsEnabled,
                        enabled = alertSettings.enabled,
                        onCheckedChange = { syncAlerts { alertPreferences.setNotificationsEnabled(it) } }
                    )
                    SettingsItemWithSwitch(
                        icon = Icons.Default.FactCheck,
                        title = context.getString(R.string.steam_alerts_confirmations),
                        subtitle = context.getString(R.string.steam_alerts_confirmations_description),
                        checked = alertSettings.confirmationsEnabled,
                        enabled = alertSettings.enabled,
                        onCheckedChange = { syncAlerts { alertPreferences.setConfirmationsEnabled(it) } }
                    )
                    SettingsItemWithSwitch(
                        icon = Icons.Default.Favorite,
                        title = context.getString(R.string.steam_notification_wishlist_discounts),
                        subtitle = context.getString(R.string.steam_notification_wishlist_discounts_description),
                        checked = alertSettings.wishlistDiscountsEnabled,
                        enabled = alertSettings.enabled,
                        onCheckedChange = { syncAlerts { alertPreferences.setWishlistDiscountsEnabled(it) } }
                    )
                    SettingsItemWithSwitch(
                        icon = Icons.Default.Security,
                        title = context.getString(R.string.steam_alerts_session),
                        subtitle = context.getString(R.string.steam_alerts_session_description),
                        checked = alertSettings.sessionEnabled,
                        enabled = alertSettings.enabled,
                        onCheckedChange = { syncAlerts { alertPreferences.setSessionEnabled(it) } }
                    )
                    SettingsItemWithSwitch(
                        icon = Icons.Default.Devices,
                        title = context.getString(R.string.steam_alerts_devices),
                        subtitle = context.getString(R.string.steam_alerts_devices_description),
                        checked = alertSettings.devicesEnabled,
                        enabled = alertSettings.enabled,
                        onCheckedChange = { syncAlerts { alertPreferences.setDevicesEnabled(it) } }
                    )
                }
            }
            item {
                Text(
                    text = context.getString(R.string.steam_alerts_interval),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    SteamAlertSettings.allowedIntervals.sorted().forEachIndexed { index, hours ->
                        SegmentedButton(
                            selected = alertSettings.normalizedIntervalHours == hours,
                            onClick = { syncAlerts { alertPreferences.setIntervalHours(hours) } },
                            enabled = alertSettings.enabled,
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = SteamAlertSettings.allowedIntervals.size
                            )
                        ) {
                            Text(context.getString(R.string.steam_alerts_interval_compact, hours))
                        }
                    }
                }
            }
            item {
                SettingsSection(title = context.getString(R.string.steam_notification_system_section)) {
                    SettingsItem(
                        icon = Icons.Default.Settings,
                        title = context.getString(R.string.steam_alerts_system_settings),
                        subtitle = context.getString(R.string.steam_alerts_system_settings_description),
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            })
                        }
                    )
                }
            }
        }
    }
}
