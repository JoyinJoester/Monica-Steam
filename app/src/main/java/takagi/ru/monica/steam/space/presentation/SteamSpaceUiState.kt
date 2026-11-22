package takagi.ru.monica.steam.space.presentation

import takagi.ru.monica.steam.space.domain.SteamSpaceCursor
import takagi.ru.monica.steam.space.domain.SteamSpaceEvent

enum class SteamSpaceFailureReason {
    ACCOUNT_REQUIRED,
    SESSION_REQUIRED,
    NETWORK,
    UNAVAILABLE
}

data class SteamSpaceUiState(
    val accountId: Long? = null,
    val accountSteamId: String? = null,
    val items: List<SteamSpaceEvent> = emptyList(),
    val nextCursor: SteamSpaceCursor? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val loaded: Boolean = false,
    val failure: SteamSpaceFailureReason? = null,
    val loadMoreFailure: SteamSpaceFailureReason? = null,
    val partial: Boolean = false,
    val stale: Boolean = false
)
