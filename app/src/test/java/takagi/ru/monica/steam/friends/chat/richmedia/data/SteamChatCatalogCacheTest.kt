package takagi.ru.monica.steam.friends.chat.richmedia.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEffect
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichMediaCatalog
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatSticker

class SteamChatCatalogCacheTest {
    private val catalog = SteamChatRichMediaCatalog(
        emoticons = listOf(SteamChatEmoticon("wave", 10, 123L, 4)),
        stickers = listOf(SteamChatSticker("dance", appId = 20, count = 3, useCount = 2)),
        effects = listOf(SteamChatEffect("confetti", 30, 2, true, 456L))
    )

    @Test
    fun newCacheInstanceRestoresAllOwnedAssetsWithoutPersistingCredentials() {
        val disk = mutableMapOf<String, String>()
        val original = account(1)
        SteamChatCatalogPreferencesCache(disk::get) { key, raw -> disk[key] = raw }
            .save(original, catalog)
        val restored = SteamChatCatalogPreferencesCache(disk::get) { _, _ -> }
        assertNull(restored.peek(original))
        assertEquals(catalog, restored.load(original.copy(accessToken = "new-token")))
        assertEquals(catalog, restored.peek(original))
        assertTrue(disk.keys.single().matches(Regex("[0-9a-f]{64}")))
        assertFalse(disk.values.single().contains("test-secret"))
        assertFalse(disk.values.single().contains("test-token"))
    }

    @Test
    fun memoryPeekNeverReadsDiskAndCatalogsStayAccountScoped() {
        val cache = SteamChatCatalogPreferencesCache(read = { error("Unexpected disk read") }, write = { _, _ -> })
        cache.save(account(1), catalog)
        assertEquals(catalog, cache.peek(account(1)))
        assertEquals(catalog, cache.load(account(1)))
        assertNull(cache.peek(account(1).copy(steamId = account(2).steamId)))
        for (id in 2L..9L) cache.save(account(id), catalog)
        assertNull(cache.peek(account(1)))
        assertEquals(catalog, cache.peek(account(9)))
    }

    @Test
    fun temporaryCatalogIsMemoryOnlyAndSeparateFromSavedAccount() {
        val disk = mutableMapOf<String, String>()
        val cache = SteamChatCatalogPreferencesCache(disk::get) { key, raw -> disk[key] = raw }
        val saved = account(1)
        val temporary = saved.copy(isTemporary = true)
        cache.save(saved, catalog)
        assertNull(cache.load(temporary))
        cache.save(temporary, SteamChatRichMediaCatalog())
        assertEquals(SteamChatRichMediaCatalog(), cache.peek(temporary))
        assertEquals(catalog, cache.peek(saved))
        assertEquals(1, disk.size)
        val restored = SteamChatCatalogPreferencesCache(disk::get) { _, _ -> }
        assertNull(restored.load(temporary))
        assertEquals(catalog, restored.load(saved))
    }

    @Test
    fun corruptAndOversizedEntriesAreMissesButAnEmptyCatalogIsValid() {
        assertNull(SteamChatCatalogCacheCodec.decode("broken json"))
        assertNull(SteamChatCatalogCacheCodec.decode(" ".repeat(2 * 1024 * 1024 + 1)))
        val empty = SteamChatRichMediaCatalog()
        assertEquals(empty, SteamChatCatalogCacheCodec.decode(requireNotNull(SteamChatCatalogCacheCodec.encode(empty))))
        val cache = SteamChatCatalogPreferencesCache(read = { "broken json" }, write = { _, _ -> })
        assertNull(cache.load(account(1)))
        cache.save(account(1), empty)
        assertEquals(empty, cache.load(account(1)))
    }

    @Test
    fun olderDiskReadCannotOverwriteAConcurrentRefresh() {
        val updated = catalog.copy(emoticons = listOf(SteamChatEmoticon("updated")))
        lateinit var cache: SteamChatCatalogPreferencesCache
        cache = SteamChatCatalogPreferencesCache(
            read = {
                cache.save(account(1), updated)
                SteamChatCatalogCacheCodec.encode(catalog)
            },
            write = { _, _ -> }
        )
        assertEquals(updated, cache.load(account(1)))
        assertEquals(updated, cache.peek(account(1)))
    }

    private fun account(id: Long) = SteamAccount(
        id = id, steamId = (76561198000000000L + id).toString(),
        accountName = "test", displayName = "Test", deviceId = "android:test",
        sharedSecret = "test-secret", identitySecret = null, revocationCode = null,
        tokenGid = null, accessToken = "test-token", refreshToken = null,
        steamLoginSecure = null, rawSteamGuardJson = "{}", selected = true,
        sortOrder = 0, createdAt = 0, updatedAt = 0
    )
}
