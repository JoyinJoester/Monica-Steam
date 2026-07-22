package takagi.ru.monica.steam.quickaccess

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context

object SteamQuickAccessInstaller {
    fun requestPinAccountWidget(context: Context): Boolean {
        return requestPinWidget(context, SteamAccountStatsWidgetProvider::class.java)
    }

    fun requestPinRecentGamesWidget(context: Context): Boolean {
        return requestPinWidget(context, SteamRecentGamesWidgetProvider::class.java)
    }

    private fun requestPinWidget(
        context: Context,
        providerClass: Class<out AppWidgetProvider>
    ): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(
            ComponentName(context, providerClass),
            null,
            null
        )
    }
}
