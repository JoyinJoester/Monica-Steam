package takagi.ru.monica.steam.friends.chat.actions.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassAlertDialog as AlertDialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.actions.domain.SteamChatReportReason
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatSticker
import takagi.ru.monica.steam.friends.chat.richmedia.ui.RichPickerPage
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRichPickerPageSplitButton
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRemoteImage
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRemoteImageMode
import takagi.ru.monica.steam.friends.chat.telegram.TelegramActionMenu
import takagi.ru.monica.steam.friends.chat.telegram.TelegramMenuAction

@Composable
fun SteamChatMessageActionMenu(
    canReport: Boolean,
    canReact: Boolean = true,
    onDismiss: () -> Unit,
    onOpenReactions: () -> Unit,
    onCopy: () -> Unit,
    onReport: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val actions = buildList {
        if (canReact) add(TelegramMenuAction(stringResource(R.string.steam_chat_action_react),
            com.monica.telegramchat.R.drawable.telegram_msg_reactions, onOpenReactions))
        add(TelegramMenuAction(stringResource(R.string.steam_chat_action_copy),
            com.monica.telegramchat.R.drawable.telegram_msg_copy, onCopy))
        if (onDelete != null) add(TelegramMenuAction(stringResource(R.string.delete),
            com.monica.telegramchat.R.drawable.telegram_msg_delete, onDelete))
        if (canReport) add(TelegramMenuAction(stringResource(R.string.steam_chat_action_report),
            com.monica.telegramchat.R.drawable.telegram_msg_report, onReport))
    }
    TelegramActionMenu(actions, onDismiss)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SteamChatReactionPicker(
    emoticons: List<SteamChatEmoticon>,
    stickers: List<SteamChatSticker>,
    onDismiss: () -> Unit,
    onReact: (SteamChatEmoticon) -> Unit,
    onStickerReply: (SteamChatSticker) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(RichPickerPage.EMOTICON) }
    var previewSticker by remember { mutableStateOf<SteamChatSticker?>(null) }
    val haptics = LocalHapticFeedback.current
    val filteredEmoticons = remember(query, emoticons) {
        emoticons.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }
    val filteredStickers = remember(query, stickers) {
        stickers.filter { query.isBlank() || it.name.contains(query, true) || it.title.contains(query, true) }
    }
    MessageAnchoredPopup(onDismiss) {
        Surface(
            modifier = Modifier.width(328.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.steam_chat_reaction_picker_title),
                    style = MaterialTheme.typography.titleMedium
                )
                SteamChatRichPickerPageSplitButton(
                    selectedPage = page,
                    onSelectPage = { page = it },
                    allowedPages = listOf(RichPickerPage.EMOTICON, RichPickerPage.STICKER)
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.steam_chat_rich_picker_search)) }
                )
                if (page == RichPickerPage.EMOTICON && filteredEmoticons.isEmpty() ||
                    page == RichPickerPage.STICKER && filteredStickers.isEmpty()
                ) {
                    Text(
                        text = stringResource(R.string.steam_chat_reaction_picker_empty),
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    if (page == RichPickerPage.EMOTICON) {
                        EmoticonReactionGrid(filteredEmoticons, onReact)
                    } else {
                        StickerReactionGrid(
                            stickers = filteredStickers,
                            onClick = onStickerReply,
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                previewSticker = it
                            }
                        )
                    }
                }
            }
        }
    }
    previewSticker?.let { sticker ->
        Popup(
            alignment = Alignment.Center,
            onDismissRequest = { previewSticker = null },
            properties = PopupProperties(focusable = true)
        ) {
            Surface(
                modifier = Modifier.size(260.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                SteamChatRemoteImage(
                    url = sticker.imageUrl,
                    contentDescription = sticker.title,
                    modifier = Modifier.padding(18.dp).size(224.dp),
                    mode = SteamChatRemoteImageMode.STICKER
                )
            }
        }
    }
}

@Composable
private fun EmoticonReactionGrid(
    emoticons: List<SteamChatEmoticon>,
    onClick: (SteamChatEmoticon) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(emoticons, key = SteamChatEmoticon::name) { emoticon ->
            Surface(
                onClick = { onClick(emoticon) },
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                SteamChatRemoteImage(
                    url = emoticon.imageUrl,
                    contentDescription = emoticon.name,
                    modifier = Modifier.padding(5.dp).size(54.dp),
                    mode = SteamChatRemoteImageMode.EMOTICON
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerReactionGrid(
    stickers: List<SteamChatSticker>,
    onClick: (SteamChatSticker) -> Unit,
    onLongClick: (SteamChatSticker) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(stickers, key = SteamChatSticker::name) { sticker ->
            Surface(
                modifier = Modifier.size(88.dp).combinedClickable(
                    onClick = { onClick(sticker) },
                    onLongClick = { onLongClick(sticker) }
                ),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                SteamChatRemoteImage(
                    url = sticker.imageUrl,
                    contentDescription = sticker.title,
                    modifier = Modifier.padding(5.dp).size(78.dp),
                    mode = SteamChatRemoteImageMode.STICKER
                )
            }
        }
    }
}

@Composable
private fun MessageAnchoredPopup(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Popup(
        popupPositionProvider = remember { MessageAnchoredPositionProvider() },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(spring()) + scaleIn(initialScale = 0.88f, animationSpec = spring())
        ) { content() }
    }
}

internal class MessageAnchoredPositionProvider(
    private val edgeMargin: Int = 16,
    private val anchorGap: Int = 12
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): androidx.compose.ui.unit.IntOffset {
        val x = (if (anchorBounds.center.x <= windowSize.width / 2) {
            anchorBounds.left
        } else {
            anchorBounds.right - popupContentSize.width
        }).coerceIn(
            edgeMargin,
            (windowSize.width - popupContentSize.width - edgeMargin).coerceAtLeast(edgeMargin)
        )
        val below = anchorBounds.bottom + anchorGap
        val y = (if (below + popupContentSize.height <= windowSize.height - edgeMargin) {
            below
        } else {
            anchorBounds.top - anchorGap - popupContentSize.height
        }).coerceIn(
            edgeMargin,
            (windowSize.height - popupContentSize.height - edgeMargin).coerceAtLeast(edgeMargin)
        )
        return androidx.compose.ui.unit.IntOffset(x, y)
    }
}

@Composable
fun SteamChatReportDialog(
    selectedReason: SteamChatReportReason,
    onReasonSelected: (SteamChatReportReason) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.steam_chat_report_title)) },
        text = {
            Column {
                SteamChatReportReason.entries.forEach { reason ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedReason == reason, onClick = { onReasonSelected(reason) })
                        Text(stringResource(reason.labelResource()))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.steam_chat_report_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

private fun SteamChatReportReason.labelResource(): Int = when (this) {
    SteamChatReportReason.HARASSMENT -> R.string.steam_chat_report_harassment
    SteamChatReportReason.SCAM -> R.string.steam_chat_report_scam
    SteamChatReportReason.SPAM -> R.string.steam_chat_report_spam
    SteamChatReportReason.OTHER -> R.string.steam_chat_report_other
}
