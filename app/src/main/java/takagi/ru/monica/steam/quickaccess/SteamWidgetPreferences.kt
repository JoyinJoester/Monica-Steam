package takagi.ru.monica.steam.quickaccess

import android.content.Context

internal object SteamWidgetPreferences {
    private const val NAME = "steam_home_widgets"
    private const val ACCOUNT_PREFIX = "account_"

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun accountId(context: Context, widgetId: Int): Long? {
        return prefs(context).getLong("$ACCOUNT_PREFIX$widgetId", 0L).takeIf { it != 0L }
    }

    fun databaseId(context: Context, widgetId: Int): Long? =
        prefs(context).getLong("database_$widgetId", 0L).takeIf { it > 0L }

    fun setAccountId(context: Context, widgetId: Int, accountId: Long, databaseId: Long? = null) {
        require(accountId != 0L)
        prefs(context).edit().putLong("$ACCOUNT_PREFIX$widgetId", accountId)
            .putLong("database_$widgetId", databaseId ?: 0L).apply()
    }

    fun remove(context: Context, widgetId: Int) {
        prefs(context).edit().remove("$ACCOUNT_PREFIX$widgetId").remove("database_$widgetId").apply()
    }
}
