package takagi.ru.monica.steam.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.data.SteamLibraryCacheRepository
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.market.SteamInventoryService
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamSessionRefreshService
import takagi.ru.monica.steam.quickaccess.SteamWidgetUpdater

data class SteamLibraryUiState(
    val accounts: List<SteamAccount> = emptyList(),
    val selectedAccountId: Long? = null,
    val snapshot: SteamLibrarySnapshot? = null,
    val snapshotFromCache: Boolean = false,
    val loadingLibrary: Boolean = false,
    val libraryFailure: SteamLibraryFailureReason? = null,
    val selectedGame: SteamGame? = null,
    val achievements: SteamGameAchievements? = null,
    val achievementsFromCache: Boolean = false,
    val loadingAchievements: Boolean = false,
    val achievementFailure: SteamLibraryFailureReason? = null,
    val loadingRegionalPrices: Boolean = false,
    val regionalPriceFailure: SteamLibraryFailureReason? = null
)

class SteamLibraryViewModel(
    private val accountRepository: SteamAccountRepository,
    private val cacheRepository: SteamLibraryCacheRepository,
    private val service: SteamGameLibraryService = SteamGameLibraryService(),
    private val inventoryService: SteamInventoryService = SteamInventoryService(),
    private val sessionRefreshService: SteamSessionRefreshService = SteamSessionRefreshService(),
    private val currencyExchangeService: SteamCurrencyExchangeService =
        SteamCurrencyExchangeService(),
    private val appContext: Context? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamLibraryUiState())
    val uiState: StateFlow<SteamLibraryUiState> = _uiState.asStateFlow()
    private var initializedAccountIds = mutableSetOf<Long>()
    private var libraryLoadGeneration: Long = 0L
    private var achievementLoadGeneration: Long = 0L
    private var regionalPriceLoadGeneration: Long = 0L

    init {
        viewModelScope.launch {
            accountRepository.observeAccounts().collect { accounts ->
                val selected = accounts.firstOrNull { it.id == _uiState.value.selectedAccountId }
                    ?: accounts.firstOrNull { it.selected }
                    ?: accounts.firstOrNull()
                val accountChanged = selected?.id != _uiState.value.selectedAccountId
                _uiState.value = _uiState.value.copy(
                    accounts = accounts,
                    selectedAccountId = selected?.id
                )
                if (accountChanged && selected != null) loadAccount(selected)
            }
        }
    }

    fun selectAccount(accountId: Long) {
        val account = _uiState.value.accounts.firstOrNull { it.id == accountId } ?: return
        if (_uiState.value.selectedAccountId == accountId) return
        achievementLoadGeneration++
        regionalPriceLoadGeneration++
        _uiState.value = _uiState.value.copy(
            selectedAccountId = accountId,
            snapshot = null,
            snapshotFromCache = false,
            selectedGame = null,
            achievements = null,
            loadingLibrary = false,
            libraryFailure = null,
            achievementFailure = null,
            loadingRegionalPrices = false,
            regionalPriceFailure = null
        )
        viewModelScope.launch { loadAccount(account) }
    }

    fun refreshLibrary() {
        val account = selectedAccount() ?: return
        if (_uiState.value.loadingLibrary) return
        val generation = ++libraryLoadGeneration
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingLibrary = true, libraryFailure = null)
            val cachedBeforeRefresh = _uiState.value.snapshot
            val result = runSteamLibraryCatching {
                withContext(Dispatchers.IO) {
                    fetchLibraryWithSessionRetry(account)
                }
            }.getOrElse { error ->
                SteamLibraryResult.Failure(steamLibraryFailureReason(error))
            }
            when (result) {
                is SteamLibraryResult.Success -> {
                    val inventoryResult = runSteamLibraryCatching {
                        withContext(Dispatchers.IO) {
                            fetchInventorySummaryWithSessionRetry(account)
                        }
                    }.getOrElse { error ->
                        SteamLibraryResult.Failure(steamLibraryFailureReason(error))
                    }
                    val merged = mergeLibraryDashboardSnapshot(
                        fresh = result.value,
                        cached = cachedBeforeRefresh,
                        inventoryResult = inventoryResult
                    )
                    runSteamLibraryCatching {
                        withContext(Dispatchers.IO) { cacheRepository.saveLibrary(merged) }
                    }
                    appContext?.let { context ->
                        runCatching { SteamWidgetUpdater.refreshAll(context) }
                            .onFailure { error ->
                                SteamDiagLogger.append(
                                    "library_widget_refresh failed type=${error::class.java.simpleName}"
                                )
                            }
                    }
                    if (generation != libraryLoadGeneration ||
                        _uiState.value.selectedAccountId != account.id
                    ) return@launch
                    _uiState.value = _uiState.value.copy(
                        snapshot = merged,
                        snapshotFromCache = false,
                        loadingLibrary = false,
                        libraryFailure = null
                    )
                }
                is SteamLibraryResult.Failure -> {
                    if (generation != libraryLoadGeneration ||
                        _uiState.value.selectedAccountId != account.id
                    ) return@launch
                    _uiState.value = _uiState.value.copy(
                        loadingLibrary = false,
                        libraryFailure = result.reason
                    )
                }
            }
        }
    }

    fun openGame(game: SteamGame) {
        val account = selectedAccount() ?: return
        regionalPriceLoadGeneration++
        val generation = ++achievementLoadGeneration
        _uiState.value = _uiState.value.copy(
            selectedGame = game,
            achievements = null,
            achievementsFromCache = false,
            loadingAchievements = true,
            achievementFailure = null,
            loadingRegionalPrices = false,
            regionalPriceFailure = null
        )
        viewModelScope.launch {
            val cached = runSteamLibraryCatching {
                withContext(Dispatchers.IO) {
                    cacheRepository.getAchievements(account.id, game.appId)
                }
            }.getOrNull()
            if (!achievementRequestIsCurrent(account.id, game.appId, generation)) return@launch
            if (cached != null) {
                _uiState.value = _uiState.value.copy(
                    achievements = cached,
                    achievementsFromCache = true
                )
            }
            val result = runSteamLibraryCatching {
                withContext(Dispatchers.IO) {
                    fetchAchievementsWithSessionRetry(account, game)
                }
            }.getOrElse { error ->
                SteamLibraryResult.Failure(steamLibraryFailureReason(error))
            }
            if (!achievementRequestIsCurrent(account.id, game.appId, generation)) return@launch
            when (result) {
                is SteamLibraryResult.Success -> {
                    runSteamLibraryCatching {
                        withContext(Dispatchers.IO) {
                            cacheRepository.saveAchievements(result.value)
                        }
                    }
                    if (!achievementRequestIsCurrent(account.id, game.appId, generation)) {
                        return@launch
                    }
                    _uiState.value = _uiState.value.copy(
                        achievements = result.value,
                        achievementsFromCache = false,
                        loadingAchievements = false,
                        achievementFailure = null
                    )
                }
                is SteamLibraryResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        loadingAchievements = false,
                        achievementFailure = result.reason
                    )
                }
            }
        }
    }

    fun closeGame() {
        achievementLoadGeneration++
        regionalPriceLoadGeneration++
        _uiState.value = _uiState.value.copy(
            selectedGame = null,
            achievements = null,
            achievementsFromCache = false,
            loadingAchievements = false,
            achievementFailure = null,
            loadingRegionalPrices = false,
            regionalPriceFailure = null
        )
    }

    fun loadRegionalPrices(game: SteamGame, force: Boolean = false) {
        val account = selectedAccount() ?: return
        val current = _uiState.value
        if (current.selectedGame?.appId != game.appId || current.loadingRegionalPrices) return
        val cachedPrices = current.selectedGame.regionalPrices
        val cacheIsFresh = cachedPrices.isNotEmpty() && cachedPrices.all { price ->
            System.currentTimeMillis() - price.fetchedAt < REGIONAL_PRICE_CACHE_TTL_MILLIS
        }
        val conversionsReady = cachedPrices
            .filter(SteamRegionalPrice::isAvailable)
            .all { it.cnyFinalPriceMinor != null && it.cnyOriginalPriceMinor != null }
        if (!force && cacheIsFresh && conversionsReady) return
        val generation = ++regionalPriceLoadGeneration
        _uiState.value = current.copy(
            loadingRegionalPrices = true,
            regionalPriceFailure = null
        )
        viewModelScope.launch {
            val result = runSteamLibraryCatching {
                withContext(Dispatchers.IO) {
                    when (val prices = fetchRegionalPricesWithSessionRetry(account, game)) {
                        is SteamLibraryResult.Success -> {
                            val rates = runCatching {
                                currencyExchangeService.fetchCnyRates()
                            }.getOrNull()
                            SteamLibraryResult.Success(
                                applyCnyConversions(
                                    prices = prices.value,
                                    unitsPerCny = rates?.unitsPerCny.orEmpty(),
                                    exchangeRateFetchedAt = rates?.fetchedAt
                                        ?: System.currentTimeMillis()
                                )
                            )
                        }
                        is SteamLibraryResult.Failure -> prices
                    }
                }
            }.getOrElse { error ->
                SteamLibraryResult.Failure(steamLibraryFailureReason(error))
            }
            if (generation != regionalPriceLoadGeneration ||
                _uiState.value.selectedAccountId != account.id ||
                _uiState.value.selectedGame?.appId != game.appId
            ) return@launch
            when (result) {
                is SteamLibraryResult.Success -> {
                    val updatedState = applyRegionalPricesToState(
                        state = _uiState.value,
                        gameAppId = game.appId,
                        freshPrices = result.value
                    ) ?: return@launch
                    val updatedSnapshot = updatedState.snapshot
                    if (updatedSnapshot != null) {
                        runSteamLibraryCatching {
                            withContext(Dispatchers.IO) {
                                cacheRepository.saveLibrary(updatedSnapshot)
                            }
                        }
                        appContext?.let { context ->
                            runCatching { SteamWidgetUpdater.refreshAll(context) }
                                .onFailure { error ->
                                    SteamDiagLogger.append(
                                        "regional_widget_refresh failed type=${error::class.java.simpleName}"
                                    )
                                }
                        }
                    }
                    if (generation != regionalPriceLoadGeneration ||
                        _uiState.value.selectedAccountId != account.id ||
                        _uiState.value.selectedGame?.appId != game.appId
                    ) return@launch
                    _uiState.value = updatedState
                }
                is SteamLibraryResult.Failure -> {
                    if (generation != regionalPriceLoadGeneration ||
                        _uiState.value.selectedAccountId != account.id ||
                        _uiState.value.selectedGame?.appId != game.appId
                    ) return@launch
                    _uiState.value = _uiState.value.copy(
                        loadingRegionalPrices = false,
                        regionalPriceFailure = result.reason
                    )
                }
            }
        }
    }

    private suspend fun loadAccount(account: SteamAccount) {
        libraryLoadGeneration++
        achievementLoadGeneration++
        regionalPriceLoadGeneration++
        val cached = runSteamLibraryCatching {
            withContext(Dispatchers.IO) { cacheRepository.getLibrary(account.id) }
        }.getOrNull()
        if (_uiState.value.selectedAccountId != account.id) return
        _uiState.value = _uiState.value.copy(
            snapshot = cached,
            snapshotFromCache = cached != null,
            selectedGame = null,
            achievements = null,
            loadingLibrary = false,
            libraryFailure = null,
            achievementFailure = null,
            loadingRegionalPrices = false,
            regionalPriceFailure = null
        )
        if (initializedAccountIds.add(account.id)) refreshLibrary()
    }

    private fun selectedAccount(): SteamAccount? {
        return _uiState.value.accounts.firstOrNull { it.id == _uiState.value.selectedAccountId }
    }

    private fun achievementRequestIsCurrent(
        accountId: Long,
        appId: Int,
        generation: Long
    ): Boolean {
        return steamLibraryAchievementRequestIsCurrent(
            state = _uiState.value,
            accountId = accountId,
            appId = appId,
            generation = generation,
            currentGeneration = achievementLoadGeneration
        )
    }

    private suspend fun fetchLibraryWithSessionRetry(
        account: SteamAccount
    ): SteamLibraryResult<SteamLibrarySnapshot> {
        val prepared = refreshAccountSession(account, force = false)
        val first = service.fetchLibrary(prepared, countryCode = "CN", language = "schinese")
        if (first !is SteamLibraryResult.Failure ||
            first.reason != SteamLibraryFailureReason.SESSION_REQUIRED
        ) {
            return first
        }
        val refreshed = refreshAccountSession(prepared, force = true)
        return if (refreshed.accessToken != prepared.accessToken) {
            service.fetchLibrary(refreshed, countryCode = "CN", language = "schinese")
        } else {
            first
        }
    }

    private suspend fun fetchAchievementsWithSessionRetry(
        account: SteamAccount,
        game: SteamGame
    ): SteamLibraryResult<SteamGameAchievements> {
        val prepared = refreshAccountSession(account, force = false)
        val first = service.fetchAchievements(prepared, game, language = "schinese")
        if (first !is SteamLibraryResult.Failure ||
            first.reason != SteamLibraryFailureReason.SESSION_REQUIRED
        ) {
            return first
        }
        val refreshed = refreshAccountSession(prepared, force = true)
        return if (refreshed.accessToken != prepared.accessToken) {
            service.fetchAchievements(refreshed, game, language = "schinese")
        } else {
            first
        }
    }

    private suspend fun fetchRegionalPricesWithSessionRetry(
        account: SteamAccount,
        game: SteamGame
    ): SteamLibraryResult<List<SteamRegionalPrice>> {
        val prepared = refreshAccountSession(account, force = false)
        val first = service.fetchRegionalPrices(
            prepared,
            appId = game.appId,
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
            service.fetchRegionalPrices(
                refreshed,
                appId = game.appId,
                countryCodes = REGIONAL_PRICE_COUNTRY_CODES,
                language = "schinese"
            )
        } else {
            first
        }
    }

    private suspend fun fetchInventorySummaryWithSessionRetry(
        account: SteamAccount
    ): SteamLibraryResult<SteamInventorySummary> {
        val prepared = refreshAccountSession(account, force = false)
        val first = fetchInventorySummary(prepared)
        if (first !is SteamLibraryResult.Failure ||
            first.reason != SteamLibraryFailureReason.SESSION_REQUIRED
        ) {
            return first
        }
        val refreshed = refreshAccountSession(prepared, force = true)
        return if (refreshed.accessToken != prepared.accessToken) {
            fetchInventorySummary(refreshed)
        } else {
            first
        }
    }

    private fun fetchInventorySummary(
        account: SteamAccount
    ): SteamLibraryResult<SteamInventorySummary> {
        if (!account.hasRealSteamId) {
            return SteamLibraryResult.Failure(SteamLibraryFailureReason.SESSION_REQUIRED)
        }
        return runCatching {
            val overview = inventoryService.fetchOverview(account)
            SteamInventorySummary(
                itemCount = overview.games.sumOf { it.itemCount }.coerceAtLeast(0),
                fetchedAt = System.currentTimeMillis()
            )
        }.fold(
            onSuccess = { SteamLibraryResult.Success(it) },
            onFailure = { error ->
                SteamLibraryResult.Failure(inventoryFailureReason(error))
            }
        )
    }

    private fun inventoryFailureReason(error: Throwable): SteamLibraryFailureReason {
        val message = error.message.orEmpty()
        return when {
            error is IllegalArgumentException -> SteamLibraryFailureReason.SESSION_REQUIRED
            error is SteamApiException && (
                error.eResult == 5 || error.eResult == 15 ||
                    error.eResult == 401 || error.eResult == 403 ||
                    message.contains("session expired", ignoreCase = true) ||
                    message.contains("/login/", ignoreCase = true)
                ) -> SteamLibraryFailureReason.SESSION_REQUIRED
            error is SteamApiException && (
                error.eResult == 429 || message.contains("429")
                ) -> SteamLibraryFailureReason.RATE_LIMITED
            else -> SteamLibraryFailureReason.NETWORK
        }
    }

    private suspend fun refreshAccountSession(
        account: SteamAccount,
        force: Boolean
    ): SteamAccount {
        val refreshResult = if (force) {
            val refreshToken = account.refreshToken?.takeIf { it.isNotBlank() } ?: return account
            sessionRefreshService.refresh(account.steamId, refreshToken)
        } else {
            sessionRefreshService.refreshIfNeeded(account)
        } ?: return account
        val refreshed = account.copy(
            accessToken = refreshResult.accessToken,
            refreshToken = refreshResult.refreshToken ?: account.refreshToken,
            steamLoginSecure = "${account.steamId}||${refreshResult.accessToken}"
        )
        runSteamLibraryCatching {
            accountRepository.updateSessionTokens(
                id = account.id,
                accessToken = refreshResult.accessToken,
                refreshToken = refreshed.refreshToken,
                steamLoginSecure = refreshed.steamLoginSecure
            )
        }.onFailure { error ->
            SteamDiagLogger.append(
                "library_session_persist failed type=${error::class.java.simpleName}"
            )
        }
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
                    val securityManager = SecurityManager(appContext)
                    return SteamLibraryViewModel(
                        accountRepository = SteamAccountRepository(
                            database.steamAccountDao(),
                            securityManager
                        ),
                        cacheRepository = SteamLibraryCacheRepository(
                            database.steamLibraryCacheDao(),
                            securityManager
                        ),
                        appContext = appContext
                    ) as T
                }
            }
        }
    }
}

