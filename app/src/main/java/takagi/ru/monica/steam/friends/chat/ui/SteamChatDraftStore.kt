package takagi.ru.monica.steam.friends.chat.ui

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.parcelize.Parcelize
import takagi.ru.monica.steam.store.share.domain.SteamStoreGameShare

@Parcelize
internal data class SteamChatDraft(
    val text: String = "",
    val gameShare: SteamStoreGameShare? = null
) : Parcelable

/** Owned by the chat screen, so leaving a thread does not discard its composer. */
internal class SteamChatDraftStore(initial: Map<String, SteamChatDraft> = emptyMap()) {
    private val drafts = mutableStateMapOf<String, SteamChatDraft>().apply { putAll(initial) }
    operator fun get(key: String): SteamChatDraft = drafts[key] ?: SteamChatDraft()
    operator fun set(key: String, value: SteamChatDraft) {
        if (value.text.isEmpty() && value.gameShare == null) drafts.remove(key)
        else drafts[key] = value
    }

    companion object {
        val saver = Saver<SteamChatDraftStore, HashMap<String, SteamChatDraft>>(
            save = { HashMap(it.drafts) },
            restore = { SteamChatDraftStore(it) }
        )
    }
}

internal val LocalSteamChatDraftStore = staticCompositionLocalOf<SteamChatDraftStore?> { null }

@Composable
internal fun rememberSteamChatDraftStore(): SteamChatDraftStore =
    rememberSaveable(saver = SteamChatDraftStore.saver) { SteamChatDraftStore() }
