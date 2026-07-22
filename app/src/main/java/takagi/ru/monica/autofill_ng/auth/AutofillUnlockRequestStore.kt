package takagi.ru.monica.autofill_ng.auth

import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import takagi.ru.monica.autofill_ng.model.AutofillRequest

data class PendingAutofillUnlockRequest(
    val request: AutofillRequest.Fillable,
    val passwordIds: List<Long>,
    val passwordSuggestionEnabled: Boolean,
    val grantContext: AutofillGrantContext,
)

object AutofillUnlockRequests {
    private const val REQUEST_TTL_MILLIS = 120_000L
    private const val MAX_PENDING_REQUESTS = 32

    private data class Entry(
        val request: PendingAutofillUnlockRequest,
        val createdAtMillis: Long,
        val expiresAtMillis: Long,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun put(request: PendingAutofillUnlockRequest): String {
        removeExpired()
        if (entries.size >= MAX_PENDING_REQUESTS) {
            entries.entries
                .minByOrNull { it.value.createdAtMillis }
                ?.key
                ?.let(entries::remove)
        }
        val now = SystemClock.elapsedRealtime()
        val token = UUID.randomUUID().toString()
        entries[token] = Entry(
            request = request,
            createdAtMillis = now,
            expiresAtMillis = now + REQUEST_TTL_MILLIS,
        )
        return token
    }

    fun peek(token: String?): PendingAutofillUnlockRequest? {
        if (token.isNullOrBlank()) return null
        val entry = entries[token] ?: return null
        if (SystemClock.elapsedRealtime() >= entry.expiresAtMillis) {
            entries.remove(token)
            return null
        }
        return entry.request
    }

    fun consume(token: String?): PendingAutofillUnlockRequest? {
        val request = peek(token) ?: return null
        entries.remove(token)
        return request
    }

    fun discard(token: String?) {
        if (!token.isNullOrBlank()) {
            entries.remove(token)
        }
    }

    fun clear() {
        entries.clear()
    }

    private fun removeExpired() {
        val now = SystemClock.elapsedRealtime()
        entries.entries.removeIf { now >= it.value.expiresAtMillis }
    }
}
