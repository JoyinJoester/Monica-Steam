package takagi.ru.monica.steam.space.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.data.SteamLibraryCacheRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.space.domain.withKnownGameNames
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep
import takagi.ru.monica.steam.space.data.SteamSpaceService
import takagi.ru.monica.steam.space.data.SteamSpacePreferencesFeedCache
import takagi.ru.monica.steam.space.domain.SteamSpaceCacheKey
import takagi.ru.monica.steam.space.domain.SteamSpaceCachedFeed
import takagi.ru.monica.steam.space.domain.SteamSpaceFeedCache
import takagi.ru.monica.steam.space.domain.SteamSpaceMemoryFeedCache
import takagi.ru.monica.steam.space.domain.SteamSpaceCursor
import takagi.ru.monica.steam.space.domain.SteamSpaceGateway
import takagi.ru.monica.steam.space.domain.SteamSpacePage
import takagi.ru.monica.steam.space.domain.SteamSpaceEventKind

/** Leaving clears visible content and pending requests; completed feeds can be restored per account. */
class SteamSpaceViewModel(
    private val gateway: SteamSpaceGateway,
    private val sessionResolver: SteamAccountSessionResolver? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val languageProvider: () -> String = { steamSpaceLanguage(Locale.getDefault()) },
    private val requestTimeoutMillis: Long = 45_000L,
    private val cache: SteamSpaceFeedCache = SteamSpaceMemoryFeedCache(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val libraryNamesLookup: suspend (SteamAccount) -> Map<Int, String> = { emptyMap() }
) : ViewModel() {
    init {
        require(requestTimeoutMillis > 0L)
    }

    private val _uiState = MutableStateFlow(SteamSpaceUiState())
    val uiState: StateFlow<SteamSpaceUiState> = _uiState.asStateFlow()

    private var activeAccount: SteamAccount? = null
    private var generation = 0L
    private var request: Job? = null
    private var cacheWrite: Job? = null
    private var libraryNamesJob: Job? = null
    private var knownGameNames: Map<Int, String> = emptyMap()
    private var pageCount = 0
    private var feedLanguage = "english"
    private var fetchedAtMillis = 0L
    private val visitedCursors = mutableSetOf<SteamSpaceCursor>()

    fun selectAccount(account: SteamAccount?) {
        if (account == null || !account.hasRealSteamId) {
            deactivate()
            _uiState.value = SteamSpaceUiState(failure = SteamSpaceFailureReason.ACCOUNT_REQUIRED)
            return
        }
        val language = languageProvider()
        val cacheKey = account.cacheKey(language)
        if (activeAccount?.cacheKey(feedLanguage) == cacheKey) {
            activeAccount = account
            return
        }
        deactivate()
        activeAccount = account
        feedLanguage = language
        loadLibraryNames(account, cacheKey)
        _uiState.value = SteamSpaceUiState(accountId = account.id, accountSteamId = account.steamId, loading = true)
        val cached = cache.peek(cacheKey)?.takeIf { it.key == cacheKey }
        if (cached != null) {
            restore(cached)
            if (!cached.isFresh()) refresh()
            return
        }
        val currentGeneration = generation
        val loadJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val stored = try {
                withTimeoutOrNull(1_500L) {
                    runInterruptible(ioDispatcher) { cache.load(cacheKey) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            requireCurrent(account, currentGeneration)
            val usable = stored?.takeIf { it.key == cacheKey }
            if (usable != null) restore(usable)
            request = null
            if (usable == null || !usable.isFresh()) refresh()
        }
        // A fast cache read may finish inline and replace request with the network job.
        // Install this handle first so the completed load cannot overwrite that replacement.
        request = loadJob
        loadJob.start()
    }

    fun refresh() {
        val account = activeAccount ?: run {
            _uiState.value = SteamSpaceUiState(failure = SteamSpaceFailureReason.ACCOUNT_REQUIRED)
            return
        }
        val currentGeneration = ++generation
        request?.cancel()
        val language = languageProvider()
        _uiState.value = _uiState.value.copy(
            loading = !_uiState.value.loaded,
            refreshing = _uiState.value.loaded,
            loadingMore = false,
            failure = null,
            loadMoreFailure = null
        )
        request = viewModelScope.launch {
            try {
                val page = fetchWithSessionRetry(account, currentGeneration, language, null)
                requireCurrent(account, currentGeneration)
                publishPage(account, page, null, currentGeneration, language)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!isCurrent(account, currentGeneration)) return@launch
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    refreshing = false,
                    failure = error.toSpaceFailure(),
                    stale = _uiState.value.loaded
                )
            }
        }
    }

    fun loadMore() {
        val account = activeAccount ?: return
        val state = _uiState.value
        val cursor = state.nextCursor ?: return
        if (state.loading || state.refreshing || state.loadingMore) return
        val currentGeneration = generation
        _uiState.value = state.copy(loadingMore = true, loadMoreFailure = null)
        request = viewModelScope.launch {
            try {
                val page = fetchWithSessionRetry(account, currentGeneration, feedLanguage, cursor)
                requireCurrent(account, currentGeneration)
                publishPage(account, page, cursor, currentGeneration, feedLanguage)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!isCurrent(account, currentGeneration)) return@launch
                _uiState.value = _uiState.value.copy(
                    loadingMore = false,
                    loadMoreFailure = error.toSpaceFailure()
                )
            }
        }
    }

    fun deactivate() {
        ++generation
        request?.cancel()
        request = null
        libraryNamesJob?.cancel()
        libraryNamesJob = null
        knownGameNames = emptyMap()
        activeAccount = null
        visitedCursors.clear()
        pageCount = 0
        fetchedAtMillis = 0L
        _uiState.value = SteamSpaceUiState()
    }

    private fun SteamAccount.cacheKey(language: String) = SteamSpaceCacheKey(
        id, steamId, language, isTemporary,
        // MDBX projects a fresh createdAt on each read; its negative ID already binds database + entry UUID.
        if (id < 0L && !isTemporary) 0L else createdAt
    )

    private fun loadLibraryNames(account: SteamAccount, key: SteamSpaceCacheKey) {
        libraryNamesJob = viewModelScope.launch {
            val names = try {
                withTimeoutOrNull(2_000L) { withContext(ioDispatcher) { libraryNamesLookup(account) } }.orEmpty()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyMap()
            }
            currentCoroutineContext().ensureActive()
            if (activeAccount?.cacheKey(feedLanguage) != key) return@launch
            knownGameNames = names.filter { (id, name) -> id > 0 && name.isNotBlank() && name.length <= 512 }
            val current = _uiState.value
            _uiState.value = current.copy(items = current.items.map { it.withKnownGameNames(knownGameNames) })
        }
    }

    private fun SteamSpaceCachedFeed.isFresh(): Boolean =
        (nowMillis() - fetchedAtMillis) in -1_000L until CACHE_FRESH_MILLIS &&
            items.none { it.kind != SteamSpaceEventKind.UNKNOWN && it.appIds.any { id -> it.appNames[id].isNullOrBlank() } }

    private fun SteamSpaceCachedFeed.toUiState() = SteamSpaceUiState(
        accountId = key.accountId, accountSteamId = key.accountSteamId,
        items = items.map { it.withKnownGameNames(knownGameNames) }, nextCursor = nextCursor, loaded = true, partial = partial, stale = !isFresh()
    )

    private fun restore(feed: SteamSpaceCachedFeed) {
        pageCount = feed.pageCount
        visitedCursors.clear()
        visitedCursors.addAll(feed.visitedCursors)
        fetchedAtMillis = feed.fetchedAtMillis
        feedLanguage = feed.key.language
        _uiState.value = feed.toUiState()
    }

    private suspend fun fetchWithSessionRetry(
        account: SteamAccount,
        requestGeneration: Long,
        language: String,
        cursor: SteamSpaceCursor?
    ): SteamSpacePage {
        return try {
            val prepared = prepareSession(account, requestGeneration, force = false)
            fetchPage(prepared, language, cursor)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!error.requiresSpaceSessionRefresh()) throw error
            // A late authentication failure must not refresh an account that was left behind.
            requireCurrent(account, requestGeneration)
            val refreshed = prepareSession(activeAccount ?: account, requestGeneration, force = true)
            fetchPage(refreshed, language, cursor)
        }
    }

    private suspend fun fetchPage(account: SteamAccount, language: String, cursor: SteamSpaceCursor?): SteamSpacePage =
        withTimeoutOrNull(requestTimeoutMillis) {
            runInterruptible(ioDispatcher) { gateway.fetchPage(account, language, cursor) }
        } ?: throw SocketTimeoutException("Steam activity request timed out")

    private suspend fun prepareSession(
        account: SteamAccount,
        requestGeneration: Long,
        force: Boolean
    ): SteamAccount {
        requireCurrent(account, requestGeneration)
        val resolved = sessionResolver.resolveOrKeep(account, force)
        requireCurrent(account, requestGeneration)
        check(resolved.id == account.id && resolved.steamId == account.steamId) {
            "Session resolver returned a different account"
        }
        activeAccount = resolved
        return resolved
    }

    private suspend fun publishPage(
        account: SteamAccount, page: SteamSpacePage, cursor: SteamSpaceCursor?,
        requestGeneration: Long, language: String
    ) {
        val previous = _uiState.value
        val merged = if (cursor == null) page.events else previous.items + page.events
        val unique = merged.distinctBy { it.id }
        val items = unique.take(MAX_ITEMS).map { it.withKnownGameNames(knownGameNames) }
        val nextPageCount = if (cursor == null) 1 else pageCount + 1
        val nextVisited = if (cursor == null) emptySet() else visitedCursors + cursor
        val candidate = page.nextCursor
        val validCursor = candidate?.takeIf {
            it.accountId == account.id && it.accountSteamId == account.steamId &&
                it.beforeEpochSeconds > 0L && it !in nextVisited &&
                (cursor == null || it.beforeEpochSeconds < cursor.beforeEpochSeconds)
        }
        val limitReached = items.size >= MAX_ITEMS || nextPageCount >= MAX_PAGES
        val nextState = previous.copy(
            items = items,
            nextCursor = validCursor.takeUnless { limitReached },
            loading = false,
            refreshing = false,
            loadingMore = false,
            loaded = true,
            failure = if (cursor == null) null else previous.failure,
            loadMoreFailure = null,
            stale = cursor != null && previous.stale,
            partial = (cursor != null && previous.partial) || page.partial ||
                items.any { it.partial } || unique.size > MAX_ITEMS ||
                (candidate != null && (validCursor == null || limitReached))
        )
        // Preserve ordering when a very fast manual refresh completes in the same millisecond.
        val fetchedAt = if (cursor == null) maxOf(nowMillis(), fetchedAtMillis + 1L) else fetchedAtMillis
        val snapshot = SteamSpaceCachedFeed(
            key = account.cacheKey(language), items = nextState.items, nextCursor = nextState.nextCursor,
            partial = nextState.partial, pageCount = nextPageCount, visitedCursors = nextVisited,
            fetchedAtMillis = fetchedAt
        )
        requireCurrent(account, requestGeneration)
        cache.remember(snapshot)
        pageCount = nextPageCount
        visitedCursors.clear()
        visitedCursors.addAll(nextVisited)
        fetchedAtMillis = fetchedAt
        feedLanguage = language
        _uiState.value = nextState
        // A completed feed is visible and reusable before optional encrypted persistence.
        // Leaving the page only cancels network work; this already completed snapshot may finish saving.
        cacheWrite?.cancel()
        cacheWrite = viewModelScope.launch {
            try {
                runInterruptible(ioDispatcher) { cache.save(snapshot) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Disk/cipher failure does not turn a successful network response into an error.
            }
        }
    }

    private fun isCurrent(account: SteamAccount, requestGeneration: Long): Boolean =
        generation == requestGeneration && activeAccount?.id == account.id &&
            activeAccount?.steamId == account.steamId

    private suspend fun requireCurrent(account: SteamAccount, requestGeneration: Long) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent(account, requestGeneration)) throw CancellationException("Space account changed")
    }

    override fun onCleared() {
        deactivate()
        super.onCleared()
    }

    companion object {
        internal const val MAX_ITEMS = 400
        internal const val MAX_PAGES = 20
        internal const val CACHE_FRESH_MILLIS = 5 * 60_000L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            val accounts = SteamAccountSourceRepository.get(appContext)
            val library by lazy {
                SteamLibraryCacheRepository(appContext, SteamDatabase.getDatabase(appContext).steamLibraryCacheDao(), SecurityManager(appContext))
            }
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SteamSpaceViewModel(
                        gateway = SteamSpaceService(),
                        sessionResolver = accounts.sessionResolver(),
                        cache = SteamSpacePreferencesFeedCache.get(appContext),
                        libraryNamesLookup = { account ->
                            if (account.isTemporary) emptyMap() else library.getLibrary(account.id)
                                ?.takeIf { it.accountId == account.id }?.games.orEmpty()
                                .associate { it.appId to it.name }
                        }
                    ) as T
            }
        }
    }
}

private fun Exception.requiresSpaceSessionRefresh(): Boolean = this is SteamApiException &&
    (authenticationRequired || httpStatusCode in setOf(401, 403) ||
        eResult in setOf(5, 15, 21, 27, 63, 65, 401, 403))

private fun Exception.toSpaceFailure(): SteamSpaceFailureReason = when {
    requiresSpaceSessionRefresh() -> SteamSpaceFailureReason.SESSION_REQUIRED
    this is IOException -> SteamSpaceFailureReason.NETWORK
    else -> SteamSpaceFailureReason.UNAVAILABLE
}

internal fun steamSpaceLanguage(locale: Locale): String = when (locale.language) {
    "zh" -> if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) "tchinese" else "schinese"
    "ja" -> "japanese"
    "ru" -> "russian"
    "vi" -> "vietnamese"
    else -> "english"
}
