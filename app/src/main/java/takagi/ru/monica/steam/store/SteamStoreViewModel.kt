package takagi.ru.monica.steam.store

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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
import takagi.ru.monica.steam.library.SteamCurrencyExchangeService
import takagi.ru.monica.steam.library.SteamGameLibraryService
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamLibraryResult
import takagi.ru.monica.steam.library.SteamRegionalPrice
import takagi.ru.monica.steam.library.applyCnyConversions
import takagi.ru.monica.steam.library.mergeCachedRegionalPriceConversions
import takagi.ru.monica.steam.network.SteamSessionRefreshService
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger

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
    val collectionTab: SteamStoreCollectionTab = SteamStoreCollectionTab.CART,
    val wishlist: List<SteamWishlistItem> = emptyList(),
    val wishlistLoaded: Boolean = false,
    val wishlistFromCache: Boolean = false,
    val loadingWishlist: Boolean = false,
    val wishlistError: String? = null,
    val wishlistMutatingAppIds: Set<Int> = emptySet(),
    val regionalPrices: List<SteamRegionalPrice> = emptyList(),
    val regionalPricesAppId: Int? = null,
    val regionalPricesFromCache: Boolean = false,
    val loadingRegionalPrices: Boolean = false,
    val regionalPriceFailure: SteamLibraryFailureReason? = null,
    val regionalPriceSheetOpen: Boolean = false,
    val checkoutPackageIds: List<Int> = emptyList()
)

