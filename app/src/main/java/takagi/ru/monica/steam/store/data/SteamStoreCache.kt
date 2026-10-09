package takagi.ru.monica.steam.store.data

import android.content.Context
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.library.SteamRegionalPrice
import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.store.filters.domain.SteamStoreFilterMetadata
import takagi.ru.monica.steam.store.filters.domain.SteamStoreFilterSelection

class SteamStoreCache(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "steam_store_cache")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun readHome(accountId: Long?): SteamStoreHome? = read(steamStoreHomeCacheName(accountId))

    fun writeHome(accountId: Long?, home: SteamStoreHome) =
        write(steamStoreHomeCacheName(accountId), home)

    fun readCatalog(
        accountId: Long?,
        filter: SteamStoreBrowseFilter,
        filters: SteamStoreFilterSelection = SteamStoreFilterSelection()
    ): SteamStoreCatalogPage? = read(catalogCacheName(accountId, filter, filters))

    fun writeCatalog(
        accountId: Long?,
        page: SteamStoreCatalogPage,
        filters: SteamStoreFilterSelection = SteamStoreFilterSelection()
    ) = write(catalogCacheName(accountId, page.filter, filters), page)

    fun readFilterMetadata(accountId: Long?): SteamStoreFilterMetadata? =
        read("${scope(accountId)}_filter_metadata.json")

    fun writeFilterMetadata(accountId: Long?, metadata: SteamStoreFilterMetadata) =
        write("${scope(accountId)}_filter_metadata.json", metadata)

    fun readDetail(accountId: Long?, appId: Int): SteamStoreDetail? =
        read(steamStoreDetailCacheName(accountId, appId))

    fun writeDetail(accountId: Long?, detail: SteamStoreDetail) =
        write(steamStoreDetailCacheName(accountId, detail.appId), detail)

    fun readCart(accountId: Long?): List<SteamCartItem> =
        read<List<SteamCartItem>>("${scope(accountId)}_cart.json").orEmpty()

    fun writeCart(accountId: Long?, items: List<SteamCartItem>) =
        write("${scope(accountId)}_cart.json", items)

    fun readWishlist(accountId: Long?): SteamWishlistSnapshot? =
        read(steamWishlistCacheName(accountId))

    fun writeWishlist(accountId: Long?, snapshot: SteamWishlistSnapshot) =
        write(steamWishlistCacheName(accountId), snapshot)

    fun readRegionalPrices(accountId: Long?, appId: Int): List<SteamRegionalPrice> =
        read<List<SteamRegionalPrice>>(steamRegionalPriceCacheName(accountId, appId)).orEmpty()

    fun writeRegionalPrices(
        accountId: Long?,
        appId: Int,
        prices: List<SteamRegionalPrice>
    ) = write(steamRegionalPriceCacheName(accountId, appId), prices)

    private fun scope(accountId: Long?): String = storeCacheScope(
        accountId = accountId,
        version = STORE_ACCOUNT_CACHE_VERSION
    )

    private inline fun <reified T> read(name: String): T? = runCatching {
        val file = File(directory, name)
        if (!file.isFile) return null
        json.decodeFromString<T>(file.readText())
    }.getOrNull()

    private inline fun <reified T> write(name: String, value: T) {
        runCatching {
            directory.mkdirs()
            val target = File(directory, name)
            val pending = File(directory, "$name.tmp")
            pending.writeText(json.encodeToString(value))
            if (!pending.renameTo(target)) {
                target.writeText(pending.readText())
                pending.delete()
            }
        }
    }
}

internal fun catalogCacheName(
    accountId: Long?,
    filter: SteamStoreBrowseFilter,
    filters: SteamStoreFilterSelection
): String {
    val scope = storeContentCacheScope(accountId)
    val base = "${scope}_catalog_${filter.name.lowercase()}"
    val filterKey = filters.cacheKey()
    return if (filterKey == "default") "$base.json" else "${base}_$filterKey.json"
}

internal fun steamStoreHomeCacheName(accountId: Long?): String =
    "${storeContentCacheScope(accountId)}_home_games_v2.json"

internal fun steamStoreDetailCacheName(accountId: Long?, appId: Int): String =
    "${storeContentCacheScope(accountId)}_detail_$appId.json"

internal fun steamWishlistCacheName(accountId: Long?): String =
    "${storeCacheScope(accountId, STORE_ACCOUNT_CACHE_VERSION)}_wishlist.json"

internal fun steamRegionalPriceCacheName(accountId: Long?, appId: Int): String {
    val scope = storeCacheScope(accountId, STORE_ACCOUNT_CACHE_VERSION)
    return "${scope}_regional_prices_$appId.json"
}

private fun storeContentCacheScope(accountId: Long?): String =
    storeCacheScope(accountId, STORE_CONTENT_CACHE_VERSION)

private fun storeCacheScope(accountId: Long?, version: String): String =
    accountId?.let { "${version}_account_$it" } ?: "${version}_guest"

private const val STORE_CONTENT_CACHE_VERSION = "v3"
private const val STORE_ACCOUNT_CACHE_VERSION = "v2"
