package takagi.ru.monica.steam.store.data

import android.content.Context
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.library.SteamRegionalPrice
import takagi.ru.monica.steam.store.domain.*

class SteamStoreCache(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "steam_store_cache")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun readHome(accountId: Long?): SteamStoreHome? = read("${scope(accountId)}_home.json")

    fun writeHome(accountId: Long?, home: SteamStoreHome) =
        write("${scope(accountId)}_home.json", home)

    fun readDetail(accountId: Long?, appId: Int): SteamStoreDetail? =
        read("${scope(accountId)}_detail_$appId.json")

    fun writeDetail(accountId: Long?, detail: SteamStoreDetail) =
        write("${scope(accountId)}_detail_${detail.appId}.json", detail)

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

    private fun scope(accountId: Long?): String = accountId?.let { "v2_account_$it" } ?: "v2_guest"

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

internal fun steamWishlistCacheName(accountId: Long?): String =
    accountId?.let { "v2_account_${it}_wishlist.json" } ?: "v2_guest_wishlist.json"

internal fun steamRegionalPriceCacheName(accountId: Long?, appId: Int): String {
    val scope = accountId?.let { "v2_account_$it" } ?: "v2_guest"
    return "${scope}_regional_prices_$appId.json"
}
