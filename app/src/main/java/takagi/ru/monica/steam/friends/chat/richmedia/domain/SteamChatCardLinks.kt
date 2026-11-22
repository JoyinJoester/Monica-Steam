package takagi.ru.monica.steam.friends.chat.richmedia.domain

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Only recognized Steam destinations become actionable cards. Never load arbitrary embed URLs. */
internal object SteamChatCardLinks {
    private const val individualBase = 76561197960265728L
    private val digits = Regex("[0-9]{1,20}")
    private val code = Regex("[A-Za-z0-9_-]{1,128}")

    fun positiveId(value: String?): String? = value?.takeIf {
        digits.matches(it) && it.toULongOrNull()?.let { number -> number > 0uL } == true
    }

    fun accountId(steamId: String?): Long? = steamId?.toLongOrNull()
        ?.takeIf { it > individualBase && it <= individualBase + 0xffffffffL }
        ?.minus(individualBase)

    fun steamIdFromAccount(value: String?): String? = value?.toLongOrNull()
        ?.takeIf { it in 1..0xffffffffL }?.let { (individualBase + it).toString() }

    fun tradeWith(steamId: String?): String? = accountId(steamId)?.let {
        "https://steamcommunity.com/tradeoffer/new/?partner=$it"
    }

    fun profile(steamId: String?): String? = accountId(steamId)?.let {
        "https://steamcommunity.com/profiles/$steamId/"
    }

    fun broadcast(steamId: String?): String? = accountId(steamId)?.let {
        "https://steamcommunity.com/broadcast/watch/$steamId"
    }

    fun invite(codeValue: String?): String? = codeValue?.takeIf(code::matches)?.let {
        "https://steamcommunity.com/chat/invite/$it"
    }

    fun uri(value: String?): URI? = value?.trim()?.replace("&amp;", "&")?.takeIf {
        it.length <= 4096 && it.none { c -> c.isWhitespace() || c.isISOControl() || c == '\\' }
    }?.let { runCatching { URI(it) }.getOrNull() }?.takeIf {
        it.rawUserInfo == null && it.port == -1 && it.host != null &&
            !it.rawPath.orEmpty().contains(Regex("(?i)%2f|%5c|%2e")) &&
            it.path.split('/').none { part -> part == "." || part == ".." }
    }

    fun query(uri: URI, key: String): String? {
        val values = uri.rawQuery.orEmpty().split('&').mapNotNull { pair ->
            if (pair.substringBefore('=') != key) null else runCatching {
                URLDecoder.decode(pair.substringAfter('=', ""), StandardCharsets.UTF_8.name())
            }.getOrNull()
        }
        return values.singleOrNull()
    }

    fun gameInvite(value: String?): String? {
        val uri = uri(value) ?: return null
        val path = uri.path.trim('/').split('/')
        val app = path.firstOrNull()?.toIntOrNull()?.takeIf { it > 0 }
        val accepted = when {
            uri.scheme.equals("steam", true) -> when (uri.host.lowercase()) {
                "joinlobby" -> app != null && path.size in 2..3 && positiveId(path[1]) != null &&
                    (path.size == 2 || accountId(path[2]) != null)
                "rungame" -> app != null && path.size == 3 && accountId(path[1]) != null && path[2].isNotBlank()
                "run", "rungameid", "launch" -> app != null && path.size == 1
                "remoteplay" -> path.size == 2 && path[0] == "connect" && accountId(path[1]) != null &&
                    query(uri, "appid")?.toIntOrNull()?.let { it > 0 } == true
                else -> false
            }
            uri.scheme.equals("https", true) && uri.host.equals("s.team", true) ->
                path.size == 3 && path[0] == "remoteplay" && path[1] == "connect" &&
                    accountId(path[2]) != null && query(uri, "appid")?.toIntOrNull()?.let { it > 0 } == true
            else -> false
        }
        return uri.toASCIIString().takeIf { accepted }
    }

