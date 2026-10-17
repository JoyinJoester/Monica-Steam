package takagi.ru.monica.steam.backup.share

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.FileNotFoundException
import java.util.concurrent.Semaphore

/** Private provider: Android's exact URI grant AND the original recipient UID are required. */
class SteamMaFileShareProvider : ContentProvider() {
    override fun onCreate() = true

    private fun token(uri: Uri): String {
        val expected = requireNotNull(context).packageName + SteamMaFileShareContract.AUTHORITY_SUFFIX
        if (uri.scheme != "content" || uri.authority != expected || uri.query != null ||
            uri.fragment != null || !uri.encodedPath.orEmpty().matches(Regex("/v1/[0-9a-f]{64}"))) {
            throw FileNotFoundException("Invalid handoff")
        }
        return uri.lastPathSegment!!
    }

    override fun getType(uri: Uri): String {
        token(uri)
        return SteamMaFileShareContract.MIME_TYPE
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val size = store.size(token(uri), Binder.getCallingUid())
            ?: throw FileNotFoundException("Handoff unavailable")
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        require(columns.all { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE })
        return MatrixCursor(columns).apply {
            addRow(columns.map { if (it == OpenableColumns.DISPLAY_NAME) SteamMaFileShareContract.FILE_NAME else size })
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        val key = token(uri)
        if (!writers.tryAcquire()) throw FileNotFoundException("Handoff busy")
        val payload = store.consume(key, Binder.getCallingUid())
        if (payload == null) {
            writers.release()
            throw FileNotFoundException("Handoff unavailable")
        }
        var pipe: Array<ParcelFileDescriptor>? = null
        try {
            val descriptors = ParcelFileDescriptor.createReliablePipe()
            pipe = descriptors
            val writer = descriptors[1]
            val fd = writer.fileDescriptor
            // Non-blocking writes keep a recipient that never reads from pinning a secret/thread.
            Os.fcntlInt(fd, OsConstants.F_SETFL, Os.fcntlInt(fd, OsConstants.F_GETFL, 0) or OsConstants.O_NONBLOCK)
            Thread({
                try {
                    val deadline = minOf(payload.expiresAt, SystemClock.elapsedRealtime() + 30_000L)
                    var offset = 0
                    while (offset < payload.bytes.size) {
                        if (SystemClock.elapsedRealtime() >= deadline) throw IllegalStateException("Expired")
                        try {
                            offset += Os.write(fd, payload.bytes, offset, payload.bytes.size - offset)
                        } catch (error: ErrnoException) {
                            when (error.errno) {
                                OsConstants.EAGAIN -> Thread.sleep(10)
                                OsConstants.EINTR -> Unit
                                else -> throw error
                            }
                        }
                    }
                    writer.close()
                } catch (_: Exception) {
                    runCatching { writer.closeWithError("Handoff unavailable") }
                } finally {
                    payload.bytes.fill(0)
                    runCatching { writer.close() }
                    writers.release()
                }
            }, "mafile-handoff").apply { isDaemon = true }.start()
            return descriptors[0]
        } catch (_: Exception) {
            pipe?.forEach { runCatching { it.close() } }
            payload.bytes.fill(0)
            writers.release()
            throw FileNotFoundException("Handoff unavailable")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        private val store = OneShotMaFileStore(SystemClock::elapsedRealtime)
        private val writers = Semaphore(4)
        private val expiry = Handler(Looper.getMainLooper())

        internal fun publish(context: Context, uid: Int, bytes: ByteArray): Uri {
            val key = store.issue(uid, bytes)
            val uri = Uri.Builder().scheme("content")
                .authority(context.packageName + SteamMaFileShareContract.AUTHORITY_SUFFIX)
                .appendPath("v1").appendPath(key).build()
            val app = context.applicationContext
            expiry.postDelayed({ revoke(app, uri) }, SteamMaFileShareContract.TTL_MS)
            return uri
        }

        internal fun revoke(context: Context, uri: Uri) {
            uri.lastPathSegment?.let(store::revoke)
            runCatching { context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }
}
