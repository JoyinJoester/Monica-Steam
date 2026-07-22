package takagi.ru.monica.steam.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import takagi.ru.monica.security.SecurityManager

class SteamSecurityEventRepository(
    private val dao: SteamSecurityEventDao,
    private val securityManager: SecurityManager
) {
    fun observeRecent(limit: Int = SteamSecurityEventRetention.MAX_EVENTS): Flow<List<SteamSecurityEvent>> {
        return dao.observeRecent(limit).map { rows -> rows.map(::decrypt) }.flowOn(Dispatchers.Default)
    }

    fun observeRecentForAccount(
        accountId: Long,
        limit: Int = SteamSecurityEventRetention.MAX_EVENTS
    ): Flow<List<SteamSecurityEvent>> {
        return dao.observeRecentForAccount(accountId, limit)
            .map { rows -> rows.map(::decrypt) }
            .flowOn(Dispatchers.Default)
    }

    suspend fun record(
        accountId: Long?,
        type: SteamSecurityEventType,
        severity: SteamSecurityEventSeverity,
        summary: String,
        detail: String? = null,
        occurredAt: Long = System.currentTimeMillis()
    ): Long {
        val safeSummary = SteamSecurityEventSanitizer.sanitize(summary).ifBlank { type.name }
        val safeDetail = detail?.let(SteamSecurityEventSanitizer::sanitize)?.takeIf(String::isNotBlank)
        return dao.insertAndTrim(
            SteamSecurityEventEntity(
                accountId = accountId,
                type = type.name,
                severity = severity.name,
                summary = securityManager.encryptDataLegacyCompat(safeSummary),
                detail = safeDetail?.let(securityManager::encryptDataLegacyCompat),
                occurredAt = occurredAt
            )
        )
    }

    suspend fun clear() = dao.deleteAll()

    private fun decrypt(entity: SteamSecurityEventEntity): SteamSecurityEvent {
        return SteamSecurityEvent(
            id = entity.id,
            accountId = entity.accountId,
            type = enumValueOrDefault(entity.type, SteamSecurityEventType.HEALTH_CHECK),
            severity = enumValueOrDefault(entity.severity, SteamSecurityEventSeverity.INFO),
            summary = securityManager.decryptDataIfMonicaCiphertext(entity.summary),
            detail = entity.detail?.let(securityManager::decryptDataIfMonicaCiphertext),
            occurredAt = entity.occurredAt
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T {
        return enumValues<T>().firstOrNull { it.name == value } ?: fallback
    }
}
