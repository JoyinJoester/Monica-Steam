package takagi.ru.monica.steam.space

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.steam.space.data.SteamSpacePreferencesFeedCache
import takagi.ru.monica.steam.space.domain.SteamSpaceCacheKey
import takagi.ru.monica.steam.space.domain.SteamSpaceCachedFeed
import takagi.ru.monica.steam.space.domain.SteamSpaceEvent
import takagi.ru.monica.steam.space.domain.SteamSpaceEventKind

/** Real private preferences and existing app cipher; only synthetic data in an isolated test user. */
@RunWith(AndroidJUnit4::class)
class SteamSpaceCacheDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun encryptedFeedSurvivesCacheRecreationWithoutPlaintextAndSeparatesAccounts() {
        val name = "steam_space_cache_fixture_${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val key = SteamSpaceCacheKey(701, "76561198000000701", "english", false, 7)
        val body = "Synthetic cached activity ${UUID.randomUUID()}"
        val snapshot = SteamSpaceCachedFeed(key, listOf(SteamSpaceEvent(
            id = "cache-fixture", kind = SteamSpaceEventKind.STATUS,
            actorSteamId = "76561198000000702", occurredAtEpochSeconds = 1_794_016_800,
            body = body, sourceUrl = "https://steamcommunity.com/my/home/"
        )), null, false, 1, emptySet(), System.currentTimeMillis())
        try {
            val first = SteamSpacePreferencesFeedCache.create(context, name)
            first.save(snapshot)
            assertEquals(snapshot, first.peek(key))
            assertTrue("encrypted preferences were written", preferences.all.isNotEmpty())
            val raw = preferences.all.values.joinToString("\n")
            assertFalse("body is not persisted as plaintext", raw.contains(body))
            assertFalse("account identity is not persisted as plaintext", raw.contains(key.accountSteamId))
            val reopened = SteamSpacePreferencesFeedCache.create(context, name)
            assertNull("new instance has no warmed memory", reopened.peek(key))
            assertEquals(snapshot, reopened.load(key))
            assertNull(reopened.load(key.copy(accountId = 702)))
            assertNull(reopened.load(key.copy(accountSteamId = "76561198000000703")))
            assertNull(reopened.load(key.copy(language = "japanese")))

            val beforeTemporary = preferences.all.toMap()
            val temporary = snapshot.copy(key = key.copy(accountId = -701, temporary = true))
            reopened.save(temporary)
            assertEquals(temporary, reopened.peek(temporary.key))
            assertEquals(beforeTemporary, preferences.all)
            assertNull(SteamSpacePreferencesFeedCache.create(context, name).load(temporary.key))

            val editor = preferences.edit()
            preferences.all.keys.forEach { editor.putString(it, "invalid-ciphertext") }
            assertTrue(editor.commit())
            assertNull(SteamSpacePreferencesFeedCache.create(context, name).load(key))
        } finally {
            preferences.edit().clear().commit()
            check(context.deleteSharedPreferences(name) || preferences.all.isEmpty())
        }
    }
}
