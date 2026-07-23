package takagi.ru.monica.steam.backup

import com.thegrizzlylabs.sardineandroid.Sardine
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.webdav.WebDavCredentials
import takagi.ru.monica.webdav.WebDavGateway
import takagi.ru.monica.webdav.WebDavUrlBuilder

data class SteamMaFileRemoteBackup(
    val name: String,
    val size: Long,
    val modifiedAt: Long
)

internal fun steamRemoteBackupLazyKey(index: Int, backup: SteamMaFileRemoteBackup): String =
    "${backup.name}-$index"

class SteamMaFileWebDavService {
    suspend fun testConnection(
        serverUrl: String,
        username: String,
        password: String
    ): String = withContext(Dispatchers.IO) {
        resolveBase(serverUrl, WebDavCredentials(username.trim(), password)).second
    }

    suspend fun upload(
        serverUrl: String,
        username: String,
        password: String,
        zipBytes: ByteArray,
        now: Date = Date()
    ): String = withContext(Dispatchers.IO) {
        require(zipBytes.isNotEmpty()) { "Empty mafile ZIP" }
        require(zipBytes.size <= SteamMaFileZipCodec.MAX_ARCHIVE_BYTES) { "mafile ZIP is too large" }
        val (client, base) = resolveBase(serverUrl, WebDavCredentials(username.trim(), password))
        val remoteDirectory = ensureBackupDirectory(client, base)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
        val fileName = "steam_mafiles_$timestamp.zip"
        client.put(
            WebDavUrlBuilder.join(remoteDirectory, fileName),
            zipBytes,
            ZIP_CONTENT_TYPE
        )
        fileName
    }

    suspend fun list(
        serverUrl: String,
        username: String,
        password: String
    ): List<SteamMaFileRemoteBackup> = withContext(Dispatchers.IO) {
        val (client, base) = resolveBase(serverUrl, WebDavCredentials(username.trim(), password))
        val directory = WebDavUrlBuilder.join(base, REMOTE_DIRECTORY)
        if (!client.exists(directory)) return@withContext emptyList()
        client.list(directory)
            .asSequence()
            .filter { !it.isDirectory && it.name.isSafeZipName() }
            .map {
                SteamMaFileRemoteBackup(
                    name = it.name,
                    size = it.contentLength ?: 0L,
                    modifiedAt = it.modified?.time ?: 0L
                )
            }
            .sortedByDescending(SteamMaFileRemoteBackup::modifiedAt)
            .toList()
    }

    suspend fun download(
        serverUrl: String,
        username: String,
        password: String,
        fileName: String
    ): ByteArray = withContext(Dispatchers.IO) {
        require(fileName.isSafeZipName()) { "Invalid mafile ZIP name" }
        val (client, base) = resolveBase(serverUrl, WebDavCredentials(username.trim(), password))
        val remotePath = WebDavUrlBuilder.join(
            WebDavUrlBuilder.join(base, REMOTE_DIRECTORY),
            fileName
        )
        client.get(remotePath).use { input ->
            input.readBytesLimited(SteamMaFileZipCodec.MAX_ARCHIVE_BYTES)
        }
    }

    private fun resolveBase(
        serverUrl: String,
        credentials: WebDavCredentials
    ): Pair<Sardine, String> {
        val candidates = WebDavUrlBuilder.candidates(serverUrl)
        require(candidates.isNotEmpty()) { "WebDAV server is required" }
        var lastError: Throwable? = null
        candidates.forEach { candidate ->
            val client = WebDavGateway.buildClient(credentials)
            try {
                client.list(candidate)
                return client to candidate
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw IOException("Cannot connect to WebDAV", lastError)
    }

    private fun ensureBackupDirectory(client: Sardine, base: String): String {
        var current = base
        REMOTE_DIRECTORY.split('/').forEach { segment ->
            current = WebDavUrlBuilder.join(current, segment)
            if (!client.exists(current)) client.createDirectory(current)
        }
        return current
    }

    private fun String.isSafeZipName(): Boolean {
        return matches(Regex("[A-Za-z0-9._-]+\\.zip", RegexOption.IGNORE_CASE))
    }

    private fun java.io.InputStream.readBytesLimited(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("mafile ZIP is too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        const val REMOTE_DIRECTORY = "steam/mafiles"
        private const val ZIP_CONTENT_TYPE = "application/zip"
    }
}
