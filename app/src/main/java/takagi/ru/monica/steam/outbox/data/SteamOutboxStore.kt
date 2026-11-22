package takagi.ru.monica.steam.outbox.data

import android.content.Context
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.outbox.domain.SteamOutboxEvent
import takagi.ru.monica.steam.outbox.domain.SteamOutboxRecord
import takagi.ru.monica.steam.outbox.domain.SteamOutboxStateMachine

/**
 * Durable, account-scoped Outbox repository. The payload is protected before
 * it reaches Room, while the dedupe key remains queryable for idempotency.
 */
class SteamOutboxStore(
    private val dao: SteamOutboxDao,
    private val protectPayload: (String) -> String,
    private val revealPayload: (String) -> String,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun enqueue(command: SteamOutboxCommand): SteamOutboxRecord {
        require(command.id.isNotBlank()) { "Outbox request id is required" }
        require(command.dedupeKey.isNotBlank()) { "Outbox dedupe key is required" }
        require(command.payload.isNotBlank()) { "Outbox payload is required" }
        dao.insert(
            command.toEntity(
                protectedAccountSteamId = protectPayload(command.accountSteamId),
                protectedPayload = protectPayload(command.payload)
            )
        )
        return requireNotNull(
            dao.getById(command.id) ?: dao.getByDedupeKey(command.dedupeKey)
        ).toDomain(revealPayload)
    }

    suspend fun claimNext(
        accountId: Long,
        now: Long = nowMillis(),
        limit: Int = DEFAULT_CLAIM_LIMIT
    ): SteamOutboxRecord? {
        dao.findReady(accountId, now, limit.coerceIn(1, MAX_CLAIM_LIMIT))
            .forEach { candidate ->
                val current = candidate.toDomain(revealPayload)
                SteamOutboxStateMachine.transition(
                    record = current,
                    event = SteamOutboxEvent.CLAIM,
                    nowMillis = now
                )
                if (dao.claim(candidate.id, now, ignoreSchedule = false) == 1) {
                    return dao.getById(candidate.id)?.toDomain(revealPayload)
                }
            }
        return null
    }

    suspend fun transition(
        id: String,
        event: SteamOutboxEvent,
        error: String? = null,
        now: Long = nowMillis(),
        forceClaim: Boolean = false
    ): SteamOutboxRecord {
        val existingEntity = requireNotNull(dao.getById(id)) {
            "Outbox item not found: $id"
        }
        val existing = existingEntity.toDomain(revealPayload)
        val transitionTime = if (event == SteamOutboxEvent.CLAIM && forceClaim) {
            maxOf(now, existing.nextAttemptAtMillis)
        } else {
            now
        }
        val next = SteamOutboxStateMachine.transition(existing, event, transitionTime, error)
        if (event == SteamOutboxEvent.CLAIM) {
            check(dao.claim(id, now, ignoreSchedule = forceClaim) == 1) {
                "Outbox item was claimed by another worker: $id"
            }
        } else {
            check(
                dao.transition(
                    id = id,
                    expectedStatus = existing.status.name,
                    status = next.status.name,
                    attemptCount = next.attemptCount,
                    nextAttemptAtMillis = next.nextAttemptAtMillis,
                    updatedAtMillis = next.updatedAtMillis,
                    lastError = next.lastError
                ) == 1
            ) { "Outbox item changed while transitioning: $id" }
        }
        return requireNotNull(dao.getById(id)).toDomain(revealPayload)
    }

    suspend fun recoverable(accountId: Long): List<SteamOutboxRecord> =
        dao.findRecoverable(accountId).map { it.toDomain(revealPayload) }

    suspend fun pruneTerminal(beforeMillis: Long): Int = dao.pruneTerminal(beforeMillis)

    companion object {
        fun from(context: Context): SteamOutboxStore {
            val appContext = context.applicationContext
            val security by lazy { SecurityManager(appContext) }
            return SteamOutboxStore(
                dao = SteamDatabase.getDatabase(appContext).steamOutboxDao(),
                protectPayload = { security.encryptDataLegacyCompat(it) },
                revealPayload = { security.decryptDataIfMonicaCiphertext(it) }
            )
        }

        private const val DEFAULT_CLAIM_LIMIT = 8
        private const val MAX_CLAIM_LIMIT = 32
    }
}

data class SteamOutboxCommand(
    val id: String,
    val accountId: Long,
    val accountSteamId: String,
    val operation: takagi.ru.monica.steam.outbox.domain.SteamOutboxOperation,
    val dedupeKey: String,
    val payload: String,
    val createdAtMillis: Long
)

private fun SteamOutboxCommand.toEntity(
    protectedAccountSteamId: String,
    protectedPayload: String
): SteamOutboxEntity =
    SteamOutboxEntity(
        id = id,
        accountId = accountId,
        accountSteamId = protectedAccountSteamId,
        operation = operation.name,
        dedupeKey = dedupeKey,
        payload = protectedPayload,
        status = takagi.ru.monica.steam.outbox.domain.SteamOutboxStatus.QUEUED.name,
        attemptCount = 0,
        nextAttemptAtMillis = createdAtMillis,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = createdAtMillis
    )

private fun SteamOutboxEntity.toDomain(revealPayload: (String) -> String): SteamOutboxRecord =
    SteamOutboxRecord(
        id = id,
        accountId = accountId,
        accountSteamId = revealPayload(accountSteamId),
        operation = takagi.ru.monica.steam.outbox.domain.SteamOutboxOperation.valueOf(operation),
        dedupeKey = dedupeKey,
        payload = revealPayload(payload),
        status = takagi.ru.monica.steam.outbox.domain.SteamOutboxStatus.valueOf(status),
        attemptCount = attemptCount,
        nextAttemptAtMillis = nextAttemptAtMillis,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        lastError = lastError
    )
