package takagi.ru.monica.steam.store.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import takagi.ru.monica.steam.store.data.*
import takagi.ru.monica.steam.store.domain.*
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.hasAuthenticatedSession
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.data.SteamLibraryCacheRepository
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.library.SteamCurrencyExchangeService
import takagi.ru.monica.steam.library.SteamGameLibraryService
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamLibraryResult
import takagi.ru.monica.steam.library.SteamRegionalPrice
import takagi.ru.monica.steam.library.applyCnyConversions
import takagi.ru.monica.steam.library.mergeCachedRegionalPriceConversions
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.store.purchase.data.SteamStorePurchaseContextCache
import takagi.ru.monica.steam.store.purchase.data.SteamStorePurchaseContextSessionException
import takagi.ru.monica.steam.store.purchase.data.SteamStorePurchaseContextService
import takagi.ru.monica.steam.store.purchase.data.SteamStorePurchasePreferencesCache
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreOwnershipStatus
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePackageOption
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContext
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContextFailure
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContextGateway
import takagi.ru.monica.steam.store.freebie.data.SteamFreebieService
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimResult
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimStatus
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimMethod
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieItem
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieOfferKind
import takagi.ru.monica.steam.store.navigation.domain.SteamStoreDetailHistory
import takagi.ru.monica.steam.store.navigation.domain.SteamStoreDetailRoute
import takagi.ru.monica.steam.store.filters.domain.SteamStoreFilterMetadata
import takagi.ru.monica.steam.store.filters.domain.SteamStoreFilterSelection
import takagi.ru.monica.steam.store.filters.domain.findTagId
import takagi.ru.monica.steam.friends.data.SteamFriendsPreferencesCache
import takagi.ru.monica.steam.friends.data.SteamFriendsService
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.store.gift.data.SteamStoreGiftFriendRepository
import takagi.ru.monica.steam.store.gift.domain.SteamStoreCheckoutLine
import takagi.ru.monica.steam.store.gift.domain.SteamStoreGiftFailure
import takagi.ru.monica.steam.store.gift.domain.toSteamStoreGiftRecipient
import takagi.ru.monica.steam.store.gift.presentation.SteamStoreGiftUiState
import takagi.ru.monica.steam.store.interest.domain.withoutIgnoredGames
import takagi.ru.monica.steam.store.interest.domain.SteamStoreIgnoreSyncState
import takagi.ru.monica.steam.web.domain.SteamWebNavigationPolicy

data class SteamStoreUiState(
    val accounts: List<SteamAccount> = emptyList(),
    val selectedAccountId: Long? = null,
    val storageSource: SteamStorageSource = SteamStorageSource.Local,
    val mdbxDatabases: List<LocalMdbxDatabase> = emptyList(),
    val accountsLoading: Boolean = false,
    val accountSourceError: String? = null,
    val home: SteamStoreHome? = null,
    val homeFromCache: Boolean = false,
    val loadingHome: Boolean = false,
    val browseFilter: SteamStoreBrowseFilter = SteamStoreBrowseFilter.ALL,
    val catalogPage: SteamStoreCatalogPage? = null,
    val catalogFromCache: Boolean = false,
    val loadingCatalog: Boolean = false,
    val loadingMoreCatalog: Boolean = false,
    val catalogError: String? = null,
    val storeFilters: SteamStoreFilterSelection = SteamStoreFilterSelection(),
    val filterMetadata: SteamStoreFilterMetadata? = null,
    val filterMetadataFromCache: Boolean = false,
    val loadingFilterMetadata: Boolean = false,
    val filterMetadataError: String? = null,
    val query: String = "",
    val searchResults: List<SteamStoreItem> = emptyList(),
    val searching: Boolean = false,
    val detailAppId: Int? = null,
    val detailDiscoveryCountryCode: String? = null,
    val detail: SteamStoreDetail? = null,
    val detailFromCache: Boolean = false,
    val loadingDetail: Boolean = false,
    val purchaseContext: SteamStorePurchaseContext? = null,
    val purchaseContextFromCache: Boolean = false,
    val loadingPurchaseContext: Boolean = false,
    val purchaseContextFailure: SteamStorePurchaseContextFailure? = null,
    val freeLicenseClaimingAppIds: Set<Int> = emptySet(),
    val freeLicenseClaimResults: Map<Int, SteamFreebieClaimResult> = emptyMap(),
    val reviewFilters: SteamReviewFilterSelection = SteamReviewFilterSelection(),
    val loadingMoreReviews: Boolean = false,
    val reviewLoadError: String? = null,
    val error: String? = null,
    val familyViewUnlockRequired: Boolean = false,
    val webUrl: String? = null,
    val webRequiresAuthenticatedSession: Boolean = false,
    val webReturnRefreshRequired: Boolean = false,
    val pointsShopOpen: Boolean = false,
    val cart: List<SteamCartItem> = emptyList(),
    val cartOpen: Boolean = false,
    val collectionTab: SteamStoreCollectionTab = SteamStoreCollectionTab.CART,
    val wishlist: List<SteamWishlistItem> = emptyList(),
    val wishlistLoaded: Boolean = false,
    val wishlistFromCache: Boolean = false,
    val loadingWishlist: Boolean = false,
    val wishlistError: String? = null,
    val wishlistMutatingAppIds: Set<Int> = emptySet(),
    val ignoredMutatingAppIds: Set<Int> = emptySet(),
    val ignoredSyncStates: Map<Int, SteamStoreIgnoreSyncState> = emptyMap(),
    val ignoredError: String? = null,
    val ownedAppIds: Set<Int> = emptySet(),
    val familySharedAppIds: Set<Int> = emptySet(),
    val regionalPrices: List<SteamRegionalPrice> = emptyList(),
    val regionalPricesAppId: Int? = null,
    val regionalPricesFromCache: Boolean = false,
    val loadingRegionalPrices: Boolean = false,
    val regionalPriceFailure: SteamLibraryFailureReason? = null,
    val regionalPriceSheetOpen: Boolean = false,
    val gift: SteamStoreGiftUiState = SteamStoreGiftUiState(),
    val checkoutLines: List<SteamStoreCheckoutLine> = emptyList()
)

internal fun SteamStoreUiState.withIgnoredGameState(
    appId: Int,
    ignored: Boolean,
    syncState: SteamStoreIgnoreSyncState? = null
): SteamStoreUiState {
    val updatedDetail = detail?.let { current ->
        if (current.appId == appId) current.copy(ignored = ignored) else current
    }
    val updatedSyncStates = when {
        syncState != null -> ignoredSyncStates + (appId to syncState)
        !ignored -> ignoredSyncStates - appId
        else -> ignoredSyncStates
    }
    if (!ignored) {
        return copy(
            detail = updatedDetail,
            ignoredSyncStates = updatedSyncStates
        )
    }
    val ignoredIds = setOf(appId)
    return copy(
        home = home?.withoutIgnoredGames(ignoredIds),
        catalogPage = catalogPage?.withoutIgnoredGames(ignoredIds),
        searchResults = searchResults.withoutIgnoredGames(ignoredIds),
        detail = updatedDetail,
        ignoredSyncStates = updatedSyncStates
    )
}

