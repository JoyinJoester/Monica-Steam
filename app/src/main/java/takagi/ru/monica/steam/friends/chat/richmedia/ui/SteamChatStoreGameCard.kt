package takagi.ru.monica.steam.friends.chat.richmedia.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.gameinvite.data.SteamChatGameInviteMetadataRepository
import takagi.ru.monica.steam.friends.chat.gameinvite.domain.SteamChatGameInviteMetadata
import takagi.ru.monica.steam.friends.chat.gameinvite.domain.steamGameInviteHeaderUrl
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatCardLinks

@Composable
internal fun SteamChatStoreGameCard(
    content: SteamChatRichContent.StoreGameShare,
    onOpenStoreApp: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onMessageActions: (() -> Unit)? = null,
    onDeleteMessage: (() -> Unit)? = null
) {
    val locale = LocalConfiguration.current.locales[0]
    val language = remember(locale.language) {
        if (locale.language.equals("zh", ignoreCase = true)) "schinese" else "english"
    }
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        SteamChatGameInviteMetadataRepository.get(context.applicationContext)
    }
    val metadata by produceState<SteamChatGameInviteMetadata?>(
        initialValue = null,
        key1 = content.appId,
        key2 = language
    ) {
        value = repository.resolve(content.appId, language)
    }
    val title = metadata?.name?.takeIf(String::isNotBlank)
        ?: content.label?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.steam_chat_store_game_app_id, content.appId)
    val artworkUrl = metadata?.headerImageUrl?.takeIf(String::isNotBlank)
        ?: steamGameInviteHeaderUrl(content.appId)

    val primary = SteamChatCardAction(stringResource(R.string.steam_chat_store_game_view), icon = Icons.Rounded.Storefront) {
        onOpenStoreApp(content.appId)
    }
    val copy = steamChatCopyLink(title, SteamChatCardLinks.action(content.url))
    val messageActions = onMessageActions?.let {
        SteamChatCardAction(stringResource(R.string.steam_preview_message_actions),
            icon = Icons.Rounded.ChatBubbleOutline, run = it)
    }
    val delete = onDeleteMessage?.let { SteamChatCardAction(stringResource(R.string.delete),
        icon = Icons.Rounded.DeleteOutline, destructive = true, run = it) }
    SteamChatRichCard(
        kind = stringResource(R.string.steam_chat_store_game_share),
        title = title,
        icon = Icons.Default.SportsEsports,
        artwork = artworkUrl,
        metadata = stringResource(R.string.steam_chat_store_game_app_id, content.appId),
        description = content.caption,
        primary = primary,
        actions = listOfNotNull(copy, messageActions, delete),
        modifier = modifier,
        featuredArtwork = true
    )
}
