package takagi.ru.monica.steam.alerts.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger

object SteamAlertScheduler {
    suspend fun sync(context: Context) {
        try {
            val appContext = context.applicationContext
            val settings = SteamAlertPreferences(appContext).settings.first()
            val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
            val pendingIntent = pendingIntent(appContext)
            alarmManager.cancel(pendingIntent)
            if (!settings.enabled) return
            val interval = settings.normalizedIntervalHours * 60L * 60L * 1000L
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + FIRST_CHECK_DELAY_MS,
                interval,
                pendingIntent
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            SteamDiagLogger.append(
                "alert_scheduler_sync failed type=${error::class.java.simpleName}"
            )
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, SteamAlertReceiver::class.java).setAction(ACTION_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val ACTION_CHECK = "takagi.ru.monica.steamapp.action.CHECK_ALERTS"
    private const val REQUEST_CODE = 73
    private const val FIRST_CHECK_DELAY_MS = 15L * 60L * 1000L
}
