package takagi.ru.monica.steam.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "steam_accounts",
    indices = [
        Index(value = ["steam_id"], unique = true, name = "index_steam_accounts_steam_id")
    ]
)
data class SteamAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "steam_id")
    val steamId: String,
    val accountName: String,
    val displayName: String,
    val deviceId: String,
    val sharedSecret: String,
    val identitySecret: String?,
    val revocationCode: String?,
    val tokenGid: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val steamLoginSecure: String?,
    val rawSteamGuardJson: String,
    val selected: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val groupName: String? = null,
    @ColumnInfo(defaultValue = "'[]'")
    val tagsJson: String = "[]",
    val accentArgb: Long? = null,
    @ColumnInfo(defaultValue = "''")
    val note: String = "",
    @ColumnInfo(defaultValue = "0")
    val pinned: Boolean = false,
    val lastHealthCheckAt: Long? = null
)

data class SteamAccount(
    val id: Long,
    val steamId: String,
    val accountName: String,
    val displayName: String,
    val deviceId: String,
    val sharedSecret: String,
    val identitySecret: String?,
    val revocationCode: String?,
    val tokenGid: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val steamLoginSecure: String?,
    val rawSteamGuardJson: String,
    val selected: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val groupName: String? = null,
    val tags: List<String> = emptyList(),
    val accentArgb: Long? = null,
    val note: String = "",
    val pinned: Boolean = false,
    val lastHealthCheckAt: Long? = null
) {
    val hasRealSteamId: Boolean
        get() = steamId.matches(Regex("""7656119\d{10}"""))

    val visibleSteamId: String
        get() = steamId.takeIf { hasRealSteamId }.orEmpty()

    val canUseConfirmations: Boolean
        get() = hasRealSteamId &&
            !identitySecret.isNullOrBlank() &&
            (!accessToken.isNullOrBlank() || !refreshToken.isNullOrBlank())

    val canApproveLogins: Boolean
        get() = hasRealSteamId &&
            sharedSecret.isNotBlank() &&
            (!accessToken.isNullOrBlank() || !refreshToken.isNullOrBlank())
}
