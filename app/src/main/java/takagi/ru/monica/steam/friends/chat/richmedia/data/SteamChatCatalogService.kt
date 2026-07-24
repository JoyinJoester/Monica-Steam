package takagi.ru.monica.steam.friends.chat.richmedia.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatSticker
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamChatCatalogService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun loadEmoticons(account: SteamAccount): List<SteamChatEmoticon> {
        val token = account.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required for emoticons")
        val response = api.callProtobuf(
            iface = "IPlayerService",
            method = "GetEmoticonList",
            request = SteamProtoWriter(),
            accessToken = token,
            useGet = true
        )
        return SteamProtoReader(response).parseAll()
            .asSequence()
            .filter { it.number == 1 && it.bytes != null }
            .mapNotNull { field ->
                val item = runCatching { SteamProtoReader(field.bytes!!).parse() }.getOrNull()
                    ?: return@mapNotNull null
                val name = item[1]?.asString.orEmpty().trim().trim(':')
                if (name.isBlank() || name.startsWith('^')) return@mapNotNull null
                SteamChatEmoticon(
                    name = name,
                    appId = item[6]?.asInt?.coerceAtLeast(0) ?: 0,
                    lastUsedAt = item[3]?.asLong?.coerceAtLeast(0L) ?: 0L,
                    useCount = item[4]?.asInt?.coerceAtLeast(0) ?: 0
                )
            }
            .distinctBy { it.name.lowercase() }
            .sortedWith(
                compareByDescending<SteamChatEmoticon> { it.lastUsedAt }
                    .thenByDescending { it.useCount }
                    .thenBy { it.name.lowercase() }
            )
            .toList()
    }

    fun loadStickers(language: String = "schinese"): List<SteamChatSticker> {
        val response = api.callProtobuf(
            iface = "ILoyaltyRewardsService",
            method = "QueryRewardItems",
            request = SteamProtoWriter().apply {
                writeVarint(3, COMMUNITY_ITEM_CLASS_STICKER)
                writeString(4, language)
                writeVarint(5, STICKER_PAGE_SIZE)
            },
            useGet = true
        )
        return SteamProtoReader(response).parseAll()
            .asSequence()
            .filter { it.number == 1 && it.bytes != null }
            .mapNotNull { field -> parseSticker(field.bytes!!) }
            .distinctBy { it.name.lowercase() }
            .sortedBy { it.title.lowercase() }
            .toList()
    }

    private fun parseSticker(bytes: ByteArray): SteamChatSticker? {
        val definition = runCatching { SteamProtoReader(bytes).parse() }.getOrNull() ?: return null
        if (definition[4]?.asInt != COMMUNITY_ITEM_CLASS_STICKER.toInt()) return null
        val itemData = definition[13]?.bytes?.let { nested ->
            runCatching { SteamProtoReader(nested).parse() }.getOrNull()
        } ?: return null
        val name = itemData[1]?.asString.orEmpty().trim()
        if (name.isBlank()) return null
        return SteamChatSticker(
            name = name,
            title = itemData[2]?.asString.orEmpty().trim().ifBlank { name },
            appId = definition[1]?.asInt?.coerceAtLeast(0) ?: 0,
            imageUrl = "https://steamcommunity.com/economy/stickerstatic/${encodePath(name)}"
        )
    }

    private fun encodePath(value: String): String = URLEncoder.encode(
        value,
        StandardCharsets.UTF_8.name()
    ).replace("+", "%20")

    private companion object {
        const val COMMUNITY_ITEM_CLASS_STICKER = 11L
        const val STICKER_PAGE_SIZE = 250L
    }
}
