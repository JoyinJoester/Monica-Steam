package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.friends.chat.info.data.SteamChatInfoPreferencesStore
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationId
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationPreferences

internal class SteamChatConversationPreferencesState(initiallyLoaded: Boolean) :
    MutableState<SteamChatConversationPreferences> by mutableStateOf(SteamChatConversationPreferences()) {
    var loaded by mutableStateOf(initiallyLoaded)
        internal set
}

@Composable
internal fun rememberSteamChatConversationPreferences(
    store: SteamChatInfoPreferencesStore,
    id: SteamChatConversationId?
): SteamChatConversationPreferencesState {
    val state = remember(store, id) { SteamChatConversationPreferencesState(initiallyLoaded = id == null) }
    LaunchedEffect(store, id) {
        if (id != null) {
            state.value = withContext(Dispatchers.IO) { store.load(id) }
            state.loaded = true
        }
    }
    return state
}
