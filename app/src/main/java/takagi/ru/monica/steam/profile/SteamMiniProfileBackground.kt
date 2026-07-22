package takagi.ru.monica.steam.profile

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import takagi.ru.monica.steam.network.SteamApiClient

data class SteamMiniProfileBackground(
    val mp4Url: String?,
    val webmUrl: String?
) {
    val preferredUrl: String?
        get() = mp4Url ?: webmUrl
}

data class SteamMiniProfileSummary(
    val personaName: String,
    val level: Int?,
    val avatarUrl: String?,
    val avatarFrameUrl: String?,
    val background: SteamMiniProfileBackground?,
    val currentGameName: String? = null,
    val currentGameAppId: Int? = null,
    val currentGameImageUrl: String? = null
)

data class SteamMiniProfilePreparedMedia(
    val videoFile: File,
    val posterFile: File?
)

class SteamMiniProfileBackgroundService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun fetch(steamId: String): SteamMiniProfileBackground? {
        return fetchProfile(steamId)?.background
    }

    fun fetchProfile(steamId: String): SteamMiniProfileSummary? {
        val accountId = steamIdToAccountId(steamId) ?: return null
        val payload = api.communityGetJson(
            path = "/miniprofile/$accountId/json/",
            query = emptyMap()
        )
        return parseProfile(payload)
    }

    companion object {
        private const val STEAM_ID64_OFFSET = 76561197960265728L

        fun steamIdToAccountId(steamId: String): Long? {
            val value = steamId.trim().toLongOrNull() ?: return null
            return (value - STEAM_ID64_OFFSET).takeIf { it >= 0L }
        }

        fun parse(payload: JsonObject): SteamMiniProfileBackground? {
            val raw = payload["profile_background"] as? JsonObject ?: return null
            val mp4 = raw.stringValue("video/mp4").takeIf(::isAllowedSteamMediaUrl)
            val webm = raw.stringValue("video/webm").takeIf(::isAllowedSteamMediaUrl)
            if (mp4 == null && webm == null) return null
            return SteamMiniProfileBackground(mp4Url = mp4, webmUrl = webm)
        }

        fun parseProfile(payload: JsonObject): SteamMiniProfileSummary {
            val avatarUrl = payload.stringValue("avatar_url")
                .takeIf(::isAllowedSteamMediaUrl)
            val avatarFrameUrl = payload.imageUrlValue("avatar_frame")
                ?.takeIf(::isAllowedSteamMediaUrl)
            val inGame = (payload["in_game_info"] as? JsonObject)
                ?: (payload["in_game"] as? JsonObject)
            val currentGameName = listOf(
                payload.stringValue("game_name"),
                payload.stringValue("gameextrainfo"),
                inGame?.stringValue("game_name").orEmpty(),
                inGame?.stringValue("name").orEmpty()
            ).firstOrNull { it.isNotBlank() }
            val currentGameAppId = listOf(
                payload.stringValue("gameid"),
                payload.stringValue("game_id"),
                inGame?.stringValue("gameid").orEmpty(),
                inGame?.stringValue("appid").orEmpty()
            ).firstNotNullOfOrNull { it.toIntOrNull() }
            val currentGameImageUrl = listOf(
                payload.stringValue("game_logo_url"),
                payload.stringValue("game_logo"),
                inGame?.stringValue("game_logo_url").orEmpty(),
                inGame?.stringValue("image_url").orEmpty()
            ).firstOrNull { isAllowedSteamMediaUrl(it) }
            return SteamMiniProfileSummary(
                personaName = payload.stringValue("persona_name"),
                level = payload.stringValue("level").toIntOrNull(),
                avatarUrl = avatarUrl,
                avatarFrameUrl = avatarFrameUrl,
                background = parse(payload),
                currentGameName = currentGameName,
                currentGameAppId = currentGameAppId,
                currentGameImageUrl = currentGameImageUrl
            )
        }

        fun isAllowedSteamMediaUrl(raw: String): Boolean {
            val url = raw.toHttpUrlOrNull() ?: return false
            if (!url.isHttps) return false
            val host = url.host.lowercase()
            return host == "steamstatic.com" || host.endsWith(".steamstatic.com")
        }
    }
}

