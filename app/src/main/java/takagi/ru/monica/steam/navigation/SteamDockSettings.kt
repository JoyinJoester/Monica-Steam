package takagi.ru.monica.steam.navigation

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs

enum class SteamDockTab {
    TOKEN,
    LIBRARY,
    STORE,
    SETTINGS;

    companion object {
        val DEFAULT_ORDER: List<SteamDockTab> = listOf(STORE, LIBRARY, SETTINGS)

        fun sanitizeOrder(order: List<SteamDockTab>): List<SteamDockTab> {
            val result = order.distinct().filter { it in DEFAULT_ORDER }.toMutableList()
            DEFAULT_ORDER.forEach { tab ->
                if (tab !in result) result += tab
            }
            return result
        }
    }
}

private val LEGACY_DEFAULT_DOCK_ORDER = listOf(
    SteamDockTab.LIBRARY,
    SteamDockTab.STORE,
    SteamDockTab.SETTINGS
)

/** Keeps custom orders while migrating the order used by pre-swipe builds. */
internal fun resolveStoredDockOrder(stored: List<SteamDockTab>): List<SteamDockTab> =
    if (stored.isEmpty() || SteamDockTab.sanitizeOrder(stored) == LEGACY_DEFAULT_DOCK_ORDER) {
        SteamDockTab.DEFAULT_ORDER
    } else {
        SteamDockTab.sanitizeOrder(stored)
    }

/**
 * Resolves a horizontal swipe made on the Dock to the adjacent content tab.
 * The token action is intentionally kept outside the sortable order; when it
 * is selected, a swipe enters from the corresponding edge of the content
 * Dock.  Returning null keeps short/ambiguous drags inert.
 */
internal fun dockSwipeTarget(
    order: List<SteamDockTab>,
    selected: SteamDockTab,
    totalDragPx: Float,
    thresholdPx: Float
): SteamDockTab? {
    if (thresholdPx <= 0f || abs(totalDragPx) < thresholdPx) return null
    val tabs = SteamDockTab.sanitizeOrder(order)
        .filterNot { it == SteamDockTab.TOKEN }
    if (tabs.isEmpty()) return null

    val selectedIndex = tabs.indexOf(selected)
    val targetIndex = when {
        selectedIndex < 0 && totalDragPx < 0f -> 0
        selectedIndex < 0 -> tabs.lastIndex
        totalDragPx < 0f -> selectedIndex + 1
        else -> selectedIndex - 1
    }
    return tabs.getOrNull(targetIndex)
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
        resolveStoredDockOrder(parsed)
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
