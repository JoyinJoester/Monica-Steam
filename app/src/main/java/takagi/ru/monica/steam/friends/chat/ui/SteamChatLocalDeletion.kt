package takagi.ru.monica.steam.friends.chat.ui

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.data.SteamChatLocalDeletionStore
import takagi.ru.monica.steam.friends.chat.data.steamChatLocalDeletionKey
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatFailureReason
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState

internal class SteamChatLocalDeletionState {
    var markers by mutableStateOf<Set<String>?>(null)
    var failed by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var revision by mutableIntStateOf(0)
    var delete: (SteamChatMessage) -> Unit = {}

    // Keep the ViewModel's raw history and pagination cursor intact; filter only the UI projection.
    fun visibleState(source: SteamChatUiState): SteamChatUiState {
        val hidden = markers ?: return source.copy(
            thread = null, threadLoading = !failed,
            threadFailure = if (failed) SteamChatFailureReason.UNAVAILABLE else null
        )
        val snapshot = source.thread ?: return source
        if (hidden.isEmpty()) return source
        return source.copy(thread = snapshot.copy(messages = snapshot.messages.filterNot {
            steamChatLocalDeletionKey(it) in hidden
        }))
    }
}

@Composable
internal fun rememberSteamChatLocalDeletion(account: String, partner: String?): SteamChatLocalDeletionState {
    val context = LocalContext.current
    val store = remember(context) { SteamChatLocalDeletionStore(context) }
    val state = remember(account, partner, store) { SteamChatLocalDeletionState() }
    val scope = rememberCoroutineScope()
    DisposableEffect(state) {
        // A confirmed write may finish after navigating away and back to this conversation.
        val stop = if (account.isNotBlank() && !partner.isNullOrBlank()) {
            store.observe(account, partner) { state.revision++ }
        } else ({})
        onDispose { stop() }
    }
    LaunchedEffect(state, state.revision) {
        state.failed = false
        if (account.isBlank() || partner.isNullOrBlank()) {
            state.markers = emptySet()
        } else try {
            val stored = withContext(Dispatchers.IO) { store.load(account, partner) }
            // A refresh started before a delete must not restore the older marker set.
            state.markers = state.markers.orEmpty() + stored
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state.failed = true
        }
    }
    state.delete = { message ->
        if (state.markers != null && !state.saving && !partner.isNullOrBlank() &&
            account.isNotBlank() && message.partnerSteamId == partner && steamChatLocalDeletionKey(message) != null
        ) {
            state.saving = true
            scope.launch {
                try {
                    val stored = withContext(Dispatchers.IO) { store.hide(account, partner, message) }
                    state.markers = state.markers.orEmpty() + stored
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.steam_chat_local_delete_failed, Toast.LENGTH_LONG).show()
                } finally {
                    state.saving = false
                }
            }
        }
    }
    return state
}
