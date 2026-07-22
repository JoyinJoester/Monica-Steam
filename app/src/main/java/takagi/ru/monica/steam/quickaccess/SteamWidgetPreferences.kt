package takagi.ru.monica.steam.quickaccess

import android.content.Context

internal object SteamWidgetPreferences {
    private const val NAME = "steam_home_widgets"
    private const val ACCOUNT_PREFIX = "account_"

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun accountId(context: Context, widgetId: Int): Long? {
        return prefs(context).getLong("$ACCOUNT_PREFIX$widgetId", 0L).takeIf { it > 0L }
    }

    fun setAccountId(context: Context, widgetId: Int, accountId: Long) {
        prefs(context).edit().putLong("$ACCOUNT_PREFIX$widgetId", accountId).apply()
    }

    fun remove(context: Context, widgetId: Int) {
        prefs(context).edit().remove("$ACCOUNT_PREFIX$widgetId").apply()
    }
}
