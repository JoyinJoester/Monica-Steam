package takagi.ru.monica.steam.friends.chat.richmedia.ui

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.monica.telegramchat.TelegramAttachmentPickerView.Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentPolicy

/** Storage boundary for the source photo/document cells. Only queries granted media
 * and the document tree explicitly selected by the user. */
internal suspend fun loadTelegramAttachmentEntries(context: Context, folder: Uri?, parents: List<Uri>): List<Entry> = withContext(Dispatchers.IO) {
    val result = mutableListOf<Entry>()
    val resolver = context.contentResolver
    for ((collection, video) in listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI to false, MediaStore.Video.Media.EXTERNAL_CONTENT_URI to true)) {
        val projection = mutableListOf("_id", "_display_name", "mime_type", "_size", "date_modified", "bucket_display_name")
        if (video) projection += "duration"
        // On Android 14 a partial grant limits this query to selected media.
        try {
            resolver.query(collection, projection.toTypedArray(), null, null, "date_modified DESC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (SteamChatAttachmentPolicy.mediaType(cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty()) == null) continue
                    result += Entry(ContentUris.withAppendedId(collection, cursor.getLong(0)).toString(), cursor.getString(1).orEmpty(),
                        cursor.getString(2).orEmpty(), cursor.getString(5).orEmpty(), cursor.getLong(3), cursor.getLong(4),
                        if (video) cursor.getLong(6) else 0, false)
                }
            }
        } catch (_: SecurityException) { /* Grant denied/revoked; SAF remains available. */ }
    }
    if (folder != null) {
        if (parents.isNotEmpty()) result += Entry(parents.last().toString(), "上一级文件夹", "", "", 0, Long.MAX_VALUE, 0, true)
        val documentId = if (DocumentsContract.isDocumentUri(context, folder)) DocumentsContract.getDocumentId(folder) else DocumentsContract.getTreeDocumentId(folder)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, documentId)
        resolver.query(children, arrayOf("document_id", "_display_name", "mime_type", "_size", "last_modified"), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val mime = cursor.getString(2).orEmpty()
                val directory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                if (directory || SteamChatAttachmentPolicy.mediaType(cursor.getString(1).orEmpty(), mime) != null) {
                    result += Entry(DocumentsContract.buildDocumentUriUsingTree(folder, cursor.getString(0)).toString(), cursor.getString(1).orEmpty(),
                        mime, "", cursor.getLong(3), cursor.getLong(4) / 1000, 0, directory)
                }
            }
        }
    }
    result.distinctBy { it.uri }
}
