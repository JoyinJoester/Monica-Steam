package takagi.ru.monica.steam.friends.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.data.SteamFriendsCache
import takagi.ru.monica.steam.friends.data.SteamFriendsPreferencesCache
import takagi.ru.monica.steam.friends.data.SteamFriendsService
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.domain.SteamFriendsGateway
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamSessionRefreshService

class SteamFriendsViewModel(
    private val gateway: SteamFriendsGateway,
    private val cache: SteamFriendsCache,
    private val sessionRefreshService: SteamSessionRefreshService = SteamSessionRefreshService()
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamFriendsUiState())
    val uiState: StateFlow<SteamFriendsUiState> = _uiState.asStateFlow()

    private var activeAccount: SteamAccount? = null
    private var requestGeneration = 0L
    private var actionGeneration = 0L

    fun selectAccount(account: SteamAccount?) {
        if (account?.id == activeAccount?.id) {
            activeAccount = account
            return
        }
        activeAccount = account
        val generation = ++requestGeneration
        actionGeneration++
        if (account == null) {
            _uiState.value = SteamFriendsUiState(
                failure = SteamFriendsFailureReason.ACCOUNT_REQUIRED
            )
            return
        }
        _uiState.value = SteamFriendsUiState(
            accountId = account.id,
            loading = true
        )
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { cache.load(account.steamId) }
            if (!isCurrent(account.id, generation)) return@launch
            _uiState.value = _uiState.value.copy(
                snapshot = cached,
                loading = cached == null,
                refreshing = cached != null,
                fromCache = cached != null
            )
            fetch(account, generation, silent = cached != null)
        }
    }

    fun refresh() {
        val account = activeAccount ?: run {
            _uiState.value = _uiState.value.copy(
                failure = SteamFriendsFailureReason.ACCOUNT_REQUIRED
            )
            return
        }
        val generation = ++requestGeneration
        _uiState.value = _uiState.value.copy(
            loading = _uiState.value.snapshot == null,
            refreshing = _uiState.value.snapshot != null,
            failure = null
        )
        fetch(account, generation, silent = false)
    }

    fun respondToInvite(friend: SteamFriend, accept: Boolean) {
        if (friend.relationship != SteamFriendRelationship.REQUEST_INCOMING) return
        val account = activeAccount ?: return
        if (_uiState.value.actionSteamId != null) return
        val generation = ++actionGeneration
        _uiState.value = _uiState.value.copy(
            actionSteamId = friend.steamId,
            actionFeedback = null
        )
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val prepared = prepareSession(account)
                    gateway.respondToInvite(prepared, friend.steamId, accept)
                }
            }
            if (!isActionCurrent(account.id, generation)) return@launch
            val error = result.exceptionOrNull()
            if (error != null) {
                SteamDiagLogger.append(
                    "friends invite_action failed accept=$accept type=${error.javaClass.simpleName}"
                )
                _uiState.value = _uiState.value.copy(
                    actionSteamId = null,
                    actionFeedback = SteamFriendActionFeedback(
                        steamId = friend.steamId,
                        accepted = accept,
                        success = false,
                        message = error.message
                    )
                )
                return@launch
            }

            val actionResult = result.getOrThrow()
            if (actionResult.success) {
                val current = _uiState.value.snapshot
                val updated = current?.copy(
                    friends = current.friends.mapNotNull { existing ->
                        if (existing.steamId != friend.steamId) return@mapNotNull existing
                        if (accept) {
                            existing.copy(relationship = SteamFriendRelationship.FRIEND)
                        } else {
                            null
                        }
                    }
                )
                if (updated != null) {
                    withContext(Dispatchers.IO) { cache.save(account.steamId, updated) }
                }
                if (!isActionCurrent(account.id, generation)) return@launch
                _uiState.value = _uiState.value.copy(
                    snapshot = updated,
                    fromCache = false,
                    actionSteamId = null,
                    actionFeedback = SteamFriendActionFeedback(
                        steamId = friend.steamId,
                        accepted = accept,
                        success = true,
                        message = actionResult.message
                    )
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    actionSteamId = null,
                    actionFeedback = SteamFriendActionFeedback(
                        steamId = friend.steamId,
                        accepted = accept,
                        success = false,
                        message = actionResult.message
                    )
                )
            }
        }
    }

    fun consumeActionFeedback() {
        _uiState.value = _uiState.value.copy(actionFeedback = null)
    }

    private fun fetch(account: SteamAccount, generation: Long, silent: Boolean) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val prepared = prepareSession(account)
                    gateway.fetch(prepared)
                }
            }
            if (!isCurrent(account.id, generation)) return@launch
            val error = result.exceptionOrNull()
            if (error != null) {
                SteamDiagLogger.append(
                    "friends refresh failed silent=$silent type=${error.javaClass.simpleName}"
                )
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    refreshing = false,
                    fromCache = _uiState.value.snapshot != null,
                    failure = error.toFailureReason()
                )
                return@launch
            }
            val snapshot = result.getOrThrow()
            withContext(Dispatchers.IO) { cache.save(account.steamId, snapshot) }
            if (!isCurrent(account.id, generation)) return@launch
            _uiState.value = _uiState.value.copy(
                snapshot = snapshot,
                loading = false,
                refreshing = false,
                fromCache = false,
                failure = null
            )
        }
    }

    private fun prepareSession(account: SteamAccount): SteamAccount {
        val refreshed = sessionRefreshService.refreshIfNeeded(account) ?: return account
        return account.copy(
            accessToken = refreshed.accessToken,
            refreshToken = refreshed.refreshToken ?: account.refreshToken,
            steamLoginSecure = "${account.steamId}||${refreshed.accessToken}"
        )
    }

    private fun isCurrent(accountId: Long, generation: Long): Boolean =
        activeAccount?.id == accountId && requestGeneration == generation

    private fun isActionCurrent(accountId: Long, generation: Long): Boolean =
        activeAccount?.id == accountId && actionGeneration == generation

    private fun Throwable.toFailureReason(): SteamFriendsFailureReason = when (this) {
        is IOException -> SteamFriendsFailureReason.NETWORK
        is SteamApiException -> when (eResult) {
            401, 403, 5, 15 -> SteamFriendsFailureReason.SESSION_REQUIRED
            else -> SteamFriendsFailureReason.UNAVAILABLE
        }
        is IllegalArgumentException, is IllegalStateException -> SteamFriendsFailureReason.SESSION_REQUIRED
        else -> SteamFriendsFailureReason.UNAVAILABLE
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SteamFriendsViewModel(
                        gateway = SteamFriendsService(),
                        cache = SteamFriendsPreferencesCache(appContext)
                    ) as T
                }
            }
        }
    }
}
