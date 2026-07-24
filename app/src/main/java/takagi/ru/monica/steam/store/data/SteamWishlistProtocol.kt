package takagi.ru.monica.steam.store.data

import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.store.domain.SteamWishlistItem

internal fun buildSteamWishlistProtoRequest(
    steamId: String,
    startIndex: Int,
    pageSize: Int,
    countryCode: String?,
    language: String
): SteamProtoWriter {
    val numericSteamId = requireNotNull(steamId.toLongOrNull()?.takeIf { it > 0L }) {
        "Invalid Steam account id"
    }
    return SteamProtoWriter().apply {
        writeFixed64(1, numericSteamId)
        writeMessage(2, SteamProtoWriter().apply {
            writeString(1, language)
            countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 }?.let {
                writeString(3, it)
            }
        })
        writeMessage(3, SteamProtoWriter().apply {
            writeBool(1, true)
            writeBool(4, true)
            writeBool(10, true)
        })
        writeVarint(6, startIndex.coerceAtLeast(0).toLong())
        writeVarint(7, pageSize.coerceIn(1, MAX_WISHLIST_PAGE_SIZE).toLong())
    }
}

internal fun buildSteamWishlistMutationProtoRequest(appId: Int): SteamProtoWriter =
    SteamProtoWriter().apply {
        require(appId > 0) { "Invalid Steam app id" }
        writeVarint(1, appId.toLong())
    }

internal fun parseSteamWishlistProtoResponse(response: ByteArray): List<SteamWishlistItem> =
    SteamProtoReader(response).parseAll()
        .asSequence()
        .filter { it.number == 1 && it.bytes != null }
        .mapNotNull { field -> parseSteamWishlistItem(field.bytes ?: return@mapNotNull null) }
        .toList()

private fun parseSteamWishlistItem(bytes: ByteArray): SteamWishlistItem? {
    val wishlist = runCatching { SteamProtoReader(bytes).parse() }.getOrNull() ?: return null
    val storeBytes = wishlist[4]?.bytes
    val storeFields = storeBytes?.let {
        runCatching { SteamProtoReader(it).parseAll() }.getOrNull()
    }.orEmpty()
    val store = storeFields.associateBy { it.number }
    val appId = wishlist[1]?.asInt?.takeIf { it > 0 }
        ?: store[9]?.asInt?.takeIf { it > 0 }
        ?: return null
    val assets = store[30]?.bytes?.let {
        runCatching { SteamProtoReader(it).parse() }.getOrNull()
    }
    val assetFormat = assets?.get(1)?.asString.orEmpty()
    val assetFilename = sequenceOf(2, 4, 3)
        .mapNotNull { assets?.get(it)?.asString?.takeIf(String::isNotBlank) }
        .firstOrNull()
    val purchase = storeFields
        .firstOrNull { (it.number == 40 || it.number == 41) && it.bytes != null }
        ?.bytes
        ?.let { runCatching { SteamProtoReader(it).parse() }.getOrNull() }
    return SteamWishlistItem(
        appId = appId,
        name = store[6]?.asString?.takeIf(String::isNotBlank) ?: "App $appId",
        imageUrl = buildWishlistAssetUrl(appId, assetFormat, assetFilename),
        packageId = purchase?.get(1)?.asInt?.takeIf { it > 0 },
        discountPercent = purchase?.get(10)?.asInt?.coerceIn(0, 100) ?: 0,
        formattedInitialPrice = purchase?.get(9)?.asString.orEmpty(),
        formattedFinalPrice = purchase?.get(8)?.asString.orEmpty(),
        priority = wishlist[2]?.asInt ?: 0,
        addedAtEpochSeconds = wishlist[3]?.asLong ?: 0L
    )
}

private fun buildWishlistAssetUrl(
    appId: Int,
    format: String,
    filename: String?
): String {
    if (format.isNotBlank() && !filename.isNullOrBlank()) {
        val resolved = format.replace("\${FILENAME}", filename)
        return if (resolved.startsWith("https://")) {
            resolved
        } else {
            STORE_ASSET_BASE + resolved.trimStart('/')
        }
    }
    return "${STORE_ASSET_BASE}steam/apps/$appId/header.jpg"
}

private const val MAX_WISHLIST_PAGE_SIZE = 100
private const val STORE_ASSET_BASE =
    "https://shared.akamai.steamstatic.com/store_item_assets/"
