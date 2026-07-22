package takagi.ru.monica.steam.alerts

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.steamAlertDataStore by preferencesDataStore(name = "steam_alert_settings")

class SteamAlertPreferences(context: Context) {
    private val appContext = context.applicationContext

    val settings: Flow<SteamAlertSettings> = appContext.steamAlertDataStore.data.map { values ->
        SteamAlertSettings(
            enabled = values[KEY_ENABLED] ?: false,
            confirmationsEnabled = values[KEY_CONFIRMATIONS] ?: true,
            sessionEnabled = values[KEY_SESSION] ?: true,
            devicesEnabled = values[KEY_DEVICES] ?: true,
            pricesEnabled = values[KEY_PRICES] ?: true,
            intervalHours = values[KEY_INTERVAL_HOURS] ?: 12,
            lastDeviceCount = values[KEY_LAST_DEVICE_COUNT],
            lastAlertSignature = values[KEY_LAST_ALERT_SIGNATURE].orEmpty(),
            lastNotificationAt = values[KEY_LAST_NOTIFICATION_AT] ?: 0L
        )
    }

    suspend fun setEnabled(enabled: Boolean) = update { this[KEY_ENABLED] = enabled }
    suspend fun setConfirmationsEnabled(enabled: Boolean) = update { this[KEY_CONFIRMATIONS] = enabled }
    suspend fun setSessionEnabled(enabled: Boolean) = update { this[KEY_SESSION] = enabled }
    suspend fun setDevicesEnabled(enabled: Boolean) = update { this[KEY_DEVICES] = enabled }
    suspend fun setPricesEnabled(enabled: Boolean) = update { this[KEY_PRICES] = enabled }

    suspend fun setIntervalHours(hours: Int) {
        update { this[KEY_INTERVAL_HOURS] = hours.takeIf { it in SteamAlertSettings.allowedIntervals } ?: 12 }
    }

    suspend fun recordDecision(decision: SteamAlertDecision, notifiedAt: Long?) {
        update {
            decision.deviceBaseline?.let { count -> this[KEY_LAST_DEVICE_COUNT] = count }
            if (notifiedAt != null) {
                this[KEY_LAST_ALERT_SIGNATURE] = decision.signature
                this[KEY_LAST_NOTIFICATION_AT] = notifiedAt
            }
        }
    }

    private suspend fun update(
        transform: androidx.datastore.preferences.core.MutablePreferences.() -> Unit
    ) {
        appContext.steamAlertDataStore.edit(transform)
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("enabled")
        val KEY_CONFIRMATIONS = booleanPreferencesKey("confirmations_enabled")
        val KEY_SESSION = booleanPreferencesKey("session_enabled")
        val KEY_DEVICES = booleanPreferencesKey("devices_enabled")
        val KEY_PRICES = booleanPreferencesKey("prices_enabled")
        val KEY_INTERVAL_HOURS = intPreferencesKey("interval_hours")
        val KEY_LAST_DEVICE_COUNT = intPreferencesKey("last_device_count")
        val KEY_LAST_ALERT_SIGNATURE = stringPreferencesKey("last_alert_signature")
        val KEY_LAST_NOTIFICATION_AT = longPreferencesKey("last_notification_at")
    }
}
