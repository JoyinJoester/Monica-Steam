package takagi.ru.monica.steam.quickaccess

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.UUID

class SteamWidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val manager = AppWidgetManager.getInstance(context)
        val provider = manager.getAppWidgetInfo(widgetId)?.provider ?: return
        val expected = intent.getStringExtra(EXTRA_PROVIDER) ?: return
        if (!isWidgetProvider(expected) || provider != ComponentName(context, expected)) return
        bind(context, widgetId, intent)
    }

    internal fun bind(context: Context, widgetId: Int, intent: Intent) {
        val accountId = intent.getLongExtra(EXTRA_ACCOUNT, 0L)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || accountId == 0L) return
        SteamWidgetPreferences.setAccountId(context, widgetId, accountId,
            intent.getLongExtra(EXTRA_DATABASE, 0L).takeIf { it > 0 })
        SteamWidgetUpdater.refresh(context, widgetId)
    }

    companion object {
        internal const val EXTRA_PROVIDER = "steam_widget_provider"
        internal const val EXTRA_ACCOUNT = "steam_widget_account"
        internal const val EXTRA_DATABASE = "steam_widget_database"

        internal fun isWidgetProvider(name: String?): Boolean = name != null && name in listOf(
            SteamAccountStatsWidgetProvider::class.java.name,
            SteamRecentGamesWidgetProvider::class.java.name
        )

        internal fun callback(context: Context, provider: String, accountId: Long, databaseId: Long?): PendingIntent {
            val intent = Intent(context, SteamWidgetPinReceiver::class.java)
                .setData(Uri.parse("monica-widget://pin/${UUID.randomUUID()}"))
                .putExtra(EXTRA_PROVIDER, provider)
                .putExtra(EXTRA_ACCOUNT, accountId)
                .putExtra(EXTRA_DATABASE, databaseId ?: 0L)
            // The launcher fills EXTRA_APPWIDGET_ID; the component and account are already fixed.
            return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        }
    }
}
