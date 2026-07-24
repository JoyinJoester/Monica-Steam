package takagi.ru.monica.steam.friends.chat.presentation

import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot

enum class SteamChatFailureReason {
    ACCOUNT_REQUIRED,
    SESSION_REQUIRED,
    NETWORK,
    UNAVAILABLE
}

data class SteamChatUiState(
    val accountId: Long? = null,
    val accountSteamId: String = "",
    val sessions: SteamChatSessionsSnapshot? = null,
    val selectedPartnerSteamId: String? = null,
    val thread: SteamChatThreadSnapshot? = null,
    val sessionsLoading: Boolean = false,
    val sessionsRefreshing: Boolean = false,
    val threadLoading: Boolean = false,
    val threadRefreshing: Boolean = false,
    val loadingOlder: Boolean = false,
    val sessionsFromCache: Boolean = false,
    val threadFromCache: Boolean = false,
    val sessionsFailure: SteamChatFailureReason? = null,
    val threadFailure: SteamChatFailureReason? = null
) {
    val unreadCount: Int get() = sessions?.unreadCount ?: 0
}
