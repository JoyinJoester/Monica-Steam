package takagi.ru.monica.steam.quickaccess

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent

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
        // Pinning from an app does not run the provider's configure activity.
        // Choose the account first, then bind the returned widget ID in the success callback.
        context.startActivity(Intent(context, SteamWidgetConfigureActivity::class.java)
            .putExtra(SteamWidgetPinReceiver.EXTRA_PROVIDER, providerClass.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
