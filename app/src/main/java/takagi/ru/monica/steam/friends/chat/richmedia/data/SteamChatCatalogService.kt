package takagi.ru.monica.steam.friends.chat.richmedia.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEffect
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichMediaCatalog
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatSticker
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoField
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

/**
 * Reads the account's official Steam chat catalogue.
 *
 * The Point Shop endpoint returns public definitions, not owned assets.  It
 * must never be used to decide what this account may send.  Steam's own client
 * receives CMsgClientEmoticonList, whose unified response carries owned
 * emoticons, stickers and effects in fields 1, 2 and 3 respectively.  The
 * WebAPI endpoint may omit fields 2/3 on older servers; in that case we return
 * an empty owned-only list instead of exposing locked items.
 */
class SteamChatCatalogService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun loadCatalog(account: SteamAccount): SteamChatRichMediaCatalog {
        val token = account.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required for chat media")
        val response = api.callProtobuf(
            iface = "IPlayerService",
            method = "GetEmoticonList",
            request = SteamProtoWriter(),
            accessToken = token,
            useGet = true
        )
        val fields = SteamProtoReader(response).parseAll()
        return SteamChatRichMediaCatalog(
            emoticons = parseEmoticons(fields),
            stickers = parseStickers(fields),
            effects = parseEffects(fields)
        )
    }

    fun loadEmoticons(account: SteamAccount): List<SteamChatEmoticon> =
        loadCatalog(account).emoticons

    fun loadStickers(account: SteamAccount): List<SteamChatSticker> =
        loadCatalog(account).stickers

    fun loadEffects(account: SteamAccount): List<SteamChatEffect> =
        loadCatalog(account).effects

    private fun parseEmoticons(fields: List<SteamProtoField>): List<SteamChatEmoticon> =
        nested(fields, EMOTICONS_FIELD)
            .mapNotNull { item ->
                val rawName = item[1]?.asString.orEmpty().trim()
                val name = rawName.trim(':')
                if (name.isBlank() || rawName.startsWith('^')) {
                    null
                } else {
                    SteamChatEmoticon(
                        name = name,
                        appId = item[6]?.asInt?.coerceAtLeast(0) ?: 0,
                        lastUsedAt = item[3]?.asLong?.coerceAtLeast(0L) ?: 0L,
                        useCount = item[4]?.asInt?.coerceAtLeast(0) ?: 0
                    )
                }
            }
            .distinctBy { it.name.lowercase() }
            .sortedWith(
                compareByDescending<SteamChatEmoticon> { it.lastUsedAt }
                    .thenByDescending { it.useCount }
                    .thenBy { it.name.lowercase() }
            )

    private fun parseStickers(fields: List<SteamProtoField>): List<SteamChatSticker> =
        nested(fields, STICKERS_FIELD)
            .mapNotNull { item ->
                val name = item[1]?.asString.orEmpty().trim()
                val count = item[2]?.asInt?.takeIf { it > 0 } ?: 1
                if (name.isBlank()) {
                    null
                } else {
                    SteamChatSticker(
                        name = name,
                        title = name,
                        appId = item[4]?.asInt?.coerceAtLeast(0) ?: 0,
                        count = count,
                        lastUsedAt = item[5]?.asLong?.coerceAtLeast(0L) ?: 0L,
                        useCount = item[6]?.asInt?.coerceAtLeast(0) ?: 0,
                        imageUrl = stickerImageUrl(name)
                    )
                }
            }
            .distinctBy { it.name.lowercase() }
            .sortedWith(
                compareByDescending<SteamChatSticker> { it.lastUsedAt }
                    .thenByDescending { it.useCount }
                    .thenBy { it.name.lowercase() }
            )

    private fun parseEffects(fields: List<SteamProtoField>): List<SteamChatEffect> =
        nested(fields, EFFECTS_FIELD)
            .mapNotNull { item ->
                val name = item[1]?.asString.orEmpty().trim()
                val count = item[2]?.asInt?.takeIf { it > 0 } ?: 1
                val infiniteUse = item[4]?.asBool == true
                if (name.isBlank()) {
                    null
                } else {
                    SteamChatEffect(
                        name = name,
                        appId = item[5]?.asInt?.coerceAtLeast(0) ?: 0,
                        count = count,
                        infiniteUse = infiniteUse,
                        receivedAt = item[3]?.asLong?.coerceAtLeast(0L) ?: 0L
                    )
                }
            }
            .distinctBy { it.name.lowercase() }
            .sortedWith(
                compareByDescending<SteamChatEffect> { it.receivedAt }
                    .thenBy { it.name.lowercase() }
            )

    private fun nested(fields: List<SteamProtoField>, fieldNumber: Int): List<Map<Int, SteamProtoField>> =
        fields.asSequence()
            .filter { it.number == fieldNumber && it.bytes != null }
            .mapNotNull { field ->
                runCatching { SteamProtoReader(field.bytes!!).parse() }.getOrNull()
            }
            .toList()

    private companion object {
        const val EMOTICONS_FIELD = 1
        const val STICKERS_FIELD = 2
        const val EFFECTS_FIELD = 3

        fun stickerImageUrl(name: String): String =
            "https://steamcommunity.com/economy/sticker/${encodeSteamPath(name)}"

        fun encodeSteamPath(value: String): String = URLEncoder.encode(
            value,
            StandardCharsets.UTF_8.name()
        ).replace("+", "%20")
    }
}
