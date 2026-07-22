package takagi.ru.monica.steam.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.library.SteamGameAchievements
import takagi.ru.monica.steam.library.SteamLibrarySnapshot

class SteamLibraryCacheRepository(
    private val dao: SteamLibraryCacheDao,
    private val securityManager: SecurityManager,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun observeLibrary(accountId: Long): Flow<SteamLibrarySnapshot?> {
        return dao.observeLibrary(accountId)
            .map { entity -> entity?.let(::decodeLibrary) }
            .flowOn(Dispatchers.Default)
    }

    suspend fun getLibrary(accountId: Long): SteamLibrarySnapshot? {
        return dao.getLibrary(accountId)?.let(::decodeLibrary)
    }

    suspend fun saveLibrary(snapshot: SteamLibrarySnapshot) {
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
        return dao.getAchievements(accountId, appId)?.let(::decodeAchievements)
    }

    suspend fun saveAchievements(details: SteamGameAchievements) {
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
}
