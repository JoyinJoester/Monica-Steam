package takagi.ru.monica.steam.friends.chat.presentation

import takagi.ru.monica.steam.data.SteamAccount

internal class SteamChatRequestGuard {
    private var accountId: Long? = null
    private var accountSteamId: String? = null
    private var partnerSteamId: String? = null
    private var sessionsGeneration = 0L
    private var threadGeneration = 0L

    fun selectAccount(account: SteamAccount?): Long {
        accountId = account?.id
        accountSteamId = account?.steamId
        partnerSteamId = null
        threadGeneration++
        return ++sessionsGeneration
    }

    fun nextSessions(): Long = ++sessionsGeneration

    fun selectThread(partnerSteamId: String): Long {
        this.partnerSteamId = partnerSteamId
        return ++threadGeneration
    }

    fun closeThread() {
        partnerSteamId = null
        threadGeneration++
    }

    fun currentThreadGeneration(): Long = threadGeneration

    fun isSessionsCurrent(account: SteamAccount, generation: Long): Boolean =
        accountId == account.id &&
            accountSteamId == account.steamId &&
            sessionsGeneration == generation

    fun isThreadCurrent(
        account: SteamAccount,
        partnerSteamId: String,
        generation: Long
    ): Boolean = accountId == account.id &&
        accountSteamId == account.steamId &&
        this.partnerSteamId == partnerSteamId &&
        threadGeneration == generation
}
