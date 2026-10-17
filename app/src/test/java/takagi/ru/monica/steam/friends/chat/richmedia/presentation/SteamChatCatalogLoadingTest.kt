package takagi.ru.monica.steam.friends.chat.richmedia.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.richmedia.data.SteamChatCatalogCache
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentGateway
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentTarget
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatCatalogGateway
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatPendingAttachment
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichMediaCatalog
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatUploadedAttachment
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

@OptIn(ExperimentalCoroutinesApi::class)
class SteamChatCatalogLoadingTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val old = catalog("cached")
    private val fresh = catalog("fresh")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun diskCatalogAppearsBeforeSessionResolutionAndIsThenUpdated() = runTest(scheduler) {
        val cache = Cache().apply { disk[account(1).steamId] = old }
        val gate = CompletableDeferred<Unit>()
        val model = model(cache, resolver = { account, _ -> gate.await(); account })
        model.selectAccount(account(1))
        runCurrent()
        assertEquals(old.emoticons, model.uiState.value.emoticons)
        assertFalse(model.uiState.value.catalogFailure)
        gate.complete(Unit)
        runCurrent()
        assertEquals(fresh.emoticons, model.uiState.value.emoticons)
        assertEquals(fresh, cache.disk[account(1).steamId])
        assertFalse(model.uiState.value.catalogLoading)
    }

    @Test
    fun reopeningUsesMemoryImmediatelyAndOfflineRefreshKeepsIt() = runTest(scheduler) {
        val cache = Cache().apply { save(account(1), old) }
        repeat(2) {
            val model = model(cache, gateway = SteamChatCatalogGateway { error("offline") })
            model.selectAccount(account(1))
            assertEquals(old.emoticons, model.uiState.value.emoticons)
            runCurrent()
            assertEquals(old.emoticons, model.uiState.value.emoticons)
            assertFalse(model.uiState.value.catalogFailure)
            assertFalse(model.uiState.value.catalogLoading)
        }
    }

    @Test
    fun firstLoadFailureWithoutCacheStillAllowsExplicitRetry() = runTest(scheduler) {
        var offline = true
        val model = model(Cache(), gateway = SteamChatCatalogGateway {
            if (offline) error("offline") else fresh
        })
        model.selectAccount(account(1))
        runCurrent()
        assertTrue(model.uiState.value.catalogFailure)
        assertFalse(model.uiState.value.catalogLoading)
        offline = false
        model.refreshCatalogs()
        runCurrent()
        assertEquals(fresh.emoticons, model.uiState.value.emoticons)
        assertFalse(model.uiState.value.catalogFailure)
    }

    @Test
    fun confirmedEmptyCatalogReplacesOldAssetsAndSurvivesLaterFailure() = runTest(scheduler) {
        val cache = Cache().apply { save(account(1), old) }
        var offline = false
        val model = model(cache, gateway = SteamChatCatalogGateway {
            if (offline) error("offline") else SteamChatRichMediaCatalog()
        })
        model.selectAccount(account(1))
        runCurrent()
        assertTrue(model.uiState.value.emoticons.isEmpty())
        assertEquals(SteamChatRichMediaCatalog(), cache.disk[account(1).steamId])
        offline = true
        model.refreshCatalogs()
        runCurrent()
        assertTrue(model.uiState.value.emoticons.isEmpty())
        assertFalse(model.uiState.value.catalogFailure)
    }

    @Test
    fun switchingAccountsDoesNotShowOrPersistALateOldResponse() = runTest(scheduler) {
        val cache = Cache().apply { save(account(1), old) }
        val gate = CompletableDeferred<Unit>()
        val model = model(cache, resolver = { account, _ ->
            if (account.id == 1L) withContext(NonCancellable) { gate.await() }
            account
        })
        model.selectAccount(account(1))
        runCurrent()
        model.selectAccount(account(2))
        assertTrue(model.uiState.value.emoticons.isEmpty())
        runCurrent()
        assertEquals(fresh.emoticons, model.uiState.value.emoticons)
        gate.complete(Unit)
        runCurrent()
        assertEquals(fresh.emoticons, model.uiState.value.emoticons)
        assertEquals(old, cache.disk[account(1).steamId])
        assertEquals(fresh, cache.disk[account(2).steamId])
        model.selectAccount(null)
        assertTrue(model.uiState.value.emoticons.isEmpty())
    }

    @Test
    fun credentialRotationKeepsCatalogAndRepeatedRefreshDoesNotDuplicateRequests() = runTest(scheduler) {
        val gate = CompletableDeferred<Unit>()
        var resolutions = 0
        val model = model(Cache(), resolver = { account, _ ->
            resolutions++
            if (account.accessToken == "rotated") gate.await()
            account
        })
        model.selectAccount(account(1))
        runCurrent()
        model.selectAccount(account(1).copy(accessToken = "rotated"))
        assertEquals(fresh.emoticons, model.uiState.value.emoticons)
        runCurrent()
        repeat(5) { model.refreshCatalogs() }
        runCurrent()
        assertEquals(2, resolutions)
        assertEquals(fresh.emoticons, model.uiState.value.emoticons)
        gate.complete(Unit)
        runCurrent()
        assertFalse(model.uiState.value.catalogLoading)
    }

    @Test
    fun cacheReadAndWriteFailuresDoNotDiscardNetworkResults() = runTest(scheduler) {
        val cache = object : SteamChatCatalogCache {
            override fun load(account: SteamAccount): SteamChatRichMediaCatalog? = error("disk read")
            override fun save(account: SteamAccount, catalog: SteamChatRichMediaCatalog) = error("disk write")
        }
        val model = model(cache)
        model.selectAccount(account(1))
        runCurrent()
        assertEquals(fresh.emoticons, model.uiState.value.emoticons)
        assertFalse(model.uiState.value.catalogFailure)
        assertFalse(model.uiState.value.catalogLoading)
    }

    private fun model(
        cache: SteamChatCatalogCache,
        gateway: SteamChatCatalogGateway = SteamChatCatalogGateway { fresh },
        resolver: SteamAccountSessionResolver? = null
    ) = SteamChatRichMediaViewModel(
        catalogGateway = gateway,
        attachmentGateway = object : SteamChatAttachmentGateway {
            override suspend fun inspect(rawUri: String): SteamChatPendingAttachment = error("unused")
            override suspend fun upload(
                account: SteamAccount, target: SteamChatAttachmentTarget,
                attachment: SteamChatPendingAttachment, spoiler: Boolean, onProgress: (Float) -> Unit
            ): SteamChatUploadedAttachment = error("unused")
        },
        sessionResolver = resolver, ioDispatcher = dispatcher, catalogCache = cache
    )

    private class Cache : SteamChatCatalogCache {
        val memory = mutableMapOf<String, SteamChatRichMediaCatalog>()
        val disk = mutableMapOf<String, SteamChatRichMediaCatalog>()
        override fun peek(account: SteamAccount) = memory[account.steamId]
        override fun load(account: SteamAccount) = disk[account.steamId]
        override fun save(account: SteamAccount, catalog: SteamChatRichMediaCatalog) {
            memory[account.steamId] = catalog
            disk[account.steamId] = catalog
        }
    }

    private fun catalog(name: String) = SteamChatRichMediaCatalog(emoticons = listOf(SteamChatEmoticon(name)))

    private fun account(id: Long) = SteamAccount(
        id = id, steamId = (76561198000000000L + id).toString(),
        accountName = "test", displayName = "Test", deviceId = "android:test",
        sharedSecret = "test-secret", identitySecret = null, revocationCode = null,
        tokenGid = null, accessToken = "test-token", refreshToken = null,
        steamLoginSecure = null, rawSteamGuardJson = "{}", selected = true,
        sortOrder = 0, createdAt = 0, updatedAt = 0
    )
}
