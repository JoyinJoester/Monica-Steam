package takagi.ru.monica.steam.store.ui

import android.content.Context
import kotlinx.coroutines.CancellationException
import takagi.ru.monica.steam.network.SteamApiException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import takagi.ru.monica.steam.profile.SteamMiniProfileDecorRepository
import takagi.ru.monica.steam.store.data.SteamGameStatsExtrasService
import takagi.ru.monica.steam.store.points.data.SteamPointsShopService
import takagi.ru.monica.steam.store.points.domain.SteamPointsShopCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.*
import takagi.ru.monica.steam.library.*
import takagi.ru.monica.steam.session.domain.resolveOrKeep
import takagi.ru.monica.steam.token.domain.steamCommunityLanguage

internal data class SteamGamePersonalStats(
    val game: SteamGame? = null,
    val achievements: SteamGameAchievements? = null,
    val activityCached: Boolean = false,
    val achievementsCached: Boolean = false,
    val recentUnavailable: Boolean = false,
    val loading: Boolean = true,
    val activityFailure: SteamLibraryFailureReason? = null,
    val achievementFailure: SteamLibraryFailureReason? = null
)

internal interface SteamGameStatsGateway {
    suspend fun extraDetails(account: SteamAccount, appId: Int, publish: (SteamGameStatsExtras) -> Unit) {}
    suspend fun cached(accountId: Long, appId: Int): Pair<SteamLibrarySnapshot?, SteamGameAchievements?>
    suspend fun playerGame(account: SteamAccount, appId: Int): SteamLibraryResult<SteamGame?>
    suspend fun achievements(account: SteamAccount, game: SteamGame): SteamLibraryResult<SteamGameAchievements>
}

/** The caller owns the account/source/app lifetime; cancelled requests never publish a late result. */
internal suspend fun loadSteamGamePersonalStats(
    gateway: SteamGameStatsGateway, account: SteamAccount, appId: Int, name: String,
    publish: (SteamGamePersonalStats) -> Unit
) {
    var state = SteamGamePersonalStats()
    // All storage/network failures remain visible; cancellation must propagate.
    val cached = statsAttempt { gateway.cached(account.id, appId) }
    cached.getOrNull()?.let { (snapshot, achievements) ->
        val ownedSnapshot = snapshot?.takeIf { it.accountId == account.id }
        val game = ownedSnapshot?.games?.firstOrNull { it.appId == appId }
        val ownAchievements = achievements?.takeIf { it.accountId == account.id && it.appId == appId }
        state = state.copy(game = game, achievements = ownAchievements,
            activityCached = game != null, achievementsCached = ownAchievements != null,
            recentUnavailable = appId in ownedSnapshot?.recentPlaytimeUnavailableAppIds.orEmpty())
        coroutineContext.ensureActive(); publish(state)
    }
    when (val activity = statsAttempt { gateway.playerGame(account, appId) }.getOrElse {
        SteamLibraryResult.Failure(SteamLibraryFailureReason.NETWORK)
    }) {
        is SteamLibraryResult.Success -> state = state.copy(
            game = activity.value?.takeIf { it.appId == appId }?.let { it.copy(name = it.name.ifBlank { name }) },
            activityCached = false, recentUnavailable = false)
        is SteamLibraryResult.Failure -> state = state.copy(activityFailure = activity.reason)
    }
    coroutineContext.ensureActive(); publish(state)
    val game = state.game ?: SteamGame(appId, name, 0, 0)
    when (val result = statsAttempt { gateway.achievements(account, game) }.getOrElse {
        SteamLibraryResult.Failure(SteamLibraryFailureReason.NETWORK)
    }) {
        is SteamLibraryResult.Success -> {
            val own = result.value.takeIf { it.accountId == account.id && it.appId == appId }
            state = if (own != null) state.copy(achievements = own, achievementsCached = false)
                else state.copy(achievementFailure = SteamLibraryFailureReason.INVALID_RESPONSE)
        }
        is SteamLibraryResult.Failure -> state = state.copy(achievementFailure = result.reason)
    }
    state = state.copy(loading = false)
    coroutineContext.ensureActive(); publish(state)
}

private suspend fun <T> statsAttempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

