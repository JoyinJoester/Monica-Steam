package takagi.ru.monica.steam.space.data

import android.content.Context
import android.content.SharedPreferences
import takagi.ru.monica.steam.space.domain.SteamSpaceEventKind

/** Local display preferences only. Feed contents and account credentials never enter this store. */
internal interface SteamSpaceContentSettingsStore {
    fun loadHiddenKinds(): Set<SteamSpaceEventKind>
    fun saveHiddenKinds(kinds: Set<SteamSpaceEventKind>): Boolean
}

internal class SteamSpaceContentSettings(private val preferences: SharedPreferences) : SteamSpaceContentSettingsStore {
    override fun loadHiddenKinds(): Set<SteamSpaceEventKind> {
        val names = preferences.getStringSet(HIDDEN_KINDS, emptySet()).orEmpty()
        return SteamSpaceEventKind.entries.filterTo(mutableSetOf()) { it.name in names }
    }

    override fun saveHiddenKinds(kinds: Set<SteamSpaceEventKind>): Boolean = preferences.edit()
        .putStringSet(HIDDEN_KINDS, kinds.mapTo(mutableSetOf()) { it.name })
        .commit()

    companion object {
        internal const val HIDDEN_KINDS = "hidden_event_kinds"
        internal const val PREFERENCES_NAME = "steam_space_content_settings"

        // Call on IO: even opening SharedPreferences can wait for its initial disk read.
        fun create(context: Context, name: String = PREFERENCES_NAME) = SteamSpaceContentSettings(
            context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        )
    }
}
