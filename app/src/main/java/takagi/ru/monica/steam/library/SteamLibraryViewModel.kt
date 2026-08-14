package takagi.ru.monica.steam.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.hasAuthenticatedSession
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.data.SteamLibraryCacheRepository
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.library.analytics.data.SteamPlayActivityRepository
import takagi.ru.monica.steam.library.analytics.domain.SteamPlayActivityHistory
import takagi.ru.monica.steam.library.context.data.SteamLibraryGameContextCache
import takagi.ru.monica.steam.library.context.data.SteamLibraryGameContextPreferencesCache
import takagi.ru.monica.steam.library.context.data.SteamLibraryGameContextService
import takagi.ru.monica.steam.library.context.domain.SteamLibraryGameContext
import takagi.ru.monica.steam.library.context.domain.SteamLibraryGameContextGateway
import takagi.ru.monica.steam.library.context.domain.mergeSteamLibraryGameContext
import takagi.ru.monica.steam.library.context.domain.steamLibraryGameContextIsCacheable
import takagi.ru.monica.steam.market.SteamInventoryService
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.quickaccess.SteamWidgetUpdater
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep
import takagi.ru.monica.steam.store.data.SteamStoreService

data class SteamLibraryUiState(
    val accounts: List<SteamAccount> = emptyList(),
    val selectedAccountId: Long? = null,
    val storageSource: SteamStorageSource = SteamStorageSource.Local,
    val mdbxDatabases: List<LocalMdbxDatabase> = emptyList(),
    val accountsLoading: Boolean = false,
    val accountSourceError: String? = null,
    val snapshot: SteamLibrarySnapshot? = null,
    val snapshotFromCache: Boolean = false,
    val playActivity: SteamPlayActivityHistory? = null,
    val loadingLibrary: Boolean = false,
    val libraryFailure: SteamLibraryFailureReason? = null,
    val syncingAchievementProgress: Boolean = false,
    val achievementProgressFailure: SteamLibraryFailureReason? = null,
    val selectedGame: SteamGame? = null,
    val gameContext: SteamLibraryGameContext? = null,
    val gameContextFromCache: Boolean = false,
    val loadingGameContext: Boolean = false,
    val gameContextFailure: SteamLibraryFailureReason? = null,
    val achievements: SteamGameAchievements? = null,
    val achievementsFromCache: Boolean = false,
    val loadingAchievements: Boolean = false,
    val achievementFailure: SteamLibraryFailureReason? = null,
    val loadingRegionalPrices: Boolean = false,
    val regionalPriceFailure: SteamLibraryFailureReason? = null
)

