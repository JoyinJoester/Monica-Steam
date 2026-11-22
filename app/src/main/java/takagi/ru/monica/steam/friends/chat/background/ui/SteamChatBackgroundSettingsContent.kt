package takagi.ru.monica.steam.friends.chat.background.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.background.data.SteamChatBackgroundPreferences
import takagi.ru.monica.steam.friends.chat.background.data.SteamChatNotificationRuntime
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatBackgroundSettings
import takagi.ru.monica.ui.screens.SettingsItemWithSwitch
import takagi.ru.monica.ui.screens.SettingsSection

@Composable
fun SteamChatBackgroundSettingsContent() {
    val context = LocalContext.current
    val preferences = remember(context) { SteamChatBackgroundPreferences(context) }
    val settings by preferences.settings.collectAsState(
        initial = SteamChatBackgroundSettings()
    )
    val coroutineScope = rememberCoroutineScope()

    fun updateEnabled(enabled: Boolean) {
        coroutineScope.launch {
            preferences.setEnabled(enabled)
            if (enabled) {
                if (!SteamChatNotificationRuntime.start(context)) {
                    preferences.setEnabled(false)
                    Toast.makeText(
                        context,
                        R.string.steam_chat_background_start_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                SteamChatNotificationRuntime.stop(context)
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            updateEnabled(true)
        } else {
            Toast.makeText(
                context,
                R.string.steam_chat_background_permission_required,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    SettingsSection(title = context.getString(R.string.steam_chat_background_section)) {
        SettingsItemWithSwitch(
            icon = Icons.Default.ChatBubble,
            title = context.getString(R.string.steam_chat_background_title),
            subtitle = context.getString(R.string.steam_chat_background_description),
            checked = settings.enabled,
            onCheckedChange = { enabled ->
                val needsPermission = enabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    updateEnabled(enabled)
                }
            }
        )
    }
}
