package takagi.ru.monica.steam.friends.chat.richmedia.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatCardLinks
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatOfficialMessage
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatOfficialMessageKind
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatOfficialMessageKind.*
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassAlertDialog

internal fun steamCardTitle(kind: SteamChatOfficialMessageKind): Int = when (kind) {
    TRADE_LINK -> R.string.steam_card_trade_link
    TRADE_OFFER -> R.string.steam_card_trade_offer
    PROFILE -> R.string.steam_card_profile
    PUBLISHED_FILE -> R.string.steam_card_published_file
    SCREENSHOT -> R.string.steam_card_screenshot
    VIDEO -> R.string.steam_card_video
    GUIDE -> R.string.steam_card_guide
    COLLECTION -> R.string.steam_card_collection
    STORE_BUNDLE -> R.string.steam_card_bundle
    STORE_PACKAGE -> R.string.steam_card_package
    BROADCAST_INVITE -> R.string.steam_card_broadcast
    BROADCAST_VIEW_REQUEST -> R.string.steam_card_broadcast_request
    PLAYTEST_INVITE -> R.string.steam_card_playtest
    REMOTE_PLAY_INVITE -> R.string.steam_card_remote_play
    GIFT -> R.string.steam_card_gift
    INVENTORY_ITEM -> R.string.steam_card_inventory
    FRIEND_REQUEST -> R.string.steam_card_friend_request
    GROUP_INVITE -> R.string.steam_card_group
    EVENT -> R.string.steam_card_event
    COMMENT -> R.string.steam_card_comment
    MARKET -> R.string.steam_card_market
    ROOM_EFFECT -> R.string.steam_card_effect
    UNKNOWN -> R.string.steam_card_unknown
}

internal fun steamCardAction(kind: SteamChatOfficialMessageKind): Int = when (kind) {
    TRADE_LINK -> R.string.steam_card_start_trade
    TRADE_OFFER -> R.string.steam_card_view_trade
    BROADCAST_INVITE -> R.string.steam_card_watch
    GROUP_INVITE -> R.string.steam_card_view_group
    PROFILE -> R.string.steam_card_view_profile
    INVENTORY_ITEM -> R.string.steam_card_view_inventory
    MARKET -> R.string.steam_card_view_market
    REMOTE_PLAY_INVITE -> R.string.steam_card_open_invite
    else -> R.string.steam_card_open
}

private fun cardIcon(kind: SteamChatOfficialMessageKind) = when (kind) {
    TRADE_LINK, TRADE_OFFER -> Icons.Default.SwapHoriz
    BROADCAST_INVITE, BROADCAST_VIEW_REQUEST, VIDEO -> Icons.Default.LiveTv
    PLAYTEST_INVITE -> Icons.Default.Science
    REMOTE_PLAY_INVITE, STORE_BUNDLE, STORE_PACKAGE -> Icons.Default.SportsEsports
    GIFT -> Icons.Default.CardGiftcard
    INVENTORY_ITEM, MARKET -> Icons.Default.Inventory2
    PROFILE, FRIEND_REQUEST, GROUP_INVITE -> Icons.Default.PersonAdd
    ROOM_EFFECT -> Icons.Default.AutoAwesome
    UNKNOWN -> Icons.Default.HelpOutline
    else -> Icons.Default.OpenInNew
}

internal fun openSteamChatCard(context: Context, url: String) {
    val safe = SteamChatCardLinks.action(url) ?: return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safe)))
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, R.string.steam_card_no_handler, Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.steam_card_no_handler, Toast.LENGTH_LONG).show()
    }
}

@Composable
internal fun SteamOfficialMessageContent(
    content: SteamChatOfficialMessage,
    modifier: Modifier = Modifier,
    onMessageActions: (() -> Unit)? = null,
    onDeleteMessage: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showDetails by remember(content.rawBody) { mutableStateOf(false) }
    val kind = stringResource(steamCardTitle(content.kind))
    val title = content.displayName.ifBlank { kind }
    val safeUrl = remember(content.url) { SteamChatCardLinks.action(content.url) }
    val primary = safeUrl?.let { url ->
        SteamChatCardAction(stringResource(steamCardAction(content.kind))) { openSteamChatCard(context, url) }
    }
    val metadata = content.tradeOfferId?.let { stringResource(R.string.steam_card_offer_id, it) }
        ?: safeUrl?.let { SteamChatCardLinks.uri(it)?.host }
    val details = SteamChatCardAction(stringResource(R.string.steam_card_details)) { showDetails = true }
    val messageActions = onMessageActions?.let {
        SteamChatCardAction(stringResource(R.string.steam_preview_message_actions),
            icon = Icons.Rounded.ChatBubbleOutline, run = it)
    }
    val delete = onDeleteMessage?.let { SteamChatCardAction(stringResource(R.string.delete),
        icon = Icons.Rounded.DeleteOutline, destructive = true, run = it) }
    val description = listOf(content.caption, content.description.takeUnless { it == content.url })
        .filterNotNull().filter(String::isNotBlank).distinct().joinToString("\n")
    SteamChatRichCard(
        kind = kind,
        title = title,
        icon = cardIcon(content.kind),
        artwork = content.imageUrl,
        metadata = metadata,
        description = description,
        primary = primary,
        actions = listOfNotNull(steamChatCopyLink(title, safeUrl), details, messageActions, delete),
        modifier = modifier
    )
    if (showDetails) SteamGlassAlertDialog(
        onDismissRequest = { showDetails = false },
        title = { Text(title) },
        text = {
            SelectionContainer {
                Text(content.rawBody, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
            }
        },
        confirmButton = { TextButton(onClick = { showDetails = false }) { Text(stringResource(R.string.steam_chat_close)) } }
    )
}
