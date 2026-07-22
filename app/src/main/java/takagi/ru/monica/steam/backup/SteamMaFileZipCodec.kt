package takagi.ru.monica.steam.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.importer.SteamMaFileBackupCodec
import takagi.ru.monica.steam.importer.SteamMaFileParser
import takagi.ru.monica.steam.importer.SteamMaFilePayload

data class SteamMaFileZipImport(
    val payloads: List<SteamMaFilePayload>,
    val skippedEntries: Int
)

class SteamMaFileZipCodec(
    private val parser: SteamMaFileParser = SteamMaFileParser()
) {
    fun encode(accounts: List<SteamAccount>): ByteArray {
        require(accounts.isNotEmpty()) { "No Steam accounts to export" }
        require(accounts.size <= MAX_ENTRIES) { "Too many Steam accounts" }

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val usedNames = mutableSetOf<String>()
            accounts.forEach { account ->
                val entryName = uniqueName(SteamMaFileBackupCodec.fileName(account), usedNames)
                val content = SteamMaFileBackupCodec.encode(account).toByteArray(Charsets.UTF_8)
                require(content.size <= MAX_ENTRY_BYTES) { "maFile entry is too large" }
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): SteamMaFileZipImport {
        require(bytes.isNotEmpty()) { "Empty ZIP" }
        require(bytes.size <= MAX_ARCHIVE_BYTES) { "ZIP is too large" }

        val payloads = mutableListOf<SteamMaFilePayload>()
        var skippedEntries = 0
        var entryCount = 0
        var totalUncompressedBytes = 0L

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                if (entryCount > MAX_ENTRIES) throw IOException("ZIP contains too many entries")

                val normalizedName = entry.name.replace('\\', '/')
                val leafName = normalizedName.substringAfterLast('/')
                val supported = !entry.isDirectory &&
                    !normalizedName.split('/').any { it == ".." } &&
                    leafName.isSupportedMaFileName()
                if (!supported) {
                    skippedEntries += 1
                    zip.closeEntry()
                    continue
                }

                val content = readEntry(zip) { bytesRead ->
                    totalUncompressedBytes += bytesRead
                    if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                        throw IOException("ZIP expands beyond the allowed size")
                    }
                }
                val payload = runCatching {
                    parser.parse(
                        maFileContent = content.toString(Charsets.UTF_8),
                        fileName = leafName,
                        allowMissingSteamId = true
                    )
                }.getOrNull()
                if (payload == null) skippedEntries += 1 else payloads += payload
                zip.closeEntry()
            }
        }

        require(payloads.isNotEmpty()) { "ZIP does not contain a valid maFile" }
        return SteamMaFileZipImport(payloads = payloads, skippedEntries = skippedEntries)
    }

    private fun readEntry(
        zip: ZipInputStream,
        onRead: (Int) -> Unit
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var entryBytes = 0
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            entryBytes += read
            if (entryBytes > MAX_ENTRY_BYTES) throw IOException("maFile entry is too large")
            onRead(read)
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun uniqueName(rawName: String, usedNames: MutableSet<String>): String {
        val safeName = rawName.substringAfterLast('/').substringAfterLast('\\')
            .ifBlank { "steam.maFile" }
        if (usedNames.add(safeName.lowercase(Locale.ROOT))) return safeName
        val base = safeName.substringBeforeLast('.', safeName)
        val extension = safeName.substringAfterLast('.', "maFile")
        var suffix = 2
        while (true) {
            val candidate = "${base}_$suffix.$extension"
            if (usedNames.add(candidate.lowercase(Locale.ROOT))) return candidate
            suffix += 1
        }
    }

    private fun String.isSupportedMaFileName(): Boolean {
        val lower = lowercase(Locale.ROOT)
        return lower.endsWith(".mafile") || lower.endsWith(".json")
    }

    companion object {
        const val MAX_ENTRIES = 500
        const val MAX_ENTRY_BYTES = 1024 * 1024
        const val MAX_ARCHIVE_BYTES = 32 * 1024 * 1024
        const val MAX_TOTAL_UNCOMPRESSED_BYTES = 64L * 1024 * 1024
    }
}