/**
 * Converts unexpected storage/session/network exceptions into a normal
 * library failure while preserving coroutine cancellation semantics.
 */
internal suspend fun <T> runSteamLibraryCatching(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        SteamDiagLogger.append(
            "library_request failed type=${error::class.java.simpleName}"
        )
        Result.failure(error)
    }
}

internal fun steamLibraryFailureReason(error: Throwable): SteamLibraryFailureReason {
    return when {
        error is SteamApiException && (
            error.eResult == 5 || error.eResult == 15 ||
                error.eResult == 401 || error.eResult == 403
            ) -> SteamLibraryFailureReason.SESSION_REQUIRED
        error is SteamApiException && (
            error.eResult == 429 || error.message?.contains("429") == true
            ) -> SteamLibraryFailureReason.RATE_LIMITED
        error is IllegalArgumentException -> SteamLibraryFailureReason.SESSION_REQUIRED
        else -> SteamLibraryFailureReason.NETWORK
    }
}

internal fun steamLibraryAchievementRequestIsCurrent(
    state: SteamLibraryUiState,
    accountId: Long,
    appId: Int,
    generation: Long,
    currentGeneration: Long
): Boolean {
    return generation == currentGeneration &&
        state.selectedAccountId == accountId &&
        state.selectedGame?.appId == appId
}

