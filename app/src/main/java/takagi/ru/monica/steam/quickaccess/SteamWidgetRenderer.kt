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
                if (snapshot == null) SteamQuickAccessContract.configureIntent(context, widgetId)
                else SteamQuickAccessContract.pendingIntent(context, widgetId)
            )
            if (snapshot == null) {
                setTextViewText(R.id.steam_account_stats_name, context.getString(if (SteamWidgetPreferences.accountId(context, widgetId) == null)
                    R.string.steam_widget_choose_account else R.string.steam_widget_unavailable))
                setTextViewText(R.id.steam_account_stats_status, context.getString(R.string.steam_widget_tap_configure))
                setTextViewText(R.id.steam_account_stats_playtime, "—")
                setTextViewText(R.id.steam_account_stats_inventory, "—")
                setTextViewText(R.id.steam_account_stats_value, "—")
            } else {
                setTextViewText(R.id.steam_account_stats_name, snapshot.displayName)
                setTextViewText(R.id.steam_account_stats_status, snapshot.fetchedAt?.let {
                    context.getString(R.string.steam_widget_updated, android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it)))
                } ?: context.getString(R.string.steam_widget_open_to_refresh))
                setTextViewText(
                    R.id.steam_account_stats_playtime,
                    snapshot.totalPlaytimeMinutes?.let(SteamWidgetDataLoader::formatPlaytime) ?: "—"
                )
                setTextViewText(R.id.steam_account_stats_inventory, snapshot.inventoryCount?.toString() ?: "—")
                setTextViewText(
                    R.id.steam_account_stats_value,
                    snapshot.valueMinor?.let { SteamWidgetDataLoader.formatValue(it, snapshot.currency) } ?: "—"
                )
                snapshot.avatar?.let { setImageViewBitmap(R.id.steam_account_stats_avatar, circularAvatar(it)) }
            }
            setOnClickPendingIntent(R.id.steam_account_stats_configure, SteamQuickAccessContract.configureIntent(context, widgetId))
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
        showSecondGame: Boolean,
        narrow: Boolean = false
    ): RemoteViews {
        return RemoteViews(context.packageName, if (narrow) R.layout.steam_recent_games_widget_narrow else R.layout.steam_recent_games_widget).apply {
            setOnClickPendingIntent(
                R.id.steam_recent_games_widget_root,
                if (snapshot == null) SteamQuickAccessContract.configureIntent(context, widgetId)
                else SteamQuickAccessContract.pendingIntent(context, widgetId)
            )
            if (snapshot == null) {
                setTextViewText(R.id.steam_recent_games_account, context.getString(if (SteamWidgetPreferences.accountId(context, widgetId) == null)
                    R.string.steam_widget_choose_account else R.string.steam_widget_unavailable))
                setTextViewText(R.id.steam_recent_games_empty, context.getString(R.string.steam_widget_tap_configure))
                setViewVisibility(R.id.steam_recent_games_empty, android.view.View.VISIBLE)
                setViewVisibility(R.id.steam_recent_game_row_one, android.view.View.GONE)
                setViewVisibility(R.id.steam_recent_game_row_two, android.view.View.GONE)
            } else {
                setTextViewText(R.id.steam_recent_games_account, snapshot.displayName)
                snapshot.avatar?.let { setImageViewBitmap(R.id.steam_recent_games_avatar, circularAvatar(it)) }
                val first = snapshot.games.firstOrNull()
                val second = snapshot.games.drop(1).firstOrNull()
                bindGame(context, this, R.id.steam_recent_game_row_one, first, grouped = showSecondGame && second != null)
                if (showSecondGame && second != null) {
                    bindGame(context, this, R.id.steam_recent_game_row_two, second, grouped = true)
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
                    setTextViewText(R.id.steam_recent_games_empty, context.getString(if (snapshot.fetchedAt == null) R.string.steam_widget_open_to_refresh else R.string.steam_widget_no_recent_games))
                }
            }
            setOnClickPendingIntent(R.id.steam_recent_games_configure, SteamQuickAccessContract.configureIntent(context, widgetId))
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
        game: SteamWidgetGame?,
        grouped: Boolean
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
            else context.getString(R.string.steam_widget_recent_time, SteamWidgetDataLoader.formatGamePlaytime(game.playtimeMinutes))
        )
        views.setTextViewText(
            stateId,
            if (game.isCurrentlyPlaying) context.getString(R.string.steam_widget_now_playing) else ""
        )
        views.setViewVisibility(stateId, android.view.View.GONE)
        game.image?.let { views.setImageViewBitmap(imageId, it) }
        val background = when {
            !grouped -> if (game.isCurrentlyPlaying) R.drawable.steam_widget_primary_surface else R.drawable.steam_widget_row_surface
            rowId == R.id.steam_recent_game_row_one -> if (game.isCurrentlyPlaying) R.drawable.steam_widget_game_top_live else R.drawable.steam_widget_game_top
            else -> if (game.isCurrentlyPlaying) R.drawable.steam_widget_game_bottom_live else R.drawable.steam_widget_game_bottom
        }
        views.setInt(rowId, "setBackgroundResource", background)
    }

    private fun circularAvatar(source: android.graphics.Bitmap): android.graphics.Bitmap {
        val size = minOf(source.width, source.height, 128)
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        val side = minOf(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        canvas.drawBitmap(source, android.graphics.Rect(left, top, left + side, top + side),
            android.graphics.Rect(0, 0, size, size), paint)
        return bitmap
    }

    private data class Quad(val nameId: Int, val timeId: Int, val imageId: Int, val stateId: Int)
}
