package takagi.ru.monica.steam.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import takagi.ru.monica.MonicaSteamActivity
import takagi.ru.monica.R
import takagi.ru.monica.steam.network.SteamPendingLogin

object SteamLoginNotificationHelper {
    private const val CHANNEL_ID = "steam_login_requests"
    private const val NOTIFICATION_ID_BASE = 770_000

    fun show(context: Context, login: SteamPendingLogin) {
        val appContext = context.applicationContext
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createChannel(appContext)
        val contentIntent = PendingIntent.getActivity(
            appContext,
            login.clientId.hashCode(),
            Intent(appContext, MonicaSteamActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val device = login.deviceName.ifBlank {
            appContext.getString(R.string.steam_unknown_device)
        }
        val location = login.location.ifBlank { "-" }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_key_24dp)
            .setContentTitle(appContext.getString(R.string.steam_login_request_title))
            .setContentText("$device - $location")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$device\n$location\n${appContext.getString(R.string.steam_client_id_fallback, login.clientId)}"
                )
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        NotificationManagerCompat.from(appContext).notify(
            NOTIFICATION_ID_BASE + login.clientId.hashCode().and(0x0fff),
            notification
        )
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.nav_steam),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.steam_login_request_title)
            }
        )
    }
}