/**
 * Applies a regional-price response only when the detail page still points at
 * the requested game. A response can arrive after the user closes the detail
 * page or switches games; returning null lets the caller discard it without
 * dereferencing a cleared selection.
 */
internal fun applyRegionalPricesToState(
    state: SteamLibraryUiState,
    gameAppId: Int,
    freshPrices: List<SteamRegionalPrice>
): SteamLibraryUiState? {
    val currentGame = state.selectedGame ?: return null
    if (currentGame.appId != gameAppId) return null

    val regionalPrices = mergeCachedRegionalPriceConversions(
        fresh = freshPrices,
        cached = currentGame.regionalPrices
    )
    val updatedGame = currentGame.copy(regionalPrices = regionalPrices)
    val updatedSnapshot = state.snapshot?.let { snapshot ->
        snapshot.copy(
            games = snapshot.games.map { existing ->
                if (existing.appId == gameAppId) updatedGame else existing
            }
        )
    }
    return state.copy(
        snapshot = updatedSnapshot,
        selectedGame = updatedGame,
        loadingRegionalPrices = false,
        regionalPriceFailure = null
    )
}

internal fun mergeLibraryDashboardSnapshot(
    fresh: SteamLibrarySnapshot,
    cached: SteamLibrarySnapshot?,
    inventoryResult: SteamLibraryResult<SteamInventorySummary>
): SteamLibrarySnapshot {
    val cachedGames = cached?.games.orEmpty().associateBy(SteamGame::appId)
    val gamesWithCachedStoreFallback = fresh.games.map { game ->
        val previous = cachedGames[game.appId]
        game.copy(
            headerImageUrl = if (fresh.priceFailure != null) {
                game.headerImageUrl.ifBlank { previous?.headerImageUrl.orEmpty() }
            } else {
                game.headerImageUrl
            },
            price = if (fresh.priceFailure != null) game.price ?: previous?.price else game.price,
            regionalPrices = game.regionalPrices.ifEmpty { previous?.regionalPrices.orEmpty() }
        )
    }
    val library = fresh.copy(games = gamesWithCachedStoreFallback)
    return when (inventoryResult) {
        is SteamLibraryResult.Success -> library.copy(
            inventoryItemCount = inventoryResult.value.itemCount,
            inventoryFetchedAt = inventoryResult.value.fetchedAt,
            inventoryFailure = null
        )
        is SteamLibraryResult.Failure -> library.copy(
            inventoryItemCount = cached?.inventoryItemCount,
            inventoryFetchedAt = cached?.inventoryFetchedAt,
            inventoryFailure = inventoryResult.reason
        )
    }
}
