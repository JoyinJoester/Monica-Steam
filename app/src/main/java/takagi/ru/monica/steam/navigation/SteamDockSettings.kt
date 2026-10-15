package takagi.ru.monica.steam.navigation

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs

enum class SteamDockStyle {
    FIXED,
    LIQUID_GLASS;

    companion object {
        fun fromStoredValue(value: String?): SteamDockStyle =
            entries.firstOrNull { it.name == value } ?: FIXED
    }
}

enum class SteamDockTab {
    TOKEN,
    LIBRARY,
    STORE,
    CHAT,
    SETTINGS;

    companion object {
        val DEFAULT_ORDER: List<SteamDockTab> = listOf(STORE, LIBRARY, CHAT)
        val LIQUID_GLASS_DEFAULT_ORDER: List<SteamDockTab> =
            listOf(STORE, LIBRARY, CHAT, TOKEN, SETTINGS)
        val FIXED_DEFAULT_ORDER: List<SteamDockTab> =
            listOf(STORE, LIBRARY, CHAT, TOKEN, SETTINGS)

        fun sanitizeOrder(order: List<SteamDockTab>): List<SteamDockTab> {
            return order.distinct().filter { it in DEFAULT_ORDER }
        }

        fun completeOrder(order: List<SteamDockTab>): List<SteamDockTab> {
            val result = sanitizeOrder(order).toMutableList()
            DEFAULT_ORDER.forEach { tab -> if (tab !in result) result += tab }
            return result
        }

        fun sanitizeLiquidGlassOrder(order: List<SteamDockTab>): List<SteamDockTab> {
            return order.distinct().filter { it in LIQUID_GLASS_DEFAULT_ORDER }
        }

        fun completeLiquidGlassOrder(order: List<SteamDockTab>): List<SteamDockTab> {
            val result = sanitizeLiquidGlassOrder(order).toMutableList()
            LIQUID_GLASS_DEFAULT_ORDER.forEach { tab -> if (tab !in result) result += tab }
            return result
        }

        fun sanitizeFixedOrder(order: List<SteamDockTab>): List<SteamDockTab> {
            return order.distinct().filter { it in FIXED_DEFAULT_ORDER }
        }

        fun completeFixedOrder(order: List<SteamDockTab>): List<SteamDockTab> {
            val result = sanitizeFixedOrder(order).toMutableList()
            FIXED_DEFAULT_ORDER.forEach { tab -> if (tab !in result) result += tab }
            return result
        }
    }
}

data class SteamDockConfiguration(
    val style: SteamDockStyle,
    val liquidGlassOrder: List<SteamDockTab>,
    val fixedOrder: List<SteamDockTab>
)

private val LEGACY_DEFAULT_DOCK_ORDER = listOf(
    SteamDockTab.LIBRARY,
    SteamDockTab.STORE,
    SteamDockTab.SETTINGS
)

/** Keeps custom orders while migrating the order used by pre-swipe builds. */
internal fun resolveStoredDockOrder(
    stored: List<SteamDockTab>,
    chatMigrationComplete: Boolean = false
): List<SteamDockTab> {
    val normalized = if (stored.distinct() == LEGACY_DEFAULT_DOCK_ORDER) {
        listOf(SteamDockTab.STORE, SteamDockTab.LIBRARY)
    } else {
        SteamDockTab.sanitizeOrder(stored)
    }
    if (chatMigrationComplete || SteamDockTab.CHAT in normalized) return normalized
    return normalized.toMutableList().apply {
        add(SteamDockTab.CHAT)
    }
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

internal fun reorderLiquidGlassDockOrder(
    order: List<SteamDockTab>,
    fromIndex: Int,
    toIndex: Int
): List<SteamDockTab> {
    val completed = SteamDockTab.completeLiquidGlassOrder(order)
    if (fromIndex !in completed.indices || toIndex !in completed.indices) return completed
    if (fromIndex == toIndex) return completed
    return completed.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

internal fun reorderFixedDockOrder(
    order: List<SteamDockTab>,
    fromIndex: Int,
    toIndex: Int
): List<SteamDockTab> {
    val completed = SteamDockTab.completeFixedOrder(order)
    if (fromIndex !in completed.indices || toIndex !in completed.indices) return completed
    if (fromIndex == toIndex) return completed
    return completed.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

private val Context.steamDockDataStore by preferencesDataStore(name = "monica_steam_dock")

class SteamDockPreferences(context: Context) {
    private val dataStore = context.applicationContext.steamDockDataStore

    val configuration: Flow<SteamDockConfiguration> = dataStore.data.map { preferences ->
        val storedM3eOrder = preferences[ORDER_KEY]
        val legacyOrder = if (storedM3eOrder == null) {
            SteamDockTab.DEFAULT_ORDER
        } else {
            resolveStoredDockOrder(
                stored = storedM3eOrder.parseDockTabs(),
                chatMigrationComplete = preferences[CHAT_MIGRATION_KEY] == true
            )
        }
        SteamDockConfiguration(
            style = SteamDockStyle.fromStoredValue(preferences[STYLE_KEY]),
            liquidGlassOrder = SteamDockTab.completeLiquidGlassOrder(
                preferences[LIQUID_GLASS_ORDER_KEY].parseDockTabs()
            ),
            fixedOrder = resolveFixedDockOrder(
                stored = preferences[FIXED_ORDER_KEY]?.parseDockTabs(),
                legacyOrder = legacyOrder
            )
        )
    }

    val style: Flow<SteamDockStyle> = configuration.map { it.style }
    val liquidGlassOrder: Flow<List<SteamDockTab>> =
        configuration.map { it.liquidGlassOrder }
    val fixedOrder: Flow<List<SteamDockTab>> =
        configuration.map { it.fixedOrder }

    suspend fun updateStyle(style: SteamDockStyle) {
        dataStore.edit { preferences ->
            preferences[STYLE_KEY] = style.name
        }
    }

    suspend fun updateLiquidGlassOrder(order: List<SteamDockTab>) {
        val completed = SteamDockTab.completeLiquidGlassOrder(order)
        dataStore.edit { preferences ->
            preferences[LIQUID_GLASS_ORDER_KEY] = completed.joinToString(",") { it.name }
        }
    }

    suspend fun updateFixedOrder(order: List<SteamDockTab>) {
        val completed = SteamDockTab.completeFixedOrder(order)
        dataStore.edit { preferences ->
            preferences[FIXED_ORDER_KEY] = completed.joinToString(",") { it.name }
        }
    }

    private companion object {
        val STYLE_KEY = stringPreferencesKey("dock_style")
        val ORDER_KEY = stringPreferencesKey("dock_order")
        val LIQUID_GLASS_ORDER_KEY = stringPreferencesKey("liquid_glass_dock_order")
        val FIXED_ORDER_KEY = stringPreferencesKey("fixed_dock_order")
        val CHAT_MIGRATION_KEY = booleanPreferencesKey("chat_tab_migrated")
    }
}

/** Preserve existing fixed orders; otherwise carry the old toolbar order into all five tabs. */
internal fun resolveFixedDockOrder(
    stored: List<SteamDockTab>?,
    legacyOrder: List<SteamDockTab>
): List<SteamDockTab> = SteamDockTab.completeFixedOrder(stored ?: legacyOrder)

private fun String?.parseDockTabs(): List<SteamDockTab> =
    this
        ?.split(',')
        ?.mapNotNull { value -> runCatching { SteamDockTab.valueOf(value) }.getOrNull() }
        .orEmpty()
