package takagi.ru.monica.steam.friends.chat.richmedia.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.net.InetAddress
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.BufferedSink
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentPolicy
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentGateway
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentKind
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentTarget
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatPendingAttachment
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatUploadedAttachment
import takagi.ru.monica.steam.network.SteamHttpClientProvider

class SteamChatAttachmentUploader internal constructor(
    context: Context,
    private val client: OkHttpClient = defaultClient(),
    private val responseParser: SteamChatAttachmentUploadResponseParser =
        SteamChatAttachmentUploadResponseParser()
) : SteamChatAttachmentGateway {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun inspect(rawUri: String): SteamChatPendingAttachment {
        val uri = Uri.parse(rawUri)
        val metadata = queryMetadata(uri)
        val mediaType = SteamChatAttachmentPolicy.mediaType(metadata.first, resolver.getType(uri).orEmpty())
            ?: throw SteamChatUploadException.unsupportedType()
        val mimeType = mediaType.mimeType
        val kind = mediaType.kind
        val descriptorSize = resolver.openAssetFileDescriptor(uri, "r")
            ?.use { it.length }
            ?.takeIf { it >= 0L }
        val size = metadata.second.takeIf { it >= 0L }
            ?: descriptorSize
            ?: countBytes(uri)
        if (size !in 1..MAX_FILE_BYTES) throw SteamChatUploadException.invalidSize()
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

    override suspend fun upload(
        account: SteamAccount,
        target: SteamChatAttachmentTarget,
        attachment: SteamChatPendingAttachment,
        spoiler: Boolean,
        onProgress: (Float) -> Unit
    ): SteamChatUploadedAttachment {
        // A pending object can outlive the picker. Recheck before any reservation or request.
        val mediaType = SteamChatAttachmentPolicy.mediaType(attachment.displayName, attachment.mimeType)
        if (mediaType == null || mediaType.kind != attachment.kind) throw SteamChatUploadException.unsupportedType()
        if (attachment.sizeBytes !in 1..MAX_FILE_BYTES) throw SteamChatUploadException.invalidSize()
        val secure = account.steamLoginSecure?.takeIf(String::isNotBlank)
            ?: throw SteamChatUploadException("Steam community session required for attachments")
        val targetFields = target.commitFields(spoiler)
        val uri = Uri.parse(attachment.uri)
        val session = SteamChatUploadSession(
            sessionId = randomSteamWebSessionId(),
            encodedSteamLoginSecure = encodeSteamChatCookieValue(secure)
        )
        val uploadName = "${System.nanoTime()}_${sanitizeFilename(attachment.displayName)}"
        val sha = UUID.randomUUID().toString().replace("-", "") +
            UUID.randomUUID().toString().replace("-", "").take(8)
        val begin = beginUpload(
            session = session,
            attachment = attachment,
            uploadName = uploadName,
            sha = sha
        )
        try {
            putToCloud(begin, uri, attachment, onProgress)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            try {
                commitUpload(
                    session = session,
                    attachment = attachment,
                    uploadName = uploadName,
                    sha = sha,
                    begin = begin,
                    targetFields = targetFields,
                    success = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Steam expires incomplete upload reservations server-side.
            }
            throw error
        }
        val committed = commitUpload(
            session = session,
            attachment = attachment,
            uploadName = uploadName,
            sha = sha,
            begin = begin,
            targetFields = targetFields,
            success = true
        ) ?: throw SteamChatUploadException("Steam returned no attachment result")
        onProgress(1f)
        return SteamChatUploadedAttachment(
            url = committed.url,
            label = attachment.displayName,
            kind = attachment.kind,
            spoiler = spoiler
        )
    }

    private suspend fun beginUpload(
        session: SteamChatUploadSession,
        attachment: SteamChatPendingAttachment,
        uploadName: String,
        sha: String
    ): SteamChatBeginUploadResponse {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("sessionid", session.sessionId)
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
            .headers(buildSteamChatCommunityHeaders(session))
            .post(body)
            .build()
        return client.newCall(request).awaitSteamChatResponse().use { response ->
            val parsed = responseParser.parseBegin(
                response.requireBody("begin Steam chat attachment")
            )
            requireSafeCloudHost(
                parsed.cloudUrl.toHttpUrlOrNull()?.host
                    ?: throw SteamChatUploadException("Steam returned an invalid upload URL")
            )
            parsed
        }
    }

    private suspend fun putToCloud(
        begin: SteamChatBeginUploadResponse,
        uri: Uri,
        attachment: SteamChatPendingAttachment,
        onProgress: (Float) -> Unit
    ) {
        val body = ContentUriRequestBody(resolver, uri, attachment, onProgress)
        val request = Request.Builder()
            .url(begin.cloudUrl)
            .headers(buildSteamChatCloudHeaders(begin.requestHeaders))
            .put(body)
            .build()
        client.newCall(request).awaitSteamChatResponse().use { response ->
            if (!response.isSuccessful) {
                throw SteamChatUploadException.cloudFailure(response.code)
            }
        }
    }

    private suspend fun commitUpload(
        session: SteamChatUploadSession,
        attachment: SteamChatPendingAttachment,
        uploadName: String,
        sha: String,
        begin: SteamChatBeginUploadResponse,
        targetFields: List<Pair<String, String>>,
        success: Boolean
    ): SteamChatCommitUploadResponse? {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("sessionid", session.sessionId)
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
        targetFields.forEach { (name, value) -> body.addFormDataPart(name, value) }
        val requestBody = body.build()
        val request = Request.Builder()
            .url(COMMIT_URL)
            .headers(buildSteamChatCommunityHeaders(session))
            .post(requestBody)
            .build()
        return client.newCall(request).awaitSteamChatResponse().use { response ->
            if (!success) return@use null
            val committed = responseParser.parseCommit(
                response.requireBody("commit Steam chat attachment")
            )
            requireSafeCloudHost(
                committed.url.toHttpUrlOrNull()?.host
                    ?: throw SteamChatUploadException("Steam returned an invalid attachment URL")
            )
            committed
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

    private fun Response.requireBody(operation: String): String {
        val raw = body?.string().orEmpty()
        if (!isSuccessful) {
            responseParser.parseFailure(raw)?.let { throw it }
            throw SteamChatUploadException.httpFailure(operation, code)
        }
        return raw
    }

    companion object {
        const val MAX_FILE_BYTES = SteamChatAttachmentPolicy.MAX_FILE_BYTES
        private const val BEGIN_URL = "https://steamcommunity.com/chat/beginfileupload/"
        private const val COMMIT_URL = "https://steamcommunity.com/chat/commitfileupload/"

        private fun defaultClient(): OkHttpClient = SteamHttpClientProvider.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}

internal suspend fun Call.awaitSteamChatResponse(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response)
                else response.close()
            }
        })
    }

internal data class SteamChatUploadSession(
    val sessionId: String,
    val encodedSteamLoginSecure: String
)

internal enum class SteamChatUploadFailure {
    AUTHENTICATION,
    LIMITED_ACCOUNT,
    FILE_REJECTED,
    UNSUPPORTED_TYPE,
    INVALID_SIZE,
    SERVICE,
    UNKNOWN
}

internal class SteamChatUploadException(
    message: String,
    cause: Throwable? = null,
    val failure: SteamChatUploadFailure = SteamChatUploadFailure.UNKNOWN
) : IOException(message, cause) {
    val isAuthenticationFailure: Boolean
        get() = failure == SteamChatUploadFailure.AUTHENTICATION

    companion object {
        internal fun unsupportedType(): SteamChatUploadException =
            SteamChatUploadException("Unsupported Steam chat attachment type", failure = SteamChatUploadFailure.UNSUPPORTED_TYPE)

        internal fun invalidSize(): SteamChatUploadException =
            SteamChatUploadException("Steam chat attachments must be non-empty and 30 MiB or smaller", failure = SteamChatUploadFailure.INVALID_SIZE)

        internal fun authentication(message: String): SteamChatUploadException =
            SteamChatUploadException(message, failure = SteamChatUploadFailure.AUTHENTICATION)

        internal fun httpFailure(operation: String, code: Int): SteamChatUploadException =
            SteamChatUploadException(
                message = "Unable to $operation ($code)",
                failure = if (code in setOf(400, 401, 403)) {
                    SteamChatUploadFailure.AUTHENTICATION
                } else {
                    SteamChatUploadFailure.SERVICE
                }
            )

        internal fun cloudFailure(code: Int): SteamChatUploadException =
            SteamChatUploadException(
                message = "Steam cloud upload failed ($code)",
                failure = SteamChatUploadFailure.SERVICE
            )

        internal fun steamRejected(code: Int, message: String?): SteamChatUploadException {
            val failure = when (code) {
                5, 15, 21, 50, 65 -> SteamChatUploadFailure.AUTHENTICATION
                112 -> SteamChatUploadFailure.LIMITED_ACCOUNT
                20 -> SteamChatUploadFailure.SERVICE
                8, 9, 11, 13, 25 -> SteamChatUploadFailure.FILE_REJECTED
                else -> SteamChatUploadFailure.UNKNOWN
            }
            return SteamChatUploadException(
                message = message?.takeIf(String::isNotBlank)
                    ?: "Steam rejected the attachment upload (result $code)",
                failure = failure
            )
        }
    }
}

internal fun randomSteamWebSessionId(): String = UUID.randomUUID()
    .toString()
    .replace("-", "")
    .take(STEAM_WEB_SESSION_ID_LENGTH)

internal fun encodeSteamChatCookieValue(value: String): String = URLEncoder.encode(
    runCatching { java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }
        .getOrDefault(value),
    StandardCharsets.UTF_8.name()
).replace("+", "%20")

internal fun buildSteamChatCommunityHeaders(session: SteamChatUploadSession) =
    okhttp3.Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Steam Chat")
        .add("Accept", "application/json, text/plain, */*")
        .add("Origin", "https://steamcommunity.com")
        .add("Referer", "https://steamcommunity.com/chat/")
        .add(
            "Cookie",
            "steamLoginSecure=${session.encodedSteamLoginSecure}; sessionid=${session.sessionId}"
        )
        .build()

internal fun buildSteamChatCloudHeaders(
    headers: List<Pair<String, String>>
): okhttp3.Headers = okhttp3.Headers.Builder().apply {
    headers.forEach { (name, value) ->
        if (!name.equals("Host", true) &&
            !name.equals("Content-Length", true)
        ) {
            // These values are issued by Steam for this one cloud reservation.
            // No community cookies or account credentials are added here.
            add(name, value)
        }
    }
}.build()

private const val STEAM_WEB_SESSION_ID_LENGTH = 24

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
                    throw SteamChatUploadException.invalidSize()
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
