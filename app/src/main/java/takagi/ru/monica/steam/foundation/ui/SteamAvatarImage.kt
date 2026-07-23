package takagi.ru.monica.steam.foundation.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount

private const val STEAM_AVATAR_TIMEOUT_MS = 4_000
private const val STEAM_AVATAR_CACHE_TTL_MS = 3L * 24L * 60L * 60L * 1000L

@Composable
internal fun SteamAvatarImage(
    account: SteamAccount,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var avatar by remember(account.steamId) { mutableStateOf<ImageBitmap?>(null) }
    val fallbackText = remember(account.displayName, account.accountName, account.steamId) {
        account.displayName
            .ifBlank { account.accountName }
            .ifBlank { account.visibleSteamId }
            .ifBlank { "S" }
            .take(1)
            .uppercase()
    }

    LaunchedEffect(account.steamId) {
        avatar = if (account.hasRealSteamId) {
            loadSteamAvatar(context, account.steamId)
        } else {
            null
        }
    }

    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 2.dp
    ) {
        val snapshot = avatar
        if (snapshot != null) {
            Image(
                bitmap = snapshot,
                contentDescription = stringResource(R.string.steam_account_avatar_description),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = fallbackText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

private suspend fun loadSteamAvatar(context: Context, steamId: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        val cacheFile = steamAvatarCacheFile(context, steamId)
        val cachedAvatar = readSteamAvatarCache(cacheFile)
        if (cachedAvatar != null && !isSteamAvatarCacheExpired(cacheFile)) {
            return@withContext cachedAvatar
        }

        val freshAvatar = runCatching {
            val avatarUrl = fetchSteamAvatarUrl(steamId) ?: return@runCatching null
            downloadSteamAvatarBytes(avatarUrl)?.also { bytes ->
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeBytes(bytes)
            }?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }.getOrNull()

        freshAvatar ?: cachedAvatar
    }

private fun fetchSteamAvatarUrl(steamId: String): String? {
    val normalizedSteamId = steamId.trim()
    if (normalizedSteamId.isBlank() || normalizedSteamId.any { !it.isDigit() }) return null

    val connection = (URL("https://steamcommunity.com/profiles/$normalizedSteamId/?xml=1")
        .openConnection() as HttpURLConnection).apply {
        connectTimeout = STEAM_AVATAR_TIMEOUT_MS
        readTimeout = STEAM_AVATAR_TIMEOUT_MS
        requestMethod = "GET"
    }
    return try {
        connection.inputStream.use { stream ->
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            val document = factory.newDocumentBuilder().parse(stream)
            document.documentElement.normalize()
            listOf("avatarFull", "avatarMedium", "avatarIcon").firstNotNullOfOrNull { tag ->
                document.getElementsByTagName(tag)
                    ?.item(0)
                    ?.textContent
                    ?.trim()
                    ?.takeIf { it.startsWith("https://") }
            }
        }
    } finally {
        connection.disconnect()
    }
}

private fun downloadSteamAvatarBytes(avatarUrl: String): ByteArray? {
    val connection = (URL(avatarUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = STEAM_AVATAR_TIMEOUT_MS
        readTimeout = STEAM_AVATAR_TIMEOUT_MS
        requestMethod = "GET"
    }
    return try {
        connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}

private fun steamAvatarCacheFile(context: Context, steamId: String): File {
    val safeSteamId = steamId.filter { it.isLetterOrDigit() }.ifBlank { "unknown" }
    return File(File(context.cacheDir, "steam_avatars"), "$safeSteamId.png")
}

private fun readSteamAvatarCache(cacheFile: File): ImageBitmap? {
    if (!cacheFile.isFile) return null
    return runCatching {
        BitmapFactory.decodeFile(cacheFile.absolutePath)?.asImageBitmap()
    }.getOrNull()
}

private fun isSteamAvatarCacheExpired(cacheFile: File): Boolean {
    if (!cacheFile.isFile) return true
    return System.currentTimeMillis() - cacheFile.lastModified() > STEAM_AVATAR_CACHE_TTL_MS
}