internal class AndroidSteamGameStatsGateway(context: Context) : SteamGameStatsGateway {
    private val app = context.applicationContext
    private val service = SteamGameLibraryService()
    private val language get() = steamCommunityLanguage(app.resources.configuration.locales[0].language)
    override suspend fun cached(accountId: Long, appId: Int) = withContext(Dispatchers.IO) {
        val cache = SteamLibraryCacheRepository(app, SteamDatabase.getDatabase(app).steamLibraryCacheDao(), SecurityManager(app))
        cache.getLibrary(accountId) to cache.getAchievements(accountId, appId)
    }
    private suspend fun <T> request(account: SteamAccount, action: (SteamAccount) -> SteamLibraryResult<T>): SteamLibraryResult<T> =
        withContext(Dispatchers.IO) {
            val resolver = SteamAccountSourceRepository.get(app).sessionResolver()
            val prepared = resolver.resolveOrKeep(account, false)
            fun attempt(value: SteamAccount): SteamLibraryResult<T> = try { action(value) }
                catch (error: SteamApiException) {
                    if (error.authenticationRequired || error.httpStatusCode in setOf(401, 403) || error.eResult in setOf(5, 15, 401, 403))
                        SteamLibraryResult.Failure(SteamLibraryFailureReason.SESSION_REQUIRED)
                    else throw error
                }
            val first = attempt(prepared)
            if (first is SteamLibraryResult.Failure && first.reason == SteamLibraryFailureReason.SESSION_REQUIRED) {
                val refreshed = resolver.resolveOrKeep(prepared, true)
                if (refreshed.accessToken != prepared.accessToken) attempt(refreshed) else first
            } else first
        }
    override suspend fun extraDetails(account: SteamAccount, appId: Int, publish: (SteamGameStatsExtras) -> Unit) = coroutineScope {
        val decor = SteamMiniProfileDecorRepository.get(app)
        var state = SteamGameStatsExtras(avatarUrl = decor.loadCached(account.steamId)?.avatarUrl.orEmpty(),
            loading = setOf("avatar", "friends", "badges", "points"))
        publish(state)
        suspend fun section(key: String, action: suspend () -> (SteamGameStatsExtras) -> SteamGameStatsExtras) {
            val result = statsAttempt { action() }
            coroutineContext.ensureActive()
            state = result.getOrNull()?.invoke(state) ?: state.copy(failed = state.failed + key)
            state = state.copy(loading = state.loading - key)
            publish(state)
        }
        val extras = SteamGameStatsExtrasService()
        // Exactly four independent sections. All publication returns to the caller's
        // context; account/source cancellation prevents old results replacing new data.
        launch { section("avatar") {
            val avatar = decor.load(account.steamId)?.avatarUrl.orEmpty()
            return@section { old: SteamGameStatsExtras -> old.copy(avatarUrl = avatar.ifBlank { old.avatarUrl }) }
        } }
        launch { section("friends") {
            val result = request(account) { prepared ->
                SteamLibraryResult.Success(extras.friends(prepared, appId))
            }
            val value = (result as SteamLibraryResult.Success).value
            return@section { old: SteamGameStatsExtras -> old.copy(friends = value) }
        } }
        launch { section("badges") {
            val result = request(account) { prepared ->
                SteamLibraryResult.Success(extras.badges(prepared, appId, language))
            }
            val value = (result as SteamLibraryResult.Success).value
            return@section { old: SteamGameStatsExtras -> old.copy(badges = value) }
        } }
        launch { section("points") {
            val value = withContext(Dispatchers.IO) { SteamPointsShopService().page(
                SteamPointsShopCategory.FEATURED, language = language, count = 12, appId = appId) }
            return@section { old: SteamGameStatsExtras -> old.copy(points = value) }
        } }
        Unit
    }
    override suspend fun playerGame(account: SteamAccount, appId: Int) =
        request(account) { service.fetchPlayerGame(it, appId, language) }
    override suspend fun achievements(account: SteamAccount, game: SteamGame) =
        request(account) { service.fetchAchievements(it, game, language, forceRefresh = true) }
}

internal suspend fun loadSteamGameStatsExtras(gateway: SteamGameStatsGateway, account: SteamAccount, appId: Int,
    publish: (SteamGameStatsExtras) -> Unit) {
    val owner = coroutineContext
    gateway.extraDetails(account, appId) { value -> owner.ensureActive(); publish(value) }
}
