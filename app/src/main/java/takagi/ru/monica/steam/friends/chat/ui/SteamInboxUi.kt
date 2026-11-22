package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.foundation.ui.SteamAccountAvatarButton
import takagi.ru.monica.steam.foundation.ui.SteamActionOverflowMenu
import takagi.ru.monica.steam.foundation.ui.SteamPageOverflowAction
import takagi.ru.monica.steam.friends.chat.telegram.TelegramSocialSearchTopBar

/**
 * The parent owns the window inset. Space and conversation actions share one overflow entry;
 * the Telegram style gives the title and native search editor their own rows.
 */
@Composable
internal fun SteamInboxTopBar(
    account: SteamAccount?,
    canSwitchAccounts: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    searchExpanded: Boolean = false,
    onSearchExpandedChange: (Boolean) -> Unit = {},
    onShowAccounts: () -> Unit,
    onOpenSpace: (() -> Unit)? = null,
    onCreateGroup: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false
) {
    TelegramSocialSearchTopBar(
        title = stringResource(R.string.steam_chat_title), searchQuery = query,
        onSearchQueryChange = onQueryChange, isSearchExpanded = searchExpanded,
        onSearchExpandedChange = onSearchExpandedChange,
        searchHint = stringResource(R.string.steam_chat_search_hint),
        beforeSearch = {
            SteamAccountAvatarButton(account, onShowAccounts, enabled = canSwitchAccounts)
        },
        actions = {
            SteamInboxOverflowMenu(onOpenSpace, onCreateGroup, onRefresh, refreshing)
        })
}

@Composable
internal fun SteamInboxOverflowMenu(
    onOpenSpace: (() -> Unit)?,
    onCreateGroup: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false
) {
    if (onOpenSpace == null && onCreateGroup == null && onRefresh == null) return
    val focus = LocalFocusManager.current
    val space = stringResource(R.string.steam_space_title)
    val group = stringResource(R.string.steam_chat_create_group)
    val refresh = stringResource(R.string.steam_friends_refresh)
    SteamActionOverflowMenu(
        refreshing = refreshing,
        contentDescription = stringResource(R.string.steam_chat_options),
        beforeAction = { focus.clearFocus() },
        actions = buildList {
            onOpenSpace?.let { add(SteamPageOverflowAction(space, Icons.Default.DynamicFeed, onClick = it)) }
            onCreateGroup?.let { add(SteamPageOverflowAction(group, Icons.Default.GroupAdd, onClick = it)) }
            onRefresh?.let { add(SteamPageOverflowAction(refresh, Icons.Default.Refresh, enabled = !refreshing, onClick = it)) }
        }
    )
}

