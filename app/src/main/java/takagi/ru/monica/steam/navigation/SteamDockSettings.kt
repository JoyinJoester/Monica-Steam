package takagi.ru.monica.steam.navigation

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SteamDockTab {
    TOKEN,
    LIBRARY,
    STORE,
    SETTINGS;

    companion object {
        val DEFAULT_ORDER: List<SteamDockTab> = listOf(TOKEN, LIBRARY, STORE, SETTINGS)

        fun sanitizeOrder(order: List<SteamDockTab>): List<SteamDockTab> {
            val result = order.distinct().filter { it in entries }.toMutableList()
            DEFAULT_ORDER.forEach { tab ->
                if (tab !in result) result += tab
            }
            return result
        }
    }
}

internal fun reorderDockOrder(
    order: List<SteamDockTab>,
    fromIndex: Int,
    toIndex: Int
): List<SteamDockTab> {
    val sanitized = SteamDockTab.sanitizeOrder(order)
    if (fromIndex !in sanitized.indices || toIndex !in sanitized.indices) return sanitized
    if (fromIndex == toIndex) return sanitized
    return sanitized.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

private val Context.steamDockDataStore by preferencesDataStore(name = "monica_steam_dock")

class SteamDockPreferences(context: Context) {
    private val dataStore = context.applicationContext.steamDockDataStore

    val order: Flow<List<SteamDockTab>> = dataStore.data.map { preferences ->
        val parsed = preferences[ORDER_KEY]
            ?.split(',')
            ?.mapNotNull { value -> runCatching { SteamDockTab.valueOf(value) }.getOrNull() }
            .orEmpty()
        SteamDockTab.sanitizeOrder(parsed.ifEmpty { SteamDockTab.DEFAULT_ORDER })
    }

    suspend fun updateOrder(order: List<SteamDockTab>) {
        val sanitized = SteamDockTab.sanitizeOrder(order)
        dataStore.edit { preferences ->
            preferences[ORDER_KEY] = sanitized.joinToString(",") { it.name }
        }
    }

    private companion object {
        val ORDER_KEY = stringPreferencesKey("dock_order")
    }
}
