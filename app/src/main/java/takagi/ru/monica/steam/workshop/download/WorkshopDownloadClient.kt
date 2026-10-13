package takagi.ru.monica.steam.workshop.download

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import takagi.ru.monica.steam.network.SteamHttpClientProvider
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.workshop.*
import takagi.ru.monica.steam.data.SteamAccount
import kotlin.coroutines.coroutineContext

internal const val WALLPAPER_ENGINE_APP_ID = 431960
internal data class WorkshopDownloadSource(val item: WorkshopItem, val manifest: String, val fileUrl: String, val fileName: String)
internal class WorkshopDownloadAccessDenied(val result: Int? = null) : IOException("Steam content access denied ($result)")
internal class WorkshopDownloadUnavailable : IOException()
internal class WorkshopDownloadStorageUnavailable : IOException()

/** Original files only: no scene execution, PKG extraction, or automatic subscription. */
internal class WorkshopDownloadClient(
    private val sessionFactory: (SteamAccount?) -> WorkshopContentSession = { account ->
        account?.let(::WorkshopAccountContentSession) ?: WorkshopAnonymousSession()
    },
    private val contentFetch: (suspend (HttpUrl, Int) -> ByteArray)? = null,
    private val http: okhttp3.OkHttpClient = SteamHttpClientProvider.newBuilder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()
) {

    suspend fun details(appId: Int, id: String): WorkshopDownloadSource = runInterruptible(Dispatchers.IO) {
        require(appId > 0 && WorkshopShareCode.validId(id))
        val request = Request.Builder().url("https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1/")
            .post(FormBody.Builder().add("itemcount", "1").add("publishedfileids[0]", id).build()).build()
        val body = http.newCall(request).execute().use {
            if (!it.isSuccessful) throw SteamApiException("Workshop public details", httpStatusCode = it.code)
            WorkshopContentCodec.readBounded(requireNotNull(it.body).byteStream(), 2 * 1024 * 1024)
        }
        parseDetails(Json.parseToJsonElement(body.toString(Charsets.UTF_8)).jsonObject, appId, id)
    }

    suspend fun download(source: WorkshopDownloadSource, root: File, account: SteamAccount? = null,
        progress: suspend (Long, Long) -> Unit): List<File> {
        require(source.item.fileType == 0 && !source.item.banned)
        withContext(Dispatchers.IO) {
            root.mkdirs()
            if (!root.isDirectory || !root.canWrite()) throw WorkshopDownloadStorageUnavailable()
        }
        // Legacy UGC may expose both an hcontent handle and a real direct file URL.
        // That handle is not necessarily a SteamPipe manifest; prefer the usable URL.
        val directUrl = source.fileUrl.toHttpUrlOrNull()?.takeIf(::allowedUrl)
        if (directUrl == null && source.manifest.toULongOrNull()?.let { it > 0uL } == true) {
            return sessionFactory(account).use { session ->
                session.connect()
                val keyResponse = SteamProtoReader(session.depotKey(source.item.appId)).parse()
                if (keyResponse[1]?.asInt != 1) throw WorkshopDownloadAccessDenied(keyResponse[1]?.asInt)
                require(keyResponse[2]?.asInt == source.item.appId)
                val key = requireNotNull(keyResponse[3]?.bytes).also { require(it.size == 32) }
                try {
                    val code = session.service("ContentServerDirectory.GetManifestRequestCode#1", SteamProtoWriter().apply {
                        writeVarint(1, source.item.appId.toLong()); writeVarint(2, source.item.appId.toLong())
                        writeUint64(3, source.manifest)
                    }).let { SteamProtoReader(it).parse()[1]?.asLong?.toULong()?.toString() ?: "0" }
                    val servers = session.service("ContentServerDirectory.GetServersForSteamPipe#1", SteamProtoWriter().apply {
                        writeVarint(1, 0); writeVarint(2, 20)
                    }).let(::httpsServers)
                    if (servers.isEmpty()) throw IOException("No HTTPS Steam content server")
                    val tokens = mutableMapOf<String, String>()
                    suspend fun authorizedBytes(url: HttpUrl, max: Int): ByteArray {
                        if (account == null) return bytes(url, max)
                        val token = tokens[url.host] ?: session.service("ContentServerDirectory.GetCDNAuthToken#1", SteamProtoWriter().apply {
                            writeVarint(1, source.item.appId.toLong()); writeString(2, url.host)
                            writeVarint(3, source.item.appId.toLong())
                        }).let { SteamProtoReader(it).parse()[1]?.asString.orEmpty() }.also { tokens[url.host] = it }
                        return bytes(url.newBuilder().encodedQuery(token.removePrefix("?")).build(), max)
                    }
                    val path = "depot/${source.item.appId}/manifest/${source.manifest}/5" + if (code != "0") "/$code" else ""
                    val manifest = fromServers(servers) { host ->
                        WorkshopContentCodec.manifest(authorizedBytes(host.newBuilder().addPathSegments(path).build(), WorkshopContentCodec.MAX_MANIFEST),
                            source.item.appId, source.manifest, key)
                    }
                    withContext(Dispatchers.IO) {
                        if (root.usableSpace < manifest.size + 32 * 1024 * 1024) throw WorkshopDownloadStorageUnavailable()
                    }
                    var done = 0L
                    progress(done, manifest.size)
                    val files = mutableListOf<File>()
                    for (entry in manifest.files) {
                        coroutineContext.ensureActive()
                        val target = WorkshopContentCodec.resolve(root, entry.path)
                        if (!withContext(Dispatchers.IO) { WorkshopContentCodec.verified(target, entry.size, entry.sha) }) {
                            withContext(Dispatchers.IO) { target.parentFile?.mkdirs() }
                            withContext(Dispatchers.IO) {
                                target.outputStream().use { output ->
                                    for (chunk in entry.chunks) {
                                        coroutineContext.ensureActive()
                                        val data = fromServers(servers) { host ->
                                            WorkshopContentCodec.chunk(authorizedBytes(host.newBuilder().addPathSegments(
                                                "depot/${source.item.appId}/chunk/${WorkshopContentCodec.hex(chunk.sha)}").build(),
                                                chunk.compressed), chunk, key)
                                        }
                                        output.write(data)
                                        done += data.size
                                        progress(done, manifest.size)
                                    }
                                }
                                check(WorkshopContentCodec.verified(target, entry.size, entry.sha)) { "File checksum failed" }
                            }
                        } else { done += entry.size; progress(done, manifest.size) }
                        files += target
                    }
                    files
                } finally { key.fill(0) }
            }
        }
        if (directUrl == null) throw WorkshopDownloadUnavailable()
        val name = source.fileName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "${source.item.id}.bin" }
        val target = WorkshopContentCodec.resolve(root, name)
        withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(directUrl).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Steam file HTTP ${response.code}")
                val body = requireNotNull(response.body)
                val total = source.item.size.takeIf { it > 0 } ?: body.contentLength()
                require(total in 1..(100L * 1024 * 1024 * 1024))
                if (root.usableSpace < total + 32 * 1024 * 1024) throw WorkshopDownloadStorageUnavailable()
                var done = 0L
                body.byteStream().use { input -> target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer); if (n < 0) break
                        done += n; require(done <= total)
                        out.write(buffer, 0, n); progress(done, total)
                    }
                } }
                require(done == total) { "Truncated Steam file" }
            }
        }
        return listOf(target)
    }

    private suspend fun bytes(url: HttpUrl, max: Int): ByteArray {
        require(allowedUrl(url))
        contentFetch?.let { return it(url, max).also { require(it.size <= max) } }
        return runInterruptible(Dispatchers.IO) { http.newCall(Request.Builder().url(url).build()).execute().use {
            if (it.code == 401 || it.code == 403) throw WorkshopDownloadAccessDenied()
            if (!it.isSuccessful) throw IOException("Steam content HTTP ${it.code}")
            WorkshopContentCodec.readBounded(requireNotNull(it.body).byteStream(), max)
        } }
    }

    private suspend fun <T> fromServers(servers: List<HttpUrl>, block: suspend (HttpUrl) -> T): T {
        var failure: Exception? = null
        for (server in servers.take(5)) {
            coroutineContext.ensureActive()
            try { return block(server) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { failure = error }
        }
        throw failure ?: IOException("No Steam content server")
    }

    companion object {
        fun allowedUrl(url: HttpUrl): Boolean = url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
            listOf("steamcontent.com", "steamusercontent.com", "steamstatic.com", "steampowered.com",
                "steamcdn-a.akamaihd.net", "steamuserimages-a.akamaihd.net").any { url.host == it || url.host.endsWith(".$it") }

        fun httpsServers(bytes: ByteArray): List<HttpUrl> = SteamProtoReader(bytes).parseAll().filter { it.number == 1 }
            .map { SteamProtoReader(requireNotNull(it.bytes)).parse() }
            .filter { it[12]?.asString in listOf("mandatory", "optional") }
            .mapNotNull { fields ->
                val host = fields[9]?.asString?.takeIf(String::isNotBlank) ?: fields[8]?.asString.orEmpty()
                "https://$host/".toHttpUrlOrNull()?.takeIf { allowedUrl(it) && it.encodedPath == "/" }
            }.distinct()

        fun parseDetails(json: JsonObject, appId: Int, id: String): WorkshopDownloadSource {
            val row = json.obj("response").array("publishedfiledetails").map { it.jsonObject }
                .singleOrNull { it.text("publishedfileid") == id } ?: throw WorkshopDownloadUnavailable()
            if (row.int("result") != 1) throw WorkshopDownloadUnavailable()
            require(row.int("consumer_app_id") == appId)
            val title = row.text("title").also { require(it.isNotBlank()) }
            val item = WorkshopItem(id, appId, title, preview = row.text("preview_url"), author = row.text("creator"),
                description = row.text("description"), tags = row.array("tags").map { it.jsonObject.text("tag") },
                size = row.long("file_size"), created = row.long("time_created"), updated = row.long("time_updated"),
                fileType = row.int("file_type"), banned = row.bool("banned"))
            return WorkshopDownloadSource(item, row.text("hcontent_file"), row.text("file_url"), row.text("filename"))
        }
    }
}
