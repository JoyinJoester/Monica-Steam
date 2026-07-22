package takagi.ru.monica.steam.quickaccess

import android.content.Context
import android.widget.RemoteViews
import takagi.ru.monica.R

internal object SteamWidgetRenderer {
    fun accountStats(
        context: Context,
        widgetId: Int,
        snapshot: SteamWidgetSnapshot?
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.steam_account_stats_widget).apply {
            setOnClickPendingIntent(
                R.id.steam_account_stats_widget_root,
                SteamQuickAccessContract.pendingIntent(context, widgetId)
            )
            if (snapshot == null) {
                setTextViewText(R.id.steam_account_stats_name, context.getString(R.string.steam_widget_no_cached_data))
                setTextViewText(R.id.steam_account_stats_status, context.getString(R.string.steam_widget_open_to_refresh))
                setTextViewText(R.id.steam_account_stats_playtime, "—")
                setTextViewText(R.id.steam_account_stats_inventory, "—")
                setTextViewText(R.id.steam_account_stats_value, "—")
            } else {
                setTextViewText(R.id.steam_account_stats_name, snapshot.displayName)
                setTextViewText(R.id.steam_account_stats_status, context.getString(R.string.steam_widget_account_summary))
                setTextViewText(
                    R.id.steam_account_stats_playtime,
                    SteamWidgetDataLoader.formatPlaytime(snapshot.totalPlaytimeMinutes)
                )
                setTextViewText(R.id.steam_account_stats_inventory, snapshot.inventoryCount.toString())
                setTextViewText(
                    R.id.steam_account_stats_value,
                    SteamWidgetDataLoader.formatValue(snapshot.valueMinor, snapshot.currency)
                )
                snapshot.avatar?.let { setImageViewBitmap(R.id.steam_account_stats_avatar, it) }
            }
            setContentDescription(
                R.id.steam_account_stats_widget_root,
                context.getString(R.string.steam_widget_account_content_description)
            )
        }
    }

    fun recentGames(
        context: Context,
        widgetId: Int,
        snapshot: SteamWidgetSnapshot?,
        showSecondGame: Boolean
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.steam_recent_games_widget).apply {
            setOnClickPendingIntent(
                R.id.steam_recent_games_widget_root,
                SteamQuickAccessContract.pendingIntent(context, widgetId)
            )
            if (snapshot == null) {
                setTextViewText(R.id.steam_recent_games_account, context.getString(R.string.steam_widget_no_cached_data))
                setTextViewText(R.id.steam_recent_games_empty, context.getString(R.string.steam_widget_open_to_refresh))
                setViewVisibility(R.id.steam_recent_games_empty, android.view.View.VISIBLE)
                setViewVisibility(R.id.steam_recent_game_row_one, android.view.View.GONE)
                setViewVisibility(R.id.steam_recent_game_row_two, android.view.View.GONE)
            } else {
                setTextViewText(R.id.steam_recent_games_account, snapshot.displayName)
                val first = snapshot.games.firstOrNull()
                val second = snapshot.games.drop(1).firstOrNull()
                bindGame(context, this, R.id.steam_recent_game_row_one, first)
                if (showSecondGame && second != null) {
                    bindGame(context, this, R.id.steam_recent_game_row_two, second)
                    setViewVisibility(R.id.steam_recent_game_row_two, android.view.View.VISIBLE)
                } else {
                    setViewVisibility(R.id.steam_recent_game_row_two, android.view.View.GONE)
                }
                setViewVisibility(
                    R.id.steam_recent_game_row_one,
                    if (first == null) android.view.View.GONE else android.view.View.VISIBLE
                )
                setViewVisibility(
                    R.id.steam_recent_games_empty,
                    if (first == null) android.view.View.VISIBLE else android.view.View.GONE
                )
                if (first == null) {
                    setTextViewText(R.id.steam_recent_games_empty, context.getString(R.string.steam_widget_no_recent_games))
                }
            }
            setContentDescription(
                R.id.steam_recent_games_widget_root,
                context.getString(R.string.steam_widget_recent_content_description)
            )
        }
    }

    private fun bindGame(
        context: Context,
        views: RemoteViews,
        rowId: Int,
        game: SteamWidgetGame?
    ) {
        if (game == null) return
        val (nameId, playtimeId, imageId, stateId) = when (rowId) {
            R.id.steam_recent_game_row_one -> Quad(
                R.id.steam_recent_game_name_one,
                R.id.steam_recent_game_time_one,
                R.id.steam_recent_game_image_one,
                R.id.steam_recent_game_state_one
            )
            else -> Quad(
                R.id.steam_recent_game_name_two,
                R.id.steam_recent_game_time_two,
                R.id.steam_recent_game_image_two,
                R.id.steam_recent_game_state_two
            )
        }
        views.setTextViewText(nameId, game.name)
        views.setTextViewText(
            playtimeId,
            if (game.isCurrentlyPlaying) context.getString(R.string.steam_widget_now_playing)
            else SteamWidgetDataLoader.formatGamePlaytime(game.playtimeMinutes)
        )
        views.setTextViewText(stateId, if (game.isCurrentlyPlaying) "●" else "")
        game.image?.let { views.setImageViewBitmap(imageId, it) }
    }

    private data class Quad(val nameId: Int, val timeId: Int, val imageId: Int, val stateId: Int)
}
