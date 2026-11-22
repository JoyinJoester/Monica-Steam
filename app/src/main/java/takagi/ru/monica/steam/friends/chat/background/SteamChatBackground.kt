package takagi.ru.monica.steam.friends.chat.background

import android.content.Context
import android.content.Intent
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.friends.chat.background.data.SteamChatNotificationRuntime
import takagi.ru.monica.steam.friends.chat.background.data.SteamChatNotificationContract
import takagi.ru.monica.steam.friends.chat.background.data.activateChatNotificationTarget
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationRequest

class SteamChatNotificationTarget internal constructor(
    internal val request: SteamChatNotificationRequest
) {
    val partnerSteamId: String?
        get() = request.partnerSteamId
}

object SteamChatBackground {
    fun consumeNotification(intent: Intent?): SteamChatNotificationTarget? {
        return SteamChatNotificationContract.consume(intent)
            ?.let(::SteamChatNotificationTarget)
    }

    suspend fun activateNotificationTarget(
        context: Context,
        target: SteamChatNotificationTarget
    ): Boolean {
        return SteamAccountSourceRepository.get(context.applicationContext)
            .activateChatNotificationTarget(target.request)
    }

    suspend fun syncNotifications(context: Context) {
        SteamChatNotificationRuntime.sync(context.applicationContext)
    }
}
