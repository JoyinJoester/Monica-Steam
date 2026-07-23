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
        val normalizedUrl = normalizeSteamImageUrl(imageUrl)
        if (!normalizedUrl.startsWith("https://") && !normalizedUrl.startsWith("http://")) {
            return@withContext null
        }

        val cacheFile = steamRemoteImageCacheFile(context, normalizedUrl)
        val cachedImage = readSteamRemoteImageCache(cacheFile)
        if (cachedImage != null && !isSteamRemoteImageCacheExpired(cacheFile)) {
            return@withContext cachedImage
        }

        val freshImage = runCatching {
            downloadSteamRemoteImageBytes(normalizedUrl)?.also { bytes ->
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeBytes(bytes)
            }?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }.getOrNull()

        freshImage ?: cachedImage
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

private fun readSteamRemoteImageCache(cacheFile: File): ImageBitmap? {
    if (!cacheFile.isFile) return null
    return runCatching {
        BitmapFactory.decodeFile(cacheFile.absolutePath)?.asImageBitmap()
    }.getOrNull()
}

private fun isSteamRemoteImageCacheExpired(cacheFile: File): Boolean {
    if (!cacheFile.isFile) return true
    return System.currentTimeMillis() - cacheFile.lastModified() > STEAM_IMAGE_CACHE_TTL_MS
}
