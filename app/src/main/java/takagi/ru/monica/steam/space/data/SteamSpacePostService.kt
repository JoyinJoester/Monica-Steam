package takagi.ru.monica.steam.space.data

import java.util.concurrent.CancellationException
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmGateway
import takagi.ru.monica.steam.space.domain.SteamSpacePostGateway
import takagi.ru.monica.steam.space.domain.isValidSteamSpacePost

internal class SteamSpacePostService(private val cm: SteamCmGateway = SteamCmClient()) : SteamSpacePostGateway {
    override fun publish(account: SteamAccount, text: String) {
        require(isValidSteamSpacePost(text))
        if (!account.hasRealSteamId || account.accessToken.isNullOrBlank())
            throw SteamApiException("A Steam session is required", authenticationRequired = true)
        if (Thread.currentThread().isInterrupted) throw CancellationException("Posting cancelled")
        // General friends' status, without falsely associating a game. The response body is empty;
        // CM validates the matching response job and EResult before returning it.
        cm.callService(account, METHOD, SteamProtoWriter().apply {
            writeVarint(1, 0L)
            writeString(2, text)
        }.toByteArray())
    }

    companion object { internal const val METHOD = "Player.PostStatusToFriends#1" }
}