class SteamStoreViewModel internal constructor(
    private val accountSourceRepository: SteamAccountSourceRepository,
    private val cache: SteamStoreCache,
    private val service: SteamStoreService = SteamStoreService(),
    /** Shared single-flight resolver; null is only the unauthenticated test/read-only mode. */
    private val sessionResolver: SteamAccountSessionResolver? = null,
    private val libraryService: SteamGameLibraryService = SteamGameLibraryService(),
    private val currencyExchangeService: SteamCurrencyExchangeService =
        SteamCurrencyExchangeService(),
    private val purchaseContextGateway: SteamStorePurchaseContextGateway =
        SteamStorePurchaseContextService(),
    private val purchaseContextCache: SteamStorePurchaseContextCache? = null,
    private val libraryCacheRepository: SteamLibraryCacheRepository? = null,
    private val giftFriendRepository: SteamStoreGiftFriendRepository? = null,
    private val freebieService: SteamFreebieService = SteamFreebieService()
) : ViewModel() {
    private var searchDebounceJob: Job? = null
    private var searchRequestJob: Job? = null
    private var catalogRequestJob: Job? = null
    private var catalogRequestGeneration: Long = 0L
    private var filterMetadataRequestGeneration: Long = 0L
    private var detailRequestGeneration: Long = 0L
    private val detailHistory = SteamStoreDetailHistory()
    private var regionalPriceRequestGeneration: Long = 0L
    private var libraryHintRequestGeneration: Long = 0L
    private var giftFriendsRequestGeneration: Long = 0L
    private val freeLicenseVerificationJobs = mutableMapOf<Int, Job>()
    private val accountLoadTracker = SteamStoreAccountLoadTracker()
    private val _uiState = MutableStateFlow(SteamStoreUiState())
    val uiState: StateFlow<SteamStoreUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accountSourceRepository.state.collect { sourceState ->
                val accounts = sourceState.accounts.filter { it.hasAuthenticatedSession }
                val selected = accounts.firstOrNull { it.id == sourceState.selectedAccountId }
                    ?: accounts.firstOrNull()
                _uiState.value = _uiState.value.copy(
                    accounts = accounts,
                    selectedAccountId = selected?.id,
                    storageSource = sourceState.storageSource,
                    mdbxDatabases = sourceState.mdbxDatabases,
                    accountsLoading = sourceState.loading,
                    accountSourceError = sourceState.errorMessage
                )
                if (accountLoadTracker.shouldInitialize(selected?.id, sourceState.storageSource)) {
                    resetStoreForAccount(selected?.id)
                    loadLibraryHints(selected?.id, sourceState.storageSource)
                    loadCart(selected?.id)
                    loadWishlistCache(selected?.id)
                    loadStoreFilterMetadata()
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
                val cached = withContext(Dispatchers.IO) {
                    cache.readHome(accountId)?.withoutIgnoredGames(
                        service.localIgnoredAppIds(account?.steamId)
                    )
                }
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
                            accessToken = credentials.accessToken,
                            steamId = credentials.steamId
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
                        loadingHome = false,
                        familyViewUnlockRequired = false,
                    )
                }
                .onFailure { error ->
                    if (_uiState.value.selectedAccountId != accountId) return@onFailure
                    _uiState.value = _uiState.value.copy(
                        loadingHome = false,
                        error = error.message ?: "Steam 商店连接失败",
                        familyViewUnlockRequired = error is SteamStoreFamilyViewException,
                    )
                }
        }
    }

    fun loadStoreFilterMetadata(force: Boolean = false) {
        val state = _uiState.value
        if (state.loadingFilterMetadata) return
        val accountId = state.selectedAccountId
        val account = selectedAccount()
        val generation = ++filterMetadataRequestGeneration
        viewModelScope.launch {
            val cachedFromDisk = state.filterMetadata == null
            val cached = if (cachedFromDisk) {
                withContext(Dispatchers.IO) { cache.readFilterMetadata(accountId) }
            } else {
                state.filterMetadata
            }
            if (!filterMetadataRequestIsCurrent(accountId, generation)) return@launch
            if (cached != null && cachedFromDisk) {
                _uiState.value = _uiState.value.copy(
                    filterMetadata = cached,
                    filterMetadataFromCache = true
                )
            }
            val cachedIsFresh = cached != null &&
                System.currentTimeMillis() - cached.fetchedAt < FILTER_METADATA_CACHE_TTL_MILLIS
            if (!force && cachedIsFresh) return@launch

            _uiState.value = _uiState.value.copy(
                loadingFilterMetadata = true,
                filterMetadataError = null
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    executeStoreRequest(account) { credentials ->
                        service.filterMetadata(
                            steamLoginSecure = credentials.steamLoginSecure,
                            accessToken = credentials.accessToken
                        )
                    }
                }
            }.onSuccess { metadata ->
                if (!filterMetadataRequestIsCurrent(accountId, generation)) return@onSuccess
                withContext(Dispatchers.IO) { cache.writeFilterMetadata(accountId, metadata) }
                _uiState.value = _uiState.value.copy(
                    filterMetadata = metadata,
                    filterMetadataFromCache = false,
                    loadingFilterMetadata = false,
                    filterMetadataError = null,
                )
            }.onFailure { error ->
                if (!filterMetadataRequestIsCurrent(accountId, generation)) return@onFailure
                _uiState.value = _uiState.value.copy(
                    loadingFilterMetadata = false,
                    filterMetadataError = error.message ?: "Steam 商店筛选信息加载失败",
                    familyViewUnlockRequired = error is SteamStoreFamilyViewException,
                )
            }
        }
    }

    private fun filterMetadataRequestIsCurrent(accountId: Long?, generation: Long): Boolean =
        generation == filterMetadataRequestGeneration &&
            _uiState.value.selectedAccountId == accountId

    fun updateQuery(value: String) {
        _uiState.value = _uiState.value.copy(
            query = value,
            searchResults = emptyList(),
            searching = value.isNotBlank()
        )
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
        val filters = _uiState.value.storeFilters
        val account = selectedAccount()
        searchRequestJob?.cancel()
        searchRequestJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(searching = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    executeStoreRequest(account) { credentials ->
                        service.search(
                            queryText = query,
                            filters = filters,
                            steamLoginSecure = credentials.steamLoginSecure,
                            accessToken = credentials.accessToken,
                            steamId = credentials.steamId
                        )
                    }
                }
            }
                .onSuccess { results ->
                    if (_uiState.value.query.trim() != query ||
                        _uiState.value.selectedAccountId != accountId ||
                        _uiState.value.storeFilters != filters
                    ) return@onSuccess
                    _uiState.value = _uiState.value.copy(
                        searchResults = results,
                        searching = false,
                        familyViewUnlockRequired = false,
                    )
                }
                .onFailure { error ->
                    if (_uiState.value.query.trim() != query ||
                        _uiState.value.selectedAccountId != accountId ||
                        _uiState.value.storeFilters != filters
                    ) return@onFailure
                    _uiState.value = _uiState.value.copy(
                        searching = false,
                        error = error.message ?: "搜索失败",
                        familyViewUnlockRequired = error is SteamStoreFamilyViewException,
                    )
                }
        }
    }

    fun openDetail(item: SteamStoreItem) {
        detailHistory.clear()
        openDetailInternal(
            appId = item.appId,
            discoveryCountryCode = item.priceCountryCode
                .takeIf { item.availableInAccountRegion == false }
        )
    }

    fun openDetail(appId: Int) {
        val state = _uiState.value
        detailHistory.clear()
        openDetailInternal(
            appId = appId,
            discoveryCountryCode = state.detailDiscoveryCountryCode
                .takeIf { state.detailAppId == appId }
        )
    }

    fun openRelatedDetail(appId: Int) {
        val state = _uiState.value
        val currentAppId = state.detailAppId
        if (appId <= 0 || appId == currentAppId) return
        if (currentAppId != null) {
            detailHistory.push(
                SteamStoreDetailRoute(currentAppId, state.detailDiscoveryCountryCode)
            )
        }
        openDetailInternal(appId, discoveryCountryCode = null)
    }

    fun retryDetail() {
        val state = _uiState.value
        val appId = state.detailAppId ?: return
        openDetailInternal(appId, state.detailDiscoveryCountryCode)
    }

    private fun openDetailInternal(appId: Int, discoveryCountryCode: String?) {
        if (appId <= 0) {
            _uiState.value = _uiState.value.copy(error = "Steam 商店没有返回有效的应用编号")
            return
        }
        val accountId = _uiState.value.selectedAccountId
        val account = selectedAccount()
        val generation = ++detailRequestGeneration
        regionalPriceRequestGeneration++
        freeLicenseVerificationJobs.values.forEach { it.cancel() }
        freeLicenseVerificationJobs.clear()
        if (account?.hasRealSteamId == true && !_uiState.value.wishlistLoaded) {
            loadWishlist()
        }
        _uiState.value = _uiState.value.copy(
            detailAppId = appId,
            detailDiscoveryCountryCode = discoveryCountryCode,
            detail = null,
            detailFromCache = false,
            loadingDetail = true,
            purchaseContext = null,
            purchaseContextFromCache = false,
            loadingPurchaseContext = false,
            purchaseContextFailure = null,
            freeLicenseClaimingAppIds = emptySet(),
            freeLicenseClaimResults = emptyMap(),
            reviewFilters = SteamReviewFilterSelection(),
            loadingMoreReviews = false,
            reviewLoadError = null,
            regionalPrices = emptyList(),
            regionalPricesAppId = appId,
            regionalPricesFromCache = false,
            loadingRegionalPrices = false,
            regionalPriceFailure = null,
            regionalPriceSheetOpen = false,
            ignoredError = null,
            error = null
        )
        viewModelScope.launch {
            val cached = runCatching {
                withContext(Dispatchers.IO) {
                    cache.readDetail(accountId, appId)?.let { cachedDetail ->
                        service.localIgnoredState(account?.steamId, appId)?.let { ignored ->
                            cachedDetail.copy(ignored = ignored)
                        } ?: cachedDetail
                    }
                }
            }.getOrNull()
            if (generation != detailRequestGeneration ||
                _uiState.value.selectedAccountId != accountId ||
                _uiState.value.detailAppId != appId
            ) return@launch
            _uiState.value = _uiState.value.copy(
                detail = cached,
                detailFromCache = cached != null,
                loadingDetail = true
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    executeStoreRequest(account) { credentials ->
                        service.detail(
                            appId = appId,
                            steamLoginSecure = credentials.steamLoginSecure,
                            accessToken = credentials.accessToken,
                            discoveryCountryCode = discoveryCountryCode,
                            steamId = credentials.steamId
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
                    val refreshedDetail = detail.preserveCachedReviews(cached)
                    val ignoredSyncState = withContext(Dispatchers.IO) {
                        service.ignoredSyncState(account?.steamId, appId)
                    }
                    withContext(Dispatchers.IO) {
                        cache.writeDetail(accountId, refreshedDetail)
                    }
                    if (!steamStoreDetailRequestIsCurrent(
                            state = _uiState.value,
                            accountId = accountId,
                            appId = appId,
                            generation = generation,
                            currentGeneration = detailRequestGeneration
                        )
                    ) return@onSuccess
                    _uiState.value = _uiState.value.copy(
                        detail = refreshedDetail,
                        detailFromCache = false,
                        loadingDetail = false,
                        reviewLoadError = null,
                        familyViewUnlockRequired = false,
                        ignoredSyncStates = ignoredSyncState?.let {
                            _uiState.value.ignoredSyncStates + (appId to it)
                        } ?: (_uiState.value.ignoredSyncStates - appId)
                    )
                    if (!refreshedDetail.isDlc) {
                        _uiState.value = _uiState.value.copy(
                            loadingPurchaseContext = account?.hasRealSteamId == true,
                            purchaseContextFailure = if (account?.hasRealSteamId == true) {
                                null
                            } else {
                                SteamStorePurchaseContextFailure.SESSION_REQUIRED
                            }
                        )
                        loadPurchaseContext(account, appId, generation)
                    }
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
                        error = error.message ?: "商品详情加载失败",
                        familyViewUnlockRequired = error is SteamStoreFamilyViewException,
                    )
                }
        }
    }

    fun closeDetail() {
        detailHistory.pop()?.let { previous ->
            openDetailInternal(previous.appId, previous.discoveryCountryCode)
            return
        }
        detailRequestGeneration++
        regionalPriceRequestGeneration++
        freeLicenseVerificationJobs.values.forEach { it.cancel() }
        freeLicenseVerificationJobs.clear()
        _uiState.value = _uiState.value.copy(
            detailAppId = null,
            detailDiscoveryCountryCode = null,
            detail = null,
            loadingDetail = false,
            purchaseContext = null,
            purchaseContextFromCache = false,
            loadingPurchaseContext = false,
            purchaseContextFailure = null,
            freeLicenseClaimingAppIds = emptySet(),
            freeLicenseClaimResults = emptyMap(),
            reviewFilters = SteamReviewFilterSelection(),
            loadingMoreReviews = false,
            reviewLoadError = null,
            regionalPrices = emptyList(),
            regionalPricesAppId = null,
            regionalPricesFromCache = false,
            loadingRegionalPrices = false,
            regionalPriceFailure = null,
            regionalPriceSheetOpen = false,
            error = null
        )
    }

    fun updateReviewFilters(filters: SteamReviewFilterSelection) {
        val initialState = _uiState.value
        if (initialState.reviewFilters == filters) return
        val detail = initialState.detail ?: return
        val previousReviews = detail.reviews ?: return
        val accountId = initialState.selectedAccountId
        val appId = detail.appId
        val generation = detailRequestGeneration
        _uiState.value = initialState.copy(
            reviewFilters = filters,
            loadingMoreReviews = true,
            reviewLoadError = null,
            detail = detail.copy(
                reviews = previousReviews.copy(items = emptyList(), nextCursor = null)
            )
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    service.reviewPage(
                        appId = appId,
                        cursor = "*",
                        filters = filters
                    )
                }
            }.onSuccess { page ->
                if (!reviewRequestIsCurrent(accountId, appId, generation, filters)) {
                    return@onSuccess
                }
                val currentDetail = _uiState.value.detail ?: return@onSuccess
                val currentReviews = currentDetail.reviews ?: previousReviews
                val filteredReviews = currentReviews.copy(
                    overall = previousReviews.overall ?: if (filters.isDefault) page.summary else null,
                    recent = previousReviews.recent,
                    items = page.items,
                    nextCursor = page.nextCursor,
                    fetchedAt = System.currentTimeMillis()
                )
                _uiState.value = _uiState.value.copy(
                    detail = currentDetail.copy(reviews = filteredReviews),
                    loadingMoreReviews = false,
                    reviewLoadError = null
                )
            }.onFailure { error ->
                if (!reviewRequestIsCurrent(accountId, appId, generation, filters)) {
                    return@onFailure
                }
                val currentDetail = _uiState.value.detail ?: return@onFailure
                _uiState.value = _uiState.value.copy(
                    detail = currentDetail.copy(reviews = previousReviews),
                    reviewFilters = initialState.reviewFilters,
                    loadingMoreReviews = false,
                    reviewLoadError = error.message ?: "Steam 评价筛选失败"
                )
            }
        }
    }

    fun loadMoreReviews() {
        val initialState = _uiState.value
        if (initialState.loadingMoreReviews) return
        val detail = initialState.detail ?: return
        val cursor = detail.reviews?.nextCursor?.takeIf(String::isNotBlank) ?: return
        val accountId = initialState.selectedAccountId
        val appId = detail.appId
        val generation = detailRequestGeneration
        val filters = initialState.reviewFilters
        _uiState.value = initialState.copy(
            loadingMoreReviews = true,
            reviewLoadError = null
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    service.reviewPage(appId = appId, cursor = cursor, filters = filters)
                }
            }.onSuccess { page ->
                if (!reviewRequestIsCurrent(accountId, appId, generation, filters)) return@onSuccess
                val currentDetail = _uiState.value.detail ?: return@onSuccess
                val terminalPage = if (page.items.isEmpty() || page.nextCursor == cursor) {
                    page.copy(nextCursor = null)
                } else {
                    page
                }
                val updatedDetail = currentDetail.copy(
                    reviews = currentDetail.reviews
                        ?.mergePage(terminalPage)
                        ?: SteamStoreReviews(
                            overall = terminalPage.summary,
                            items = terminalPage.items,
                            nextCursor = terminalPage.nextCursor
                        )
                )
                if (filters.isDefault) {
                    withContext(Dispatchers.IO) {
                        cache.writeDetail(accountId, updatedDetail)
                    }
                }
                if (!reviewRequestIsCurrent(accountId, appId, generation, filters)) return@onSuccess
                _uiState.value = _uiState.value.copy(
                    detail = updatedDetail,
                    loadingMoreReviews = false,
                    reviewLoadError = null
                )
            }.onFailure { error ->
                if (!reviewRequestIsCurrent(accountId, appId, generation, filters)) return@onFailure
                _uiState.value = _uiState.value.copy(
                    loadingMoreReviews = false,
                    reviewLoadError = error.message ?: "Steam 评价加载失败"
                )
            }
        }
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
        val currentCountry = initialState.detail?.accountCountryCode ?: initialState.detail?.priceCountryCode
        val countries = storeRegionalPriceCountries(currentCountry, REGIONAL_PRICE_COUNTRY_CODES)
        val memoryPrices = initialState.regionalPrices
            .takeIf { initialState.regionalPricesAppId == appId }
            .orEmpty()
        if (!force && regionalPricesAreReady(memoryPrices, currentCountry)) return
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
            if (!force && regionalPricesAreReady(availablePrices, currentCountry)) {
                _uiState.value = _uiState.value.copy(loadingRegionalPrices = false)
                return@launch
            }
            val result = try {
                withContext(Dispatchers.IO) {
                    when (val prices = fetchRegionalPricesWithSessionRetry(account, appId, countries)) {
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

    fun addDetailToCart(
        detail: SteamStoreDetail,
        packageOption: SteamStorePackageOption? = detail.packageOptions.firstOrNull()
    ) {
        if (detail.availableInAccountRegion == false) {
            _uiState.value = _uiState.value.copy(error = "当前账号地区不售卖该商品")
            return
        }
        val item = detail.toCartItem(packageOption).copy(giftRecipient = null)
        updateCart((_uiState.value.cart.filterNot { it.appId == item.appId } + item))
    }

    /** Adds a Steam permanent free license directly from the detail page. */
    fun claimFreeLicense(
        detail: SteamStoreDetail,
        packageOption: SteamStorePackageOption? = detail.freeLicenseOption
    ) {
        val option = packageOption?.takeIf {
            it.isFreeLicense || it.canGetFreeLicense
        } ?: return
        val account = selectedAccount()
        if (account == null) {
            _uiState.value = _uiState.value.copy(
                freeLicenseClaimResults = _uiState.value.freeLicenseClaimResults + (
                    detail.appId to SteamFreebieClaimResult(
                        SteamFreebieClaimStatus.SESSION_REQUIRED
                    )
                )
            )
            return
        }
        if (detail.appId in _uiState.value.freeLicenseClaimingAppIds) return
        val accountId = account.id
        _uiState.value = _uiState.value.copy(
            freeLicenseClaimingAppIds = _uiState.value.freeLicenseClaimingAppIds + detail.appId,
            freeLicenseClaimResults = _uiState.value.freeLicenseClaimResults - detail.appId
        )
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    claimFreeLicenseWithSessionRetry(
                        account = account,
                        appId = detail.appId,
                        packageId = option.packageId,
                        storeUrl = detail.storeUrl
                    )
                }
            }.getOrElse { error ->
                SteamFreebieClaimResult(
                    status = if (error.requiresPurchaseContextSessionRefresh()) {
                        SteamFreebieClaimStatus.SESSION_REQUIRED
                    } else {
                        SteamFreebieClaimStatus.FAILED
                    },
                    detail = error.message
                )
            }
            if (_uiState.value.selectedAccountId != accountId ||
                _uiState.value.detailAppId != detail.appId
            ) return@launch
            val owned = result.status == SteamFreebieClaimStatus.CLAIMED ||
                result.status == SteamFreebieClaimStatus.ALREADY_OWNED
            _uiState.value = _uiState.value.copy(
                freeLicenseClaimingAppIds = _uiState.value.freeLicenseClaimingAppIds - detail.appId,
                freeLicenseClaimResults = _uiState.value.freeLicenseClaimResults + (
                    detail.appId to result
                ),
                ownedAppIds = if (owned) {
                    _uiState.value.ownedAppIds + detail.appId
                } else {
                    _uiState.value.ownedAppIds
                },
                purchaseContext = if (owned) {
                    _uiState.value.purchaseContext?.copy(
                        ownership = SteamStoreOwnershipStatus.OWNED,
                        failure = null
                    )
                } else {
                    _uiState.value.purchaseContext
                }
            )
            if (result.status == SteamFreebieClaimStatus.PENDING_VERIFICATION) {
                scheduleFreeLicenseVerification(accountId, detail, option)
            }
        }
    }

    /** Re-checks a submitted free-license request without submitting it again. */
    fun refreshFreeLicense(
        detail: SteamStoreDetail,
        packageOption: SteamStorePackageOption? = detail.freeLicenseOption
    ) {
        val option = packageOption?.takeIf {
            it.isFreeLicense || it.canGetFreeLicense
        } ?: return
        val account = selectedAccount() ?: return
        if (detail.appId in _uiState.value.freeLicenseClaimingAppIds) return
        val accountId = account.id
        _uiState.value = _uiState.value.copy(
            freeLicenseClaimingAppIds = _uiState.value.freeLicenseClaimingAppIds + detail.appId
        )
        viewModelScope.launch {
            val verification = runCatching {
                val prepared = refreshAccountSession(account, force = false)
                withContext(Dispatchers.IO) {
                    freebieService.verifyOwnership(prepared, detail.toFreebieItem(option))
                }
            }
            if (_uiState.value.selectedAccountId != accountId ||
                _uiState.value.detailAppId != detail.appId
            ) return@launch
            val previousResult = _uiState.value.freeLicenseClaimResults[detail.appId]
            val error = verification.exceptionOrNull()
            val result = when {
                verification.getOrNull() == SteamStoreOwnershipStatus.OWNED ->
                    SteamFreebieClaimResult(SteamFreebieClaimStatus.CLAIMED)
                error?.requiresPurchaseContextSessionRefresh() == true ->
                    SteamFreebieClaimResult(
                        status = SteamFreebieClaimStatus.SESSION_REQUIRED,
                        detail = error.message
                    )
                error != null && previousResult?.status !=
                    SteamFreebieClaimStatus.PENDING_VERIFICATION ->
                    SteamFreebieClaimResult(
                        status = SteamFreebieClaimStatus.FAILED,
                        detail = error.message
                    )
                else -> previousResult
                    ?: SteamFreebieClaimResult(SteamFreebieClaimStatus.PENDING_VERIFICATION)
            }
            val owned = result.status == SteamFreebieClaimStatus.CLAIMED
            _uiState.value = _uiState.value.copy(
                freeLicenseClaimingAppIds = _uiState.value.freeLicenseClaimingAppIds - detail.appId,
                freeLicenseClaimResults = _uiState.value.freeLicenseClaimResults + (
                    detail.appId to result
                ),
                ownedAppIds = if (owned) _uiState.value.ownedAppIds + detail.appId
                else _uiState.value.ownedAppIds,
                purchaseContext = if (owned) {
                    _uiState.value.purchaseContext?.copy(
                        ownership = SteamStoreOwnershipStatus.OWNED,
                        failure = null
                    )
                } else _uiState.value.purchaseContext
            )
            if (owned) freeLicenseVerificationJobs.remove(detail.appId)?.cancel()
        }
    }

    fun beginGiftPurchase(
        detail: SteamStoreDetail,
        packageOption: SteamStorePackageOption? = detail.packageOptions.firstOrNull()
    ) {
        if (detail.availableInAccountRegion == false) {
            _uiState.value = _uiState.value.copy(error = "当前账号地区不售卖该商品")
            return
        }
        openGiftRecipientPicker(detail.toCartItem(packageOption))
    }

    fun editGiftRecipient(item: SteamCartItem) {
        openGiftRecipientPicker(item)
    }

    fun dismissGiftRecipientPicker() {
        _uiState.value = _uiState.value.copy(
            gift = _uiState.value.gift.copy(
                pickerOpen = false,
                pendingItem = null,
                failure = null
            )
        )
    }

    fun selectGiftRecipient(friend: SteamFriend) {
        val state = _uiState.value
        val pending = state.gift.pendingItem ?: return
        val recipient = friend.toSteamStoreGiftRecipient()
        if (recipient == null) {
            _uiState.value = state.copy(
                gift = state.gift.copy(failure = SteamStoreGiftFailure.INVALID_RECIPIENT)
            )
            return
        }
        updateCart(
            state.cart.filterNot { it.appId == pending.appId } +
                pending.copy(giftRecipient = recipient)
        )
        _uiState.value = _uiState.value.copy(
            gift = _uiState.value.gift.copy(
                pickerOpen = false,
                pendingItem = null,
                failure = null
            )
        )
    }

    fun refreshGiftFriends() = loadGiftFriends(force = true)

    fun prepareShareFriends() {
        _uiState.value = _uiState.value.copy(
            gift = _uiState.value.gift.copy(
                pickerOpen = false,
                pendingItem = null,
                failure = null
            )
        )
        loadGiftFriends()
    }

    fun removeFromCart(appId: Int) = updateCart(_uiState.value.cart.filterNot { it.appId == appId })
    fun clearCart() = updateCart(emptyList())
    fun openCart() {
        detailHistory.clear()
        detailRequestGeneration++
        regionalPriceRequestGeneration++
        _uiState.value = _uiState.value.copy(
            cartOpen = true,
            collectionTab = SteamStoreCollectionTab.CART,
            detailAppId = null,
            detailDiscoveryCountryCode = null,
            detail = null,
            purchaseContext = null,
            purchaseContextFromCache = false,
            loadingPurchaseContext = false,
            purchaseContextFailure = null
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
        if (detail.availableInAccountRegion == false && add) {
            _uiState.value = _uiState.value.copy(
                wishlistError = "当前账号地区不售卖该商品，无法加入愿望单"
            )
            return
        }
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

    fun toggleIgnored(detail: SteamStoreDetail) {
        if (detail.appId in _uiState.value.ignoredMutatingAppIds) return
        val accountId = _uiState.value.selectedAccountId
        val account = selectedAccount()
        if (account == null || !account.hasRealSteamId) {
            _uiState.value = _uiState.value.copy(
                ignoredError = "请先选择有效的 Steam 账号"
            )
            return
        }
        val ignored = !detail.ignored
        _uiState.value = _uiState.value.copy(
            ignoredMutatingAppIds = _uiState.value.ignoredMutatingAppIds + detail.appId,
            ignoredError = null
        )
        viewModelScope.launch {
            val localResult = runCatching {
                withContext(Dispatchers.IO) {
                    service.applyIgnoredLocally(
                        appId = detail.appId,
                        ignored = ignored,
                        steamId = account.steamId
                    )
                }
            }
            val localSyncState = localResult.getOrElse { error ->
                if (_uiState.value.selectedAccountId != accountId) return@launch
                _uiState.value = _uiState.value.copy(
                    ignoredMutatingAppIds = _uiState.value.ignoredMutatingAppIds - detail.appId,
                    ignoredError = error.message ?: "本地忽略状态保存失败"
                )
                return@launch
            }
            if (_uiState.value.selectedAccountId != accountId) return@launch
            val updated = _uiState.value.withIgnoredGameState(
                appId = detail.appId,
                ignored = ignored,
                syncState = localSyncState
            ).copy(
                ignoredMutatingAppIds = _uiState.value.ignoredMutatingAppIds - detail.appId,
                ignoredError = null
            )
            _uiState.value = updated
            withContext(Dispatchers.IO) {
                updated.detail?.takeIf { it.appId == detail.appId }?.let {
                    cache.writeDetail(accountId, it)
                }
                updated.home?.let { cache.writeHome(accountId, it) }
                updated.catalogPage?.let {
                    cache.writeCatalog(accountId, it, updated.storeFilters)
                }
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    executeStoreRequest(account) { credentials ->
                        service.syncIgnored(
                            steamId = credentials.steamId,
                            steamLoginSecure = credentials.steamLoginSecure,
                            accessToken = credentials.accessToken
                        )
                    }
                }
            }.onFailure { error ->
                SteamDiagLogger.append(
                    "store_ignored_sync deferred appid=${detail.appId} " +
                        "type=${error.javaClass.simpleName}"
                )
            }
            if (_uiState.value.selectedAccountId != accountId) return@launch
            val finalSyncState = withContext(Dispatchers.IO) {
                service.ignoredSyncState(account.steamId, detail.appId)
            }
            _uiState.value = _uiState.value.copy(
                ignoredSyncStates = finalSyncState?.let {
                    _uiState.value.ignoredSyncStates + (detail.appId to it)
                } ?: (_uiState.value.ignoredSyncStates - detail.appId)
            )
            if (!ignored) {
                loadHome(force = true)
                if (updated.browseFilter != SteamStoreBrowseFilter.ALL ||
                    updated.storeFilters.isActive
                ) {
                    loadCatalog(force = true)
                }
                if (updated.query.isNotBlank()) search()
            }
        }
    }

    fun checkout() {
        val lines = steamCartCheckoutLines(_uiState.value.cart)
        if (lines.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "购物车中的商品暂时无法自动同步，请从商品详情进入 Steam 购买")
            return
        }
        _uiState.value = _uiState.value.copy(
            checkoutLines = lines,
            webUrl = "https://store.steampowered.com/cart/",
            webRequiresAuthenticatedSession = true,
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
        accountSourceRepository.selectAccount(accountId)
    }

    fun selectStorageSource(source: SteamStorageSource) {
        accountSourceRepository.selectStorageSource(source)
    }

    fun refreshAccountSource() {
        accountSourceRepository.refreshCurrentSource()
    }

    fun refreshHintSources() {
        val state = _uiState.value
        loadLibraryHints(state.selectedAccountId, state.storageSource)
        loadWishlist(force = true)
    }

    fun applyStoreFilters(selection: SteamStoreFilterSelection) {
        val normalized = selection.normalized()
        if (_uiState.value.storeFilters == normalized) return
        searchDebounceJob?.cancel()
        searchRequestJob?.cancel()
        catalogRequestJob?.cancel()
        catalogRequestGeneration++
        _uiState.value = _uiState.value.copy(
            storeFilters = normalized,
            catalogPage = null,
            catalogFromCache = false,
            loadingCatalog = false,
            loadingMoreCatalog = false,
            catalogError = null,
            searchResults = emptyList(),
            searching = false,
            error = null
        )
        if (_uiState.value.query.isNotBlank()) {
            search()
        } else if (_uiState.value.browseFilter != SteamStoreBrowseFilter.ALL || normalized.isActive) {
            loadCatalog(force = false)
        }
    }

    fun clearStoreFilters() = applyStoreFilters(SteamStoreFilterSelection())

    fun filterByDetailTag(label: String): Boolean {
        val state = _uiState.value
        val tagId = state.filterMetadata?.findTagId(label) ?: return false
        val updatedFilters = state.storeFilters.copy(
            tagIds = state.storeFilters.tagIds + tagId
        )
        detailHistory.clear()
        closeDetail()
        updateQuery("")
        applyStoreFilters(updatedFilters)
        return true
    }

    fun selectBrowseFilter(filter: SteamStoreBrowseFilter) {
        if (_uiState.value.browseFilter == filter) return
        catalogRequestJob?.cancel()
        catalogRequestGeneration++
        _uiState.value = _uiState.value.copy(
            browseFilter = filter,
            catalogPage = null,
            catalogFromCache = false,
            loadingCatalog = false,
            loadingMoreCatalog = false,
            catalogError = null
        )
        if (filter != SteamStoreBrowseFilter.ALL || _uiState.value.storeFilters.isActive) {
            loadCatalog(force = false)
        }
    }

    fun loadCatalog(force: Boolean = false, loadMore: Boolean = false) {
        val state = _uiState.value
        val filter = state.browseFilter
        val filters = state.storeFilters
        if ((filter == SteamStoreBrowseFilter.ALL && !filters.isActive) ||
            state.loadingCatalog || state.loadingMoreCatalog
        ) return
        if (loadMore && state.catalogPage?.hasMore != true) return
        val accountId = state.selectedAccountId
        val account = selectedAccount()
        val generation = ++catalogRequestGeneration
        catalogRequestJob?.cancel()
        catalogRequestJob = viewModelScope.launch {
            if (!force && !loadMore && _uiState.value.catalogPage == null) {
                val cached = withContext(Dispatchers.IO) {
                    cache.readCatalog(accountId, filter, filters)?.withoutIgnoredGames(
                        service.localIgnoredAppIds(account?.steamId)
                    )
                }
                if (catalogRequestIsCurrent(accountId, filter, filters, generation) && cached != null) {
                    _uiState.value = _uiState.value.copy(
                        catalogPage = cached,
                        catalogFromCache = true
                    )
                }
            }
            val existing = _uiState.value.catalogPage
            if (!force && !loadMore && existing != null && !_uiState.value.catalogFromCache) return@launch
            _uiState.value = _uiState.value.copy(
                loadingCatalog = !loadMore,
                loadingMoreCatalog = loadMore,
                catalogError = null
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    executeStoreRequest(account) { credentials ->
                        service.catalog(
                            filter = filter,
                            filters = filters,
                            start = if (loadMore) existing?.nextStart ?: 0 else 0,
                            steamLoginSecure = credentials.steamLoginSecure,
                            accessToken = credentials.accessToken,
                            steamId = credentials.steamId
                        )
                    }
                }
            }.onSuccess { page ->
                if (!catalogRequestIsCurrent(accountId, filter, filters, generation)) return@onSuccess
                val merged = if (loadMore && existing != null) {
                    page.copy(
                        start = 0,
                        items = (existing.items + page.items).distinctBy(SteamStoreItem::appId)
                    )
                } else page
                withContext(Dispatchers.IO) { cache.writeCatalog(accountId, merged, filters) }
                _uiState.value = _uiState.value.copy(
                    catalogPage = merged,
                    catalogFromCache = false,
                    loadingCatalog = false,
                    loadingMoreCatalog = false,
                    familyViewUnlockRequired = false,
                )
            }.onFailure { error ->
                if (!catalogRequestIsCurrent(accountId, filter, filters, generation)) return@onFailure
                _uiState.value = _uiState.value.copy(
                    loadingCatalog = false,
                    loadingMoreCatalog = false,
                    catalogError = error.message ?: "Steam 商店目录加载失败",
                    familyViewUnlockRequired = error is SteamStoreFamilyViewException,
                )
            }
        }
    }

    private fun catalogRequestIsCurrent(
        accountId: Long?,
        filter: SteamStoreBrowseFilter,
        filters: SteamStoreFilterSelection,
        generation: Long
    ): Boolean = generation == catalogRequestGeneration &&
        _uiState.value.selectedAccountId == accountId &&
        _uiState.value.browseFilter == filter &&
        _uiState.value.storeFilters == filters

    fun openPointsShop() {
        _uiState.value = _uiState.value.copy(pointsShopOpen = true)
    }

    fun closePointsShop() {
        _uiState.value = _uiState.value.copy(pointsShopOpen = false)
    }

    private fun resetStoreForAccount(accountId: Long?) {
        detailHistory.clear()
        searchDebounceJob?.cancel()
        searchRequestJob?.cancel()
        catalogRequestJob?.cancel()
        catalogRequestGeneration++
        filterMetadataRequestGeneration++
        detailRequestGeneration++
        regionalPriceRequestGeneration++
        libraryHintRequestGeneration++
        giftFriendsRequestGeneration++
        freeLicenseVerificationJobs.values.forEach { it.cancel() }
        freeLicenseVerificationJobs.clear()
        _uiState.value = _uiState.value.copy(
            selectedAccountId = accountId,
            home = null,
            homeFromCache = false,
            loadingHome = false,
            browseFilter = SteamStoreBrowseFilter.ALL,
            pointsShopOpen = false,
            catalogPage = null,
            catalogFromCache = false,
            loadingCatalog = false,
            loadingMoreCatalog = false,
            catalogError = null,
            storeFilters = SteamStoreFilterSelection(),
            filterMetadata = null,
            filterMetadataFromCache = false,
            loadingFilterMetadata = false,
            filterMetadataError = null,
            query = "",
            searchResults = emptyList(),
            searching = false,
            detailAppId = null,
            detailDiscoveryCountryCode = null,
            detail = null,
            detailFromCache = false,
            loadingDetail = false,
            purchaseContext = null,
            purchaseContextFromCache = false,
            loadingPurchaseContext = false,
            purchaseContextFailure = null,
            freeLicenseClaimingAppIds = emptySet(),
            freeLicenseClaimResults = emptyMap(),
            reviewFilters = SteamReviewFilterSelection(),
            loadingMoreReviews = false,
            reviewLoadError = null,
            error = null,
            familyViewUnlockRequired = false,
            webUrl = null,
            webRequiresAuthenticatedSession = false,
            webReturnRefreshRequired = false,
            cart = emptyList(),
            cartOpen = false,
            collectionTab = SteamStoreCollectionTab.CART,
            wishlist = emptyList(),
            wishlistLoaded = false,
            wishlistFromCache = false,
            loadingWishlist = false,
            wishlistError = null,
            wishlistMutatingAppIds = emptySet(),
            ignoredMutatingAppIds = emptySet(),
            ignoredSyncStates = emptyMap(),
            ignoredError = null,
            ownedAppIds = emptySet(),
            familySharedAppIds = emptySet(),
            regionalPrices = emptyList(),
            regionalPricesAppId = null,
            regionalPricesFromCache = false,
            loadingRegionalPrices = false,
            regionalPriceFailure = null,
            regionalPriceSheetOpen = false,
            gift = SteamStoreGiftUiState(),
            checkoutLines = emptyList()
        )
    }

    private fun scheduleFreeLicenseVerification(
        accountId: Long,
        detail: SteamStoreDetail,
        option: SteamStorePackageOption
    ) {
        freeLicenseVerificationJobs[detail.appId]?.cancel()
        freeLicenseVerificationJobs[detail.appId] = viewModelScope.launch {
            repeat(FREE_LICENSE_AUTOMATIC_VERIFICATION_ATTEMPTS) {
                delay(FREE_LICENSE_AUTOMATIC_VERIFICATION_DELAY_MILLIS)
                if (_uiState.value.selectedAccountId != accountId ||
                    _uiState.value.detailAppId != detail.appId
                ) return@launch
                val account = selectedAccount() ?: return@launch
                val ownership = runCatching {
                    val prepared = refreshAccountSession(account, force = false)
                    withContext(Dispatchers.IO) {
                        freebieService.verifyOwnership(
                            account = prepared,
                            item = detail.toFreebieItem(option)
                        )
                    }
                }.onFailure { error ->
                    SteamDiagLogger.append(
                        "store_free_license automatic_verify_failed app_id=${detail.appId} " +
                            "type=${error.javaClass.simpleName}"
                    )
                }.getOrDefault(SteamStoreOwnershipStatus.UNKNOWN)
                if (ownership == SteamStoreOwnershipStatus.OWNED) {
                    _uiState.value = _uiState.value.copy(
                        freeLicenseClaimResults = _uiState.value.freeLicenseClaimResults + (
                            detail.appId to SteamFreebieClaimResult(
                                SteamFreebieClaimStatus.CLAIMED
                            )
                        ),
                        ownedAppIds = _uiState.value.ownedAppIds + detail.appId,
                        purchaseContext = _uiState.value.purchaseContext?.copy(
                            ownership = SteamStoreOwnershipStatus.OWNED,
                            failure = null
                        )
                    )
                    return@launch
                }
            }
        }.also { job ->
            job.invokeOnCompletion { freeLicenseVerificationJobs.remove(detail.appId, job) }
        }
    }

    private fun SteamStoreDetail.toFreebieItem(
        option: SteamStorePackageOption
    ) = SteamFreebieItem(
        appId = appId,
        packageId = option.packageId,
        name = name,
        storeUrl = storeUrl,
        offerKind = SteamFreebieOfferKind.KEEP_FOREVER,
        claimMethod = SteamFreebieClaimMethod.FREE_LICENSE
    )

    private fun loadLibraryHints(accountId: Long?, source: SteamStorageSource) {
        val generation = ++libraryHintRequestGeneration
        if (accountId == null || source !is SteamStorageSource.Local) {
            _uiState.value = _uiState.value.copy(
                ownedAppIds = emptySet(),
                familySharedAppIds = emptySet()
            )
            return
        }
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                libraryCacheRepository?.getLibrary(accountId)
            }
            val state = _uiState.value
            if (
                generation != libraryHintRequestGeneration ||
                state.selectedAccountId != accountId ||
                state.storageSource !is SteamStorageSource.Local
            ) {
                return@launch
            }
            _uiState.value = state.copy(
                ownedAppIds = snapshot?.ownedGames
                    ?.mapTo(linkedSetOf()) { it.appId }
                    .orEmpty(),
                familySharedAppIds = snapshot?.sharedGames
                    ?.mapTo(linkedSetOf()) { it.appId }
                    .orEmpty()
            )
        }
    }

    fun openStoreWeb(url: String) {
        if (SteamWebNavigationPolicy.isAllowed(url)) {
            _uiState.value = _uiState.value.copy(
                webUrl = url,
                webRequiresAuthenticatedSession = false
            )
        }
    }

    fun openAuthenticatedStoreWeb(url: String) {
        if (SteamWebNavigationPolicy.isAllowed(url)) {
            _uiState.value = _uiState.value.copy(
                webUrl = url,
                webRequiresAuthenticatedSession = true
            )
        }
    }

    fun openFamilyViewUnlock() {
        val account = selectedAccount() ?: return
        if (!account.hasAuthenticatedSession) return
        _uiState.value = _uiState.value.copy(
            webUrl = FAMILY_VIEW_UNLOCK_URL,
            webRequiresAuthenticatedSession = true,
            webReturnRefreshRequired = true,
        )
    }

    fun closeStoreWeb() {
        val shouldRefresh = _uiState.value.webReturnRefreshRequired
        _uiState.value = _uiState.value.copy(
            webUrl = null,
            webRequiresAuthenticatedSession = false,
            webReturnRefreshRequired = false,
            checkoutLines = emptyList()
        )
        if (shouldRefresh) refreshAfterFamilyViewUnlock()
    }

    private fun refreshAfterFamilyViewUnlock() {
        _uiState.value = _uiState.value.copy(
            familyViewUnlockRequired = false,
            error = null,
            catalogError = null,
            filterMetadataError = null,
        )
        loadStoreFilterMetadata(force = true)
        when {
            _uiState.value.detailAppId != null -> retryDetail()
            _uiState.value.query.isNotBlank() -> search()
            _uiState.value.browseFilter != SteamStoreBrowseFilter.ALL ||
                _uiState.value.storeFilters.isActive -> loadCatalog(force = true)
            else -> loadHome(force = true)
        }
    }

    private fun openGiftRecipientPicker(item: SteamCartItem) {
        val account = selectedAccount()
        _uiState.value = _uiState.value.copy(
            gift = _uiState.value.gift.copy(
                pickerOpen = true,
                pendingItem = item,
                failure = if (account == null) {
                    SteamStoreGiftFailure.ACCOUNT_REQUIRED
                } else {
                    null
                }
            )
        )
        if (account != null) loadGiftFriends()
    }

    private fun loadGiftFriends(force: Boolean = false) {
        val account = selectedAccount()
        if (account == null) {
            _uiState.value = _uiState.value.copy(
                gift = _uiState.value.gift.copy(
                    loading = false,
                    refreshing = false,
                    failure = SteamStoreGiftFailure.ACCOUNT_REQUIRED
                )
            )
            return
        }
        val repository = giftFriendRepository
        if (repository == null) {
            _uiState.value = _uiState.value.copy(
                gift = _uiState.value.gift.copy(
                    failure = SteamStoreGiftFailure.UNAVAILABLE
                )
            )
            return
        }
        val giftState = _uiState.value.gift
        if (giftState.loading || giftState.refreshing) return
        if (!force && giftState.friends.isNotEmpty() && !giftState.fromCache) return
        val accountId = account.id
        val generation = ++giftFriendsRequestGeneration
        viewModelScope.launch {
            val cached = if (giftState.friends.isEmpty()) {
                withContext(Dispatchers.IO) { repository.loadCached(account) }
            } else {
                null
            }
            if (!giftFriendsRequestIsCurrent(accountId, generation)) return@launch
            val cachedFriends = cached?.acceptedFriends.orEmpty()
            if (cachedFriends.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    gift = _uiState.value.gift.copy(
                        friends = cachedFriends,
                        loading = false,
                        refreshing = true,
                        fromCache = true,
                        failure = null
                    )
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    gift = _uiState.value.gift.copy(
                        loading = giftState.friends.isEmpty(),
                        refreshing = giftState.friends.isNotEmpty(),
                        failure = null
                    )
                )
            }
            val cacheFresh = cached != null &&
                System.currentTimeMillis() - cached.fetchedAt < GIFT_FRIENDS_CACHE_TTL_MILLIS
            if (!force && cacheFresh) {
                _uiState.value = _uiState.value.copy(
                    gift = _uiState.value.gift.copy(refreshing = false)
                )
                return@launch
            }
            val result = runCatching {
                withContext(Dispatchers.IO) { fetchGiftFriendsWithSessionRetry(account, repository) }
            }
            if (!giftFriendsRequestIsCurrent(accountId, generation)) return@launch
            result.onSuccess { snapshot ->
                _uiState.value = _uiState.value.copy(
                    gift = _uiState.value.gift.copy(
                        friends = snapshot.acceptedFriends,
                        loading = false,
                        refreshing = false,
                        fromCache = false,
                        failure = null
                    )
                )
            }.onFailure { error ->
                SteamDiagLogger.append(
                    "store_gift_friends failed type=${error.javaClass.simpleName}"
                )
                _uiState.value = _uiState.value.copy(
                    gift = _uiState.value.gift.copy(
                        loading = false,
                        refreshing = false,
                        failure = error.toGiftFailure()
                    )
                )
            }
        }
    }

    private suspend fun fetchGiftFriendsWithSessionRetry(
        account: SteamAccount,
        repository: SteamStoreGiftFriendRepository
    ) = try {
        repository.refresh(refreshAccountSession(account, force = false))
    } catch (error: Throwable) {
        if (!error.requiresGiftSessionRefresh()) throw error
        repository.refresh(refreshAccountSession(account, force = true))
    }

    private fun giftFriendsRequestIsCurrent(accountId: Long, generation: Long): Boolean =
        generation == giftFriendsRequestGeneration && _uiState.value.selectedAccountId == accountId

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

    private fun reviewRequestIsCurrent(
        accountId: Long?,
        appId: Int,
        generation: Long,
        filters: SteamReviewFilterSelection
    ): Boolean {
        val state = _uiState.value
        return generation == detailRequestGeneration &&
            state.selectedAccountId == accountId &&
            state.detailAppId == appId &&
            state.detail?.appId == appId &&
            state.reviewFilters == filters
    }

    private fun loadPurchaseContext(
        account: SteamAccount?,
        appId: Int,
        generation: Long
    ) {
        if (account?.hasRealSteamId != true) return
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) {
                purchaseContextCache?.load(account.steamId, appId)
            }
            if (!purchaseContextRequestIsCurrent(account, appId, generation)) return@launch
            if (cached != null) {
                _uiState.value = _uiState.value.copy(
                    purchaseContext = cached,
                    purchaseContextFromCache = true,
                    loadingPurchaseContext = true,
                    purchaseContextFailure = null
                )
            }

            val result = runSteamStorePurchaseContextCatching {
                withContext(Dispatchers.IO) {
                    fetchPurchaseContextWithSessionRetry(account, appId)
                }
            }
            if (!purchaseContextRequestIsCurrent(account, appId, generation)) return@launch
            val error = result.exceptionOrNull()
            if (error != null) {
                SteamDiagLogger.append(
                    "store_purchase_context failed app_id=$appId " +
                        "type=${error.javaClass.simpleName}"
                )
                _uiState.value = _uiState.value.copy(
                    loadingPurchaseContext = false,
                    purchaseContextFromCache = cached != null,
                    purchaseContextFailure = error.toPurchaseContextFailure()
                )
                return@launch
            }

            val fresh = result.getOrThrow()
            if (fresh.ownership == SteamStoreOwnershipStatus.UNKNOWN && cached != null) {
                _uiState.value = _uiState.value.copy(
                    purchaseContext = cached,
                    purchaseContextFromCache = true,
                    loadingPurchaseContext = false,
                    purchaseContextFailure = fresh.failure
                )
                return@launch
            }
            if (steamStorePurchaseContextIsCacheable(fresh)) {
                withContext(Dispatchers.IO) { purchaseContextCache?.save(fresh) }
            }
            if (!purchaseContextRequestIsCurrent(account, appId, generation)) return@launch
            _uiState.value = _uiState.value.copy(
                purchaseContext = fresh,
                purchaseContextFromCache = false,
                loadingPurchaseContext = false,
                purchaseContextFailure = fresh.failure
            )
        }
    }

    private suspend fun fetchPurchaseContextWithSessionRetry(
        account: SteamAccount,
        appId: Int
    ): SteamStorePurchaseContext {
        val prepared = refreshAccountSession(account, force = false)
        val first = try {
            purchaseContextGateway.fetch(prepared, appId, "schinese")
        } catch (error: Throwable) {
            if (!error.requiresPurchaseContextSessionRefresh()) throw error
            val refreshed = refreshAccountSession(prepared, force = true)
            if (refreshed.accessToken == prepared.accessToken) throw error
            return purchaseContextGateway.fetch(refreshed, appId, "schinese")
        }
        if (first.failure != SteamStorePurchaseContextFailure.SESSION_REQUIRED) return first
        val refreshed = refreshAccountSession(prepared, force = true)
        return if (refreshed.accessToken != prepared.accessToken) {
            purchaseContextGateway.fetch(refreshed, appId, "schinese")
        } else {
            first
        }
    }

    private suspend fun claimFreeLicenseWithSessionRetry(
        account: SteamAccount,
        appId: Int,
        packageId: Int,
        storeUrl: String
    ): SteamFreebieClaimResult {
        val prepared = refreshAccountSession(account, force = false)
        val first = freebieService.claimFreeLicense(
            account = prepared,
            appId = appId,
            packageId = packageId,
            storeUrl = storeUrl
        )
        if (first.status != SteamFreebieClaimStatus.SESSION_REQUIRED) return first
        val refreshed = refreshAccountSession(prepared, force = true)
        return if (
            refreshed.accessToken != prepared.accessToken ||
            refreshed.steamLoginSecure != prepared.steamLoginSecure
        ) {
            freebieService.claimFreeLicense(
                account = refreshed,
                appId = appId,
                packageId = packageId,
                storeUrl = storeUrl
            )
        } else {
            first
        }
    }

    private fun purchaseContextRequestIsCurrent(
        account: SteamAccount,
        appId: Int,
        generation: Long
    ): Boolean = steamStorePurchaseContextRequestIsCurrent(
        state = _uiState.value,
        account = account,
        appId = appId,
        generation = generation,
        currentGeneration = detailRequestGeneration
    )

    private fun regionalPricesAreReady(prices: List<SteamRegionalPrice>, currentCountry: String?): Boolean {
        if (!storePricesContainCurrentRegion(prices, currentCountry)) return false
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
        appId: Int,
        countries: List<String>
    ): SteamLibraryResult<List<SteamRegionalPrice>> {
        val prepared = refreshAccountSession(account, force = false)
        val first = libraryService.fetchRegionalPrices(
            account = prepared,
            appId = appId,
            countryCodes = countries,
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
                countryCodes = countries,
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
        if (account == null) return request(SteamStoreAccountCredentials(null, null, null))
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
            steamLoginSecure = steamLoginSecure,
            steamId = steamId
        )

    private suspend fun refreshAccountSession(
        account: SteamAccount,
        force: Boolean
    ): SteamAccount {
        val refreshed = sessionResolver.resolveOrKeep(account, force)
        _uiState.value = _uiState.value.copy(
            accounts = _uiState.value.accounts.map { existing ->
                if (existing.id == refreshed.id) refreshed else existing
            }
        )
        return refreshed
    }

    companion object {
        internal const val FAMILY_VIEW_UNLOCK_URL =
            "https://store.steampowered.com/parental/"
        internal val REGIONAL_PRICE_COUNTRY_CODES =
            listOf(
                "CN", "US", "JP", "KR", "HK", "TW", "DE", "GB", "BR", "RU",
                "UA", "IN", "ID", "PK"
            )
        private const val REGIONAL_PRICE_CACHE_TTL_MILLIS = 6L * 60L * 60L * 1_000L
        private const val FILTER_METADATA_CACHE_TTL_MILLIS = 24L * 60L * 60L * 1_000L
        private const val GIFT_FRIENDS_CACHE_TTL_MILLIS = 15L * 60L * 1_000L
        private const val FREE_LICENSE_AUTOMATIC_VERIFICATION_ATTEMPTS = 4
        private const val FREE_LICENSE_AUTOMATIC_VERIFICATION_DELAY_MILLIS = 2_000L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            val accountSourceRepository = SteamAccountSourceRepository.get(appContext)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val database = SteamDatabase.getDatabase(appContext)
                    val securityManager = SecurityManager(appContext)
                    return SteamStoreViewModel(
                        accountSourceRepository = accountSourceRepository,
                        cache = SteamStoreCache(appContext),
                        service = SteamStoreService(context = appContext),
                        sessionResolver = accountSourceRepository.sessionResolver(),
                        purchaseContextCache = SteamStorePurchasePreferencesCache(appContext),
                        libraryCacheRepository = SteamLibraryCacheRepository(
                            appContext,
                            database.steamLibraryCacheDao(),
                            securityManager
                        ),
                        giftFriendRepository = SteamStoreGiftFriendRepository(
                            gateway = SteamFriendsService(),
                            cache = SteamFriendsPreferencesCache(appContext)
                        )
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
        state.detailAppId == appId
}

internal fun steamStorePurchaseContextRequestIsCurrent(
    state: SteamStoreUiState,
    account: SteamAccount,
    appId: Int,
    generation: Long,
    currentGeneration: Long
): Boolean {
    val selected = state.accounts.firstOrNull { it.id == state.selectedAccountId }
    return generation == currentGeneration &&
        state.detailAppId == appId &&
        state.selectedAccountId == account.id &&
        selected?.steamId == account.steamId
}

internal fun steamStorePurchaseContextIsCacheable(
    context: SteamStorePurchaseContext
): Boolean = context.failure == null &&
    context.ownership != SteamStoreOwnershipStatus.UNKNOWN

private fun Throwable.requiresPurchaseContextSessionRefresh(): Boolean = when (this) {
    is SteamStorePurchaseContextSessionException -> true
    is SteamApiException -> eResult?.let { it == 5 || it == 15 || it == 401 || it == 403 } == true ||
        httpStatusCode?.let { it == 401 || it == 403 } == true
    else -> false
}

private fun Throwable.toPurchaseContextFailure(): SteamStorePurchaseContextFailure = when (this) {
    is SteamStorePurchaseContextSessionException -> SteamStorePurchaseContextFailure.SESSION_REQUIRED
    is SteamApiException -> when {
        requiresPurchaseContextSessionRefresh() -> SteamStorePurchaseContextFailure.SESSION_REQUIRED
        eResult == 429 || httpStatusCode == 429 -> SteamStorePurchaseContextFailure.RATE_LIMITED
        else -> SteamStorePurchaseContextFailure.NETWORK
    }
    is IOException -> SteamStorePurchaseContextFailure.NETWORK
    is IllegalArgumentException,
    is IllegalStateException,
    is IndexOutOfBoundsException -> SteamStorePurchaseContextFailure.INVALID_RESPONSE
    else -> SteamStorePurchaseContextFailure.NETWORK
}

private fun Throwable.requiresGiftSessionRefresh(): Boolean = when (this) {
    is SteamApiException -> eResult?.let { it == 5 || it == 15 || it == 401 || it == 403 } == true ||
        httpStatusCode?.let { it == 401 || it == 403 } == true
    else -> false
}

private fun Throwable.toGiftFailure(): SteamStoreGiftFailure = when (this) {
    is SteamApiException -> if (requiresGiftSessionRefresh()) {
        SteamStoreGiftFailure.SESSION_REQUIRED
    } else {
        SteamStoreGiftFailure.NETWORK
    }
    is IOException -> SteamStoreGiftFailure.NETWORK
    is IllegalArgumentException,
    is IllegalStateException -> SteamStoreGiftFailure.SESSION_REQUIRED
    else -> SteamStoreGiftFailure.UNAVAILABLE
}

private suspend fun <T> runSteamStorePurchaseContextCatching(
    block: suspend () -> T
): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}
