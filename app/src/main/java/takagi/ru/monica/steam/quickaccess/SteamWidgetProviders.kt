package takagi.ru.monica.steam.quickaccess

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.BroadcastReceiver
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class SteamBaseWidgetProvider : AppWidgetProvider() {
    final override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        refresh(context, appWidgetManager, appWidgetIds, goAsync())
    }

    final override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        refresh(context, appWidgetManager, intArrayOf(appWidgetId), goAsync())
    }

    final override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { SteamWidgetPreferences.remove(context, it) }
    }

    internal fun refresh(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        pendingResult: BroadcastReceiver.PendingResult? = null
    ) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ids.forEach { widgetId ->
                    val accountId = SteamWidgetPreferences.accountId(context, widgetId)
                    val snapshot = accountId?.let {
                        runCatching { SteamWidgetDataLoader.load(context, it) }.getOrNull()
                    }
                    manager.updateAppWidget(widgetId, render(context, manager, widgetId, snapshot))
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    protected abstract fun render(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        snapshot: SteamWidgetSnapshot?
    ): android.widget.RemoteViews
}

class SteamAccountStatsWidgetProvider : SteamBaseWidgetProvider() {
    override fun render(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        snapshot: SteamWidgetSnapshot?
    ) = SteamWidgetRenderer.accountStats(context, widgetId, snapshot)
}

class SteamRecentGamesWidgetProvider : SteamBaseWidgetProvider() {
    override fun render(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        snapshot: SteamWidgetSnapshot?
    ) = SteamWidgetRenderer.recentGames(
        context = context,
        widgetId = widgetId,
        snapshot = snapshot,
        showSecondGame = shouldShowTwoGames(manager.getAppWidgetOptions(widgetId))
    )

    companion object {
        internal fun shouldShowTwoGames(options: Bundle): Boolean {
            return shouldShowTwoGames(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            )
        }

        internal fun shouldShowTwoGames(minHeightDp: Int): Boolean {
            return minHeightDp >= 120
        }
    }
}

object SteamWidgetUpdater {
    fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        listOf(
            SteamAccountStatsWidgetProvider::class.java,
            SteamRecentGamesWidgetProvider::class.java
        ).forEach { providerClass ->
            val ids = manager.getAppWidgetIds(ComponentName(appContext, providerClass))
            if (ids.isEmpty()) return@forEach
            val provider = when (providerClass) {
                SteamAccountStatsWidgetProvider::class.java -> SteamAccountStatsWidgetProvider()
                else -> SteamRecentGamesWidgetProvider()
            }
            provider.refresh(appContext, manager, ids)
        }
    }

    fun refresh(context: Context, widgetId: Int) {
        val manager = AppWidgetManager.getInstance(context)
        val info = manager.getAppWidgetInfo(widgetId) ?: return
        val provider = when (info.provider.className) {
            SteamAccountStatsWidgetProvider::class.java.name -> SteamAccountStatsWidgetProvider()
            SteamRecentGamesWidgetProvider::class.java.name -> SteamRecentGamesWidgetProvider()
            else -> return
        }
        provider.refresh(context.applicationContext, manager, intArrayOf(widgetId))
    }
}
