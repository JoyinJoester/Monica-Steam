package takagi.ru.monica.steam.friends.chat.richmedia.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.net.InetAddress
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.BufferedSink
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentKind
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatPendingAttachment

class SteamChatAttachmentUploader(
    context: Context,
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val resolver = context.applicationContext.contentResolver

    fun inspect(uri: Uri): SteamChatPendingAttachment {
        val metadata = queryMetadata(uri)
        val reportedMimeType = resolver.getType(uri).orEmpty()
        val mimeType = if (reportedMimeType.isBlank() ||
            reportedMimeType == "application/octet-stream"
        ) {
            mimeTypeFromName(metadata.first)
        } else {
            reportedMimeType
        }
        val kind = attachmentKind(metadata.first, mimeType)
        require(kind != null) { "Unsupported Steam chat attachment type" }
        val descriptorSize = resolver.openAssetFileDescriptor(uri, "r")
            ?.use { it.length }
            ?.takeIf { it >= 0L }
        val size = metadata.second.takeIf { it >= 0L }
            ?: descriptorSize
            ?: countBytes(uri)
        require(size in 1..MAX_FILE_BYTES) { "Steam chat attachments must be 30 MB or smaller" }
        val dimensions = if (kind == SteamChatAttachmentKind.IMAGE) imageBounds(uri) else 0 to 0
        return SteamChatPendingAttachment(
            uri = uri.toString(),
            displayName = metadata.first.ifBlank { "Steam attachment" },
            mimeType = mimeType,
            sizeBytes = size,
            kind = kind,
            width = dimensions.first,
            height = dimensions.second
        )
    }

    fun upload(
        account: SteamAccount,
        partnerSteamId: String,
        attachment: SteamChatPendingAttachment,
        spoiler: Boolean,
        onProgress: (Float) -> Unit = {}
    ) {
        val secure = account.steamLoginSecure?.takeIf(String::isNotBlank)
            ?: throw SteamChatUploadException("Steam community session required for attachments")
        require(partnerSteamId.matches(Regex("7656119\\d{10}"))) { "Valid Steam friend ID required" }
        val uri = Uri.parse(attachment.uri)
        val sessionId = UUID.randomUUID().toString().replace("-", "")
        val uploadName = "${System.nanoTime()}_${sanitizeFilename(attachment.displayName)}"
        val sha = UUID.randomUUID().toString().replace("-", "") +
            UUID.randomUUID().toString().replace("-", "").take(8)
        val begin = beginUpload(
            secure = secure,
            sessionId = sessionId,
            attachment = attachment,
            uploadName = uploadName,
            sha = sha
        )
        try {
            putToCloud(begin, uri, attachment, onProgress)
        } catch (error: Throwable) {
            runCatching {
                commitUpload(
                    secure = secure,
                    sessionId = sessionId,
                    partnerSteamId = partnerSteamId,
                    attachment = attachment,
                    uploadName = uploadName,
                    sha = sha,
                    begin = begin,
                    spoiler = spoiler,
                    success = false
                )
            }
            throw error
        }
        commitUpload(
            secure = secure,
            sessionId = sessionId,
            partnerSteamId = partnerSteamId,
            attachment = attachment,
            uploadName = uploadName,
            sha = sha,
            begin = begin,
            spoiler = spoiler,
            success = true
        )
        onProgress(1f)
    }

    private fun beginUpload(
        secure: String,
        sessionId: String,
        attachment: SteamChatPendingAttachment,
        uploadName: String,
        sha: String
    ): BeginUpload {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("sessionid", sessionId)
            .addFormDataPart("l", "schinese")
            .addFormDataPart("file_size", attachment.sizeBytes.toString())
            .addFormDataPart("file_name", uploadName)
            .addFormDataPart("file_sha", sha)
            .addFormDataPart("file_image_width", attachment.width.toString())
            .addFormDataPart("file_image_height", attachment.height.toString())
            .addFormDataPart("file_type", attachment.mimeType)
            .build()
        val request = Request.Builder()
            .url("$BEGIN_URL?l=schinese")
            .headers(communityHeaders(secure, sessionId))
            .post(body)
            .build()
        return client.newCall(request).execute().use { response ->
            val payload = response.requireJson("begin Steam chat attachment")
            val result = payload["result"] as? JsonObject
                ?: throw SteamChatUploadException("Steam did not issue an attachment upload URL")
            val host = result.string("url_host")
            val path = result.string("url_path")
            val useHttps = result["use_https"]?.jsonPrimitive?.let { primitive ->
                primitive.contentOrNull == "true" || primitive.intOrNull == 1
            } != false
            if (!useHttps) throw SteamChatUploadException("Steam returned an insecure upload URL")
            val cloudUrl = "https://$host$path".toHttpUrlOrNull()
                ?: throw SteamChatUploadException("Steam returned an invalid upload URL")
            requireSafeCloudHost(cloudUrl.host)
            BeginUpload(
                cloudUrl = cloudUrl.toString(),
                requestHeaders = (result["request_headers"] as? JsonArray).orEmpty().mapNotNull { value ->
                    val header = value as? JsonObject ?: return@mapNotNull null
                    val name = header.string("name").trim()
                    val content = header.string("value")
                    if (name.isBlank() || name.equals("Host", true) ||
                        name.equals("Content-Length", true) || name.equals("Cookie", true) ||
                        name.equals("Authorization", true)
                    ) null else name to content
                },
                ugcId = result["ugcid"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                timestamp = payload["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L,
                hmac = payload.string("hmac")
            ).also {
                if (it.ugcId.isBlank() || it.timestamp <= 0L || it.hmac.isBlank()) {
                    throw SteamChatUploadException("Steam returned incomplete upload credentials")
                }
            }
        }
    }

    private fun putToCloud(
        begin: BeginUpload,
        uri: Uri,
        attachment: SteamChatPendingAttachment,
        onProgress: (Float) -> Unit
    ) {
        val body = ContentUriRequestBody(resolver, uri, attachment, onProgress)
        val request = Request.Builder().url(begin.cloudUrl).apply {
            begin.requestHeaders.forEach { (name, value) -> header(name, value) }
        }.put(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SteamChatUploadException("Steam cloud upload failed (${response.code})")
            }
        }
    }

    private fun commitUpload(
        secure: String,
        sessionId: String,
        partnerSteamId: String,
        attachment: SteamChatPendingAttachment,
        uploadName: String,
        sha: String,
        begin: BeginUpload,
        spoiler: Boolean,
        success: Boolean
    ) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("sessionid", sessionId)
            .addFormDataPart("l", "schinese")
            .addFormDataPart("file_name", uploadName)
            .addFormDataPart("file_sha", sha)
            .addFormDataPart("success", if (success) "1" else "0")
            .addFormDataPart("ugcid", begin.ugcId)
            .addFormDataPart("file_type", attachment.mimeType)
            .addFormDataPart("file_image_width", attachment.width.toString())
            .addFormDataPart("file_image_height", attachment.height.toString())
            .addFormDataPart("timestamp", begin.timestamp.toString())
            .addFormDataPart("hmac", begin.hmac)
            .addFormDataPart("friend_steamid", partnerSteamId)
            .addFormDataPart("spoiler", if (spoiler) "1" else "0")
            .build()
        val request = Request.Builder()
            .url(COMMIT_URL)
            .headers(communityHeaders(secure, sessionId))
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && success) {
                throw SteamChatUploadException("Steam could not commit the attachment (${response.code})")
            }
        }
    }

    private fun queryMetadata(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment.orEmpty().substringAfterLast('/')
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex).orEmpty().ifBlank { name }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        return name to size
    }

    private fun imageBounds(uri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        return options.outWidth.coerceAtLeast(0) to options.outHeight.coerceAtLeast(0)
    }

    private fun countBytes(uri: Uri): Long = resolver.openInputStream(uri)?.use { input ->
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_FILE_BYTES) break
        }
        total
    } ?: throw SteamChatUploadException("Selected attachment is no longer available")

    private fun attachmentKind(name: String, mimeType: String): SteamChatAttachmentKind? {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            extension in IMAGE_EXTENSIONS && mimeType.startsWith("image/") -> SteamChatAttachmentKind.IMAGE
            extension in VIDEO_EXTENSIONS && mimeType.startsWith("video/") -> SteamChatAttachmentKind.VIDEO
            extension == "zip" -> SteamChatAttachmentKind.ARCHIVE
            else -> null
        }
    }

    private fun mimeTypeFromName(name: String): String = when (
        name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    ) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "avif" -> "image/avif"
        "webm" -> "video/webm"
        "mp4" -> "video/mp4"
        "mpg", "mpeg" -> "video/mpeg"
        "ogv" -> "video/ogg"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }

    private fun sanitizeFilename(name: String): String = name
        .replace(Regex("[\\r\\n\\u0000-\\u001f\\u007f/\\\\]"), "_")
        .take(180)
        .ifBlank { "attachment" }

    private fun requireSafeCloudHost(host: String) {
        val normalized = host.lowercase(Locale.ROOT)
        if (normalized == "localhost" || normalized.endsWith(".localhost")) {
            throw SteamChatUploadException("Steam returned a blocked upload host")
        }
        val literal = runCatching { InetAddress.getByName(normalized) }.getOrNull()
        if (literal != null && (literal.isAnyLocalAddress || literal.isLoopbackAddress ||
                literal.isLinkLocalAddress || literal.isSiteLocalAddress)
        ) {
            throw SteamChatUploadException("Steam returned a private upload host")
        }
    }

    private fun communityHeaders(secure: String, sessionId: String) = okhttp3.Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Steam Chat")
        .add("Accept", "application/json, text/plain, */*")
        .add("Origin", "https://steamcommunity.com")
        .add("Referer", "https://steamcommunity.com/chat/")
        .add("X-Requested-With", "com.valvesoftware.android.steam.community")
        .add("Cookie", "steamLoginSecure=$secure; sessionid=$sessionId")
        .build()

    private fun Response.requireJson(operation: String): JsonObject {
        val raw = body?.string().orEmpty()
        if (!isSuccessful) throw SteamChatUploadException("Unable to $operation (${code})")
        return runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse {
            throw SteamChatUploadException("Steam returned invalid attachment data", it)
        }
    }

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private data class BeginUpload(
        val cloudUrl: String,
        val requestHeaders: List<Pair<String, String>>,
        val ugcId: String,
        val timestamp: Long,
        val hmac: String
    )

    companion object {
        const val MAX_FILE_BYTES = 30L * 1024L * 1024L
        private const val BEGIN_URL = "https://steamcommunity.com/chat/beginfileupload/"
        private const val COMMIT_URL = "https://steamcommunity.com/chat/commitfileupload/"
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "avif")
        private val VIDEO_EXTENSIONS = setOf("webm", "mpg", "mp4", "mpeg", "ogv")

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}

class SteamChatUploadException(message: String, cause: Throwable? = null) : IOException(message, cause)

private class ContentUriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val attachment: SteamChatPendingAttachment,
    private val onProgress: (Float) -> Unit
) : RequestBody() {
    override fun contentType() = attachment.mimeType.toMediaTypeOrNull()

    override fun contentLength(): Long = attachment.sizeBytes

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri)
            ?: throw SteamChatUploadException("Selected attachment is no longer available")
        input.use {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var written = 0L
            var lastProgress = -1
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                sink.write(buffer, 0, count)
                written += count
                if (written > SteamChatAttachmentUploader.MAX_FILE_BYTES) {
                    throw SteamChatUploadException("Steam chat attachments must be 30 MB or smaller")
                }
                val progress = ((written * 100L) / attachment.sizeBytes.coerceAtLeast(1L)).toInt()
                if (progress != lastProgress) {
                    lastProgress = progress
                    onProgress(progress.coerceIn(0, 100) / 100f)
                }
            }
        }
    }
}
