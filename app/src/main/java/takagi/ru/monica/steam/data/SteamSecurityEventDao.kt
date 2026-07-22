package takagi.ru.monica.steam.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SteamSecurityEventDao {
    @Query("SELECT * FROM steam_security_events ORDER BY occurredAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = SteamSecurityEventRetention.MAX_EVENTS): Flow<List<SteamSecurityEventEntity>>

    @Query(
        "SELECT * FROM steam_security_events " +
            "WHERE accountId = :accountId ORDER BY occurredAt DESC, id DESC LIMIT :limit"
    )
    fun observeRecentForAccount(
        accountId: Long,
        limit: Int = SteamSecurityEventRetention.MAX_EVENTS
    ): Flow<List<SteamSecurityEventEntity>>

    @Insert
    suspend fun insert(event: SteamSecurityEventEntity): Long

    @Query(
        "DELETE FROM steam_security_events WHERE id NOT IN " +
            "(SELECT id FROM steam_security_events ORDER BY occurredAt DESC, id DESC LIMIT :maxEvents)"
    )
    suspend fun trimToLatest(maxEvents: Int = SteamSecurityEventRetention.MAX_EVENTS)

    @Query("DELETE FROM steam_security_events")
    suspend fun deleteAll()

    @Transaction
    suspend fun insertAndTrim(event: SteamSecurityEventEntity): Long {
        val id = insert(event)
        trimToLatest()
        return id
    }
}
