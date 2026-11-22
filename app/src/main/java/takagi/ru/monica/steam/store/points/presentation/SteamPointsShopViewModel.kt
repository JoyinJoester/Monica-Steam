package takagi.ru.monica.steam.store.points.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.store.points.data.SteamPointsShopCache
import takagi.ru.monica.steam.store.points.data.SteamPointsShopService
import takagi.ru.monica.steam.store.points.domain.SteamPointsShopCategory
import takagi.ru.monica.steam.store.points.domain.SteamPointsShopItem

internal data class SteamPointsShopUiState(
    val category: SteamPointsShopCategory = SteamPointsShopCategory.FEATURED,
    val items: List<SteamPointsShopItem> = emptyList(),
    val totalCount: Int = 0,
    val nextCursor: String? = null,
    val pointsBalance: Long? = null,
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val fromCache: Boolean = false,
    val error: String? = null
) {
    val hasMore: Boolean get() = !nextCursor.isNullOrBlank() && items.size < totalCount
}

internal class SteamPointsShopViewModel(
    private val cache: SteamPointsShopCache,
    private val service: SteamPointsShopService = SteamPointsShopService(),
    private val appId: Int? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamPointsShopUiState())
    val uiState: StateFlow<SteamPointsShopUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var generation = 0L
    private var account: SteamAccount? = null
    private var accountAttached = false

    fun attachAccount(value: SteamAccount?) {
        if (
            accountAttached && account?.id == value?.id &&
            account?.accessToken == value?.accessToken &&
            account?.steamLoginSecure == value?.steamLoginSecure
        ) return
        generation++
        loadJob?.cancel()
        accountAttached = true
        account = value
        _uiState.value = _uiState.value.copy(
            pointsBalance = null, loading = false, loadingMore = false,
            signedIn = value?.hasRealSteamId == true &&
                (!value.accessToken.isNullOrBlank() || !value.steamLoginSecure.isNullOrBlank())
        )
        load(force = false)
    }

    fun selectCategory(category: SteamPointsShopCategory) {
        if (_uiState.value.category == category) return
        loadJob?.cancel()
        generation++
        _uiState.value = SteamPointsShopUiState(
            category = category,
            signedIn = _uiState.value.signedIn,
            pointsBalance = _uiState.value.pointsBalance
        )
        load(force = false)
    }

    fun load(force: Boolean, loadMore: Boolean = false) {
        val category = _uiState.value.category
        if (_uiState.value.loading || _uiState.value.loadingMore) return
        if (loadMore && !_uiState.value.hasMore) return
        val requestGeneration = ++generation
        val selectedAccount = account
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!force && !loadMore && _uiState.value.items.isEmpty()) {
                val cached = withContext(Dispatchers.IO) { cache.read(category, appId) }
                if (requestIsCurrent(category, requestGeneration) && cached != null) {
                    _uiState.value = _uiState.value.copy(
                        items = cached.items,
                        totalCount = cached.totalCount,
                        nextCursor = cached.nextCursor,
                        fromCache = true
                    )
                }
            }
            if (!requestIsCurrent(category, requestGeneration)) return@launch
            val existing = _uiState.value
            _uiState.value = existing.copy(
                loading = !loadMore,
                loadingMore = loadMore,
                error = null
            )
            val pageResult = runCatching {
                withContext(Dispatchers.IO) {
                    service.page(category, if (loadMore) existing.nextCursor else null, appId = appId)
                }
            }
            val balanceResult = if (!loadMore && selectedAccount != null) {
                runCatching { withContext(Dispatchers.IO) { service.balance(selectedAccount) } }
            } else null
            if (!requestIsCurrent(category, requestGeneration)) return@launch
            pageResult.onSuccess { page ->
                val merged = if (loadMore) {
                    page.copy(items = (existing.items + page.items).distinctBy(SteamPointsShopItem::definitionId))
                } else page
                withContext(Dispatchers.IO) { cache.write(merged, appId) }
                if (!requestIsCurrent(category, requestGeneration)) return@launch
                _uiState.value = _uiState.value.copy(
                    items = merged.items,
                    totalCount = merged.totalCount,
                    nextCursor = merged.nextCursor,
                    pointsBalance = balanceResult?.getOrNull() ?: _uiState.value.pointsBalance,
                    loading = false,
                    loadingMore = false,
                    fromCache = false
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    pointsBalance = balanceResult?.getOrNull() ?: _uiState.value.pointsBalance,
                    loading = false,
                    loadingMore = false,
                    error = error.message ?: "Steam 点数商城加载失败"
                )
            }
        }
    }

    private fun requestIsCurrent(category: SteamPointsShopCategory, requestGeneration: Long) =
        generation == requestGeneration && _uiState.value.category == category

    companion object {
        fun factory(context: Context, appId: Int? = null): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SteamPointsShopViewModel(SteamPointsShopCache(context), appId = appId) as T
            }
    }
}
