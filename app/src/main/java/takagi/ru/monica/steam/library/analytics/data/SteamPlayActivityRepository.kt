package takagi.ru.monica.steam.library.analytics.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.library.SteamLibrarySnapshot
import takagi.ru.monica.steam.library.analytics.domain.SteamPlayActivityHistory
import takagi.ru.monica.steam.library.analytics.domain.updateSteamPlayActivity

class SteamPlayActivityRepository(
    context: Context,
    private val securityManager: SecurityManager,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val directory = File(context.applicationContext.filesDir, "steam_play_activity")
    private val mutex = Mutex()

    suspend fun load(accountId: Long): SteamPlayActivityHistory? = mutex.withLock {
        read(accountId)
    }

    suspend fun recordSnapshot(
        snapshot: SteamLibrarySnapshot,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): SteamPlayActivityHistory = mutex.withLock {
        val history = updateSteamPlayActivity(
            previous = read(snapshot.accountId),
            snapshot = snapshot,
            localDate = LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId).toString(),
            recordedAt = nowMillis
        )
        write(history)
        history
    }

    private fun read(accountId: Long): SteamPlayActivityHistory? {
        val file = AtomicFile(fileFor(accountId))
        if (!file.baseFile.exists()) return null
        return runCatching {
            val encrypted = file.readFully().toString(Charsets.UTF_8)
            val payload = securityManager.decryptDataIfMonicaCiphertext(encrypted)
            json.decodeFromString(SteamPlayActivityHistory.serializer(), payload)
                .takeIf { it.accountId == accountId }
        }.getOrNull()
    }

    private fun write(history: SteamPlayActivityHistory) {
        directory.mkdirs()
        val atomicFile = AtomicFile(fileFor(history.accountId))
        val payload = json.encodeToString(SteamPlayActivityHistory.serializer(), history)
        val encrypted = securityManager.encryptDataLegacyCompat(payload).toByteArray(Charsets.UTF_8)
        val output = atomicFile.startWrite()
        try {
            output.write(encrypted)
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private fun fileFor(accountId: Long): File = File(directory, "$accountId.json.enc")
}
