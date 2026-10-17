package takagi.ru.monica.steam.friends.chat.richmedia.data

import android.content.Context
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichMediaCatalog

interface SteamChatCatalogCache {
    /** Memory only: safe to use while selecting the account on the UI thread. */
    fun peek(account: SteamAccount): SteamChatRichMediaCatalog? = null
    fun load(account: SteamAccount): SteamChatRichMediaCatalog?
    fun save(account: SteamAccount, catalog: SteamChatRichMediaCatalog)
}

/** Owned asset metadata only; credentials and conversations are never serialized here. */
class SteamChatCatalogPreferencesCache internal constructor(
    private val read: (String) -> String?,
    private val write: (String, String) -> Unit
) : SteamChatCatalogCache {
    private val memory = LinkedHashMap<String, SteamChatRichMediaCatalog>(8, 0.75f, true)

    override fun peek(account: SteamAccount): SteamChatRichMediaCatalog? =
        synchronized(memory) { memory[cacheKey(account)] }

    override fun load(account: SteamAccount): SteamChatRichMediaCatalog? {
        peek(account)?.let { return it }
        if (account.isTemporary || !account.hasRealSteamId) return null
        val key = cacheKey(account)
        val cached = runCatching { read(key)?.let(SteamChatCatalogCacheCodec::decode) }
            .getOrNull() ?: return null
        // A concurrent refresh may have published a newer catalogue during the disk read.
        return synchronized(memory) {
            memory[key] ?: cached.also { remember(key, it) }
        }
    }

    override fun save(account: SteamAccount, catalog: SteamChatRichMediaCatalog) {
        if (!account.hasRealSteamId) return
        val key = cacheKey(account)
        val encoded = SteamChatCatalogCacheCodec.encode(catalog) ?: return
        synchronized(memory) { remember(key, catalog) }
        if (!account.isTemporary) write(key, encoded)
    }

    private fun remember(key: String, catalog: SteamChatRichMediaCatalog) {
        memory[key] = catalog
        while (memory.size > MAX_MEMORY_ACCOUNTS) {
            val oldest = memory.entries.iterator()
            oldest.next()
            oldest.remove()
        }
    }

    private fun cacheKey(account: SteamAccount): String {
        val identity = if (account.isTemporary) {
            "temporary|${account.id}|${account.steamId}"
        } else {
            "saved|${account.steamId}"
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    companion object {
        private const val MAX_MEMORY_ACCOUNTS = 8
        @Volatile private var instance: SteamChatCatalogPreferencesCache? = null

        fun get(context: Context): SteamChatCatalogPreferencesCache =
            instance ?: synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext
                    // Initializing the ViewModel must not wait for SharedPreferences disk IO.
                    val preferences by lazy {
                        appContext.getSharedPreferences("steam_chat_catalog_cache_v1", Context.MODE_PRIVATE)
                    }
                    SteamChatCatalogPreferencesCache(
                        read = { key -> preferences.getString(key, null) },
                        write = { key, value -> preferences.edit().putString(key, value).apply() }
                    )
                }.also { instance = it }
            }
    }
}

internal object SteamChatCatalogCacheCodec {
    private const val MAX_CHARACTERS = 2 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(catalog: SteamChatRichMediaCatalog): String? =
        json.encodeToString(SteamChatRichMediaCatalog.serializer(), catalog)
            .takeIf { it.length <= MAX_CHARACTERS }

    fun decode(raw: String): SteamChatRichMediaCatalog? {
        if (raw.length > MAX_CHARACTERS) return null
        return runCatching {
            json.decodeFromString(SteamChatRichMediaCatalog.serializer(), raw)
        }.getOrNull()
    }
}
