package takagi.ru.monica.steam.backup

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount

class SteamMaFileZipCodecTest {
    private val codec = SteamMaFileZipCodec()

    @Test
    fun roundTripExportsOnlyMaFilesAndRestoresAccounts() {
        val bytes = codec.encode(
            listOf(
                account(1, "76561198000000000", "alpha"),
                account(2, "76561198000000001", "beta")
            )
        )

        val names = mutableListOf<String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
            }
        }
        val restored = codec.decode(bytes)

        assertEquals(2, names.size)
        assertTrue(names.all { it.endsWith(".maFile") })
        assertEquals(listOf("alpha", "beta"), restored.payloads.map { it.accountName })
        assertEquals(0, restored.skippedEntries)
    }

    @Test
    fun ignoresNonMaFileEntriesWithoutExtractingPaths() {
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("../outside.maFile"))
                zip.write("{}".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("notes.txt"))
                zip.write("not a mafile".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("valid/76561198000000000.maFile"))
                zip.write(maFile("76561198000000000", "valid").toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val restored = codec.decode(bytes)

        assertEquals(1, restored.payloads.size)
        assertEquals("valid", restored.payloads.single().accountName)
        assertEquals(2, restored.skippedEntries)
    }

    private fun account(id: Long, steamId: String, name: String): SteamAccount {
        val now = System.currentTimeMillis()
        return SteamAccount(
            id = id,
            steamId = steamId,
            accountName = name,
            displayName = name,
            deviceId = "android:test",
            sharedSecret = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=",
            identitySecret = "YWJjZGVmZ2hpamtsbW5vcHFyc3Q=",
            revocationCode = null,
            tokenGid = null,
            accessToken = null,
            refreshToken = null,
            steamLoginSecure = null,
            rawSteamGuardJson = maFile(steamId, name),
            selected = id == 1L,
            sortOrder = id.toInt(),
            createdAt = now,
            updatedAt = now
        )
    }

    private fun maFile(steamId: String, name: String): String = """
        {
          "steamid": "$steamId",
          "account_name": "$name",
          "device_id": "android:test",
          "shared_secret": "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=",
          "identity_secret": "YWJjZGVmZ2hpamtsbW5vcHFyc3Q="
        }
    """.trimIndent()
}
