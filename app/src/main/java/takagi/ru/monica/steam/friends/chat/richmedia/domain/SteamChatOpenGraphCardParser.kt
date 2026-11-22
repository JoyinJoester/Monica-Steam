package takagi.ru.monica.steam.friends.chat.richmedia.domain

/** Steam enriches shared links as [og url="..." img="..." title="..." desc="..."]url[/og]. */
internal object SteamChatOpenGraphCardParser {
    private val opening = Regex("""\[og(?=\s|])""", RegexOption.IGNORE_CASE)
    private val closing = Regex("""\[/og\s*]""", RegexOption.IGNORE_CASE)
    private val attribute = Regex("""\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s\]"'=]+))""")
    private val entity = Regex("""&(?:amp|quot|apos|lt|gt|#\d{1,7}|#x[0-9a-fA-F]{1,6});""")

    fun parse(body: String): SteamChatRichContent? {
        if (body.length > 16_384) return null
        val start = opening.find(body) ?: return null
        // Brackets in quoted titles or descriptions are data, not the end of the header.
        var cursor = start.range.last + 1
        var quote: Char? = null
        while (cursor < body.length) {
            val char = body[cursor]
            if (quote != null) {
                if (char == quote) quote = null
            } else when (char) {
                '"', '\'' -> quote = char
                ']' -> break
            }
            cursor++
        }
        if (cursor == body.length || quote != null) return null
        val attributes = attributes(body.substring(start.range.last + 1, cursor)) ?: return null
        val end = closing.find(body, cursor + 1) ?: return null
        if (closing.find(body, end.range.last + 1) != null) return null
        val caption = listOf(body.substring(0, start.range.first), body.substring(end.range.last + 1))
            .map(String::trim).filter(String::isNotBlank).joinToString("\n")
        // Avoid hiding additional links or rich blocks in a single-card renderer.
        if ('[' in caption || ']' in caption || Regex("(?i)(?:https?|steam)://").containsMatchIn(caption)) return null
        val url = SteamChatCardLinks.action(attributes["url"]) ?: return null
        val innerUrl = SteamChatCardLinks.action(decode(body.substring(cursor + 1, end.range.first).trim()))
        if (innerUrl != url) return null
        val title = attributes["title"].orEmpty().trim()
        val description = attributes["desc"].orEmpty().trim()
        SteamChatCardLinks.storeApp(url)?.let { appId ->
            return SteamChatRichContent.StoreGameShare(appId, title.takeIf(String::isNotBlank), url, body,
                listOf(caption, description).filter(String::isNotBlank).distinct().joinToString("\n").takeIf(String::isNotBlank))
        }
        val kind = SteamChatCardLinks.kind(url) ?: return null
        return SteamChatRichContent.OfficialMessage(SteamChatOfficialMessage(
            kind = kind, title = "", description = description, url = url,
            tradeOfferId = if (kind == SteamChatOfficialMessageKind.TRADE_OFFER)
                SteamChatCardLinks.uri(url)?.path?.trim('/')?.substringAfterLast('/') else null,
            attributes = attributes, rawBody = body, caption = caption, displayName = title,
            imageUrl = SteamChatCardLinks.image(attributes["img"])
        ))
    }

    private fun attributes(raw: String): Map<String, String>? {
        val values = linkedMapOf<String, String>()
        var cursor = 0
        while (cursor < raw.length) {
            if (raw.substring(cursor).isBlank()) break
            val match = attribute.matchAt(raw, cursor) ?: return null
            val key = match.groupValues[1].lowercase()
            if (key in values || values.size >= 32) return null
            values[key] = decode(match.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty())
            cursor = match.range.last + 1
        }
        return values
    }

    // Decode entities once; this is text metadata and is never rendered as HTML.
    private fun decode(value: String): String = entity.replace(value) { match ->
        when (val name = match.value.substring(1, match.value.length - 1)) {
            "amp" -> "&"
            "quot" -> "\""
            "apos" -> "'"
            "lt" -> "<"
            "gt" -> ">"
            else -> {
                val number = if (name.startsWith("#x")) name.substring(2).toIntOrNull(16)
                    else name.substring(1).toIntOrNull()
                if (number != null && Character.isValidCodePoint(number) && number !in 0xD800..0xDFFF && number != 0)
                    String(Character.toChars(number)) else match.value
            }
        }
    }
}
