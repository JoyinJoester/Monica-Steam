package takagi.ru.monica.steam.friends.chat.richmedia.domain

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SteamChatEmoticon(
    val name: String,
    val appId: Int = 0,
    val lastUsedAt: Long = 0L,
    val useCount: Int = 0
) {
    val messageCode: String get() = ":$name:"
    val imageUrl: String get() =
        "https://steamcommunity.com/economy/emoticon/${encodeSteamChatPath(name)}"
}

data class SteamChatSticker(
    val name: String,
    val title: String,
    val appId: Int = 0,
    val imageUrl: String = "https://steamcommunity.com/economy/stickerstatic/${encodeSteamChatPath(name)}"
) {
    val messageCode: String get() = "/sticker $name"
}

enum class SteamChatAttachmentKind {
    IMAGE,
    VIDEO,
    ARCHIVE,
    LINK
}

data class SteamChatPendingAttachment(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val kind: SteamChatAttachmentKind,
    val width: Int = 0,
    val height: Int = 0
)

sealed interface SteamChatRichContent {
    data class Text(val body: String) : SteamChatRichContent

    data class Sticker(val name: String) : SteamChatRichContent {
        val imageUrl: String get() =
            "https://steamcommunity.com/economy/stickerstatic/${encodeSteamChatPath(name)}"
    }

    data class Attachment(
        val url: String,
        val label: String,
        val kind: SteamChatAttachmentKind
    ) : SteamChatRichContent
}

object SteamChatRichContentParser {
    private val stickerPattern = Regex("^/sticker\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)
    private val imagePattern = Regex("\\[img(?:=[^]]+)?](https?://[^\\[]+)\\[/img]", RegexOption.IGNORE_CASE)
    private val videoPattern = Regex("\\[video(?:=[^]]+)?](https?://[^\\[]+)\\[/video]", RegexOption.IGNORE_CASE)
    private val urlPattern = Regex(
        "\\[url=(https?://[^]]+)]([^\\[]+)\\[/url]",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun parse(body: String): SteamChatRichContent {
        stickerPattern.matchEntire(body.trim())?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { return SteamChatRichContent.Sticker(it) }

        imagePattern.find(body)?.let { match ->
            val url = match.groupValues[1].trim()
            return SteamChatRichContent.Attachment(url, fileLabel(url), SteamChatAttachmentKind.IMAGE)
        }
        videoPattern.find(body)?.let { match ->
            val url = match.groupValues[1].trim()
            return SteamChatRichContent.Attachment(url, fileLabel(url), SteamChatAttachmentKind.VIDEO)
        }
        urlPattern.find(body)?.let { match ->
            val url = match.groupValues[1].trim()
            val label = match.groupValues[2].trim().ifBlank { fileLabel(url) }
            return SteamChatRichContent.Attachment(url, label, attachmentKind(url))
        }
        return SteamChatRichContent.Text(body)
    }

    private fun fileLabel(url: String): String = runCatching {
        URLDecoder.decode(
            url.substringBefore('?').substringAfterLast('/').ifBlank { "Steam attachment" },
            StandardCharsets.UTF_8.name()
        )
    }.getOrDefault("Steam attachment")

    private fun attachmentKind(url: String): SteamChatAttachmentKind =
        when (url.substringBefore('?').substringAfterLast('.').lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "avif" -> SteamChatAttachmentKind.IMAGE
            "webm", "mpg", "mpeg", "mp4", "ogv" -> SteamChatAttachmentKind.VIDEO
            "zip" -> SteamChatAttachmentKind.ARCHIVE
            else -> SteamChatAttachmentKind.LINK
        }
}

object SteamChatUnicodeEmojiCatalog {
    val items: List<String> = listOf(
        "😀", "😃", "😄", "😁", "😆", "🥹", "😂", "🤣",
        "😊", "🙂", "🙃", "😉", "😍", "🥰", "😘", "😎",
        "🤔", "🫡", "🤨", "😐", "😑", "😶", "🙄", "😬",
        "😴", "🥳", "😢", "😭", "😤", "😡", "🤯", "😱",
        "👍", "👎", "👌", "✌️", "🤝", "👏", "🙌", "🙏",
        "💪", "👀", "❤️", "🧡", "💛", "💚", "💙", "💜",
        "🔥", "✨", "🎉", "🎮", "🏆", "🚀", "💯", "✅"
    )
}

private fun encodeSteamChatPath(value: String): String = URLEncoder.encode(
    value,
    StandardCharsets.UTF_8.name()
).replace("+", "%20")
