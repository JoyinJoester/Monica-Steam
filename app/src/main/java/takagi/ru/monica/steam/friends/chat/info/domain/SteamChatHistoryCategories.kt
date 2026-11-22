package takagi.ru.monica.steam.friends.chat.info.domain

import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentKind
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContentParser
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatOfficialMessageKind

/**
 * Steam has no "shared media" index the way Telegram does, so the history page derives its
 * categories from the message body. Every message lands in exactly one bucket, which keeps the
 * tab counters adding up to the total instead of double counting text that happens to carry a link.
 */
enum class SteamChatHistoryCategory(val label: String) {
    ALL("全部"),
    TEXT("文本"),
    MEDIA("媒体"),
    FILE("文件"),
    LINK("链接"),
    INVITE("邀请"),
    STICKER("贴纸");

    /** Categories that earn a shortcut row on the chat info page; plain text lives in "全部". */
    val isShortcut: Boolean get() = this != ALL && this != TEXT

    companion object {
        /** Info-page shortcuts: the four buckets people actually browse, four rows stay compact. */
        val shortcuts: List<SteamChatHistoryCategory> = listOf(MEDIA, FILE, LINK, INVITE)

        /** Filter strip on the history page; stickers and plain text stay reachable here. */
        val filters: List<SteamChatHistoryCategory> = listOf(ALL, MEDIA, FILE, LINK, INVITE, STICKER, TEXT)
    }
}

data class SteamChatHistoryItem(
    val id: String,
    val senderName: String,
    /** Text shown in the list; attachments and invitations are flattened to a readable label. */
    val body: String,
    val timestamp: Long,
    val category: SteamChatHistoryCategory = SteamChatHistoryCategory.TEXT,
    /** Preview source for the media row thumbnail; null for anything without an image. */
    val mediaUrl: String? = null,
    /** Raw body plus sender, so BBCode commands and URLs stay searchable. */
    val searchText: String = body,
    /** Actual attachment metadata; a video URL is not an image thumbnail. */
    val attachmentKind: SteamChatAttachmentKind? = null,
    val contentUrl: String? = null,
    val spoiler: Boolean = false
) {
    fun matches(query: String): Boolean {
        val normalized = query.trim()
        if (normalized.isEmpty()) return true
        return searchText.contains(normalized, ignoreCase = true)
    }
}

object SteamChatHistoryClassifier {

    fun classify(body: String): SteamChatHistoryCategory =
        categoryOf(SteamChatRichContentParser.parse(body))

    fun categoryOf(content: SteamChatRichContent): SteamChatHistoryCategory = when (content) {
        is SteamChatRichContent.Attachment -> when (content.kind) {
            SteamChatAttachmentKind.IMAGE, SteamChatAttachmentKind.VIDEO -> SteamChatHistoryCategory.MEDIA
            SteamChatAttachmentKind.ARCHIVE -> SteamChatHistoryCategory.FILE
            SteamChatAttachmentKind.LINK -> SteamChatHistoryCategory.LINK
        }
        is SteamChatRichContent.Sticker -> SteamChatHistoryCategory.STICKER
        is SteamChatRichContent.GameInvite -> SteamChatHistoryCategory.INVITE
        is SteamChatRichContent.OfficialMessage -> when (content.message.kind) {
            SteamChatOfficialMessageKind.PROFILE,
            SteamChatOfficialMessageKind.INVENTORY_ITEM,
            SteamChatOfficialMessageKind.PUBLISHED_FILE,
            SteamChatOfficialMessageKind.GUIDE,
            SteamChatOfficialMessageKind.COLLECTION,
            SteamChatOfficialMessageKind.STORE_BUNDLE,
            SteamChatOfficialMessageKind.STORE_PACKAGE,
            SteamChatOfficialMessageKind.MARKET -> SteamChatHistoryCategory.LINK
            SteamChatOfficialMessageKind.SCREENSHOT,
            SteamChatOfficialMessageKind.VIDEO -> SteamChatHistoryCategory.MEDIA
            else -> SteamChatHistoryCategory.INVITE
        }
        is SteamChatRichContent.StoreGameShare -> SteamChatHistoryCategory.LINK
        is SteamChatRichContent.Text ->
            if (content.links.isEmpty()) SteamChatHistoryCategory.TEXT else SteamChatHistoryCategory.LINK
        is SteamChatRichContent.Action -> SteamChatHistoryCategory.TEXT
    }

