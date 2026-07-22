package takagi.ru.monica.steam.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class SteamRemoteImageCache private constructor(context: Context) {
    private val directory = File(context.applicationContext.cacheDir, "steam_library_images")
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()
    private val downloadSlots = Semaphore(4)
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val pruneLock = Mutex()

    suspend fun load(url: String): Bitmap? {
        if (!isAllowedSteamImageUrl(url)) return null
        return downloadSlots.withPermit {
            withContext(Dispatchers.IO) {
                directory.mkdirs()
                val key = imageCacheKey(url)
                val lock = locks.getOrPut(key) { Mutex() }
                lock.withLock {
                    val destination = File(directory, "$key.img")
                    read(destination)?.let { bitmap ->
                        destination.setLastModified(System.currentTimeMillis())
                        return@withContext bitmap
                    }
                    destination.delete()
                    if (!download(url, destination)) return@withContext null
                    val bitmap = read(destination)
                    if (bitmap == null) destination.delete()
                    pruneLock.withLock { prune(protectedPath = destination.absolutePath) }
                    bitmap
                }
            }
        }
    }

    private fun read(file: File): Bitmap? {
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun download(url: String, destination: File): Boolean {
        val partial = File(destination.parentFile, destination.name + ".part")
        partial.delete()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Monica-Steam/1.0")
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use responseUse@ { response ->
                if (!response.isSuccessful || !isAllowedSteamImageUrl(response.request.url.toString())) {
                    return@responseUse false
                }
                val body = response.body ?: return@responseUse false
                if (body.contentLength() > MAX_SINGLE_IMAGE_BYTES) return@responseUse false
                val contentType = body.contentType()?.toString().orEmpty().lowercase()
                if (!contentType.startsWith("image/")) return@responseUse false
                var copied = 0L
                body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            copied += count
                            if (copied > MAX_SINGLE_IMAGE_BYTES) return@responseUse false
                            output.write(buffer, 0, count)
                        }
                    }
                }
                copied > 0L && partial.renameTo(destination)
            }
        }.getOrDefault(false).also { success ->
            partial.delete()
            if (!success) destination.delete()
        }
    }

    private fun prune(protectedPath: String) {
        val files = directory.listFiles().orEmpty()
            .filter { it.isFile && !it.name.endsWith(".part") }
        var total = files.sumOf(File::length)
        if (total <= MAX_CACHE_BYTES) return
        files.sortedBy(File::lastModified).forEach { file ->
            if (total <= MAX_CACHE_BYTES) return@forEach
            if (file.absolutePath == protectedPath) return@forEach
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    companion object {
        private const val MAX_SINGLE_IMAGE_BYTES = 6L * 1024L * 1024L
        private const val MAX_CACHE_BYTES = 48L * 1024L * 1024L

        @Volatile
        private var instance: SteamRemoteImageCache? = null

        fun get(context: Context): SteamRemoteImageCache {
            return instance ?: synchronized(this) {
                instance ?: SteamRemoteImageCache(context.applicationContext)
                    .also { instance = it }
            }
        }

        fun isAllowedSteamImageUrl(raw: String): Boolean {
            val url = raw.toHttpUrlOrNull() ?: return false
            if (!url.isHttps) return false
            val host = url.host.lowercase()
            return host == "steamstatic.com" || host.endsWith(".steamstatic.com") ||
                host == "steampowered.com" || host.endsWith(".steampowered.com")
        }
    }
}

private fun imageCacheKey(value: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
