package takagi.ru.monica.steam.friends.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
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
    sourceView: android.view.View? = null,
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

    Column(modifier.fillMaxWidth()) {
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
                            text = richMediaState.attachmentFailureResource?.let { stringResource(it) }
                                ?: richMediaState.attachmentFailure.orEmpty(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
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
            takagi.ru.monica.steam.friends.chat.telegram.TelegramChatInput(
                text = text,
                hint = stringResource(R.string.steam_chat_message_hint),
                canSend = canSend,
                onTextChanged = { text = it },
                onSend = send,
                onEmoji = {
                    showRichPicker = !showRichPicker
                    showAttachmentPicker = false
                    if (showRichPicker) focusManager.clearFocus(force = true)
                },
                onAttach = {
                    if (!richMediaState.attachmentPreparing && !richMediaState.attachmentUploading) {
                        showRichPicker = false
                        showAttachmentPicker = !showAttachmentPicker
                        if (showAttachmentPicker) focusManager.clearFocus(force = true)
                    }
                },
                onFocus = { showRichPicker = false; showAttachmentPicker = false },
                sourceView = sourceView
            )
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
                        sourceView = sourceView,
                        onSelected = { uri ->
                            showAttachmentPicker = false
                            onAttachmentSelected(uri)
                        },
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
                    sourceView = sourceView,
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
