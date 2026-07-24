package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatAttachmentSheet
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRichMediaPickerSheet
import takagi.ru.monica.steam.friends.chat.richmedia.ui.rememberSteamChatAttachmentPicker

@Composable
internal fun SteamChatComposer(
    richMediaState: SteamChatRichMediaUiState,
    onSend: (String) -> Unit,
    onAttachmentSelected: (String) -> Unit,
    onAttachmentSpoilerChanged: (Boolean) -> Unit,
    onUploadAttachment: () -> Unit,
    onClearAttachment: () -> Unit,
    onClearAttachmentFailure: () -> Unit,
    onRefreshCatalogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    var showRichPicker by rememberSaveable { mutableStateOf(false) }
    val launchAttachmentPicker = rememberSteamChatAttachmentPicker(onAttachmentSelected)
    val canSend = text.isNotBlank()
    val send = {
        val body = text.trim()
        if (body.isNotEmpty()) {
            onSend(body)
            text = ""
        }
    }

    if (showRichPicker) {
        SteamChatRichMediaPickerSheet(
            state = richMediaState,
            onDismiss = { showRichPicker = false },
            onEmojiSelected = { emoji -> text += emoji },
            onEmoticonSelected = { emoticon ->
                text += if (text.isBlank() || text.endsWith(' ')) {
                    emoticon.messageCode
                } else {
                    " ${emoticon.messageCode}"
                }
            },
            onStickerSelected = { sticker ->
                onSend(sticker.messageCode)
                showRichPicker = false
            },
            onRefresh = onRefreshCatalogs
        )
    }
    if (richMediaState.pendingAttachment != null) {
        SteamChatAttachmentSheet(
            state = richMediaState,
            onDismiss = onClearAttachment,
            onSpoilerChanged = onAttachmentSpoilerChanged,
            onUpload = onUploadAttachment
        )
    }

    Surface(
        // The thread is hosted in an edge-to-edge activity. Window resize/IME
        // insets are consumed by the host; applying imePadding here as well
        // creates a second, visible gap above the keyboard.
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column {
            AnimatedVisibility(richMediaState.attachmentPreparing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            AnimatedVisibility(
                visible = richMediaState.attachmentFailure != null &&
                    richMediaState.pendingAttachment == null
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = richMediaState.attachmentFailure.orEmpty(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = onClearAttachmentFailure) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.steam_chat_close)
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                FilledTonalIconButton(
                    onClick = launchAttachmentPicker,
                    enabled = !richMediaState.attachmentPreparing && !richMediaState.attachmentUploading,
                    modifier = Modifier.padding(end = 8.dp, bottom = 2.dp).size(48.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = stringResource(R.string.steam_chat_attachment_select)
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp, max = 144.dp),
                    placeholder = { Text(stringResource(R.string.steam_chat_message_hint)) },
                    shape = RoundedCornerShape(24.dp),
                    minLines = 1,
                    maxLines = 5,
                    trailingIcon = {
                        IconButton(onClick = { showRichPicker = true }) {
                            Icon(
                                Icons.Default.EmojiEmotions,
                                contentDescription = stringResource(R.string.steam_chat_rich_picker_title)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { send() })
                )
                FilledIconButton(
                    onClick = send,
                    enabled = canSend,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp).size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    AnimatedContent(
                        targetState = canSend,
                        transitionSpec = {
                            (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                                scaleIn(initialScale = 0.75f, animationSpec = spring()))
                                .togetherWith(fadeOut() + scaleOut(targetScale = 0.75f))
                        },
                        label = "SteamChatSendState"
                    ) { enabled ->
                        Icon(
                            imageVector = if (enabled) {
                                Icons.AutoMirrored.Filled.Send
                            } else {
                                Icons.Default.ArrowUpward
                            },
                            contentDescription = stringResource(R.string.steam_chat_send)
                        )
                    }
                }
            }
        }
    }
}