class SteamLibraryViewModel(
    private val accountSourceRepository: SteamAccountSourceRepository,
    private val cacheRepository: SteamLibraryCacheRepository,
    private val service: SteamGameLibraryService = SteamGameLibraryService(),
    private val storeService: SteamStoreService = SteamStoreService(),
    private val inventoryService: SteamInventoryService = SteamInventoryService(),
    /** Shared single-flight resolver; null is only the unauthenticated test/read-only mode. */
    private val sessionResolver: SteamAccountSessionResolver? = null,
    private val currencyExchangeService: SteamCurrencyExchangeService =
        SteamCurrencyExchangeService(),
    private val playActivityRepository: SteamPlayActivityRepository,
    private val gameContextGateway: SteamLibraryGameContextGateway =
        SteamLibraryGameContextService(),
    private val gameContextCache: SteamLibraryGameContextCache? = null,
    private val appContext: Context? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamLibraryUiState())
    val uiState: StateFlow<SteamLibraryUiState> = _uiState.asStateFlow()
    private var initializedAccountIds = mutableSetOf<Long>()
    private var libraryLoadGeneration: Long = 0L
    private var achievementLoadGeneration: Long = 0L
    private var achievementProgressSyncGeneration: Long = 0L
    private var achievementProgressSyncJob: Job? = null
    private var regionalPriceLoadGeneration: Long = 0L
    private var gameContextLoadGeneration: Long = 0L

    init {
        viewModelScope.launch {
            accountSourceRepository.state.collect { sourceState ->
                val accounts = sourceState.accounts.filter { it.hasAuthenticatedSession }
                val selected = accounts.firstOrNull { it.id == sourceState.selectedAccountId }
                    ?: accounts.firstOrNull()
                val sourceChanged = sourceState.storageSource != _uiState.value.storageSource
                val accountChanged = selected?.id != _uiState.value.selectedAccountId
                _uiState.value = _uiState.value.copy(
                    accounts = accounts,
                    selectedAccountId = selected?.id,
                    storageSource = sourceState.storageSource,
                    mdbxDatabases = sourceState.mdbxDatabases,
                    accountsLoading = sourceState.loading,
                    accountSourceError = sourceState.errorMessage
                )
                if ((sourceChanged || accountChanged) && selected != null) {
                    loadAccount(selected)
                } else if ((sourceChanged || accountChanged) && selected == null) {
                    libraryLoadGeneration++
                    achievementLoadGeneration++
                    cancelAchievementProgressSync()
                    regionalPriceLoadGeneration++
                    gameContextLoadGeneration++
                    _uiState.value = _uiState.value.copy(
                        snapshot = null,
                        snapshotFromCache = false,
                        playActivity = null,
                        selectedGame = null,
                        gameContext = null,
                        gameContextFromCache = false,
                        loadingGameContext = false,
                        gameContextFailure = null,
                        achievements = null,
                        loadingLibrary = false,
                        libraryFailure = null,
                        syncingAchievementProgress = false,
                        achievementProgressFailure = null
                    )
                }
            }
        }
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

    fun refreshLibrary() {
        val account = selectedAccount() ?: return
        if (_uiState.value.loadingLibrary) return
        cancelAchievementProgressSync()
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
                    val normalized = normalizeLibraryPrices(result.value)
                    val inventoryResult = runSteamLibraryCatching {
                        withContext(Dispatchers.IO) {
                            fetchInventorySummaryWithSessionRetry(account)
                        }
                    }.getOrElse { error ->
                        SteamLibraryResult.Failure(steamLibraryFailureReason(error))
                    }
                    val merged = mergeLibraryDashboardSnapshot(
                        fresh = normalized,
                        cached = cachedBeforeRefresh,
                        inventoryResult = inventoryResult
                    )
                    runSteamLibraryCatching {
                        withContext(Dispatchers.IO) { cacheRepository.saveLibrary(merged) }
                    }
                    val playActivity = runSteamLibraryCatching {
                        withContext(Dispatchers.IO) {
                            playActivityRepository.recordSnapshot(merged)
                        }
                    }.getOrNull()
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
                        playActivity = playActivity ?: _uiState.value.playActivity,
                        loadingLibrary = false,
                        libraryFailure = null
                    )
                    startAchievementProgressSync(
                        account = account,
                        forceFull = false
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

    fun syncAllAchievementProgress(): Boolean {
        val account = selectedAccount() ?: return false
        if (_uiState.value.loadingLibrary) return false
        return startAchievementProgressSync(
            account = account,
            forceFull = true
        )
    }

    private fun startAchievementProgressSync(
        account: SteamAccount,
        forceFull: Boolean
    ): Boolean {
        val snapshot = _uiState.value.snapshot
            ?.takeIf { it.accountId == account.id }
            ?: return false
        val plan = planSteamAchievementProgressSync(
            current = snapshot,
            forceFull = forceFull
        )
        if (plan.appIds.isEmpty()) {
            if (plan.isFullSync && snapshot.achievementProgressFullSyncAt == null) {
                val updatedSnapshot = snapshot.copy(
                    achievementProgressFullSyncAt = System.currentTimeMillis()
                )
                _uiState.value = _uiState.value.copy(snapshot = updatedSnapshot)
                viewModelScope.launch(Dispatchers.IO) {
                    runSteamLibraryCatching { cacheRepository.saveLibrary(updatedSnapshot) }
                }
            }
            return true
        }

        val generation = ++achievementProgressSyncGeneration
        achievementProgressSyncJob?.cancel()
        _uiState.value = _uiState.value.copy(
            syncingAchievementProgress = true,
            achievementProgressFailure = null
        )
        achievementProgressSyncJob = viewModelScope.launch {
            val result = runSteamLibraryCatching {
                withContext(Dispatchers.IO) {
                    fetchAchievementProgressWithSessionRetry(account, plan.appIds)
                }
            }.getOrElse { error ->
                SteamLibraryResult.Failure(steamLibraryFailureReason(error))
            }
            if (!achievementProgressSyncIsCurrent(account.id, generation)) return@launch
            when (result) {
                is SteamLibraryResult.Success -> {
                    val updatedState = applyAchievementProgressToState(
                        state = _uiState.value,
                        accountId = account.id,
                        progress = result.value,
                        syncedAppIds = plan.appIds.toSet(),
                        fullSyncAt = System.currentTimeMillis().takeIf { plan.isFullSync }
                    )
                    if (updatedState == null) {
                        _uiState.value = _uiState.value.copy(
                            syncingAchievementProgress = false,
                            achievementProgressFailure = SteamLibraryFailureReason.INVALID_RESPONSE
                        )
                        return@launch
                    }
                    _uiState.value = updatedState.copy(
                        syncingAchievementProgress = false,
                        achievementProgressFailure = null
                    )
                    updatedState.snapshot?.let { updatedSnapshot ->
                        withContext(Dispatchers.IO) {
                            runSteamLibraryCatching {
                                cacheRepository.saveLibrary(updatedSnapshot)
                            }
                        }
                    }
                }
                is SteamLibraryResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        syncingAchievementProgress = false,
                        achievementProgressFailure = result.reason
                    )
                }
            }
        }
        return true
    }

    private fun cancelAchievementProgressSync() {
        achievementProgressSyncGeneration++
        achievementProgressSyncJob?.cancel()
        achievementProgressSyncJob = null
        if (_uiState.value.syncingAchievementProgress) {
            _uiState.value = _uiState.value.copy(syncingAchievementProgress = false)
        }
    }

    private fun achievementProgressSyncIsCurrent(accountId: Long, generation: Long): Boolean {
        return generation == achievementProgressSyncGeneration &&
            _uiState.value.selectedAccountId == accountId
    }

    private suspend fun normalizeLibraryPrices(snapshot: SteamLibrarySnapshot): SteamLibrarySnapshot {
        val rates = runCatching {
            withContext(Dispatchers.IO) { currencyExchangeService.fetchCnyRates() }
        }.getOrNull() ?: return snapshot
        val games = snapshot.games.map { game ->
            game.copy(price = game.price?.withCnyConversion(rates.unitsPerCny, rates.fetchedAt))
        }
        return snapshot.copy(games = games)
    }

    fun openGame(game: SteamGame) {
        val account = selectedAccount() ?: return
        regionalPriceLoadGeneration++
        gameContextLoadGeneration++
        val generation = ++achievementLoadGeneration
        _uiState.value = _uiState.value.copy(
            selectedGame = game,
            gameContext = null,
            gameContextFromCache = false,
            loadingGameContext = false,
            gameContextFailure = null,
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
                    _uiState.value = applyAchievementsToState(
                        state = _uiState.value,
                        achievements = result.value
                    ).copy(
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
        gameContextLoadGeneration++
        _uiState.value = _uiState.value.copy(
            selectedGame = null,
            gameContext = null,
            gameContextFromCache = false,
            loadingGameContext = false,
            gameContextFailure = null,
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

    fun refreshGameContext() {
        val account = selectedAccount() ?: return
        val game = _uiState.value.selectedGame ?: return
        if (_uiState.value.loadingGameContext) return
        val generation = ++gameContextLoadGeneration
        _uiState.value = _uiState.value.copy(
            loadingGameContext = true,
            gameContextFailure = null
        )
        loadGameContext(
            account = account,
            game = game,
            generation = generation,
            readCache = false
        )
    }

    private fun loadGameContext(
        account: SteamAccount,
        game: SteamGame,
        generation: Long,
        readCache: Boolean
    ) {
        val countryCode = resolveSteamLibraryCountryCode(
            accountCountry = null,
            cachedCountry = _uiState.value.snapshot?.region,
            deviceCountry = Locale.getDefault().country
        )
        viewModelScope.launch {
            val cached = if (readCache) {
                runSteamLibraryCatching {
                    withContext(Dispatchers.IO) {
                        gameContextCache?.load(account.steamId, game.appId)
                    }
                }.getOrNull()
            } else {
                _uiState.value.gameContext?.takeIf {
                    it.accountSteamId == account.steamId && it.appId == game.appId
                }
            }
            if (!gameContextRequestIsCurrent(account, game.appId, generation)) return@launch
            if (readCache && cached != null) {
                _uiState.value = _uiState.value.copy(
                    gameContext = cached,
                    gameContextFromCache = true,
                    loadingGameContext = true,
                    gameContextFailure = null
                )
            }

            val result = runSteamLibraryCatching {
                withContext(Dispatchers.IO) {
                    fetchGameContextWithSessionRetry(
                        account = account,
                        game = game,
                        countryCode = countryCode
                    )
                }
            }.getOrElse { error ->
                SteamLibraryResult.Failure(steamLibraryFailureReason(error))
            }
            if (!gameContextRequestIsCurrent(account, game.appId, generation)) return@launch
            when (result) {
                is SteamLibraryResult.Success -> {
                    val merged = mergeSteamLibraryGameContext(result.value, cached)
                    if (steamLibraryGameContextIsCacheable(result.value)) {
                        runSteamLibraryCatching {
                            withContext(Dispatchers.IO) {
                                gameContextCache?.save(result.value)
                            }
                        }
                    }
                    if (!gameContextRequestIsCurrent(account, game.appId, generation)) {
                        return@launch
                    }
                    val currentState = _uiState.value
                    val stateWithSupport = applyGameContextToLibraryState(
                        state = currentState,
                        context = merged.context
                    )
                    val supportChanged = currentState.selectedGame?.supportsSteamCloud !=
                        stateWithSupport.selectedGame?.supportsSteamCloud
                    _uiState.value = stateWithSupport.copy(
                        gameContext = merged.context,
                        gameContextFromCache = merged.usedCache,
                        loadingGameContext = false,
                        gameContextFailure = null
                    )
                    if (supportChanged) {
                        stateWithSupport.snapshot?.let { snapshot ->
                            runSteamLibraryCatching {
                                withContext(Dispatchers.IO) {
                                    cacheRepository.saveLibrary(snapshot)
                                }
                            }
                        }
                    }
                }
                is SteamLibraryResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        gameContext = cached,
                        gameContextFromCache = cached != null,
                        loadingGameContext = false,
                        gameContextFailure = result.reason
                    )
                }
            }
        }
    }

    private suspend fun loadAccount(account: SteamAccount) {
        libraryLoadGeneration++
        achievementLoadGeneration++
        cancelAchievementProgressSync()
        regionalPriceLoadGeneration++
        gameContextLoadGeneration++
        val cachedData = runSteamLibraryCatching {
            withContext(Dispatchers.IO) {
                cacheRepository.getLibrary(account.id) to playActivityRepository.load(account.id)
            }
        }.getOrNull()
        val cached = cachedData?.first
        if (_uiState.value.selectedAccountId != account.id) return
        _uiState.value = _uiState.value.copy(
            snapshot = cached,
            snapshotFromCache = cached != null,
            playActivity = cachedData?.second,
            selectedGame = null,
            gameContext = null,
            gameContextFromCache = false,
            loadingGameContext = false,
            gameContextFailure = null,
            achievements = null,
            loadingLibrary = false,
            libraryFailure = null,
            syncingAchievementProgress = false,
            achievementProgressFailure = null,
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

    private fun gameContextRequestIsCurrent(
        account: SteamAccount,
        appId: Int,
        generation: Long
    ): Boolean = steamLibraryGameContextRequestIsCurrent(
        state = _uiState.value,
        account = account,
        appId = appId,
        generation = generation,
        currentGeneration = gameContextLoadGeneration
    )

    private suspend fun fetchLibraryWithSessionRetry(
        account: SteamAccount
    ): SteamLibraryResult<SteamLibrarySnapshot> {
        val prepared = refreshAccountSession(account, force = false)
        val countryCode = resolveAccountCountryCode(prepared)
        val first = service.fetchLibrary(
            prepared,
            countryCode = countryCode,
            language = "schinese"
        )
        if (first !is SteamLibraryResult.Failure ||
            first.reason != SteamLibraryFailureReason.SESSION_REQUIRED
        ) {
            return first
        }
        val refreshed = refreshAccountSession(prepared, force = true)
        return if (refreshed.accessToken != prepared.accessToken) {
            service.fetchLibrary(
                refreshed,
                countryCode = resolveAccountCountryCode(refreshed, countryCode),
                language = "schinese"
            )
        } else {
            first
        }
    }

    private fun resolveAccountCountryCode(
        account: SteamAccount,
        fallbackCountry: String? = null
    ): String {
        val steamCountry = runCatching { storeService.accountCountryCode(account) }
            .onFailure { error ->
                SteamDiagLogger.append(
                    "library_account_region failed type=${error::class.java.simpleName}"
                )
            }
            .getOrNull()
        return resolveSteamLibraryCountryCode(
            accountCountry = steamCountry,
            cachedCountry = fallbackCountry ?: _uiState.value.snapshot?.region,
            deviceCountry = Locale.getDefault().country
        )
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

    private suspend fun fetchAchievementProgressWithSessionRetry(
        account: SteamAccount,
        appIds: List<Int>
    ): SteamLibraryResult<Map<Int, SteamGameAchievementProgress>> {
        val prepared = refreshAccountSession(account, force = false)
        val first = service.fetchAchievementProgress(
            account = prepared,
            appIds = appIds,
            language = "schinese"
        )
        if (first !is SteamLibraryResult.Failure ||
            first.reason != SteamLibraryFailureReason.SESSION_REQUIRED
        ) {
            return first
        }
        val refreshed = refreshAccountSession(prepared, force = true)
        return if (refreshed.accessToken != prepared.accessToken) {
            service.fetchAchievementProgress(
                account = refreshed,
                appIds = appIds,
                language = "schinese"
            )
        } else {
            first
        }
    }

    private suspend fun fetchGameContextWithSessionRetry(
        account: SteamAccount,
        game: SteamGame,
        countryCode: String
    ): SteamLibraryResult<SteamLibraryGameContext> {
        val prepared = refreshAccountSession(account, force = false)
        val first = gameContextGateway.fetch(
            account = prepared,
            game = game,
            countryCode = countryCode,
            language = "schinese"
        )
        if (first !is SteamLibraryResult.Failure ||
            first.reason != SteamLibraryFailureReason.SESSION_REQUIRED
        ) {
            return first
        }
        val refreshed = refreshAccountSession(prepared, force = true)
        return if (refreshed.accessToken != prepared.accessToken) {
            gameContextGateway.fetch(
                account = refreshed,
                game = game,
                countryCode = countryCode,
                language = "schinese"
            )
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
        val refreshed = sessionResolver.resolveOrKeep(account, force)
        return refreshed
    }

    companion object {
        internal val REGIONAL_PRICE_COUNTRY_CODES =
            listOf("CN", "US", "JP", "KR", "HK", "TW", "UA", "IN", "ID", "PK")
        private const val REGIONAL_PRICE_CACHE_TTL_MILLIS = 6L * 60L * 60L * 1_000L
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            val accountSourceRepository = SteamAccountSourceRepository.get(appContext)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val database = SteamDatabase.getDatabase(appContext)
                    val securityManager = SecurityManager(appContext)
                    return SteamLibraryViewModel(
                        accountSourceRepository = accountSourceRepository,
                        cacheRepository = SteamLibraryCacheRepository(
                            database.steamLibraryCacheDao(),
                            securityManager
                        ),
                        playActivityRepository = SteamPlayActivityRepository(
                            appContext,
                            securityManager
                        ),
                        sessionResolver = accountSourceRepository.sessionResolver(),
                        gameContextCache = SteamLibraryGameContextPreferencesCache(appContext),
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

internal fun steamLibraryGameContextRequestIsCurrent(
    state: SteamLibraryUiState,
    account: SteamAccount,
    appId: Int,
    generation: Long,
    currentGeneration: Long
): Boolean {
    val selected = state.accounts.firstOrNull { it.id == state.selectedAccountId }
    return generation == currentGeneration &&
        state.selectedAccountId == account.id &&
        state.selectedGame?.appId == appId &&
        selected?.steamId == account.steamId
}

internal fun applyGameContextToLibraryState(
    state: SteamLibraryUiState,
    context: SteamLibraryGameContext
): SteamLibraryUiState {
    val supportsSteamCloud = context.supportsSteamCloud ?: return state
    val selectedGame = state.selectedGame
        ?.takeIf { it.appId == context.appId }
        ?.copy(supportsSteamCloud = supportsSteamCloud)
        ?: state.selectedGame
    val snapshot = state.snapshot?.copy(
        games = state.snapshot.games.map { game ->
            if (game.appId == context.appId) {
                game.copy(supportsSteamCloud = supportsSteamCloud)
            } else {
                game
            }
        }
    )
    return state.copy(selectedGame = selectedGame, snapshot = snapshot)
}

internal fun applyAchievementsToState(
    state: SteamLibraryUiState,
    achievements: SteamGameAchievements
): SteamLibraryUiState {
    val total = achievements.achievements.size
    val unlocked = achievements.completed.size
    fun SteamGame.withProgress(): SteamGame = copy(
        achievementUnlockedCount = unlocked,
        achievementTotalCount = total,
        allAchievementsUnlocked = total > 0 && unlocked >= total,
        achievementProgressPlaytimeMinutes = playtimeForeverMinutes
    )

    val updatedSelectedGame = state.selectedGame
        ?.takeIf { it.appId == achievements.appId }
        ?.withProgress()
        ?: state.selectedGame
    val updatedSnapshot = state.snapshot?.copy(
        games = state.snapshot.games.map { game ->
            if (game.appId == achievements.appId) game.withProgress() else game
        }
    )
    return state.copy(
        snapshot = updatedSnapshot,
        selectedGame = updatedSelectedGame
    )
}

internal fun applyAchievementProgressToState(
    state: SteamLibraryUiState,
    accountId: Long,
    progress: Map<Int, SteamGameAchievementProgress>,
    syncedAppIds: Set<Int>,
    fullSyncAt: Long?
): SteamLibraryUiState? {
    val snapshot = state.snapshot?.takeIf { it.accountId == accountId } ?: return null
    fun SteamGame.withProgress(): SteamGame {
        if (appId !in syncedAppIds) return this
        val summary = progress[appId]
        return copy(
            achievementUnlockedCount = summary?.unlocked ?: achievementUnlockedCount,
            achievementTotalCount = summary?.total ?: achievementTotalCount,
            allAchievementsUnlocked = summary?.allUnlocked ?: allAchievementsUnlocked,
            achievementProgressPlaytimeMinutes = playtimeForeverMinutes
        )
    }

    val updatedSnapshot = snapshot.copy(
        games = snapshot.games.map { it.withProgress() },
        achievementProgressFullSyncAt = fullSyncAt
            ?: snapshot.achievementProgressFullSyncAt
    )
    val updatedSelectedGame = state.selectedGame?.withProgress()
    return state.copy(
        snapshot = updatedSnapshot,
        selectedGame = updatedSelectedGame
    )
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
            regionalPrices = game.regionalPrices.ifEmpty { previous?.regionalPrices.orEmpty() },
            achievementUnlockedCount = game.achievementUnlockedCount
                ?: previous?.achievementUnlockedCount,
            achievementTotalCount = game.achievementTotalCount
                ?: previous?.achievementTotalCount,
            allAchievementsUnlocked = if (game.achievementTotalCount != null) {
                game.allAchievementsUnlocked
            } else {
                previous?.allAchievementsUnlocked == true
            },
            achievementProgressPlaytimeMinutes = game.achievementProgressPlaytimeMinutes
                ?: previous?.achievementProgressPlaytimeMinutes,
            supportsSteamCloud = game.supportsSteamCloud ?: previous?.supportsSteamCloud
        )
    }
    val gamesWithFamilyCacheFallback = if (fresh.familyShareFailure != null) {
        val freshAppIds = gamesWithCachedStoreFallback.mapTo(hashSetOf(), SteamGame::appId)
        gamesWithCachedStoreFallback + cached
            ?.sharedGames
            .orEmpty()
            .filterNot { it.appId in freshAppIds }
    } else {
        gamesWithCachedStoreFallback
    }
    val library = fresh.copy(
        games = gamesWithFamilyCacheFallback,
        familyGroupId = fresh.familyGroupId
            ?: cached?.familyGroupId?.takeIf { fresh.familyShareFailure != null },
        achievementProgressFullSyncAt = fresh.achievementProgressFullSyncAt
            ?: cached?.achievementProgressFullSyncAt
    )
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
