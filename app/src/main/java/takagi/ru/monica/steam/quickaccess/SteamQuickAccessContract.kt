package takagi.ru.monica.steam.quickaccess

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import takagi.ru.monica.MonicaSteamActivity

object SteamQuickAccessContract {
    const val ACTION_OPEN_STEAM = "takagi.ru.monica.steamapp.action.OPEN_STEAM"
    const val EXTRA_WIDGET_KIND = "widget_kind"
    const val KIND_ACCOUNT_STATS = "account_stats"
    const val KIND_RECENT_GAMES = "recent_games"

    fun activityIntent(context: Context): Intent {
        return Intent(context, MonicaSteamActivity::class.java).apply {
            action = ACTION_OPEN_STEAM
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    fun pendingIntent(context: Context, requestCode: Int): PendingIntent {
        return PendingIntent.getActivity(
            context,
            requestCode,
            activityIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
