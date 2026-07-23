package takagi.ru.monica.steam.gifts.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.gifts.domain.*
import takagi.ru.monica.steam.market.SteamInventoryService
import takagi.ru.monica.steam.network.SteamApiClient

class SteamGiftService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun fetchPending(account: SteamAccount): List<SteamPendingGift> {
        requireCommunitySession(account)
        val sessionId = SteamInventoryService.newSessionId()
        val html = api.communityGetText(
            path = "/profiles/${account.steamId}/inventory/",
            cookies = SteamInventoryService.marketCookies(account, sessionId)
        )
        return SteamGiftParser.parsePending(html)
    }

    fun respond(
        account: SteamAccount,
        gift: SteamPendingGift,
        action: SteamGiftAction,
        note: String = ""
    ): SteamGiftActionResult {
        requireCommunitySession(account)
        require(!gift.requiresWeb) { "This gift must be handled on the Steam page" }
        val sessionId = SteamInventoryService.newSessionId()
        val path = when (action) {
            SteamGiftAction.ADD_TO_LIBRARY -> "/gifts/${gift.id}/acceptunpack"
            SteamGiftAction.KEEP_IN_INVENTORY -> "/gifts/${gift.id}/accept"
            SteamGiftAction.DECLINE -> "/gifts/${gift.id}/decline"
        }
        val form = linkedMapOf("sessionid" to listOf(sessionId))
        if (action == SteamGiftAction.DECLINE) {
            require(gift.senderSteamId.isNotBlank()) { "Gift sender is unavailable" }
            form["steamid_sender"] = listOf(gift.senderSteamId)
            form["note"] = listOf(note.trim())
        }
        val payload = api.communityPostJson(
            path = path,
            form = form,
            cookies = SteamInventoryService.marketCookies(account, sessionId),
            referer = "https://steamcommunity.com/profiles/${account.steamId}/inventory/#pending_gifts"
        )
        return SteamGiftActionResult(
            success = payload.successCode() == 1,
            message = payload.text("error").ifBlank { payload.text("message") }.takeIf(String::isNotBlank)
        )
    }

    private fun requireCommunitySession(account: SteamAccount) {
        require(account.hasRealSteamId) { "real Steam ID required" }
        require(
            !account.steamLoginSecure.isNullOrBlank() || !account.accessToken.isNullOrBlank()
        ) { "Steam community session required" }
    }

    private fun JsonObject.successCode(): Int {
        val primitive = this["success"] as? JsonPrimitive ?: return 0
        return primitive.intOrNull
            ?: primitive.contentOrNull?.toIntOrNull()
            ?: if (primitive.booleanOrNull == true) 1 else 0
    }

    private fun JsonObject.text(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
}

object SteamGiftParser {
    private val pendingGiftRegex = Regex(
        """id\s*=\s*[\"']pending_gift_(\d+)[\"']""",
        RegexOption.IGNORE_CASE
    )

    fun parsePending(html: String): List<SteamPendingGift> {
        val matches = pendingGiftRegex.findAll(html).toList()
        return matches.mapIndexedNotNull { index, match ->
            val id = match.groupValues[1]
            val end = matches.getOrNull(index + 1)?.range?.first ?: html.length
            val block = html.substring(match.range.first, end)
            parseGift(id, block)
        }.distinctBy(SteamPendingGift::id)
    }

    private fun parseGift(id: String, block: String): SteamPendingGift {
        val giftCardId = Regex(
            """AcceptRejectGiftCard\s*\(\s*['\"]?(\d+)""",
            RegexOption.IGNORE_CASE
        ).find(block)?.groupValues?.getOrNull(1)
        val actions = buildSet {
            if (giftCardId == null) {
                // The current Steam inventory page sometimes renders the
                // decline callback but wires accept through a delegated
                // handler that is not present in the gift block. The native
                // accept endpoints remain stable for ordinary game gifts.
                add(SteamGiftAction.ADD_TO_LIBRARY)
                add(SteamGiftAction.KEEP_IN_INVENTORY)
            }
            if (containsAcceptCall(block, id, unpack = true)) {
                add(SteamGiftAction.ADD_TO_LIBRARY)
            }
            if (containsAcceptCall(block, id, unpack = false)) {
                add(SteamGiftAction.KEEP_IN_INVENTORY)
            }
            if (block.contains("ShowDeclineGiftOptions", ignoreCase = true)) add(SteamGiftAction.DECLINE)
        }
        val senderSteamId = Regex(
            """ShowDeclineGiftOptions\s*\(\s*['\"]?$id['\"]?\s*,\s*['\"]?(\d+)""",
            RegexOption.IGNORE_CASE
        ).find(block)?.groupValues?.getOrNull(1).orEmpty()
        val requiresWeb = giftCardId != null || actions.isEmpty()
        return SteamPendingGift(
            id = id,
            senderSteamId = senderSteamId,
            senderName = classText(block, "gift_sender"),
            name = classText(block, "gift_name")
                .ifBlank { classText(block, "gift_title") }
                .ifBlank { if (giftCardId != null) "Digital gift card" else "Steam gift" },
            message = classText(block, "gift_message"),
            actions = actions,
            requiresWeb = requiresWeb,
            giftCardId = giftCardId
        )
    }

    private fun containsAcceptCall(block: String, id: String, unpack: Boolean): Boolean {
        val argument = if (unpack) "(?:true|1)" else "(?:false|0)"
        return Regex(
            """DoAcceptGift\s*\(\s*['\"]?$id['\"]?\s*,\s*$argument\b""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).containsMatchIn(block)
    }

    private fun classText(block: String, className: String): String {
        val raw = Regex(
            """<[^>]+class\s*=\s*[\"'][^\"']*\b${Regex.escape(className)}\b[^\"']*[\"'][^>]*>(.*?)</[^>]+>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(block)?.groupValues?.getOrNull(1).orEmpty()
        return decodeHtml(raw.replace(Regex("<[^>]+>"), " "))
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&nbsp;", " ", ignoreCase = true)
}
