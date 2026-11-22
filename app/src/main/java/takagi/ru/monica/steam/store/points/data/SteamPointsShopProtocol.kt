package takagi.ru.monica.steam.store.points.data

import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.store.points.domain.SteamPointsShopCategory
import takagi.ru.monica.steam.store.points.domain.SteamPointsShopItem
import takagi.ru.monica.steam.store.points.domain.SteamPointsShopPage

internal fun buildSteamPointsShopQuery(
    category: SteamPointsShopCategory,
    language: String,
    count: Int,
    cursor: String?,
    appId: Int? = null
): SteamProtoWriter {
    val query = SteamProtoWriter().apply {
        appId?.let { require(it > 0); writeVarint(1, it.toLong()) }
        if (category.communityItemClasses.isNotEmpty()) {
            writePackedVarints(3, category.communityItemClasses.map(Int::toLong))
        }
        writeString(4, language)
        writeVarint(5, count.coerceIn(1, 50).toLong())
        cursor?.takeIf(String::isNotBlank)?.let { writeString(6, it) }
        writeVarint(7, 1L)
        writeBool(8, true)
    }
    return SteamProtoWriter().apply { writeMessage(1, query) }
}

internal fun parseSteamPointsShopPage(
    response: ByteArray,
    category: SteamPointsShopCategory
): SteamPointsShopPage {
    val batchResponse = SteamProtoReader(response).parseAll()
        .firstOrNull { it.number == 1 }?.bytes
        ?: return SteamPointsShopPage(category)
    val batchFields = SteamProtoReader(batchResponse).parseAll()
    val eResult = batchFields.firstOrNull { it.number == 1 }?.asInt ?: 0
    require(eResult == 1) { "Steam 点数商城返回错误：$eResult" }
    val queryResponse = batchFields.firstOrNull { it.number == 2 }?.bytes
        ?: return SteamPointsShopPage(category)
    val fields = SteamProtoReader(queryResponse).parseAll()
    return SteamPointsShopPage(
        category = category,
        items = fields.filter { it.number == 1 }.mapNotNull { field ->
            field.bytes?.let(::parseRewardDefinition)
        }.distinctBy(SteamPointsShopItem::definitionId),
        totalCount = fields.firstOrNull { it.number == 2 }?.asInt ?: 0,
        nextCursor = fields.firstOrNull { it.number == 4 }?.asString?.takeIf(String::isNotBlank)
    )
}

internal fun parseSteamPointsBalance(response: ByteArray): Long? {
    val summary = SteamProtoReader(response).parse()[1]?.bytes ?: return null
    return SteamProtoReader(summary).parse()[1]?.asLong
}

private fun parseRewardDefinition(bytes: ByteArray): SteamPointsShopItem? {
    val fields = SteamProtoReader(bytes).parse()
    val appId = fields[1]?.asInt ?: return null
    val definitionId = fields[2]?.asInt ?: return null
    val itemData = fields[13]?.bytes?.let { SteamProtoReader(it).parse() }.orEmpty()
    return SteamPointsShopItem(
        appId = appId,
        definitionId = definitionId,
        type = fields[3]?.asInt ?: 0,
        communityItemClass = fields[4]?.asInt ?: 0,
        pointCost = fields[6]?.asLong ?: 0L,
        title = itemData[2]?.asString?.takeIf(String::isNotBlank)
            ?: itemData[1]?.asString.orEmpty(),
        description = itemData[3]?.asString.orEmpty(),
        smallImageUrl = buildSteamPointsMediaUrl(appId, itemData[4]?.asString),
        largeImageUrl = buildSteamPointsMediaUrl(appId, itemData[5]?.asString),
        webmUrl = buildSteamPointsMediaUrl(appId, itemData[6]?.asString),
        mp4Url = buildSteamPointsMediaUrl(appId, itemData[7]?.asString),
        animated = itemData[8]?.asBool == true,
        smallWebmUrl = buildSteamPointsMediaUrl(appId, itemData[10]?.asString),
        smallMp4Url = buildSteamPointsMediaUrl(appId, itemData[11]?.asString),
        profileThemeId = itemData[12]?.asString.orEmpty(),
        tiled = itemData[13]?.asBool == true
    )
}

internal fun buildSteamPointsMediaUrl(appId: Int, value: String?): String {
    val media = value?.trim().orEmpty()
    if (media.isBlank()) return ""
    if (media.startsWith("https://") || media.startsWith("http://")) return media
    return "$STEAM_COMMUNITY_ITEM_CDN/$appId/${media.trimStart('/')}"
}

private const val STEAM_COMMUNITY_ITEM_CDN =
    "https://shared.fastly.steamstatic.com/community_assets/images/items"
