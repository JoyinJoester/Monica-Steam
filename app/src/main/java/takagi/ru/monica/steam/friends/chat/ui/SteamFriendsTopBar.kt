package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.foundation.ui.SteamAccountAvatarButton
import takagi.ru.monica.steam.foundation.ui.SteamActionOverflowMenu
import takagi.ru.monica.steam.foundation.ui.SteamPageOverflowAction
import takagi.ru.monica.steam.friends.chat.telegram.TelegramSocialSearchTopBar

/**
 * Chat-owned contacts header. Search expands below the title; contact actions stay in overflow.
 */
@Composable
internal fun SteamFriendsTopBar(
    account: SteamAccount?,
    query: String,
    onQueryChange: (String) -> Unit,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    canSwitchAccounts: Boolean,
    onRefresh: () -> Unit,
    onCreateGroup: () -> Unit,
    onOpenChats: () -> Unit,
    onShowAccounts: () -> Unit,
    refreshing: Boolean = false
) {
    val groupLabel = stringResource(R.string.steam_chat_create_group)
    val refreshLabel = stringResource(R.string.steam_friends_refresh)
    val chatsLabel = stringResource(R.string.steam_chat_title)
    TelegramSocialSearchTopBar(
        title = stringResource(R.string.steam_friends_title),
        searchQuery = query,
        onSearchQueryChange = onQueryChange,
        isSearchExpanded = searchExpanded,
        onSearchExpandedChange = onSearchExpandedChange,
        searchHint = stringResource(R.string.steam_contacts_search_hint),
        beforeSearch = {
            SteamAccountAvatarButton(account, onShowAccounts, enabled = canSwitchAccounts)
        },
        actions = {
            SteamActionOverflowMenu(
                refreshing = refreshing,
                contentDescription = stringResource(R.string.steam_chat_options),
                actions = listOf(
                    SteamPageOverflowAction(groupLabel, Icons.Default.GroupAdd, onClick = onCreateGroup),
                    SteamPageOverflowAction(refreshLabel, Icons.Default.Refresh, enabled = !refreshing, onClick = onRefresh),
                    SteamPageOverflowAction(chatsLabel, Icons.Default.Chat, onClick = onOpenChats)
                )
            )
        }
    )
}
