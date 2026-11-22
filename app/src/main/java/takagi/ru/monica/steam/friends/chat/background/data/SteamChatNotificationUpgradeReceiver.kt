package takagi.ru.monica.steam.friends.chat.background.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Retire the old connection notification on upgrade, without starting a keepalive service. */
class SteamChatNotificationUpgradeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            SteamChatNotificationPublisher(context).retireLegacyConnectionNotifications()
        }
    }
}