    fun previewOf(content: SteamChatRichContent): String = when (content) {
        // The type prefix matters: a bare file name reads like a normal message.
        is SteamChatRichContent.Attachment -> when (content.kind) {
            SteamChatAttachmentKind.IMAGE -> attachmentPreview("图片", content.label, content.url)
            SteamChatAttachmentKind.VIDEO -> attachmentPreview("视频", content.label, content.url)
            SteamChatAttachmentKind.ARCHIVE -> attachmentPreview("文件", content.label, content.url)
            SteamChatAttachmentKind.LINK -> content.label.ifBlank { content.url }
        }
        is SteamChatRichContent.Sticker -> "贴纸 · ${content.name}"
        is SteamChatRichContent.GameInvite -> content.label.ifBlank { "游戏邀请" }
        is SteamChatRichContent.OfficialMessage ->
            content.message.displayName.ifBlank { content.message.caption }
                .ifBlank { content.message.description }.ifBlank { content.message.url.orEmpty() }.ifBlank { "Steam 通知" }
        is SteamChatRichContent.StoreGameShare -> content.label ?: content.url
        is SteamChatRichContent.Text -> content.body
        is SteamChatRichContent.Action -> content.body
    }

    fun mediaUrlOf(content: SteamChatRichContent): String? = when (content) {
        is SteamChatRichContent.Attachment ->
            when (content.kind) {
                SteamChatAttachmentKind.IMAGE -> content.url
                SteamChatAttachmentKind.VIDEO -> content.images.firstOrNull()?.url
                SteamChatAttachmentKind.LINK -> null
                SteamChatAttachmentKind.ARCHIVE -> null
            }
        is SteamChatRichContent.Sticker -> content.imageUrl
        is SteamChatRichContent.OfficialMessage -> content.message.imageUrl
        else -> null
    }

    /**
     * Steam names uploaded images after their content hash, so "图片 · 54b97d0998d15…" is
     * unreadable noise on a row that already shows the picture. Keep the name only when it looks
     * like something a person typed; otherwise the type prefix carries the meaning.
     */
    private fun attachmentPreview(prefix: String, label: String, url: String): String {
        val raw = label.ifBlank { url.substringAfterLast('/').substringBefore('?') }
        return if (raw.isBlank() || !isReadableName(raw)) prefix else "$prefix · $raw"
    }

    private fun isReadableName(name: String): Boolean {
        if (name.length > 28) return false
        if (name.length < 16) return true
        // Long and almost entirely hex/underscore: a hash, not a file name.
        val opaque = name.count { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == '_' || it == '-' }
        return opaque < name.length * 3 / 4
    }

    /**
     * Builds one search row from a raw Steam body. Parsing happens once per message while the
     * thread is mapped, not per keystroke, because [SteamChatRichContentParser] runs a dozen regexes.
     */
    fun item(
        id: String,
        senderName: String,
        body: String,
        timestamp: Long
    ): SteamChatHistoryItem {
        val content = SteamChatRichContentParser.parse(body)
        val preview = previewOf(content).trim().ifBlank { body.trim() }
        return SteamChatHistoryItem(
            id = id,
            senderName = senderName,
            body = preview,
            timestamp = timestamp,
            category = categoryOf(content),
            mediaUrl = mediaUrlOf(content),
            searchText = "$senderName\n$preview\n$body",
            attachmentKind = when (content) {
                is SteamChatRichContent.Attachment -> content.kind
                is SteamChatRichContent.OfficialMessage -> when (content.message.kind) {
                    SteamChatOfficialMessageKind.SCREENSHOT -> SteamChatAttachmentKind.IMAGE
                    SteamChatOfficialMessageKind.VIDEO -> SteamChatAttachmentKind.VIDEO
                    else -> null
                }
                else -> null
            },
            contentUrl = when (content) {
                is SteamChatRichContent.Attachment -> content.url
                is SteamChatRichContent.OfficialMessage -> content.message.url
                is SteamChatRichContent.StoreGameShare -> content.url
                is SteamChatRichContent.GameInvite -> content.url
                is SteamChatRichContent.Text -> content.links.firstOrNull()?.url
                else -> null
            },
            spoiler = (content as? SteamChatRichContent.Attachment)?.spoiler == true
        )
    }
}