    fun kind(value: String?): SteamChatOfficialMessageKind? {
        val uri = uri(value) ?: return null
        if (!uri.scheme.equals("https", true)) return null
        val path = uri.path.trim('/').split('/')
        if (uri.host.equals("s.team", true)) {
            return when {
                path.size == 2 && path[0] == "chat" && code.matches(path[1]) -> SteamChatOfficialMessageKind.GROUP_INVITE
                path.size in 2..3 && path[0] == "p" && path.drop(1).all(code::matches) -> SteamChatOfficialMessageKind.FRIEND_REQUEST
                gameInvite(value) != null -> SteamChatOfficialMessageKind.REMOTE_PLAY_INVITE
                else -> null
            }
        }
        if (uri.host.equals("store.steampowered.com", true)) {
            return when {
                path.size >= 2 && positiveId(path[1]) != null && path[0] == "bundle" -> SteamChatOfficialMessageKind.STORE_BUNDLE
                path.size >= 2 && positiveId(path[1]) != null && path[0] == "sub" -> SteamChatOfficialMessageKind.STORE_PACKAGE
                path.firstOrNull() == "news" -> SteamChatOfficialMessageKind.EVENT
                else -> null
            }
        }
        if (!uri.host.equals("steamcommunity.com", true)) return null
        return when {
            path == listOf("my", "inventory") && uri.fragment == "pending_gifts" -> SteamChatOfficialMessageKind.GIFT
            path.size == 2 && path[0] == "tradeoffer" && path[1] == "new" &&
                query(uri, "partner")?.toLongOrNull()?.let { it in 1..0xffffffffL } == true -> SteamChatOfficialMessageKind.TRADE_LINK
            path.size == 2 && path[0] == "tradeoffer" && positiveId(path[1]) != null -> SteamChatOfficialMessageKind.TRADE_OFFER
            path.size == 3 && path.take(2) == listOf("broadcast", "watch") && accountId(path[2]) != null -> SteamChatOfficialMessageKind.BROADCAST_INVITE
            path.size == 3 && path.take(2) == listOf("chat", "invite") && code.matches(path[2]) -> SteamChatOfficialMessageKind.GROUP_INVITE
            path.size == 2 && path[0] in setOf("sharedfiles", "workshop") && path[1] == "filedetails" &&
                positiveId(query(uri, "id")) != null -> SteamChatOfficialMessageKind.PUBLISHED_FILE
            path.size >= 4 && path.take(2) == listOf("market", "listings") && positiveId(path[2]) != null -> SteamChatOfficialMessageKind.MARKET
            path.size >= 2 && path[0] in setOf("profiles", "id") &&
                (if (path[0] == "profiles") accountId(path[1]) != null else code.matches(path[1])) -> when {
                    path.size == 2 -> SteamChatOfficialMessageKind.PROFILE
                    path.getOrNull(2) == "gifts" -> SteamChatOfficialMessageKind.GIFT
                    path.getOrNull(2) == "inventory" -> SteamChatOfficialMessageKind.INVENTORY_ITEM
                    path.getOrNull(2) in setOf("screenshots", "videos", "workshop", "myworkshopfiles") -> SteamChatOfficialMessageKind.PUBLISHED_FILE
                    else -> null
                }
            path.size >= 2 && path[0] == "groups" && code.matches(path[1]) ->
                if (path.getOrNull(2) in setOf("events", "announcements")) SteamChatOfficialMessageKind.EVENT else SteamChatOfficialMessageKind.GROUP_INVITE
            path.size >= 2 && path[0] == "gid" && positiveId(path[1]) != null -> SteamChatOfficialMessageKind.GROUP_INVITE
            path.size >= 2 && path[0] == "app" && positiveId(path[1]) != null -> SteamChatOfficialMessageKind.EVENT
            else -> null
        }
    }

    fun storeApp(value: String?): Int? = uri(value)?.takeIf {
        it.scheme.equals("https", true) && it.host.equals("store.steampowered.com", true)
    }?.path?.trim('/')?.split('/')?.takeIf { it.size >= 2 && it[0] == "app" }
        ?.get(1)?.toIntOrNull()?.takeIf { it > 0 }

    fun action(value: String?): String? = uri(value)?.toASCIIString()?.takeIf {
        kind(it) != null || gameInvite(it) != null || storeApp(it) != null
    }

    fun image(value: String?): String? = uri(value)?.takeIf {
        it.scheme.equals("https", true) && it.host.lowercase() in setOf(
            "steamcommunity.com", "images.steamusercontent.com", "images.akamai.steamusercontent.com",
            "steamuserimages-a.akamaihd.net", "shared.akamai.steamstatic.com", "community.akamai.steamstatic.com",
            "community.steamstatic.com"
        ) && (!it.host.equals("steamcommunity.com", true) || it.path.startsWith("/economy/image/"))
    }?.toASCIIString()
}
