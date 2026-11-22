package takagi.ru.monica.steam.store.ui

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.library.*

class SteamGamePersonalStatsTest {
    private val account = SteamAccount(1, "76561198000000001", "player", "Player", "android:test", "test",
        null, null, null, "test-token", null, null, "{}", true, 0, 0, 0)
    private val game = SteamGame(620, "Portal 2", 1740, 1026, lastPlayedAt = 1794902400)
    private val achievements = SteamGameAchievements(1, 620, "Portal 2", emptyList(), 1)
    private class Gateway : SteamGameStatsGateway {
        var cache: Pair<SteamLibrarySnapshot?, SteamGameAchievements?> = null to null
        var player: SteamLibraryResult<SteamGame?> = SteamLibraryResult.Success(null)
        var result: SteamLibraryResult<SteamGameAchievements> = SteamLibraryResult.Failure(SteamLibraryFailureReason.NETWORK)
        var beforePlayer: suspend () -> Unit = {}
        var beforeAchievements: suspend () -> Unit = {}
        override suspend fun cached(accountId: Long, appId: Int) = cache
        override suspend fun playerGame(account: SteamAccount, appId: Int): SteamLibraryResult<SteamGame?> {
            beforePlayer(); return player
        }
        override suspend fun achievements(account: SteamAccount, game: SteamGame): SteamLibraryResult<SteamGameAchievements> {
            beforeAchievements(); return result
        }
    }
    @Test fun cachedDataAppearsBeforeNetworkAndFailuresKeepItsProvenance() = runBlocking {
        val gateway = Gateway().apply {
            cache = SteamLibrarySnapshot(1, listOf(game), 1, recentPlaytimeUnavailableAppIds = setOf(620)) to achievements
            player = SteamLibraryResult.Failure(SteamLibraryFailureReason.PRIVATE_PROFILE)
        }
        val states = mutableListOf<SteamGamePersonalStats>()
        loadSteamGamePersonalStats(gateway, account, 620, "Portal 2", states::add)
        assertTrue(states.first().loading)
        assertEquals(1740, states.first().game?.playtimeForeverMinutes)
        val final = states.last()
        assertFalse(final.loading); assertTrue(final.activityCached); assertTrue(final.achievementsCached)
        assertTrue(final.recentUnavailable)
        assertEquals(SteamLibraryFailureReason.PRIVATE_PROFILE, final.activityFailure)
        assertEquals(SteamLibraryFailureReason.NETWORK, final.achievementFailure)
    }
    @Test fun unknownActivityDoesNotBecomeZeroButConfirmedZeroRemainsVisible() = runBlocking {
        val states = mutableListOf<SteamGamePersonalStats>()
        val gateway = Gateway().apply { result = SteamLibraryResult.Success(achievements) }
        loadSteamGamePersonalStats(gateway, account, 620, "Portal 2", states::add)
        assertNull(states.last().game)
        gateway.player = SteamLibraryResult.Success(game.copy(playtimeForeverMinutes = 0, playtimeRecentMinutes = 0))
        loadSteamGamePersonalStats(gateway, account, 620, "Portal 2", states::add)
        assertEquals(0, states.last().game?.playtimeForeverMinutes)
        assertFalse(states.last().activityCached)
    }
    @Test fun wrongAccountOrGameCacheAndServerAchievementsAreRejected() = runBlocking {
        val gateway = Gateway().apply {
            cache = SteamLibrarySnapshot(2, listOf(game), 1) to achievements.copy(appId = 570)
            result = SteamLibraryResult.Success(achievements.copy(accountId = 2))
        }
        val states = mutableListOf<SteamGamePersonalStats>()
        loadSteamGamePersonalStats(gateway, account, 620, "Portal 2", states::add)
        assertTrue(states.all { it.game == null && it.achievements == null })
        assertEquals(SteamLibraryFailureReason.INVALID_RESPONSE, states.last().achievementFailure)
    }
    @Test fun partialFailureDoesNotHideSuccessfulPlaytimeOrInventAchievements() = runBlocking {
        val gateway = Gateway().apply { player = SteamLibraryResult.Success(game) }
        var state = SteamGamePersonalStats()
        loadSteamGamePersonalStats(gateway, account, 620, "Portal 2") { state = it }
        assertEquals(game, state.game); assertNull(state.achievements)
        assertFalse(state.loading); assertNull(state.activityFailure)
    }
    @Test fun cancellationRejectsNonCooperativeLatePlayerResult() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val gateway = Gateway().apply {
            player = SteamLibraryResult.Success(game)
            beforePlayer = { withContext(NonCancellable) { entered.complete(Unit); release.await() } }
        }
        val states = mutableListOf<SteamGamePersonalStats>()
        val job = launch { loadSteamGamePersonalStats(gateway, account, 620, "Portal 2", states::add) }
        entered.await(); job.cancel(); release.complete(Unit); job.join()
        assertTrue(job.isCancelled)
        assertTrue(states.none { it.game != null })
    }
    @Test fun cancellationDuringAchievementsDoesNotPublishLateSuccess() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val gateway = Gateway().apply {
            player = SteamLibraryResult.Success(game); result = SteamLibraryResult.Success(achievements)
            beforeAchievements = { withContext(NonCancellable) { entered.complete(Unit); release.await() } }
        }
        val states = mutableListOf<SteamGamePersonalStats>()
        val job = launch { loadSteamGamePersonalStats(gateway, account, 620, "Portal 2", states::add) }
        entered.await(); job.cancel(); release.complete(Unit); job.join()
        assertTrue(states.none { it.achievements != null || !it.loading })
    }
    @Test fun cancelledExtrasNeverPublishLateAvatarOrFriends() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val gateway = object : SteamGameStatsGateway {
            override suspend fun cached(accountId: Long, appId: Int): Pair<SteamLibrarySnapshot?, SteamGameAchievements?> = null to null
            override suspend fun playerGame(account: SteamAccount, appId: Int): SteamLibraryResult<SteamGame?> = SteamLibraryResult.Success(null)
            override suspend fun achievements(account: SteamAccount, game: SteamGame): SteamLibraryResult<SteamGameAchievements> =
                SteamLibraryResult.Failure(SteamLibraryFailureReason.NETWORK)
            override suspend fun extraDetails(account: SteamAccount, appId: Int, publish: (SteamGameStatsExtras) -> Unit) {
                withContext(NonCancellable) { entered.complete(Unit); release.await()
                    publish(SteamGameStatsExtras(avatarUrl = "old-account-avatar")) }
            }
        }
        val states = mutableListOf<SteamGameStatsExtras>()
        val job = launch { loadSteamGameStatsExtras(gateway, account, 620, states::add) }
        entered.await(); job.cancel(); release.complete(Unit); job.join()
        assertTrue(states.isEmpty()); assertTrue(job.isCancelled)
    }
}
