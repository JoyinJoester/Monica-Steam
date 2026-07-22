package takagi.ru.monica.steam.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class SteamUiScaleOption(val percent: Int) {
    COMPACT(85),
    SMALL(90),
    DEFAULT(100),
    LARGE(110);

    val factor: Float
        get() = percent / 100f

    companion object {
        internal val supportedPercentages = listOf(85, 90, 100, 110)

        fun fromPercent(percent: Int?): SteamUiScaleOption {
            return entries.firstOrNull { it.percent == percent } ?: DEFAULT
        }
    }
}

internal fun calculateSteamUiDensity(
    baseDensity: Float,
    scale: SteamUiScaleOption
): Float = (baseDensity * scale.factor).coerceAtLeast(0.1f)

private val Context.steamUiScaleDataStore by preferencesDataStore(
    name = "monica_steam_ui_scale"
)

class SteamUiScalePreferences(context: Context) {
    private val dataStore = context.applicationContext.steamUiScaleDataStore

    val scale: Flow<SteamUiScaleOption> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            SteamUiScaleOption.fromPercent(preferences[SCALE_PERCENT_KEY])
        }
        .distinctUntilChanged()

    suspend fun updateScale(scale: SteamUiScaleOption) {
        dataStore.edit { preferences ->
            preferences[SCALE_PERCENT_KEY] = scale.percent
        }
    }

    private companion object {
        val SCALE_PERCENT_KEY = intPreferencesKey("ui_scale_percent")
    }
}