class SteamMiniProfileBackgroundRepository private constructor(
    context: Context,
    private val service: SteamMiniProfileBackgroundService,
    private val mediaCache: SteamMiniProfileMediaCache
) {
    private val appContext = context.applicationContext
    private val metadata = appContext.getSharedPreferences(
        "steam_mini_profile_background_metadata",
        Context.MODE_PRIVATE
    )
    private val accountLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun load(steamId: String): SteamMiniProfilePreparedMedia? {
        if (SteamMiniProfileBackgroundService.steamIdToAccountId(steamId) == null) return null
        val lock = accountLocks.getOrPut(steamId) { Mutex() }
        return lock.withLock {
            val now = System.currentTimeMillis()
            val cached = readMetadata(steamId)
            val background = if (cached != null && now - cached.fetchedAt < METADATA_TTL_MILLIS) {
                cached.background
            } else {
                val fetched = runCatching {
                    withContext(Dispatchers.IO) { service.fetch(steamId) }
                }
                if (fetched.isSuccess) {
                    fetched.getOrNull().also { writeMetadata(steamId, it, now) }
                } else {
                    cached?.background
                }
            } ?: return@withLock null
            mediaCache.prepare(background)
        }
    }

    suspend fun clearMediaCache() {
        mediaCache.clear()
    }

    suspend fun mediaCacheSizeBytes(): Long = mediaCache.sizeBytes()

    private fun readMetadata(steamId: String): MetadataEntry? {
        val fetchedAt = metadata.getLong("${steamId}_fetched_at", 0L)
        if (fetchedAt <= 0L) return null
        val mp4 = metadata.getString("${steamId}_mp4", null)?.takeIf { it.isNotBlank() }
        val webm = metadata.getString("${steamId}_webm", null)?.takeIf { it.isNotBlank() }
        val background = if (mp4 == null && webm == null) {
            null
        } else {
            SteamMiniProfileBackground(mp4Url = mp4, webmUrl = webm)
        }
        return MetadataEntry(fetchedAt = fetchedAt, background = background)
    }

    private fun writeMetadata(
        steamId: String,
        background: SteamMiniProfileBackground?,
        fetchedAt: Long
    ) {
        metadata.edit()
            .putLong("${steamId}_fetched_at", fetchedAt)
            .putString("${steamId}_mp4", background?.mp4Url.orEmpty())
            .putString("${steamId}_webm", background?.webmUrl.orEmpty())
            .apply()
    }

    private data class MetadataEntry(
        val fetchedAt: Long,
        val background: SteamMiniProfileBackground?
    )

    companion object {
        private const val METADATA_TTL_MILLIS = 24L * 60L * 60L * 1000L
        @Volatile
        private var instance: SteamMiniProfileBackgroundRepository? = null

        fun get(context: Context): SteamMiniProfileBackgroundRepository {
            return instance ?: synchronized(this) {
                instance ?: SteamMiniProfileBackgroundRepository(
                    context = context.applicationContext,
                    service = SteamMiniProfileBackgroundService(),
                    mediaCache = SteamMiniProfileMediaCache(context.applicationContext)
                ).also { instance = it }
            }
        }
    }
}

