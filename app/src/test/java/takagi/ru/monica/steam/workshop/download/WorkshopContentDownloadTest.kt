package takagi.ru.monica.steam.workshop.download

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAOutputStream
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.workshop.WorkshopItem

class WorkshopContentDownloadTest {
    private val key = ByteArray(32) { it.toByte() }
    private val original = """{"type":"scene","file":"scene.pkg","title":"Test wallpaper"}""".toByteArray()
    private val sha = MessageDigest.getInstance("SHA-1").digest(original)
    private val appId = WALLPAPER_ENGINE_APP_ID
    private val manifestId = "18446744073709551610"
    private fun proto(block: SteamProtoWriter.() -> Unit) = SteamProtoWriter().apply(block).toByteArray()
    private fun le(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    private fun zip(data: ByteArray) = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { it.putNextEntry(ZipEntry("content")); it.write(data); it.closeEntry() }
    }.toByteArray()
    private fun encrypt(data: ByteArray): ByteArray {
        val secret = SecretKeySpec(key, "AES")
        val iv = ByteArray(16) { (it + 17).toByte() }
        val encryptedIv = Cipher.getInstance("AES/ECB/NoPadding").run { init(Cipher.ENCRYPT_MODE, secret); doFinal(iv) }
        return encryptedIv + Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE, secret, IvParameterSpec(iv)); doFinal(data)
        }
    }
    private fun crc(): Long {
        var a = 0L; var b = 0L
        original.forEach { a = (a + (it.toInt() and 255)) % 65521; b = (b + a) % 65521 }
        return (b shl 16) or a
    }
    private val encrypted get() = encrypt(zip(original))
    private fun chunk(encrypted: ByteArray) = WorkshopChunk(sha, crc(), 0, original.size, encrypted.size)
    private fun manifest(path: String = "project.json", offset: Long = 0, flags: Long = 0, encryptedName: Boolean = true): ByteArray {
        val file = proto {
            writeString(1, if (encryptedName) Base64.getEncoder().encodeToString(encrypt((path + "\u0000").toByteArray())) else path)
            writeVarint(2, original.size.toLong()); writeVarint(3, flags); writeBytes(5, sha)
            writeBytes(6, proto {
                writeBytes(1, sha); writeFixed32(2, crc()); writeVarint(3, offset)
                writeVarint(4, original.size.toLong()); writeVarint(5, encrypted.size.toLong())
            })
        }
        val payload = proto { writeBytes(1, file) }
        val metadata = proto {
            writeVarint(1, appId.toLong()); writeUint64(2, manifestId); writeBool(4, encryptedName)
        }
        return zip(le(0x71F617D0) + le(payload.size) + payload + le(0x1F4812BE) + le(metadata.size) + metadata + le(0x32C415AB))
    }

    @Test fun encryptedZipChunkAndFilenameRoundTrip() {
        assertArrayEquals(original, WorkshopContentCodec.chunk(encrypted, chunk(encrypted), key))
        val parsed = WorkshopContentCodec.manifest(manifest("sub/project.json"), appId, manifestId, key)
        assertEquals("sub/project.json", parsed.files.single().path)
        assertEquals(original.size.toLong(), parsed.size)
    }

    @Test fun lzmaChunkUsesSteamFramingAndChecksum() {
        val options = LZMA2Options().apply { dictSize = 1 shl 20 }
        val out = ByteArrayOutputStream()
        val stream = LZMAOutputStream(out, options, false)
        val properties = stream.props.toByte()
        stream.use { it.write(original) }
        val framed = byteArrayOf(86,90,97) + le(0) + byteArrayOf(properties) + le(options.dictSize) +
            out.toByteArray() + le(0) + le(original.size) + byteArrayOf(122,118)
        val cipher = encrypt(framed)
        assertArrayEquals(original, WorkshopContentCodec.chunk(cipher, chunk(cipher), key))
    }

    @Test fun rejectsTraversalAbsolutePathsAndSymlinks() {
        listOf("../escape", "/etc/file", "C:/file", "safe/../../bad", "one//two", "a\u0000b", "a/./b").forEach {
            assertThrows(IllegalArgumentException::class.java) { WorkshopContentCodec.safePath(it) }
        }
        assertThrows(IllegalArgumentException::class.java) { WorkshopContentCodec.manifest(manifest("../bad"), appId, manifestId, key) }
        assertThrows(IllegalArgumentException::class.java) { WorkshopContentCodec.manifest(manifest(flags=512), appId, manifestId, key) }
    }

    @Test fun rejectsWrongManifestIdentityAndMissingChunkCoverage() {
        assertThrows(IllegalArgumentException::class.java) { WorkshopContentCodec.manifest(manifest(), 480, manifestId, key) }
        assertThrows(IllegalArgumentException::class.java) { WorkshopContentCodec.manifest(manifest(), appId, "123", key) }
        assertThrows(IllegalArgumentException::class.java) { WorkshopContentCodec.manifest(manifest(offset=1), appId, manifestId, key) }
        assertThrows(IllegalArgumentException::class.java) { WorkshopContentCodec.readBounded(ByteArray(33).inputStream(),32) }
    }

    @Test fun rejectsChecksumMismatchAndTruncation() {
        assertThrows(IllegalArgumentException::class.java) { WorkshopContentCodec.chunk(encrypted, chunk(encrypted).copy(crc=1), key) }
        assertThrows(IllegalArgumentException::class.java) { WorkshopContentCodec.chunk(encrypted, chunk(encrypted).copy(sha=ByteArray(20)), key) }
        assertThrows(IllegalArgumentException::class.java) { WorkshopContentCodec.chunk(encrypted.copyOf(16), chunk(encrypted), key) }
    }

    @Test fun endpointPolicyRejectsHttpForeignAndCredentialBearingUrls() {
        listOf("http://cdn.steamcontent.com/a", "https://steamcontent.com.evil.test/a",
            "https://127.0.0.1/a", "https://user:secret@cdn.steamcontent.com/a", "https://cdn.steamcontent.com:444/a").forEach {
            assertFalse(WorkshopDownloadClient.allowedUrl(it.toHttpUrl()))
        }
        assertTrue(WorkshopDownloadClient.allowedUrl("https://cdn.steamcontent.com/a".toHttpUrl()))
    }

    private inner class Session(private val allowed: Boolean = true) : WorkshopContentSession {
        var closed = false
        override suspend fun connect() = Unit
        override fun close() { closed = true }
        override suspend fun depotKey(appId: Int) = proto {
            writeVarint(1, if (allowed) 1 else 15); writeVarint(2, appId.toLong()); writeBytes(3, key.copyOf())
        }
        override suspend fun service(method: String, request: SteamProtoWriter): ByteArray = when {
            method.contains("ManifestRequestCode") -> {
                val fields = SteamProtoReader(request.toByteArray()).parse()
                assertEquals(manifestId, fields[3]?.asLong?.toULong()?.toString())
                proto { writeVarint(1,123) }
            }
            method.contains("ServersForSteamPipe") -> proto { writeBytes(1, proto {
                writeString(8,"cdn.steamcontent.com"); writeString(12,"mandatory")
            }) }
            else -> error(method)
        }
    }

    @Test fun completeDownloadWritesVerifiedOriginalDirectory() = runBlocking {
        val session = Session()
        val urls = mutableListOf<String>()
        val client = WorkshopDownloadClient({ assertNull(it); session }, { url, _ ->
            urls += url.toString()
            if (url.encodedPath.contains("/manifest/")) manifest("sub/project.json") else encrypted
        })
        val dir = Files.createTempDirectory("wallpaper-fixture").toFile()
        try {
            val progress = mutableListOf<Long>()
            val source = WorkshopDownloadSource(WorkshopItem("123",appId,"Test"),manifestId,"","")
            val files = client.download(source, dir) { done, _ -> progress += done }
            assertEquals("sub/project.json", files.single().relativeTo(dir).invariantSeparatorsPath)
            assertArrayEquals(original, files.single().readBytes())
            assertEquals(original.size.toLong(), progress.last())
            assertTrue(urls.all { it.startsWith("https://cdn.steamcontent.com/") && !it.contains("?") })
            assertTrue(session.closed)
        } finally { dir.deleteRecursively() }
    }

    @Test fun accessDenialCreatesNoFileAndClosesSession() = runBlocking {
        val session = Session(false)
        val dir = Files.createTempDirectory("wallpaper-denied").toFile()
        try {
            val client = WorkshopDownloadClient({session}, { _, _ -> error("No CDN request permitted") })
            try { client.download(WorkshopDownloadSource(WorkshopItem("123",appId,"Test"),manifestId,"",""),dir) { _,_-> }; fail() }
            catch (e: WorkshopDownloadAccessDenied) { assertEquals(15,e.result) }
            assertTrue(dir.listFiles().isNullOrEmpty()); assertTrue(session.closed)
        } finally { dir.deleteRecursively() }
    }

    @Test fun cancellationDoesNotBecomeSuccessOrTryAnotherServer() = runBlocking {
        val session = Session()
        val dir = Files.createTempDirectory("wallpaper-cancel").toFile()
        try {
            val client = WorkshopDownloadClient({session},{_,_-> throw CancellationException("cancel")})
            try { client.download(WorkshopDownloadSource(WorkshopItem("123",appId,"Test"),manifestId,"",""),dir) { _,_-> }; fail() }
            catch (_: CancellationException) { }
            assertTrue(session.closed); assertTrue(dir.listFiles().isNullOrEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun publicDetailsRejectCrossGameAndRemovedItems() {
        fun body(result: Int, app: Int) = Json.parseToJsonElement("""{"response":{"publishedfiledetails":[{"result":$result,"publishedfileid":"123","consumer_app_id":$app,"title":"Wallpaper","hcontent_file":"$manifestId","file_size":"48"}]}}""").jsonObject
        assertEquals(manifestId,WorkshopDownloadClient.parseDetails(body(1,appId),appId,"123").manifest)
        assertThrows(IllegalArgumentException::class.java) { WorkshopDownloadClient.parseDetails(body(1,480),appId,"123") }
        assertThrows(WorkshopDownloadUnavailable::class.java) { WorkshopDownloadClient.parseDetails(body(9,appId),appId,"123") }
        val logon = SteamProtoReader(WorkshopAnonymousSession.anonymousLogon()).parse()
        assertEquals(-500, logon[7]?.asInt)
        assertFalse(logon.containsKey(103)); assertFalse(logon.containsKey(108))
    }

    @Test fun accountContentAccessUsesOnlyTheExplicitlyCapturedAccount() = runBlocking {
        val captured = takagi.ru.monica.steam.workshop.SteamWorkshopViewModelTest.account()
        val seen = mutableListOf<takagi.ru.monica.steam.data.SteamAccount>()
        val gateway = object : takagi.ru.monica.steam.network.cm.SteamCmGateway {
            override fun callService(account: takagi.ru.monica.steam.data.SteamAccount, method: String, request: ByteArray): ByteArray {
                seen += account
                assertEquals("ContentServerDirectory.GetManifestRequestCode#1", method)
                return byteArrayOf(8, 1)
            }
            override fun exchangeClientMessage(account: takagi.ru.monica.steam.data.SteamAccount,
                requestEMsg: Int, responseEMsg: Int, request: ByteArray): ByteArray {
                seen += account
                assertEquals(5438,requestEMsg); assertEquals(5439,responseEMsg)
                val fields=SteamProtoReader(request).parse()
                assertEquals(appId,fields[1]?.asInt); assertEquals(appId,fields[2]?.asInt)
                assertEquals(2,fields.size)
                return byteArrayOf(8, 1)
            }
        }
        WorkshopAccountContentSession(captured,gateway).use { session ->
            session.connect(); session.depotKey(appId)
            session.service("ContentServerDirectory.GetManifestRequestCode#1",SteamProtoWriter())
        }
        assertEquals(listOf(captured,captured),seen)
    }

    @Test fun legacyDirectFileTakesPrecedenceOverItsUgcHandle() = runBlocking {
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("https://cdn.steamusercontent.com/original",chain.request().url.toString())
            assertNull(chain.request().header("Cookie")); assertNull(chain.request().header("Authorization"))
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK").body(original.toResponseBody()).build()
        }.build()
        val client = WorkshopDownloadClient({error("A legacy direct file needs no CM login")},http=http)
        val root=Files.createTempDirectory("workshop-direct").toFile()
        try {
            val source=WorkshopDownloadSource(WorkshopItem("123",appId,"Test",size=original.size.toLong()),
                manifestId,"https://cdn.steamusercontent.com/original","sub/original.mp4")
            val files=client.download(source,root) {_,_->}
            assertEquals("original.mp4",files.single().name)
            assertArrayEquals(original,files.single().readBytes())
        } finally { root.deleteRecursively() }
    }
}
