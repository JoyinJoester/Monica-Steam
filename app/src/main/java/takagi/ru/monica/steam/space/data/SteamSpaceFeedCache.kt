package takagi.ru.monica.steam.space.data

import android.content.Context
import java.util.concurrent.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.space.domain.SteamSpaceCacheKey
import takagi.ru.monica.steam.space.domain.SteamSpaceCachedFeed
import takagi.ru.monica.steam.space.domain.SteamSpaceFeedCache
import takagi.ru.monica.steam.space.domain.SteamSpaceMemoryFeedCache
import takagi.ru.monica.steam.space.domain.detachedCopy
import takagi.ru.monica.steam.space.domain.isValid
import takagi.ru.monica.steam.space.domain.isValidAt

/** Bounded encrypted display snapshots. All disk and cipher work stays behind load/save. */
class SteamSpacePreferencesFeedCache internal constructor(
    private val read: () -> String?,
    private val write: (String) -> Unit,
    private val encrypt: (String) -> String,
    private val decrypt: (String) -> String,
    private val clockMillis: () -> Long = System::currentTimeMillis
) : SteamSpaceFeedCache {
    private val memory = SteamSpaceMemoryFeedCache(clockMillis)
    // Never acquired by peek: slow storage cannot block the UI's memory lookup.
    private val diskLock = Any()

    override fun peek(key: SteamSpaceCacheKey): SteamSpaceCachedFeed? = memory.peek(key)

    override fun remember(feed: SteamSpaceCachedFeed) = memory.save(feed)

    override fun load(key: SteamSpaceCacheKey): SteamSpaceCachedFeed? {
        peek(key)?.let { return it }
        if (key.temporary || !key.isValid()) return null
        return synchronized(diskLock) {
            peek(key)?.let { return@synchronized it }
            cacheAttempt { readSnapshot().firstOrNull { it.key == key } }?.let(memory::offer)
            peek(key)
        }
    }

    override fun save(feed: SteamSpaceCachedFeed) {
        if (!feed.isValidAt(clockMillis())) return
        if (feed.key.temporary) {
            memory.save(feed)
            return
        }
        synchronized(diskLock) {
            // Merge disk before accepting a write, including writes from a newly created cache.
            val previous = cacheAttempt(::readSnapshot).orEmpty()
            previous.firstOrNull { it.key == feed.key }?.let(memory::offer)
            if (!memory.offer(feed)) return
            cacheAttempt {
                val latest = requireNotNull(memory.peek(feed.key))
                val merged = previous.filter { it.key != latest.key }.toMutableList().apply { add(latest) }
                while (merged.size > SteamSpaceMemoryFeedCache.MAX_ENTRIES) merged.removeAt(0)
                var encoded = SteamSpaceFeedCacheCodec.encode(merged)
                while (encoded == null && merged.size > 1) {
                    merged.removeAt(0)
                    encoded = SteamSpaceFeedCacheCodec.encode(merged)
                }
                if (encoded != null) {
                    val ciphertext = encrypt(encoded)
                    // Cipher failure must never silently turn this into plaintext storage.
                    require(ciphertext.isNotEmpty() && ciphertext != encoded && ciphertext.length <= MAX_CIPHERTEXT_CHARACTERS)
                    write(ciphertext)
                }
            }
        }
    }

    private fun readSnapshot(): List<SteamSpaceCachedFeed> {
        val ciphertext = read() ?: return emptyList()
        if (ciphertext.length > MAX_CIPHERTEXT_CHARACTERS) return emptyList()
        val plaintext = decrypt(ciphertext)
        if (plaintext == ciphertext) return emptyList()
        return SteamSpaceFeedCacheCodec.decode(plaintext, clockMillis()).orEmpty()
    }

    companion object {
        internal const val SNAPSHOT_KEY = "encrypted_feed_snapshot_v1"
        private const val MAX_CIPHERTEXT_CHARACTERS = 16 * 1024 * 1024
        @Volatile private var instance: SteamSpacePreferencesFeedCache? = null

        fun get(context: Context): SteamSpacePreferencesFeedCache = instance ?: synchronized(this) {
            instance ?: create(context, "steam_space_feed_cache_v1").also { instance = it }
        }

        internal fun create(context: Context, preferencesName: String): SteamSpacePreferencesFeedCache {
            val appContext = context.applicationContext
            val preferences by lazy { appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE) }
            val security by lazy { SecurityManager(appContext) }
            return SteamSpacePreferencesFeedCache(
                read = { preferences.getString(SNAPSHOT_KEY, null) },
                write = { encrypted -> check(preferences.edit().putString(SNAPSHOT_KEY, encrypted).commit()) },
                encrypt = { plain -> security.encryptDataLegacyCompat(plain).also { check(security.looksLikeMonicaCiphertext(it)) } },
                decrypt = { encrypted ->
                    require(security.looksLikeMonicaCiphertext(encrypted))
                    security.decryptDataIfMonicaCiphertext(encrypted)
                }
            )
        }
    }
}

@Serializable
private data class SteamSpaceCacheEnvelope(val version: Int = 1, val feeds: List<SteamSpaceCachedFeed>)

internal object SteamSpaceFeedCacheCodec {
    const val MAX_CHARACTERS = 2 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(feeds: List<SteamSpaceCachedFeed>): String? =
        json.encodeToString(SteamSpaceCacheEnvelope.serializer(), SteamSpaceCacheEnvelope(feeds = feeds))
            .takeIf { it.length <= MAX_CHARACTERS }

    fun decode(raw: String, now: Long): List<SteamSpaceCachedFeed>? = cacheAttempt {
        if (raw.length > MAX_CHARACTERS) return@cacheAttempt null
        val envelope = json.decodeFromString(SteamSpaceCacheEnvelope.serializer(), raw)
        if (envelope.version != 1 || envelope.feeds.size > SteamSpaceMemoryFeedCache.MAX_ENTRIES) return@cacheAttempt null
        if (envelope.feeds.map { it.key }.toSet().size != envelope.feeds.size) return@cacheAttempt null
        envelope.feeds.filter { !it.key.temporary && it.isValidAt(now) }.map { it.detachedCopy() }
    }
}

private inline fun <T> cacheAttempt(block: () -> T): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}
