package takagi.ru.monica.steam.friends.chat.richmedia.domain

enum class SteamChatOfficialMessageKind {
    TRADE_OFFER,
    TRADE_LINK,
    PROFILE,
    PUBLISHED_FILE,
    SCREENSHOT,
    VIDEO,
    GUIDE,
    COLLECTION,
    STORE_BUNDLE,
    STORE_PACKAGE,
    BROADCAST_INVITE,
    BROADCAST_VIEW_REQUEST,
    PLAYTEST_INVITE,
    REMOTE_PLAY_INVITE,
    GIFT,
    INVENTORY_ITEM,
    FRIEND_REQUEST,
    GROUP_INVITE,
    EVENT,
    COMMENT,
    MARKET,
    ROOM_EFFECT,
    UNKNOWN
}

data class SteamChatOfficialMessage(
    val kind: SteamChatOfficialMessageKind,
    val title: String,
    val description: String = "",
    val url: String? = null,
    val appId: Int? = null,
    val senderSteamId: String? = null,
    val tradeOfferId: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val rawBody: String,
    val caption: String = "",
    val displayName: String = "",
    val imageUrl: String? = null
)

internal object SteamChatOfficialMessageParser {
    private const val tags = "tradeofferlink|tradeoffer|trade_offer|incomingtradeoffer|broadcastinvite|broadcastviewrequest|broadcast|playtestinvite|remoteplayinvite|gift|giftreceived|giftnotification|econitem|inventoryitem|itemnotification|newitem|publishedfile|inviteurl|invite|friendinvite|friendrequest|claninvite|groupinvite|eventnotification|commentnotification|marketnotification"
    private val paired = Regex("""\[($tags)(?:\s+([^]]+))?](.*?)\[/\1\s*]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val single = Regex("""^\[($tags)(?:\s+([^]]+))?]\s*$""", RegexOption.IGNORE_CASE)
    private val urlPattern = Regex("""(?:https?|steam)://[^\s\[\]<>"']+""", RegexOption.IGNORE_CASE)
    private val linked = Regex("""\[url=(https://[^\s\[\]]+)]([^\[\]]*)\[/url]""", RegexOption.IGNORE_CASE)

    fun parseBody(body: String, messageSenderSteamId: String? = null): SteamChatOfficialMessage? {
        val matches = paired.findAll(body).take(2).toList()
        if (matches.size == 1) {
            val match = matches.single()
            val caption = body.removeRange(match.range).trim()
            // Multiple/nested rich blocks stay readable instead of losing everything after the first card.
            if ('[' in caption || '[' in match.groupValues[3]) return null
            return parse(match.groupValues[1], match.groupValues[2], match.groupValues[3], body, messageSenderSteamId)
                .copy(caption = caption)
        }
        single.matchEntire(body.trim())?.let { match ->
            return parse(match.groupValues[1], match.groupValues[2], "", body, messageSenderSteamId)
        }
        val linkMatches = linked.findAll(body).take(2).toList()
        if (linkMatches.size == 1) {
            val match = linkMatches.single()
            val rest = body.removeRange(match.range).trim()
            if ('[' in rest || urlPattern.containsMatchIn(rest)) return null
            val url = SteamChatCardLinks.action(match.groupValues[1]) ?: return null
            val kind = SteamChatCardLinks.kind(url) ?: return null
            return SteamChatOfficialMessage(
                kind = kind, title = "", url = url, rawBody = body,
                caption = listOf(rest, match.groupValues[2].takeUnless { it == url }.orEmpty())
                    .filter(String::isNotBlank).joinToString("\n"),
                tradeOfferId = if (kind == SteamChatOfficialMessageKind.TRADE_OFFER)
                    SteamChatCardLinks.uri(url)?.path?.trim('/')?.substringAfterLast('/') else null
            )
        }
        if ('[' in body || ']' in body) return null
        val links = urlPattern.findAll(body).take(2).toList()
        val match = links.singleOrNull() ?: return null
        val url = SteamChatCardLinks.action(match.value) ?: return null
        val kind = SteamChatCardLinks.kind(url) ?: return null
        return SteamChatOfficialMessage(
            kind = kind, title = "", url = url, rawBody = body,
            caption = body.removeRange(match.range).trim(),
            tradeOfferId = if (kind == SteamChatOfficialMessageKind.TRADE_OFFER)
                SteamChatCardLinks.uri(url)?.path?.trim('/')?.substringAfterLast('/') else null
        )
    }

