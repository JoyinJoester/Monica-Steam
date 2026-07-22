package takagi.ru.monica.steam.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SteamLibraryCacheDao {
    @Query("SELECT * FROM steam_library_cache WHERE accountId = :accountId LIMIT 1")
    suspend fun getLibrary(accountId: Long): SteamLibraryCacheEntity?

    @Query("SELECT * FROM steam_library_cache WHERE accountId = :accountId LIMIT 1")
    fun observeLibrary(accountId: Long): Flow<SteamLibraryCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLibrary(cache: SteamLibraryCacheEntity)

    @Query("DELETE FROM steam_library_cache WHERE accountId = :accountId")
    suspend fun deleteLibrary(accountId: Long)

    @Query("SELECT * FROM steam_achievements_cache WHERE accountId = :accountId AND appId = :appId LIMIT 1")
    suspend fun getAchievements(accountId: Long, appId: Int): SteamAchievementsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAchievements(cache: SteamAchievementsCacheEntity)

    @Query("DELETE FROM steam_achievements_cache WHERE accountId = :accountId AND appId = :appId")
    suspend fun deleteAchievements(accountId: Long, appId: Int)
}
