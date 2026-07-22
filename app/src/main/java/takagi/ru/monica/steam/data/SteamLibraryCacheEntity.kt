package takagi.ru.monica.steam.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "steam_library_cache",
    foreignKeys = [
        ForeignKey(
            entity = SteamAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SteamLibraryCacheEntity(
    @PrimaryKey val accountId: Long,
    val payload: String,
    val fetchedAt: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "steam_achievements_cache",
    primaryKeys = ["accountId", "appId"],
    foreignKeys = [
        ForeignKey(
            entity = SteamAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SteamAchievementsCacheEntity(
    val accountId: Long,
    val appId: Int,
    val payload: String,
    val fetchedAt: Long,
    val updatedAt: Long = System.currentTimeMillis()
)
