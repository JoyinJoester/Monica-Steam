package takagi.ru.monica.steam.backup.share

import java.security.SecureRandom

/** Owns each input array, including on failure. No disk, account names or URI grants here. */
internal class OneShotMaFileStore(private val now: () -> Long) {
    internal class Payload(val bytes: ByteArray, val expiresAt: Long)
    private class Ticket(val uid: Int, val payload: Payload)
    private val entries = mutableMapOf<String, Ticket>()
    private val random = SecureRandom()

    @Synchronized fun issue(uid: Int, bytes: ByteArray): String {
        try {
            prune()
            require(uid >= 0 && bytes.isNotEmpty() && bytes.size <= SteamMaFileShareContract.MAX_BYTES)
            check(entries.size < 4)
            var token: String
            do {
                token = ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it) }
            } while (entries.containsKey(token))
            entries[token] = Ticket(uid, Payload(bytes, now() + SteamMaFileShareContract.TTL_MS))
            return token
        } catch (error: Throwable) {
            bytes.fill(0)
            throw error
        }
    }

    @Synchronized fun size(token: String, uid: Int): Int? {
        prune()
        return entries[token]?.takeIf { it.uid == uid }?.payload?.bytes?.size
    }

    /** Wrong UIDs cannot read or burn the real recipient's ticket. Caller must wipe on completion. */
    @Synchronized fun consume(token: String, uid: Int): Payload? {
        prune()
        val ticket = entries[token]?.takeIf { it.uid == uid } ?: return null
        entries.remove(token)
        return ticket.payload
    }

    @Synchronized fun revoke(token: String) {
        entries.remove(token)?.payload?.bytes?.fill(0)
    }

    private fun prune() {
        val time = now()
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            val ticket = iterator.next()
            if (time >= ticket.payload.expiresAt) {
                ticket.payload.bytes.fill(0)
                iterator.remove()
            }
        }
    }
}
