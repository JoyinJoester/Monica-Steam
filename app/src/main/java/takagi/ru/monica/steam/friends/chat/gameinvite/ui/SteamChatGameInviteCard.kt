package takagi.ru.monica.steam.friends.chat.gameinvite.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
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
import takagi.ru.monica.steam.friends.chat.gameinvite.domain.toGameInvitePresentation
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatCardLinks
import takagi.ru.monica.steam.friends.chat.richmedia.ui.openSteamChatCard
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatCardAction
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRichCard
import takagi.ru.monica.steam.friends.chat.richmedia.ui.steamChatCopyLink

@Composable
internal fun SteamChatGameInviteCard(
    content: SteamChatRichContent.GameInvite,
    onOpenStoreApp: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
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
        value = content.appId?.let { repository.resolve(it, language) }
    }
    val presentation = remember(content, metadata) {
        content.toGameInvitePresentation(metadata)
    }
    val title = presentation.gameName
        ?: presentation.appId?.let { stringResource(R.string.steam_chat_game_invite_app_id, it) }
        ?: stringResource(R.string.steam_chat_notification_game_invite)
    val openStore = {
        presentation.appId?.let(onOpenStoreApp)
        Unit
    }
    val storeLabel = stringResource(R.string.steam_chat_game_invite_view_store)
    val inviteUrl = remember(content.url) { SteamChatCardLinks.gameInvite(content.url) }
    val copy = steamChatCopyLink(title, inviteUrl)
    val openInvite = inviteUrl?.let { url ->
        SteamChatCardAction(stringResource(R.string.open_link), icon = Icons.Rounded.OpenInNew, prominent = true) { openSteamChatCard(context, url) }
    }
    val messageActions = onLongClick?.let {
        SteamChatCardAction(stringResource(R.string.steam_preview_message_actions),
            icon = Icons.Rounded.ChatBubbleOutline, run = it)
    }
    val delete = onDeleteMessage?.let { SteamChatCardAction(stringResource(R.string.delete),
        icon = Icons.Rounded.DeleteOutline, destructive = true, run = it) }
    val caption = listOf(content.caption, content.label.takeUnless {
        it == "Steam game invitation" || it == content.url || it == title
    }).filterNotNull().filter(String::isNotBlank).distinct().joinToString("\n")
    SteamChatRichCard(
        kind = stringResource(R.string.steam_notification_kind_game_invite),
        title = title,
        icon = Icons.Default.SportsEsports,
        artwork = presentation.artworkUrl,
        metadata = presentation.appId?.let { stringResource(R.string.steam_chat_game_invite_app_id, it) },
        description = caption,
        primary = if (presentation.appId != null) SteamChatCardAction(storeLabel, icon = Icons.Rounded.Storefront, run = openStore) else openInvite,
        actions = listOfNotNull(copy, openInvite.takeIf { presentation.appId != null }, messageActions, delete),
        modifier = modifier,
        featuredArtwork = true
    )
}