    fun parse(
        tag: String, rawAttributes: String, innerText: String, rawBody: String,
        messageSenderSteamId: String? = null
    ): SteamChatOfficialMessage {
        val attributes = parseAttributes(rawAttributes)
        val appId = attributes["appid"]?.toIntOrNull()?.takeIf { it > 0 }
        var kind = when (tag.lowercase()) {
            "tradeofferlink" -> SteamChatOfficialMessageKind.TRADE_LINK
            "tradeoffer", "trade_offer", "incomingtradeoffer" -> SteamChatOfficialMessageKind.TRADE_OFFER
            "broadcast", "broadcastinvite" -> SteamChatOfficialMessageKind.BROADCAST_INVITE
            "broadcastviewrequest" -> SteamChatOfficialMessageKind.BROADCAST_VIEW_REQUEST
            "playtestinvite" -> SteamChatOfficialMessageKind.PLAYTEST_INVITE
            "remoteplayinvite" -> SteamChatOfficialMessageKind.REMOTE_PLAY_INVITE
            "gift", "giftreceived", "giftnotification" -> SteamChatOfficialMessageKind.GIFT
            "econitem", "inventoryitem", "itemnotification", "newitem" -> SteamChatOfficialMessageKind.INVENTORY_ITEM
            "publishedfile" -> when (attributes["file_type"]?.toIntOrNull()) {
                2 -> SteamChatOfficialMessageKind.COLLECTION
                3, 5 -> SteamChatOfficialMessageKind.SCREENSHOT
                4, 16 -> SteamChatOfficialMessageKind.VIDEO
                9 -> SteamChatOfficialMessageKind.GUIDE
                else -> SteamChatOfficialMessageKind.PUBLISHED_FILE
            }
            "friendinvite", "friendrequest" -> SteamChatOfficialMessageKind.FRIEND_REQUEST
            "invite", "inviteurl", "claninvite", "groupinvite" -> SteamChatOfficialMessageKind.GROUP_INVITE
            "eventnotification" -> SteamChatOfficialMessageKind.EVENT
            "commentnotification" -> SteamChatOfficialMessageKind.COMMENT
            "marketnotification" -> SteamChatOfficialMessageKind.MARKET
            "roomeffect" -> SteamChatOfficialMessageKind.ROOM_EFFECT
            else -> SteamChatOfficialMessageKind.UNKNOWN
        }
        val suppliedUrl = attributes["url"] ?: attributes["link"] ?: urlPattern.find(innerText)?.value
        val linkedKind = SteamChatCardLinks.kind(suppliedUrl)
        val offerId = if (kind == SteamChatOfficialMessageKind.TRADE_OFFER) {
            SteamChatCardLinks.positiveId(attributes["id"] ?: attributes["tradeofferid"] ?: attributes["trade_offer_id"])
                ?: suppliedUrl?.takeIf { linkedKind == SteamChatOfficialMessageKind.TRADE_OFFER }
                    ?.let { SteamChatCardLinks.uri(it)?.path?.trim('/')?.substringAfterLast('/') }
        } else null
        if (kind == SteamChatOfficialMessageKind.TRADE_OFFER && offerId == null && linkedKind == SteamChatOfficialMessageKind.TRADE_LINK) {
            kind = SteamChatOfficialMessageKind.TRADE_LINK
        }
        val sender = attributes["steamid"] ?: attributes["steamid64"]
            ?: SteamChatCardLinks.steamIdFromAccount(attributes["sender"])
        val generatedUrl = when {
            offerId != null -> "https://steamcommunity.com/tradeoffer/$offerId/"
            tag.equals("publishedfile", true) -> SteamChatCardLinks.positiveId(attributes["fileid"])?.let {
                "https://steamcommunity.com/sharedfiles/filedetails/?id=$it"
            }
            tag.equals("inviteurl", true) -> SteamChatCardLinks.invite(attributes["invitecode"])
            kind == SteamChatOfficialMessageKind.BROADCAST_INVITE ->
                SteamChatCardLinks.broadcast(attributes["broadcaster"] ?: sender ?: messageSenderSteamId)
            kind == SteamChatOfficialMessageKind.INVENTORY_ITEM -> {
                val owner = SteamChatCardLinks.steamIdFromAccount(attributes["owner"]) ?: sender
                SteamChatCardLinks.profile(owner)?.let { profile ->
                    val context = SteamChatCardLinks.positiveId(attributes["contextid"])
                    val asset = SteamChatCardLinks.positiveId(attributes["assetid"])
                    profile + "inventory/" + if (appId != null && context != null && asset != null) "#${appId}_${context}_$asset" else ""
                }
            }
            kind == SteamChatOfficialMessageKind.PLAYTEST_INVITE && appId != null ->
                "https://store.steampowered.com/app/$appId/"
            else -> null
        }
        // A typed trade or broadcast card must not disguise an unrelated destination.
        val allowedKinds = when (kind) {
            SteamChatOfficialMessageKind.INVENTORY_ITEM -> setOf(kind, SteamChatOfficialMessageKind.MARKET)
            SteamChatOfficialMessageKind.SCREENSHOT, SteamChatOfficialMessageKind.VIDEO,
            SteamChatOfficialMessageKind.GUIDE, SteamChatOfficialMessageKind.COLLECTION -> setOf(SteamChatOfficialMessageKind.PUBLISHED_FILE)
            SteamChatOfficialMessageKind.PLAYTEST_INVITE -> setOf(SteamChatOfficialMessageKind.EVENT)
            SteamChatOfficialMessageKind.FRIEND_REQUEST -> setOf(kind, SteamChatOfficialMessageKind.PROFILE)
            SteamChatOfficialMessageKind.COMMENT -> setOf(SteamChatOfficialMessageKind.PROFILE, SteamChatOfficialMessageKind.EVENT)
            else -> setOf(kind)
        }
        val url = suppliedUrl?.takeIf {
            linkedKind in allowedKinds && (offerId == null ||
                SteamChatCardLinks.uri(it)?.path?.trim('/')?.substringAfterLast('/') == offerId)
        }?.let(SteamChatCardLinks::action)
            ?: generatedUrl?.let(SteamChatCardLinks::action)
        val image = attributes["image"]?.takeIf { Regex("[A-Za-z0-9_-]{1,2048}").matches(it) }
            ?.let { "https://steamcommunity.com/economy/image/$it/256x256" }
            ?: SteamChatCardLinks.image(attributes["preview_url"])
        return SteamChatOfficialMessage(
            kind = kind, title = "", description = innerText.trim().ifBlank { attributes["description"].orEmpty() },
            url = url, appId = appId, senderSteamId = sender, tradeOfferId = offerId,
            attributes = attributes, rawBody = rawBody,
            displayName = attributes["title"] ?: attributes["name"] ?: attributes["market_hash_name"].orEmpty(),
            imageUrl = image
        )
    }

    private fun parseAttributes(raw: String): Map<String, String> =
        Regex("""([A-Za-z0-9_]+)=(?:"([^"]*)"|'([^']*)'|([^\s]+))""")
            .findAll(raw).associate { match ->
                match.groupValues[1].lowercase() to match.groupValues.drop(2)
                    .firstOrNull(String::isNotEmpty).orEmpty().replace("&amp;", "&")
            }
}
