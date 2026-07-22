package takagi.ru.monica.steam.store

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.network.SteamSessionRefreshService

data class SteamStoreUiState(
    val accounts: List<SteamAccount> = emptyList(),
    val selectedAccountId: Long? = null,
    val home: SteamStoreHome? = null,
    val homeFromCache: Boolean = false,
    val loadingHome: Boolean = false,
    val query: String = "",
    val searchResults: List<SteamStoreItem> = emptyList(),
    val searching: Boolean = false,
    val detail: SteamStoreDetail? = null,
    val detailFromCache: Boolean = false,
    val loadingDetail: Boolean = false,
    val error: String? = null,
    val webUrl: String? = null,
    val cart: List<SteamCartItem> = emptyList(),
    val cartOpen: Boolean = false,
    val checkoutPackageIds: List<Int> = emptyList()
)

class SteamStoreViewModel(
    private val accountRepository: SteamAccountRepository,
    private val cache: SteamStoreCache,
    private val service: SteamStoreService = SteamStoreService(),
    private val sessionRefreshService: SteamSessionRefreshService = SteamSessionRefreshService()
) : ViewModel() {
    private var searchDebounceJob: Job? = null
    private var searchRequestJob: Job? = null
    private val _uiState = MutableStateFlow(SteamStoreUiState())
    val uiState: StateFlow<SteamStoreUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.observeAccounts().collect { accounts ->
                val previousId = _uiState.value.selectedAccountId
                val selected = accounts.firstOrNull { it.id == _uiState.value.selectedAccountId }
                    ?: accounts.firstOrNull { it.selected }
                    ?: accounts.firstOrNull()
                _uiState.value = _uiState.value.copy(
                    accounts = accounts,
                    selectedAccountId = selected?.id
                )
                if (previousId != selected?.id || _uiState.value.home == null) {
                    resetStoreForAccount(selected?.id)
                    loadCart(selected?.id)
                    loadHome(force = true)
                }
            }
        }
    }

    fun loadHome(force: Boolean = false) {
        if (_uiState.value.loadingHome) return
        val accountId = _uiState.value.selectedAccountId
        val account = selectedAccount()
        viewModelScope.launch {
            if (_uiState.value.home == null) {
                val cached = withContext(Dispatchers.IO) { cache.readHome(accountId) }
                if (cached != null) {
                    _uiState.value = _uiState.value.copy(home = cached, homeFromCache = true)
                }
            }
            if (!force && _uiState.value.home != null && !_uiState.value.homeFromCache) return@launch
            _uiState.value = _uiState.value.copy(loadingHome = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    executeStoreRequest(account) { credentials ->
                        service.featured(
                            steamLoginSecure = credentials.steamLoginSecure,
                            accessToken = credentials.accessToken
                        )
                    }
                }
            }
                .onSuccess { home ->
                    if (_uiState.value.selectedAccountId != accountId) return@onSuccess
                    withContext(Dispatchers.IO) { cache.writeHome(accountId, home) }
                    _uiState.value = _uiState.value.copy(
                        home = home,
                        homeFromCache = false,
                        loadingHome = false
                    )
                }
                .onFailure { error ->
                    if (_uiState.value.selectedAccountId != accountId) return@onFailure
                    _uiState.value = _uiState.value.copy(
                        loadingHome = false,
                        error = error.message ?: "Steam 商店连接失败"
                    )
                }
        }
    }

    fun updateQuery(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
        searchDebounceJob?.cancel()
        searchRequestJob?.cancel()
        if (value.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), searching = false)
        } else {
            searchDebounceJob = viewModelScope.launch {
                delay(350)
                search()
            }
        }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return
        val accountId = _uiState.value.selectedAccountId
        val account = selectedAccount()
        searchRequestJob?.cancel()
        searchRequestJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(searching = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    executeStoreRequest(account) { credentials ->
                        service.search(
                            queryText = query,
                            steamLoginSecure = credentials.steamLoginSecure,
                            accessToken = credentials.accessToken
                        )
                    }
                }
            }
                .onSuccess { results ->
                    if (_uiState.value.query.trim() != query ||
                        _uiState.value.selectedAccountId != accountId
                    ) return@onSuccess
                    _uiState.value = _uiState.value.copy(
                        searchResults = results,
                        searching = false
                    )
                }
                .onFailure { error ->
                    if (_uiState.value.query.trim() != query ||
                        _uiState.value.selectedAccountId != accountId
                    ) return@onFailure
                    _uiState.value = _uiState.value.copy(
                        searching = false,
                        error = error.message ?: "搜索失败"
                    )
                }
        }
    }

    fun openDetail(appId: Int) {
        val accountId = _uiState.value.selectedAccountId
        val account = selectedAccount()
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { cache.readDetail(accountId, appId) }
            _uiState.value = _uiState.value.copy(
                detail = cached,
                detailFromCache = cached != null,
                loadingDetail = true,
                error = null
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    executeStoreRequest(account) { credentials ->
                        service.detail(
                            appId = appId,
                            steamLoginSecure = credentials.steamLoginSecure,
                            accessToken = credentials.accessToken
                        )
                    }
                }
            }
                .onSuccess { detail ->
                    if (_uiState.value.selectedAccountId != accountId) return@onSuccess
                    withContext(Dispatchers.IO) { cache.writeDetail(accountId, detail) }
                    _uiState.value = _uiState.value.copy(
                        detail = detail,
                        detailFromCache = false,
                        loadingDetail = false
                    )
                }
                .onFailure { error ->
                    if (_uiState.value.selectedAccountId != accountId) return@onFailure
                    _uiState.value = _uiState.value.copy(
                        loadingDetail = false,
                        error = error.message ?: "商品详情加载失败"
                    )
                }
        }
    }

    fun closeDetail() {
        _uiState.value = _uiState.value.copy(detail = null, loadingDetail = false, error = null)
    }

    fun addDetailToCart(detail: SteamStoreDetail) {
        val item = SteamCartItem(
            appId = detail.appId,
            packageId = detail.packageId,
            name = detail.name,
            imageUrl = detail.headerImageUrl,
            currency = detail.currency,
            initialPriceCents = detail.initialPriceCents,
            finalPriceCents = detail.finalPriceCents,
            discountPercent = detail.discountPercent
        )
        updateCart((_uiState.value.cart.filterNot { it.appId == item.appId } + item))
    }

    fun removeFromCart(appId: Int) = updateCart(_uiState.value.cart.filterNot { it.appId == appId })
    fun clearCart() = updateCart(emptyList())
    fun openCart() { _uiState.value = _uiState.value.copy(cartOpen = true, detail = null) }
    fun closeCart() { _uiState.value = _uiState.value.copy(cartOpen = false) }
    fun isInCart(appId: Int): Boolean = _uiState.value.cart.any { it.appId == appId }

    fun checkout() {
        val ids = steamCartCheckoutPackageIds(_uiState.value.cart)
        if (ids.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "购物车中的商品暂时无法自动同步，请从商品详情进入 Steam 购买")
            return
        }
        _uiState.value = _uiState.value.copy(
            checkoutPackageIds = ids,
            webUrl = "https://store.steampowered.com/cart/",
            cartOpen = false
        )
    }

    private fun loadCart(accountId: Long?) {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { cache.readCart(accountId) }
            if (_uiState.value.selectedAccountId == accountId) _uiState.value = _uiState.value.copy(cart = items)
        }
    }

    private fun updateCart(items: List<SteamCartItem>) {
        val accountId = _uiState.value.selectedAccountId
        _uiState.value = _uiState.value.copy(cart = items)
        viewModelScope.launch(Dispatchers.IO) { cache.writeCart(accountId, items) }
    }

    fun selectAccount(accountId: Long) {
        if (_uiState.value.accounts.none { it.id == accountId }) return
        if (_uiState.value.selectedAccountId == accountId) return
        resetStoreForAccount(accountId)
        loadHome(force = true)
        viewModelScope.launch { accountRepository.select(accountId) }
    }

    private fun resetStoreForAccount(accountId: Long?) {
        searchDebounceJob?.cancel()
        searchRequestJob?.cancel()
        _uiState.value = _uiState.value.copy(
            selectedAccountId = accountId,
            home = null,
            homeFromCache = false,
            loadingHome = false,
            query = "",
            searchResults = emptyList(),
            searching = false,
            detail = null,
            detailFromCache = false,
            loadingDetail = false,
            error = null,
            cart = emptyList(),
            cartOpen = false,
            checkoutPackageIds = emptyList()
        )
    }

    fun openStoreWeb(url: String) {
        if (SteamStoreNavigationPolicy.isAllowed(url)) {
            _uiState.value = _uiState.value.copy(webUrl = url)
        }
    }

    fun closeStoreWeb() {
        _uiState.value = _uiState.value.copy(webUrl = null, checkoutPackageIds = emptyList())
    }

    fun selectedAccount(): SteamAccount? = _uiState.value.accounts
        .firstOrNull { it.id == _uiState.value.selectedAccountId }

    private suspend fun <T> executeStoreRequest(
        account: SteamAccount?,
        request: suspend (SteamStoreAccountCredentials) -> T
    ): T {
        if (account == null) return request(SteamStoreAccountCredentials(null, null))
        val prepared = refreshAccountSession(account, force = false)
        return executeSteamStoreAccountRetry(
            initialCredentials = prepared.toStoreCredentials(),
            forceRefreshCredentials = {
                refreshAccountSession(prepared, force = true).toStoreCredentials()
            },
            request = request
        )
    }

    private fun SteamAccount.toStoreCredentials(): SteamStoreAccountCredentials =
        SteamStoreAccountCredentials(
            accessToken = accessToken,
            steamLoginSecure = steamLoginSecure
        )

    private suspend fun refreshAccountSession(
        account: SteamAccount,
        force: Boolean
    ): SteamAccount {
        val refreshResult = if (force) {
            val refreshToken = account.refreshToken?.takeIf(String::isNotBlank) ?: return account
            sessionRefreshService.refresh(account.steamId, refreshToken)
        } else {
            sessionRefreshService.refreshIfNeeded(account)
        } ?: return account
        val refreshed = account.copy(
            accessToken = refreshResult.accessToken,
            refreshToken = refreshResult.refreshToken ?: account.refreshToken,
            steamLoginSecure = "${account.steamId}||${refreshResult.accessToken}"
        )
        accountRepository.updateSessionTokens(
            id = account.id,
            accessToken = refreshResult.accessToken,
            refreshToken = refreshed.refreshToken,
            steamLoginSecure = refreshed.steamLoginSecure
        )
        _uiState.value = _uiState.value.copy(
            accounts = _uiState.value.accounts.map { existing ->
                if (existing.id == refreshed.id) refreshed else existing
            }
        )
        return refreshed
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val database = SteamDatabase.getDatabase(appContext)
                    return SteamStoreViewModel(
                        accountRepository = SteamAccountRepository(
                            database.steamAccountDao(),
                            SecurityManager(appContext)
                        ),
                        cache = SteamStoreCache(appContext)
                    ) as T
                }
            }
        }
    }
}