class SteamMiniProfileMediaCache(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val maxSingleVideoBytes: Long = DEFAULT_MAX_SINGLE_VIDEO_BYTES,
    private val maxCacheBytes: Long = DEFAULT_MAX_CACHE_BYTES
) {
    private val cacheDirectory = File(
        context.applicationContext.cacheDir,
        "steam_mini_profile_backgrounds"
    )
    private val downloadSemaphore = Semaphore(2)
    private val cacheMutex = Mutex()
    private val mediaLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun prepare(
        background: SteamMiniProfileBackground
    ): SteamMiniProfilePreparedMedia? = downloadSemaphore.withPermit {
        withContext(Dispatchers.IO) {
            val mediaUrl = background.preferredUrl ?: return@withContext null
            if (!SteamMiniProfileBackgroundService.isAllowedSteamMediaUrl(mediaUrl)) {
                return@withContext null
            }
            cacheDirectory.mkdirs()
            val extension = mediaUrl.toHttpUrlOrNull()?.pathSegments?.lastOrNull()
                ?.substringAfterLast('.', "mp4")
                ?.lowercase()
                ?.takeIf { it == "mp4" || it == "webm" }
                ?: "mp4"
            val hash = sha256(mediaUrl)
            val mediaLock = mediaLocks.getOrPut(hash) { Mutex() }
            mediaLock.withLock {
            removeStalePartialFiles()
            val video = File(cacheDirectory, "video_${hash}.$extension")
            val poster = File(cacheDirectory, "poster_${hash}_768.webp")
            if (!video.isFile || video.length() !in 1..maxSingleVideoBytes) {
                video.delete()
                if (!download(mediaUrl, video)) return@withContext null
            }
            video.setLastModified(System.currentTimeMillis())
            val posterFile = when {
                poster.isFile && poster.length() > 0L -> poster.also {
                    it.setLastModified(System.currentTimeMillis())
                }
                createPoster(video, poster) -> poster
                else -> null
            }
            cacheMutex.withLock {
                pruneSteamMiniProfileCache(
                    directory = cacheDirectory,
                    maxBytes = maxCacheBytes,
                    protectedPaths = setOf(video.absolutePath, posterFile?.absolutePath.orEmpty())
                )
            }
            SteamMiniProfilePreparedMedia(videoFile = video, posterFile = posterFile)
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            cacheDirectory.listFiles().orEmpty().forEach(File::delete)
        }
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        cacheDirectory.listFiles().orEmpty().filter(File::isFile).sumOf(File::length)
    }

    private fun download(url: String, destination: File): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Monica-Android/SteamMiniProfile")
            .get()
            .build()
        val partial = File(destination.parentFile, destination.name + ".part")
        partial.delete()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body ?: return@use false
                val declaredLength = body.contentLength()
                if (declaredLength > maxSingleVideoBytes) return@use false
                val contentType = body.contentType()?.toString().orEmpty().lowercase()
                if (!contentType.startsWith("video/")) return@use false
                var copied = 0L
                body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            copied += read
                            if (copied > maxSingleVideoBytes) {
                                throw IllegalStateException("Steam mini profile video exceeds cache limit")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                copied > 0L && partial.renameTo(destination)
            }
        }.getOrDefault(false).also { success ->
            if (!success) {
                partial.delete()
                destination.delete()
            }
        }
    }

    private fun createPoster(video: File, destination: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(video.absolutePath)
            val source = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return@runCatching false
            val scaled = scalePoster(source, POSTER_MAX_WIDTH)
            destination.outputStream().buffered().use { output ->
                @Suppress("DEPRECATION")
                scaled.compress(Bitmap.CompressFormat.WEBP, 82, output)
            }.also {
                if (scaled !== source) scaled.recycle()
                source.recycle()
            }
        }.getOrDefault(false).also { success ->
            if (!success) destination.delete()
            retriever.release()
        }
    }

    private fun removeStalePartialFiles() {
        val staleBefore = System.currentTimeMillis() - PARTIAL_FILE_STALE_MILLIS
        cacheDirectory.listFiles { file -> file.name.endsWith(".part") }
            .orEmpty()
            .filter { it.lastModified() < staleBefore }
            .forEach(File::delete)
    }

    companion object {
        const val DEFAULT_MAX_SINGLE_VIDEO_BYTES = 12L * 1024L * 1024L
        const val DEFAULT_MAX_CACHE_BYTES = 64L * 1024L * 1024L
        private const val POSTER_MAX_WIDTH = 768
        private const val PARTIAL_FILE_STALE_MILLIS = 60L * 60L * 1000L
    }
}

internal fun pruneSteamMiniProfileCache(
    directory: File,
    maxBytes: Long,
    protectedPaths: Set<String> = emptySet()
): List<File> {
    val files = directory.listFiles().orEmpty()
        .filter { it.isFile && !it.name.endsWith(".part") }
    var total = files.sumOf(File::length)
    if (total <= maxBytes) return emptyList()
    val deleted = mutableListOf<File>()
    files.sortedBy(File::lastModified).forEach { file ->
        if (total <= maxBytes) return@forEach
        if (file.absolutePath in protectedPaths) return@forEach
        val length = file.length()
        if (file.delete()) {
            total -= length
            deleted += file
        }
    }
    return deleted
}

private fun scalePoster(source: Bitmap, maxWidth: Int): Bitmap {
    if (source.width <= maxWidth) return source
    val targetHeight = (source.height.toLong() * maxWidth / source.width)
        .toInt()
        .coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, maxWidth, targetHeight, true)
}

private fun sha256(value: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

private fun JsonObject.stringValue(key: String): String {
    return (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
}

private fun JsonObject.imageUrlValue(key: String): String? {
    return when (val raw: JsonElement? = this[key]) {
        is JsonPrimitive -> raw.contentOrNull
        is JsonObject -> listOf("image", "image_url", "url", "static")
            .firstNotNullOfOrNull { nestedKey -> raw.stringValue(nestedKey).takeIf(String::isNotBlank) }
        else -> null
    }
}
