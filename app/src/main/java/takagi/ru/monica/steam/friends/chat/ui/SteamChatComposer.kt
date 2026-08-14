package takagi.ru.monica.steam.friends.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatAttachmentPickerPanel
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatAttachmentSheet
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRichMediaPickerPanel
import takagi.ru.monica.steam.friends.chat.richmedia.ui.rememberSteamChatFilePicker
import takagi.ru.monica.steam.friends.chat.richmedia.ui.rememberSteamChatGalleryPicker
import takagi.ru.monica.steam.store.share.domain.SteamStoreGameShare

@Composable
internal fun SteamChatComposer(
    draftKey: String,
    richMediaState: SteamChatRichMediaUiState,
    initialGameShare: SteamStoreGameShare? = null,
    onConsumeInitialGameShare: () -> Unit = {},
    onSend: (String) -> Unit,
    onAttachmentSelected: (String) -> Unit,
    onAttachmentSpoilerChanged: (Boolean) -> Unit,
    onUploadAttachment: () -> Unit,
    onClearAttachment: () -> Unit,
    onClearAttachmentFailure: () -> Unit,
    onRefreshCatalogs: () -> Unit,
    onOpenStoreApp: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable(draftKey) { mutableStateOf("") }
    var pendingGameShare by rememberSaveable(draftKey) {
        mutableStateOf<SteamStoreGameShare?>(null)
    }
    var showRichPicker by rememberSaveable(draftKey) { mutableStateOf(false) }
    var showAttachmentPicker by rememberSaveable(draftKey) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val launchGalleryPicker = rememberSteamChatGalleryPicker(onAttachmentSelected)
    val launchFilePicker = rememberSteamChatFilePicker(onAttachmentSelected)
    val canSend = text.isNotBlank() || pendingGameShare != null
    val send = {
        val body = pendingGameShare?.messageBody(text).orEmpty().ifBlank { text.trim() }
        if (body.isNotEmpty()) {
            onSend(body)
            text = ""
            pendingGameShare = null
        }
    }

    LaunchedEffect(draftKey, initialGameShare) {
        val share = initialGameShare ?: return@LaunchedEffect
        pendingGameShare = share
        onConsumeInitialGameShare()
    }

    BackHandler(enabled = showRichPicker || showAttachmentPicker) {
        showRichPicker = false
        showAttachmentPicker = false
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
        // IME insets are owned by SteamChatThread's root layout. Keeping the
        // composer free of another imePadding avoids a duplicate keyboard gap.
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
            AnimatedVisibility(visible = pendingGameShare != null) {
                pendingGameShare?.let { share ->
                    SteamChatGameShareDraftPreview(
                        share = share,
                        onOpenStoreApp = onOpenStoreApp,
                        onRemove = { pendingGameShare = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = {
                        showRichPicker = false
                        showAttachmentPicker = !showAttachmentPicker
                        if (showAttachmentPicker) focusManager.clearFocus(force = true)
                    },
                    enabled = !richMediaState.attachmentPreparing && !richMediaState.attachmentUploading,
                    modifier = Modifier.padding(end = 8.dp, bottom = 2.dp).size(48.dp)
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = stringResource(
                            if (showAttachmentPicker) {
                                R.string.steam_chat_close
                            } else {
                                R.string.steam_chat_attachment_select
                            }
                        ),
                        tint = if (showAttachmentPicker) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp, max = 144.dp)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                showRichPicker = false
                                showAttachmentPicker = false
                            }
                        },
                    placeholder = { Text(stringResource(R.string.steam_chat_message_hint)) },
                    shape = RoundedCornerShape(24.dp),
                    minLines = 1,
                    maxLines = 5,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                showRichPicker = !showRichPicker
                                showAttachmentPicker = false
                                if (showRichPicker) focusManager.clearFocus(force = true)
                            }
                        ) {
                            Icon(
                                imageVector = if (showRichPicker) {
                                    Icons.Default.Close
                                } else {
                                    Icons.Default.EmojiEmotions
                                },
                                contentDescription = stringResource(
                                    if (showRichPicker) {
                                        R.string.steam_chat_close
                                    } else {
                                        R.string.steam_chat_rich_picker_title
                                    }
                                )
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
            AnimatedVisibility(
                visible = showAttachmentPicker,
                enter = fadeIn(tween(durationMillis = 180)) + expandVertically(
                    animationSpec = tween(durationMillis = 220),
                    expandFrom = Alignment.Top
                ),
                exit = fadeOut(tween(durationMillis = 120)) + shrinkVertically(
                    animationSpec = tween(durationMillis = 160),
                    shrinkTowards = Alignment.Top
                )
            ) {
                SteamChatAttachmentPickerPanel(
                    onGalleryClick = {
                        showAttachmentPicker = false
                        launchGalleryPicker()
                    },
                    onFileClick = {
                        showAttachmentPicker = false
                        launchFilePicker()
                    }
                )
            }
            AnimatedVisibility(
                visible = showRichPicker,
                enter = fadeIn(tween(durationMillis = 180)) + expandVertically(
                    animationSpec = tween(durationMillis = 220),
                    expandFrom = Alignment.Top
                ),
                exit = fadeOut(tween(durationMillis = 120)) + shrinkVertically(
                    animationSpec = tween(durationMillis = 160),
                    shrinkTowards = Alignment.Top
                )
            ) {
                SteamChatRichMediaPickerPanel(
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
                    onEffectSelected = { effect ->
                        onSend(effect.messageCode)
                        showRichPicker = false
                    },
                    onRefresh = onRefreshCatalogs
                )
            }
        }
    }
}
