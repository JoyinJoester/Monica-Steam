package takagi.ru.monica.steam.friends.chat.richmedia.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEffect
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatSticker
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatUnicodeEmojiCatalog
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState

@Composable
internal fun SteamChatRichMediaPickerPanel(
    state: SteamChatRichMediaUiState,
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit,
    onEmoticonSelected: (SteamChatEmoticon) -> Unit,
    onStickerSelected: (SteamChatSticker) -> Unit,
    onEffectSelected: (SteamChatEffect) -> Unit,
    onRefresh: () -> Unit
) {
    var page by remember { mutableStateOf(RichPickerPage.EMOJI) }
    var query by remember { mutableStateOf("") }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SteamChatRichPickerPageSplitButton(
                    selectedPage = page,
                    onSelectPage = { selected ->
                        page = selected
                        query = ""
                    }
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh, enabled = !state.catalogLoading) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.steam_chat_rich_picker_refresh)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.steam_chat_close)
                    )
                }
            }
            if (page != RichPickerPage.EMOJI) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    placeholder = { Text(stringResource(R.string.steam_chat_rich_picker_search)) },
                    trailingIcon = {
                        AnimatedVisibility(query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(
                                        R.string.steam_chat_rich_picker_clear_search
                                    )
                                )
                            }
                        }
                    }
                )
            }
            if (state.catalogFailure && page != RichPickerPage.EMOJI) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(
                                    R.string.steam_chat_rich_picker_catalog_unavailable
                                ),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = stringResource(
                                    R.string.steam_chat_rich_picker_catalog_unavailable_summary
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = onRefresh, enabled = !state.catalogLoading) {
                            Text(stringResource(R.string.steam_chat_rich_picker_refresh))
                        }
                    }
                }
            }
            RichPickerPageContent(
                page = page,
                query = query,
                state = state,
                onEmojiSelected = onEmojiSelected,
                onEmoticonSelected = onEmoticonSelected,
                onStickerSelected = onStickerSelected,
                onEffectSelected = onEffectSelected
            )
        }
    }
}

@Composable
private fun RichPickerPageContent(
    page: RichPickerPage,
    query: String,
    state: SteamChatRichMediaUiState,
    onEmojiSelected: (String) -> Unit,
    onEmoticonSelected: (SteamChatEmoticon) -> Unit,
    onStickerSelected: (SteamChatSticker) -> Unit,
    onEffectSelected: (SteamChatEffect) -> Unit
) {
    when (page) {
        RichPickerPage.EMOJI -> EmojiGrid(onEmojiSelected = onEmojiSelected)
        RichPickerPage.EMOTICON -> EmoticonGrid(
            query = query,
            emoticons = state.emoticons,
            onEmoticonSelected = onEmoticonSelected
        )
        RichPickerPage.STICKER -> StickerGrid(
            query = query,
            stickers = state.stickers,
            onStickerSelected = onStickerSelected
        )
        RichPickerPage.EFFECT -> EffectGrid(
            query = query,
            effects = state.effects,
            onEffectSelected = onEffectSelected
        )
    }
}

@Composable
private fun EmojiGrid(
    onEmojiSelected: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(56.dp),
        modifier = Modifier.fillMaxWidth().height(PICKER_GRID_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(SteamChatUnicodeEmojiCatalog.items, key = { "unicode-$it" }) { emoji ->
            Surface(
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onEmojiSelected(emoji) },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(emoji, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun EmoticonGrid(
    query: String,
    emoticons: List<SteamChatEmoticon>,
    onEmoticonSelected: (SteamChatEmoticon) -> Unit
) {
    val filteredEmoticons = remember(query, emoticons) {
        emoticons.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(56.dp),
        modifier = Modifier.fillMaxWidth().height(PICKER_GRID_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(filteredEmoticons, key = { "steam-${it.name}" }) { emoticon ->
            Surface(
                modifier = Modifier.size(56.dp).clip(CircleShape).clickable { onEmoticonSelected(emoticon) },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = CircleShape
            ) {
                SteamChatRemoteImage(
                    url = emoticon.imageUrl,
                    contentDescription = emoticon.name,
                    // 56dp cell - 1dp on each edge = Steam's native 54dp
                    // large emoticon, avoiding a second resampling pass.
                    modifier = Modifier.padding(1.dp),
                    mode = SteamChatRemoteImageMode.EMOTICON
                )
            }
        }
    }
}

@Composable
private fun EffectGrid(
    query: String,
    effects: List<SteamChatEffect>,
    onEffectSelected: (SteamChatEffect) -> Unit
) {
    val filtered = remember(query, effects) {
        effects.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().height(PICKER_GRID_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filtered, key = { "effect-${it.name}" }) { effect ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onEffectSelected(effect) },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Text(
                        text = effect.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerGrid(
    query: String,
    stickers: List<SteamChatSticker>,
    onStickerSelected: (SteamChatSticker) -> Unit
) {
    var previewSticker by remember { mutableStateOf<SteamChatSticker?>(null) }
    val haptics = LocalHapticFeedback.current
    val filtered = remember(query, stickers) {
        stickers.filter {
            query.isBlank() || it.name.contains(query, true) || it.title.contains(query, true)
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height(PICKER_GRID_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filtered, key = { "sticker-${it.name}" }) { sticker ->
            Surface(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).combinedClickable(
                    onClick = { onStickerSelected(sticker) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        previewSticker = sticker
                    }
                ),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SteamChatRemoteImage(
                        url = sticker.imageUrl,
                        contentDescription = sticker.title,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        mode = SteamChatRemoteImageMode.STICKER
                    )
                    Text(
                        sticker.title,
                        modifier = Modifier.widthIn(max = 100.dp),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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

private val PICKER_GRID_HEIGHT = 268.dp
