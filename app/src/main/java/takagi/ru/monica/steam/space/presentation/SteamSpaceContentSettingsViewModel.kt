package takagi.ru.monica.steam.space.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.space.data.SteamSpaceContentSettings
import takagi.ru.monica.steam.space.data.SteamSpaceContentSettingsStore
import takagi.ru.monica.steam.space.domain.SteamSpaceEventKind

internal data class SteamSpaceContentSettingsState(
    val hiddenKinds: Set<SteamSpaceEventKind> = emptySet(),
    val ready: Boolean = false,
    val saving: Boolean = false,
    val failed: Boolean = false
)

/** Filtering is local: changing a category never reloads, rewrites or truncates the feed cache. */
internal class SteamSpaceContentSettingsViewModel(
    private val store: () -> SteamSpaceContentSettingsStore,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val mutableState = MutableStateFlow(SteamSpaceContentSettingsState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val hidden = withContext(io) { store().loadHiddenKinds() }
                mutableState.value = SteamSpaceContentSettingsState(hiddenKinds = hidden, ready = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = SteamSpaceContentSettingsState(ready = true, failed = true)
            }
        }
    }

    fun setKindVisible(kind: SteamSpaceEventKind, visible: Boolean) {
        val hidden = state.value.hiddenKinds
        save(if (visible) hidden - kind else hidden + kind)
    }

    fun showAllKinds() = save(emptySet())

    fun retrySave() = save(state.value.hiddenKinds)

    private fun save(hidden: Set<SteamSpaceEventKind>) {
        val current = state.value
        if (!current.ready || current.saving || (hidden == current.hiddenKinds && !current.failed)) return
        // Apply immediately. A failed commit keeps this session's choice and reports that it
        // was not saved, matching SharedPreferences' memory-before-disk commit semantics.
        mutableState.value = current.copy(hiddenKinds = hidden, saving = true, failed = false)
        viewModelScope.launch {
            val saved = try {
                withContext(io) { store().saveHiddenKinds(hidden) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            mutableState.value = mutableState.value.copy(saving = false, failed = !saved)
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val app = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(SteamSpaceContentSettingsViewModel::class.java))
                    return SteamSpaceContentSettingsViewModel({ SteamSpaceContentSettings.create(app) }) as T
                }
            }
        }
    }
}
