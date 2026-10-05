package takagi.ru.monica.steam.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.library.SteamGameAchievements
import takagi.ru.monica.steam.library.SteamLibrarySnapshot

class SteamLibraryCacheRepository(
    private val appContext: Context,
    private val dao: SteamLibraryCacheDao,
    private val securityManager: SecurityManager,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val sourceAwareDirectory: File by lazy {
        File(appContext.noBackupFilesDir, "steam/library_cache").apply { mkdirs() }
    }
    private val sourceAwareFlows = ConcurrentHashMap<Long, MutableStateFlow<SteamLibrarySnapshot?>>()

    fun observeLibrary(accountId: Long): Flow<SteamLibrarySnapshot?> {
        if (accountId < 0L) {
            return sourceAwareFlow(accountId)
        }
        return dao.observeLibrary(accountId)
            .map { entity -> entity?.let(::decodeLibrary) }
            .flowOn(Dispatchers.Default)
    }

    suspend fun getLibrary(accountId: Long): SteamLibrarySnapshot? {
        if (accountId < 0L) return readSourceAwareLibrary(accountId)
        return dao.getLibrary(accountId)?.let(::decodeLibrary)
    }

    suspend fun saveLibrary(snapshot: SteamLibrarySnapshot) {
        if (snapshot.accountId < 0L) {
            writeSourceAwareLibrary(snapshot)
            return
        }
        libraryLocks.getOrPut(snapshot.accountId) { Mutex() }.withLock {
            saveLibraryUnlocked(snapshot)
        }
    }

    suspend fun updateLibrary(
        accountId: Long,
        transform: (SteamLibrarySnapshot?) -> SteamLibrarySnapshot?
    ): SteamLibrarySnapshot? {
        if (accountId < 0L) {
            return libraryLocks.getOrPut(accountId) { Mutex() }.withLock {
                val updated = transform(readSourceAwareLibrary(accountId)) ?: return@withLock null
                writeSourceAwareLibrary(updated)
                updated
            }
        }
        return libraryLocks.getOrPut(accountId) { Mutex() }.withLock {
            val current = dao.getLibrary(accountId)?.let(::decodeLibrary)
            val updated = transform(current) ?: return@withLock null
            saveLibraryUnlocked(updated)
            updated
        }
    }

    private suspend fun saveLibraryUnlocked(snapshot: SteamLibrarySnapshot) {
        dao.saveLibrary(
            SteamLibraryCacheEntity(
                accountId = snapshot.accountId,
                payload = securityManager.encryptDataLegacyCompat(
                    json.encodeToString(SteamLibrarySnapshot.serializer(), snapshot)
                ),
                fetchedAt = snapshot.fetchedAt
            )
        )
    }

    suspend fun getAchievements(accountId: Long, appId: Int): SteamGameAchievements? {
        if (accountId < 0L) return readSourceAwareAchievements(accountId, appId)
        return dao.getAchievements(accountId, appId)?.let(::decodeAchievements)
    }

    suspend fun saveAchievements(details: SteamGameAchievements) {
        if (details.accountId < 0L) {
            writeSourceAwareAchievements(details)
            return
        }
        dao.saveAchievements(
            SteamAchievementsCacheEntity(
                accountId = details.accountId,
                appId = details.appId,
                payload = securityManager.encryptDataLegacyCompat(
                    json.encodeToString(SteamGameAchievements.serializer(), details)
                ),
                fetchedAt = details.fetchedAt
            )
        )
    }

    private fun decodeLibrary(entity: SteamLibraryCacheEntity): SteamLibrarySnapshot? {
        return runCatching {
            json.decodeFromString(SteamLibrarySnapshot.serializer(),
                securityManager.decryptDataIfMonicaCiphertext(entity.payload)
            )
        }.getOrNull()
    }

    private fun decodeAchievements(entity: SteamAchievementsCacheEntity): SteamGameAchievements? {
        return runCatching {
            json.decodeFromString(SteamGameAchievements.serializer(),
                securityManager.decryptDataIfMonicaCiphertext(entity.payload)
            )
        }.getOrNull()
    }

    private fun sourceAwareFlow(accountId: Long): Flow<SteamLibrarySnapshot?> =
        sourceAwareFlows.getOrPut(accountId) {
            MutableStateFlow(readSourceAwareLibrary(accountId))
        }

    private fun sourceAwareLibraryFile(accountId: Long): File =
        File(sourceAwareDirectory, "$accountId.json")

    private fun sourceAwareAchievementsFile(accountId: Long, appId: Int): File =
        File(sourceAwareDirectory, "${accountId}_$appId.achievement.json")

    private fun readSourceAwareLibrary(accountId: Long): SteamLibrarySnapshot? =
        readProtected(sourceAwareLibraryFile(accountId))?.let { payload ->
            runCatching {
                json.decodeFromString(SteamLibrarySnapshot.serializer(), payload)
            }.getOrNull()
        }

    private fun writeSourceAwareLibrary(snapshot: SteamLibrarySnapshot) {
        val file = sourceAwareLibraryFile(snapshot.accountId)
        writeProtected(file, json.encodeToString(SteamLibrarySnapshot.serializer(), snapshot))
        sourceAwareFlows.getOrPut(snapshot.accountId) { MutableStateFlow(null) }.value = snapshot
    }

    private fun readSourceAwareAchievements(accountId: Long, appId: Int): SteamGameAchievements? =
        readProtected(sourceAwareAchievementsFile(accountId, appId))?.let { payload ->
            runCatching {
                json.decodeFromString(SteamGameAchievements.serializer(), payload)
            }.getOrNull()
        }

    private fun writeSourceAwareAchievements(details: SteamGameAchievements) {
        writeProtected(
            sourceAwareAchievementsFile(details.accountId, details.appId),
            json.encodeToString(SteamGameAchievements.serializer(), details)
        )
    }

    private fun readProtected(file: File): String? = takagi.ru.monica.steam.security.SteamProtectedFileAccess.withFile(file) { runCatching {
        if (!file.exists()) return@runCatching null
        val encrypted = AtomicFile(file).readFully().toString(Charsets.UTF_8)
        securityManager.decryptDataIfMonicaCiphertext(encrypted)
    }.getOrNull() }

    private fun writeProtected(file: File, payload: String) = takagi.ru.monica.steam.security.SteamProtectedFileAccess.withFile(file) {
        val atomicFile = AtomicFile(file)
        val stream = atomicFile.startWrite()
        try {
            stream.write(securityManager.encryptDataLegacyCompat(payload).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    private companion object {
        val libraryLocks = ConcurrentHashMap<Long, Mutex>()
    }
}
