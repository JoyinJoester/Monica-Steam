package takagi.ru.monica.steam.friends.chat.info.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryCategory
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryRange

@Composable
internal fun steamHistoryCategoryLabel(category: SteamChatHistoryCategory): String = stringResource(when (category) {
    SteamChatHistoryCategory.ALL -> R.string.steam_history_all
    SteamChatHistoryCategory.TEXT -> R.string.steam_history_text
    SteamChatHistoryCategory.MEDIA -> R.string.steam_history_media
    SteamChatHistoryCategory.FILE -> R.string.steam_history_files
    SteamChatHistoryCategory.LINK -> R.string.steam_history_links
    SteamChatHistoryCategory.INVITE -> R.string.steam_history_invites
    SteamChatHistoryCategory.STICKER -> R.string.steam_history_stickers
})

@Composable
internal fun steamHistoryRangeLabel(range: SteamChatHistoryRange): String = stringResource(when (range) {
    SteamChatHistoryRange.ANY_TIME -> R.string.steam_history_any_time
    SteamChatHistoryRange.TODAY -> R.string.steam_history_today
    SteamChatHistoryRange.LAST_WEEK -> R.string.steam_history_last_week
    SteamChatHistoryRange.LAST_MONTH -> R.string.steam_history_last_month
})
