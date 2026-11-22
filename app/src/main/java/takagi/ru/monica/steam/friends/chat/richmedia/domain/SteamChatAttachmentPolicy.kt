package takagi.ru.monica.steam.friends.chat.richmedia.domain

import java.util.Locale

/** Matches the media path used by Steam chat, not the shared uploader's ZIP path.
 * Metadata is a selection filter; the Steam server still validates file contents. */
internal object SteamChatAttachmentPolicy {
    const val MAX_FILE_BYTES = 30L * 1024L * 1024L

    private val formats = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
        "gif" to "image/gif", "webp" to "image/webp", "avif" to "image/avif",
        "webm" to "video/webm", "mpg" to "video/mpeg", "mpeg" to "video/mpeg",
        "mp4" to "video/mp4", "ogv" to "video/ogg"
    )
    private val mimeAliases = mapOf(
        "image/jpg" to "image/jpeg", "image/pjpeg" to "image/jpeg",
        "image/x-png" to "image/png", "video/x-webm" to "video/webm"
    )

    val pickerMimeTypes: Array<String> get() = (formats.values + mimeAliases.keys).distinct().toTypedArray()

    data class MediaType(val mimeType: String, val kind: SteamChatAttachmentKind)

    fun mediaType(name: String, reportedMimeType: String): MediaType? {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val expected = formats[extension] ?: return null
        val reported = reportedMimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
        val normalized = mimeAliases[reported] ?: reported
        if (normalized.isNotEmpty() && normalized != "application/octet-stream" && normalized != expected) return null
        return MediaType(expected, if (expected.startsWith("image/")) SteamChatAttachmentKind.IMAGE else SteamChatAttachmentKind.VIDEO)
    }
}
