package takagi.ru.monica.steam.workshop.download

import com.github.luben.zstd.Zstd
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.Adler32
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.tukaani.xz.LZMAInputStream
import takagi.ru.monica.steam.network.SteamProtoReader

internal data class WorkshopChunk(val sha: ByteArray, val crc: Long, val offset: Long, val size: Int, val compressed: Int)
internal data class WorkshopFile(val path: String, val size: Long, val sha: ByteArray, val chunks: List<WorkshopChunk>)
internal data class WorkshopManifest(val files: List<WorkshopFile>) { val size: Long get() = files.sumOf { it.size } }

/** SteamPipe wire formats, cross-checked against JavaSteam (MIT); see THIRD_PARTY_NOTICES.md. */
internal object WorkshopContentCodec {
    const val MAX_CHUNK = 16 * 1024 * 1024
    const val MAX_MANIFEST = 32 * 1024 * 1024

    fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(output.size().toLong() + n <= limit) { "Steam content exceeds limit" }
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }

    fun safePath(raw: String): String {
        val path = raw.replace('\\', '/')
        require(path.isNotBlank() && path.length <= 1024 && !path.startsWith('/'))
        val parts = path.split('/')
        require(parts.all { it.isNotBlank() && it != "." && it != ".." && it.length <= 255 &&
            it.none { c -> c < ' ' || c in ":*?\"<>|\u007f" } })
        return path
    }

    fun resolve(root: File, relative: String): File = File(root, safePath(relative)).canonicalFile.also {
        require(it.path.startsWith(root.canonicalPath + File.separator))
    }

    fun manifest(zipped: ByteArray, appId: Int, manifestId: String, key: ByteArray): WorkshopManifest {
        val data = ZipInputStream(ByteArrayInputStream(zipped)).use { zip ->
            require(zip.nextEntry != null)
            readBounded(zip, MAX_MANIFEST)
        }
        val input = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        var payload: ByteArray? = null
        var metadata: ByteArray? = null
        var ended = false
        while (input.remaining() >= 4) {
            val magic = input.int
            if (magic == 0x32C415AB) { ended = true; break }
            require(magic in listOf(0x71F617D0, 0x1F4812BE, 0x1B81B817) && input.remaining() >= 4)
            val length = input.int
            require(length in 0..input.remaining())
            val section = ByteArray(length).also(input::get)
            when (magic) {
                0x71F617D0 -> { require(payload == null); payload = section }
                0x1F4812BE -> { require(metadata == null); metadata = section }
            }
        }
        require(ended && !input.hasRemaining())
        val meta = SteamProtoReader(requireNotNull(metadata)).parse()
        require(meta[1]?.asInt == appId && meta[2]?.asLong?.toULong()?.toString() == manifestId)
        val encrypted = meta[4]?.asBool == true
        val rows = SteamProtoReader(requireNotNull(payload)).parseAll().filter { it.number == 1 }
        require(rows.size <= 20_000)
        val files = rows.mapNotNull { row ->
            val fields = SteamProtoReader(requireNotNull(row.bytes)).parseAll()
            val f = fields.associateBy { it.number }
            val raw = f[1]?.asString.orEmpty()
            val path = safePath(if (encrypted) decrypt(Base64.getDecoder().decode(raw), key)
                .toString(Charsets.UTF_8).trimEnd('\u0000') else raw)
            val flags = f[3]?.asInt ?: 0
            require(flags and 512 == 0 && f[7]?.asString.isNullOrBlank()) { "Symlinks are not supported" }
            if (flags and 64 != 0) return@mapNotNull null
            val size = f[2]?.asLong ?: 0
            require(size in 0..(100L * 1024 * 1024 * 1024))
            val sha = requireNotNull(f[5]?.bytes).also { require(it.size == 20) }
            val chunks = fields.filter { it.number == 6 }.map {
                val c = SteamProtoReader(requireNotNull(it.bytes)).parse()
                WorkshopChunk(requireNotNull(c[1]?.bytes).also { require(it.size == 20) },
                    c[2]?.asFixed32UnsignedLong ?: 0, c[3]?.asLong ?: 0,
                    c[4]?.asInt ?: 0, c[5]?.asInt ?: 0)
            }.sortedBy { it.offset }
            var offset = 0L
            chunks.forEach {
                require(it.offset == offset && it.size in 1..MAX_CHUNK && it.compressed in 16..MAX_CHUNK)
                offset = Math.addExact(offset, it.size.toLong())
            }
            require(offset == size)
            WorkshopFile(path, size, sha, chunks)
        }
        require(files.isNotEmpty() && files.map { it.path.lowercase(java.util.Locale.ROOT) }.distinct().size == files.size)
        return WorkshopManifest(files).also { require(it.size <= 100L * 1024 * 1024 * 1024) }
    }

    fun decrypt(encrypted: ByteArray, key: ByteArray): ByteArray {
        require(key.size == 32 && encrypted.size >= 32 && encrypted.size % 16 == 0)
        val secret = SecretKeySpec(key, "AES")
        val iv = Cipher.getInstance("AES/ECB/NoPadding").run {
            init(Cipher.DECRYPT_MODE, secret); doFinal(encrypted, 0, 16)
        }
        return Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE, secret, IvParameterSpec(iv))
            doFinal(encrypted, 16, encrypted.size - 16)
        }
    }

    fun chunk(encrypted: ByteArray, info: WorkshopChunk, key: ByteArray): ByteArray {
        require(info.size in 1..MAX_CHUNK && encrypted.size == info.compressed)
        val data = decrypt(encrypted, key)
        try {
            val bytes = when {
                data.take(4).toByteArray().contentEquals(byteArrayOf(86, 83, 90, 97)) -> {
                    require(data.size >= 23 && data.takeLast(3).toByteArray().contentEquals(byteArrayOf(122, 115, 118)))
                    require(littleInt(data, data.size - 11) == info.size)
                    val out = ByteArray(info.size)
                    val n = Zstd.decompress(out, data.copyOfRange(8, data.size - 15))
                    require(n == info.size.toLong() && !Zstd.isError(n))
                    out
                }
                data.take(3).toByteArray().contentEquals(byteArrayOf(86, 90, 97)) -> {
                    require(data.size >= 22 && data.takeLast(2).toByteArray().contentEquals(byteArrayOf(122, 118)))
                    require(littleInt(data, data.size - 6) == info.size)
                    val dictionary = littleInt(data, 8)
                    require(dictionary in 4096..(32 * 1024 * 1024))
                    LZMAInputStream(ByteArrayInputStream(data, 12, data.size - 22),
                        info.size.toLong(), data[7], dictionary).use { readBounded(it, info.size) }
                }
                else -> ZipInputStream(ByteArrayInputStream(data)).use { zip ->
                    require(zip.nextEntry != null)
                    readBounded(zip, info.size)
                }
            }
            require(bytes.size == info.size && MessageDigest.getInstance("SHA-1").digest(bytes).contentEquals(info.sha))
            // Steam's Adler checksum starts at zero instead of the RFC's one.
            val standard = Adler32().apply { update(bytes) }.value
            val a = ((standard and 65535) - 1 + 65521) % 65521
            val b = ((standard ushr 16) - bytes.size % 65521 + 65521) % 65521
            require((b shl 16 or a) == info.crc)
            return bytes
        } finally { data.fill(0) }
    }

    fun verified(file: File, size: Long, sha: ByteArray): Boolean = file.isFile && file.length() == size &&
        file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-1")
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            digest.digest().contentEquals(sha)
        }

    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun littleInt(bytes: ByteArray, offset: Int) = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
}
