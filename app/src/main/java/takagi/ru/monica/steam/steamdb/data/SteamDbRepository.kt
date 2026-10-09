package takagi.ru.monica.steam.steamdb.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import takagi.ru.monica.steam.steamdb.domain.*

class SteamDbRepository internal constructor(
    private val api: SteamDbGateway,
    scope: CoroutineScope,
    clock: () -> Long = System::currentTimeMillis
) {
    private val apps = SteamDbRequests<Int, SteamDbAppInfo>(60_000, scope, clock)
    private val players = SteamDbRequests<Int, Long>(30_000, scope, clock)
    private val ratings = SteamDbRequests<Int, SteamDbRating>(300_000, scope, clock)
    private val prices = SteamDbRequests<Pair<Int, String>, SteamDbLowestPrice>(300_000, scope, clock)

    fun observe(query: SteamDbQuery, force: Boolean = false): Flow<SteamDbState> = if (query.appId <= 0) {
        val missing = SteamDbResult.Failed(SteamDbFailure.NO_DATA)
        flowOf(SteamDbState(missing, missing, missing, missing))
    } else combine(
        loading { apps.load(query.appId, force) { api.info(query.appId) } },
        loading { players.load(query.appId, force) { api.players(query.appId) } },
        loading { ratings.load(query.appId, force) { api.rating(query.appId) } },
        loading { price(query, force) }
    ) { info, players, rating, price -> SteamDbState(info, players, rating, price) }

    suspend fun price(query: SteamDbQuery, force: Boolean = false): SteamDbResult<SteamDbLowestPrice> {
        if (query.appId <= 0) return SteamDbResult.Failed(SteamDbFailure.NO_DATA)
        if (query.free) return SteamDbResult.Failed(SteamDbFailure.FREE)
        val region = query.priceRegion ?: return SteamDbResult.Failed(SteamDbFailure.UNKNOWN_REGION)
        return prices.load(query.appId to region, force) { api.price(query.appId, region) }
    }

    private fun <T> loading(block: suspend () -> SteamDbResult<T>): Flow<SteamDbResult<T>> = flow {
        emit(SteamDbResult.Loading)
        emit(block())
    }

    companion object {
        val shared by lazy { SteamDbRepository(SteamDbApi(), CoroutineScope(SupervisorJob() + Dispatchers.IO)) }
    }
}

/** Cancelling one screen only detaches its observer; a shared, bounded request can finish for others. */
internal class SteamDbRequests<K, V>(
    private val ttl: Long, private val scope: CoroutineScope, private val clock: () -> Long
) {
    private val lock = Any()
    private val cache = LinkedHashMap<K, SteamDbResult.Ready<V>>()
    private val pending = mutableMapOf<K, Deferred<SteamDbResult<V>>>()
    private val cooldowns = LinkedHashMap<K, SteamDbResult.Failed>()

    suspend fun load(key: K, force: Boolean = false, fetch: () -> SteamDbResult<V>): SteamDbResult<V> {
        val request = synchronized(lock) {
            val cached = cache[key]
            if (!force && cached != null && clock() - cached.fetchedAt in 0 until ttl) {
                return cached.copy(cached = true)
            }
            cooldowns[key]?.let {
                if ((it.retryAt ?: 0) > clock()) return cached?.copy(cached = true, stale = true) ?: it
                cooldowns.remove(key)
            }
            pending[key] ?: scope.async(start = CoroutineStart.LAZY) {
                try {
                    val result = fetch()
                    synchronized(lock) {
                        when (result) {
                            is SteamDbResult.Ready -> {
                                cache.remove(key)
                                cache[key] = result
                                while (cache.size > 64) cache.remove(cache.keys.first())
                                result
                            }
                            is SteamDbResult.Failed -> {
                                if (result.reason == SteamDbFailure.RATE_LIMITED) {
                                    cooldowns[key] = result
                                    while (cooldowns.size > 64) cooldowns.remove(cooldowns.keys.first())
                                }
                                cache[key]?.copy(cached = true, stale = true) ?: result
                            }
                            SteamDbResult.Loading -> result
                        }
                    }
                } finally { synchronized(lock) { pending.remove(key) } }
            }.also { pending[key] = it }
        }
        request.start()
        return request.await()
    }
}