class SteamStoreViewModel(
    private val accountRepository: SteamAccountRepository,
    private val cache: SteamStoreCache,
    private val service: SteamStoreService = SteamStoreService(),
    private val sessionRefreshService: SteamSessionRefreshService = SteamSessionRefreshService(),
    private val libraryService: SteamGameLibraryService = SteamGameLibraryService(),
    private val currencyExchangeService: SteamCurrencyExchangeService =
        SteamCurrencyExchangeService()
) : ViewModel() {
    private var searchDebounceJob: Job? = null
    private var searchRequestJob: Job? = null
    private var detailRequestGeneration: Long = 0L
    private var regionalPriceRequestGeneration: Long = 0L
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
                    loadWishlistCache(selected?.id)
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
                if (_uiState.value.selectedAccountId != accountId) return@launch
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
        val generation = ++detailRequestGeneration
        regionalPriceRequestGeneration++
        if (account?.hasRealSteamId == true && !_uiState.value.wishlistLoaded) {
            loadWishlist()
        }
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { cache.readDetail(accountId, appId) }
            if (generation != detailRequestGeneration ||
                _uiState.value.selectedAccountId != accountId
            ) return@launch
            _uiState.value = _uiState.value.copy(
                detail = cached,
                detailFromCache = cached != null,
                loadingDetail = true,
                regionalPrices = emptyList(),
                regionalPricesAppId = appId,
                regionalPricesFromCache = false,
                loadingRegionalPrices = false,
                regionalPriceFailure = null,
                regionalPriceSheetOpen = false,
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
                    if (!steamStoreDetailRequestIsCurrent(
                            state = _uiState.value,
                            accountId = accountId,
                            appId = appId,
                            generation = generation,
                            currentGeneration = detailRequestGeneration
                        )
                    ) return@onSuccess
                    withContext(Dispatchers.IO) { cache.writeDetail(accountId, detail) }
                    if (!steamStoreDetailRequestIsCurrent(
                            state = _uiState.value,
                            accountId = accountId,
                            appId = appId,
                            generation = generation,
                            currentGeneration = detailRequestGeneration
                        )
                    ) return@onSuccess
                    _uiState.value = _uiState.value.copy(
                        detail = detail,
                        detailFromCache = false,
                        loadingDetail = false
                    )
                }
                .onFailure { error ->
                    if (!steamStoreDetailRequestIsCurrent(
                            state = _uiState.value,
                            accountId = accountId,
                            appId = appId,
                            generation = generation,
                            currentGeneration = detailRequestGeneration
                        )
                    ) return@onFailure
                    _uiState.value = _uiState.value.copy(
                        loadingDetail = false,
                        error = error.message ?: "商品详情加载失败"
                    )
                }
        }
    }

    fun closeDetail() {
        detailRequestGeneration++
        regionalPriceRequestGeneration++
        _uiState.value = _uiState.value.copy(
            detail = null,
            loadingDetail = false,
            regionalPrices = emptyList(),
            regionalPricesAppId = null,
            regionalPricesFromCache = false,
            loadingRegionalPrices = false,
            regionalPriceFailure = null,
            regionalPriceSheetOpen = false,
            error = null
        )
    }

    fun openRegionalPrices(appId: Int) {
        if (_uiState.value.detail?.appId != appId) return
        _uiState.value = _uiState.value.copy(
            regionalPricesAppId = appId,
            regionalPriceSheetOpen = true
        )
        loadRegionalPrices(appId)
    }

    fun closeRegionalPrices() {
        _uiState.value = _uiState.value.copy(regionalPriceSheetOpen = false)
    }

    fun loadRegionalPrices(appId: Int, force: Boolean = false) {
        val initialState = _uiState.value
        if (initialState.detail?.appId != appId) return
        if (initialState.loadingRegionalPrices && initialState.regionalPricesAppId == appId) return
        val accountId = initialState.selectedAccountId
        val account = selectedAccount()
        if (account == null || !account.hasRealSteamId) {
            _uiState.value = initialState.copy(
                regionalPricesAppId = appId,
                loadingRegionalPrices = false,
                regionalPriceFailure = SteamLibraryFailureReason.SESSION_REQUIRED
            )
            return
        }
        val memoryPrices = initialState.regionalPrices
            .takeIf { initialState.regionalPricesAppId == appId }
            .orEmpty()
        if (!force && regionalPricesAreReady(memoryPrices)) return
        val generation = ++regionalPriceRequestGeneration
        _uiState.value = initialState.copy(
            regionalPrices = memoryPrices,
            regionalPricesAppId = appId,
            loadingRegionalPrices = true,
            regionalPriceFailure = null
        )
        viewModelScope.launch {
            var availablePrices = memoryPrices
            if (availablePrices.isEmpty()) {
                availablePrices = withContext(Dispatchers.IO) {
                    cache.readRegionalPrices(accountId, appId)
                }
                if (!regionalPriceRequestIsCurrent(accountId, appId, generation)) return@launch
                if (availablePrices.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        regionalPrices = availablePrices,
                        regionalPricesFromCache = true
                    )
                }
            }
            if (!force && regionalPricesAreReady(availablePrices)) {
                _uiState.value = _uiState.value.copy(loadingRegionalPrices = false)
                return@launch
            }
            val result = try {
                withContext(Dispatchers.IO) {
                    when (val prices = fetchRegionalPricesWithSessionRetry(account, appId)) {
                        is SteamLibraryResult.Success -> {
                            val exchangeRates = runCatching {
                                currencyExchangeService.fetchCnyRates()
                            }.getOrNull()
                            val converted = applyCnyConversions(
                                prices = prices.value,
                                unitsPerCny = exchangeRates?.unitsPerCny.orEmpty(),
                                exchangeRateFetchedAt = exchangeRates?.fetchedAt
                                    ?: System.currentTimeMillis()
                            )
                            SteamLibraryResult.Success(
                                mergeCachedRegionalPriceConversions(
                                    fresh = converted,
                                    cached = availablePrices
                                )
                            )
                        }
                        is SteamLibraryResult.Failure -> prices
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                SteamDiagLogger.append(
                    "store_regional_prices failed type=${error::class.java.simpleName}"
                )
                SteamLibraryResult.Failure(SteamLibraryFailureReason.NETWORK)
            }
            if (!regionalPriceRequestIsCurrent(accountId, appId, generation)) return@launch
            when (result) {
                is SteamLibraryResult.Success -> {
                    withContext(Dispatchers.IO) {
                        cache.writeRegionalPrices(accountId, appId, result.value)
                    }
                    if (!regionalPriceRequestIsCurrent(accountId, appId, generation)) return@launch
                    _uiState.value = _uiState.value.copy(
                        regionalPrices = result.value,
                        regionalPricesFromCache = false,
                        loadingRegionalPrices = false,
                        regionalPriceFailure = null
                    )
                }
                is SteamLibraryResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        loadingRegionalPrices = false,
                        regionalPriceFailure = result.reason
                    )
                }
            }
        }
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
    fun openCart() {
        _uiState.value = _uiState.value.copy(
            cartOpen = true,
            collectionTab = SteamStoreCollectionTab.CART,
            detail = null
        )
    }
    fun closeCart() { _uiState.value = _uiState.value.copy(cartOpen = false) }
    fun isInCart(appId: Int): Boolean = _uiState.value.cart.any { it.appId == appId }
    fun isInWishlist(appId: Int): Boolean = _uiState.value.wishlist.any { it.appId == appId }

    fun selectCollectionTab(tab: SteamStoreCollectionTab) {
        _uiState.value = _uiState.value.copy(collectionTab = tab)
        if (tab == SteamStoreCollectionTab.WISHLIST) loadWishlist()
    }

    fun loadWishlist(force: Boolean = false) {
        val state = _uiState.value
        if (state.loadingWishlist) return
        if (!force && state.wishlistLoaded && !state.wishlistFromCache) return
        val accountId = state.selectedAccountId
        val account = selectedAccount()
        if (account == null || !account.hasRealSteamId) {
            _uiState.value = state.copy(
                wishlistLoaded = true,
                wishlistError = "请先选择有效的 Steam 账号"
            )
            return
        }
        viewModelScope.launch {
            if (_uiState.value.selectedAccountId != accountId) return@launch
            _uiState.value = _uiState.value.copy(loadingWishlist = true, wishlistError = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    executeStoreRequest(account) { credentials ->
                        service.wishlist(
                            steamId = account.steamId,
                            steamLoginSecure = credentials.steamLoginSecure,
                            accessToken = credentials.accessToken
                        )
                    }
                }
            }.onSuccess { items ->
                if (_uiState.value.selectedAccountId != accountId) return@onSuccess
                val snapshot = SteamWishlistSnapshot(items)
                withContext(Dispatchers.IO) { cache.writeWishlist(accountId, snapshot) }
                _uiState.value = _uiState.value.copy(
                    wishlist = items,
                    wishlistLoaded = true,
                    wishlistFromCache = false,
                    loadingWishlist = false,
                    wishlistError = null
                )
            }.onFailure { error ->
                if (_uiState.value.selectedAccountId != accountId) return@onFailure
                _uiState.value = _uiState.value.copy(
                    wishlistLoaded = true,
                    loadingWishlist = false,
                    wishlistError = error.message ?: "Steam 愿望单同步失败"
                )
            }
        }
    }

    fun toggleWishlist(detail: SteamStoreDetail) {
        if (detail.appId in _uiState.value.wishlistMutatingAppIds) return
        val accountId = _uiState.value.selectedAccountId
        val account = selectedAccount() ?: return
        val add = !isInWishlist(detail.appId)
        _uiState.value = _uiState.value.copy(
            wishlistMutatingAppIds = _uiState.value.wishlistMutatingAppIds + detail.appId,
            wishlistError = null
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    executeStoreRequest(account) { credentials ->
                        service.setWishlist(
                            appId = detail.appId,
                            add = add,
                            steamLoginSecure = credentials.steamLoginSecure,
                            accessToken = credentials.accessToken
                        )
                    }
                }
            }.onSuccess {
                if (_uiState.value.selectedAccountId != accountId) return@onSuccess
                val updated = if (add) {
                    (_uiState.value.wishlist.filterNot { it.appId == detail.appId } +
                        detail.toWishlistItem()).sortedByDescending { it.addedAtEpochSeconds }
                } else {
                    _uiState.value.wishlist.filterNot { it.appId == detail.appId }
                }
                _uiState.value = _uiState.value.copy(
                    wishlist = updated,
                    wishlistLoaded = true,
                    wishlistFromCache = false,
                    wishlistMutatingAppIds = _uiState.value.wishlistMutatingAppIds - detail.appId
                )
                withContext(Dispatchers.IO) {
                    cache.writeWishlist(accountId, SteamWishlistSnapshot(updated))
                }
            }.onFailure { error ->
                if (_uiState.value.selectedAccountId != accountId) return@onFailure
                _uiState.value = _uiState.value.copy(
                    wishlistMutatingAppIds = _uiState.value.wishlistMutatingAppIds - detail.appId,
                    wishlistError = error.message ?: "Steam 愿望单修改失败"
                )
            }
        }
    }

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

    private fun loadWishlistCache(accountId: Long?) {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { cache.readWishlist(accountId) }
            if (_uiState.value.selectedAccountId == accountId && snapshot != null) {
                _uiState.value = _uiState.value.copy(
                    wishlist = snapshot.items,
                    wishlistLoaded = true,
                    wishlistFromCache = true
                )
            }
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
        detailRequestGeneration++
        regionalPriceRequestGeneration++
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
            collectionTab = SteamStoreCollectionTab.CART,
            wishlist = emptyList(),
            wishlistLoaded = false,
            wishlistFromCache = false,
            loadingWishlist = false,
            wishlistError = null,
            wishlistMutatingAppIds = emptySet(),
            regionalPrices = emptyList(),
            regionalPricesAppId = null,
            regionalPricesFromCache = false,
            loadingRegionalPrices = false,
            regionalPriceFailure = null,
            regionalPriceSheetOpen = false,
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

    private fun regionalPriceRequestIsCurrent(
        accountId: Long?,
        appId: Int,
        generation: Long
    ): Boolean {
        val state = _uiState.value
        return generation == regionalPriceRequestGeneration &&
            state.selectedAccountId == accountId &&
            state.detail?.appId == appId &&
            state.regionalPricesAppId == appId
    }

    private fun regionalPricesAreReady(prices: List<SteamRegionalPrice>): Boolean {
        if (prices.isEmpty()) return false
        val cacheIsFresh = prices.all { price ->
            System.currentTimeMillis() - price.fetchedAt < REGIONAL_PRICE_CACHE_TTL_MILLIS
        }
        val conversionsReady = prices
            .filter(SteamRegionalPrice::isAvailable)
            .all { it.cnyFinalPriceMinor != null && it.cnyOriginalPriceMinor != null }
        return cacheIsFresh && conversionsReady
    }

    private suspend fun fetchRegionalPricesWithSessionRetry(
        account: SteamAccount,
        appId: Int
    ): SteamLibraryResult<List<SteamRegionalPrice>> {
        val prepared = refreshAccountSession(account, force = false)
        val first = libraryService.fetchRegionalPrices(
            account = prepared,
            appId = appId,
            countryCodes = REGIONAL_PRICE_COUNTRY_CODES,
            language = "schinese"
        )
        if (first !is SteamLibraryResult.Failure ||
            first.reason != SteamLibraryFailureReason.SESSION_REQUIRED
        ) {
            return first
        }
        val refreshed = refreshAccountSession(prepared, force = true)
        return if (refreshed.accessToken != prepared.accessToken) {
            libraryService.fetchRegionalPrices(
                account = refreshed,
                appId = appId,
                countryCodes = REGIONAL_PRICE_COUNTRY_CODES,
                language = "schinese"
            )
        } else {
            first
        }
    }

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
        try {
            accountRepository.updateSessionTokens(
                id = account.id,
                accessToken = refreshResult.accessToken,
                refreshToken = refreshed.refreshToken,
                steamLoginSecure = refreshed.steamLoginSecure
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // Keep the fresh in-memory credentials usable for this request;
            // a transient persistence failure should not crash the store.
            SteamDiagLogger.append(
                "store_session_persist failed type=${error::class.java.simpleName}"
            )
        }
        _uiState.value = _uiState.value.copy(
            accounts = _uiState.value.accounts.map { existing ->
                if (existing.id == refreshed.id) refreshed else existing
            }
        )
        return refreshed
    }

    companion object {
        internal val REGIONAL_PRICE_COUNTRY_CODES =
            listOf("CN", "US", "JP", "KR", "HK", "TW", "UA", "IN", "ID")
        private const val REGIONAL_PRICE_CACHE_TTL_MILLIS = 6L * 60L * 60L * 1_000L

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

internal fun steamStoreDetailRequestIsCurrent(
    state: SteamStoreUiState,
    accountId: Long?,
    appId: Int,
    generation: Long,
    currentGeneration: Long
): Boolean {
    return generation == currentGeneration &&
        state.selectedAccountId == accountId &&
        (state.detail?.appId == appId ||
            (state.loadingDetail && state.regionalPricesAppId == appId))
}
