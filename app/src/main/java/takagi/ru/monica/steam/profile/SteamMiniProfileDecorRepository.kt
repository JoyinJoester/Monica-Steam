package takagi.ru.monica.steam.profile

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SteamMiniProfileDecor(
    val personaName: String,
    val level: Int?,
    val avatarFrameUrl: String?,
    val avatarUrl: String? = null,
    val currentGameName: String? = null,
    val currentGameAppId: Int? = null,
    val currentGameImageUrl: String? = null
)

class SteamMiniProfileDecorRepository private constructor(
    context: Context,
    private val service: SteamMiniProfileBackgroundService
) {
    private val metadata = context.applicationContext.getSharedPreferences(
        "steam_mini_profile_decor_metadata",
        Context.MODE_PRIVATE
    )
    private val accountLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun load(steamId: String): SteamMiniProfileDecor? {
        if (SteamMiniProfileBackgroundService.steamIdToAccountId(steamId) == null) return null
        val lock = accountLocks.getOrPut(steamId) { Mutex() }
        return lock.withLock {
            val now = System.currentTimeMillis()
            val cached = read(steamId)
            val summary = if (cached != null && now - cached.fetchedAt < METADATA_TTL_MILLIS) {
                cached.summary
            } else {
                val fetched = runCatching {
                    withContext(Dispatchers.IO) { service.fetchProfile(steamId) }
                }.getOrNull()
                if (fetched != null) {
                    write(steamId, fetched, now)
                    fetched
                } else {
                    cached?.summary?.let { summary ->
                        if (now - cached.fetchedAt <= CURRENT_GAME_MAX_STALE_MILLIS) {
                            summary
                        } else {
                            summary.copy(
                                currentGameName = null,
                                currentGameAppId = null,
                                currentGameImageUrl = null
                            )
                        }
                    }
                }
            } ?: return@withLock null
            SteamMiniProfileDecor(
                personaName = summary.personaName,
                level = summary.level,
                avatarFrameUrl = summary.avatarFrameUrl,
                avatarUrl = summary.avatarUrl,
                currentGameName = summary.currentGameName,
                currentGameAppId = summary.currentGameAppId,
                currentGameImageUrl = summary.currentGameImageUrl
            )
        }
    }

    private fun read(steamId: String): MetadataEntry? {
        val fetchedAt = metadata.getLong("${steamId}_fetched_at", 0L)
        if (fetchedAt <= 0L) return null
        val hasLevel = metadata.contains("${steamId}_level")
        return MetadataEntry(
            fetchedAt = fetchedAt,
            summary = SteamMiniProfileSummary(
                personaName = metadata.getString("${steamId}_persona_name", null).orEmpty(),
                level = if (hasLevel) metadata.getInt("${steamId}_level", 0) else null,
                avatarUrl = metadata.getString("${steamId}_avatar_url", null)
                    ?.takeIf(String::isNotBlank),
                avatarFrameUrl = metadata.getString("${steamId}_avatar_frame_url", null)
                    ?.takeIf(String::isNotBlank),
                background = null,
                currentGameName = metadata.getString("${steamId}_current_game_name", null)
                    ?.takeIf(String::isNotBlank),
                currentGameAppId = metadata.getInt("${steamId}_current_game_app_id", 0)
                    .takeIf { it > 0 },
                currentGameImageUrl = metadata.getString("${steamId}_current_game_image_url", null)
                    ?.takeIf(String::isNotBlank)
            )
        )
    }

    private fun write(steamId: String, summary: SteamMiniProfileSummary, fetchedAt: Long) {
        metadata.edit()
            .putLong("${steamId}_fetched_at", fetchedAt)
            .putString("${steamId}_persona_name", summary.personaName)
            .putString("${steamId}_avatar_url", summary.avatarUrl.orEmpty())
            .putString("${steamId}_avatar_frame_url", summary.avatarFrameUrl.orEmpty())
            .putString("${steamId}_current_game_name", summary.currentGameName.orEmpty())
            .putString("${steamId}_current_game_image_url", summary.currentGameImageUrl.orEmpty())
            .apply {
                if (summary.level != null) putInt("${steamId}_level", summary.level)
                else remove("${steamId}_level")
                if (summary.currentGameAppId != null) {
                    putInt("${steamId}_current_game_app_id", summary.currentGameAppId)
                } else {
                    remove("${steamId}_current_game_app_id")
                }
            }
            .apply()
    }

    private data class MetadataEntry(
        val fetchedAt: Long,
        val summary: SteamMiniProfileSummary
    )

    companion object {
        private const val METADATA_TTL_MILLIS = 5L * 60L * 1000L
        private const val CURRENT_GAME_MAX_STALE_MILLIS = 15L * 60L * 1000L

        @Volatile
        private var instance: SteamMiniProfileDecorRepository? = null

        fun get(context: Context): SteamMiniProfileDecorRepository {
            return instance ?: synchronized(this) {
                instance ?: SteamMiniProfileDecorRepository(
                    context = context.applicationContext,
                    service = SteamMiniProfileBackgroundService()
                ).also { instance = it }
            }
        }
    }
}
