package takagi.ru.monica.steam.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "steam_security_events",
    foreignKeys = [
        ForeignKey(
            entity = SteamAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId"), Index("occurredAt")]
)
data class SteamSecurityEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val accountId: Long?,
    val type: String,
    val severity: String,
    val summary: String,
    val detail: String?,
    val occurredAt: Long = System.currentTimeMillis()
)
