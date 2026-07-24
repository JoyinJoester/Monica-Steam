package takagi.ru.monica.steam.foundation.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val STEAM_IMAGE_TIMEOUT_MS = 4_000
private const val STEAM_IMAGE_CACHE_TTL_MS = 3L * 24L * 60L * 60L * 1000L

internal suspend fun loadSteamRemoteImage(context: Context, imageUrl: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        loadSteamRemoteBytesBlocking(context, imageUrl)
            ?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }

/** Loads the original CDN bytes so animated WebP/GIF stickers are not flattened. */
internal suspend fun loadSteamRemoteBytes(context: Context, imageUrl: String): ByteArray? =
    withContext(Dispatchers.IO) { loadSteamRemoteBytesBlocking(context, imageUrl) }

private fun loadSteamRemoteBytesBlocking(context: Context, imageUrl: String): ByteArray? {
    val normalizedUrl = normalizeSteamImageUrl(imageUrl)
    if (!normalizedUrl.startsWith("https://") && !normalizedUrl.startsWith("http://")) return null

    val cacheFile = steamRemoteImageCacheFile(context, normalizedUrl)
    val cachedBytes = cacheFile.takeIf(File::isFile)?.let { runCatching { it.readBytes() }.getOrNull() }
    if (cachedBytes != null && !isSteamRemoteImageCacheExpired(cacheFile)) return cachedBytes

    return runCatching {
        downloadSteamRemoteImageBytes(normalizedUrl)?.also { bytes ->
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeBytes(bytes)
        } ?: cachedBytes
    }.getOrNull() ?: cachedBytes
}

private fun normalizeSteamImageUrl(imageUrl: String): String {
    val trimmed = imageUrl.trim()
    return when {
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("/") -> "https://steamcommunity.com$trimmed"
        else -> trimmed
    }
}

private fun downloadSteamRemoteImageBytes(imageUrl: String): ByteArray? {
    val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = STEAM_IMAGE_TIMEOUT_MS
        readTimeout = STEAM_IMAGE_TIMEOUT_MS
        requestMethod = "GET"
    }
    return try {
        connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}

private fun steamRemoteImageCacheFile(context: Context, imageUrl: String): File {
    val safeName = imageUrl.hashCode().toUInt().toString(16)
    return File(File(context.cacheDir, "steam_confirmation_images"), "$safeName.png")
}

private fun isSteamRemoteImageCacheExpired(cacheFile: File): Boolean {
    if (!cacheFile.isFile) return true
    return System.currentTimeMillis() - cacheFile.lastModified() > STEAM_IMAGE_CACHE_TTL_MS
}
