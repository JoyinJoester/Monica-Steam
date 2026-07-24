package takagi.ru.monica.steam.friends.chat.richmedia.ui

import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.graphics.ImageDecoder
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteBytes
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEffect
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatSticker
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatUnicodeEmojiCatalog
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState

@Composable
internal fun rememberSteamChatAttachmentPicker(
    onSelected: (String) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.toString()?.let(onSelected)
    }
    return remember(launcher) {
        {
            launcher.launch(
                arrayOf(
                    "image/jpeg", "image/png", "image/gif", "image/webp", "image/avif",
                    "video/webm", "video/mpeg", "video/mp4", "video/ogg", "application/zip"
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamChatRichMediaPickerSheet(
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.steam_chat_rich_picker_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onRefresh, enabled = !state.catalogLoading) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.steam_chat_rich_picker_refresh)
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                RichPickerPage.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = page == item,
                        onClick = { page = item },
                        shape = SegmentedButtonDefaults.itemShape(index, RichPickerPage.entries.size),
                        label = {
                            Text(
                                stringResource(
                                    item.labelRes
                                )
                            )
                        }
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
            if (state.catalogLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when (page) {
                RichPickerPage.EMOJI -> EmojiGrid(
                    query = query,
                    onEmojiSelected = onEmojiSelected
                )
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
    }
}

@Composable
private fun EmojiGrid(
    query: String,
    onEmojiSelected: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(44.dp),
        modifier = Modifier.fillMaxWidth().height(340.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (query.isBlank()) {
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
        columns = GridCells.Adaptive(44.dp),
        modifier = Modifier.fillMaxWidth().height(340.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(filteredEmoticons, key = { "steam-${it.name}" }) { emoticon ->
            Surface(
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onEmoticonSelected(emoticon) },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = CircleShape
            ) {
                SteamChatRemoteImage(
                    url = emoticon.imageUrl,
                    contentDescription = emoticon.name,
                    modifier = Modifier.padding(8.dp)
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
        modifier = Modifier.fillMaxWidth().height(300.dp),
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

@Composable
private fun StickerGrid(
    query: String,
    stickers: List<SteamChatSticker>,
    onStickerSelected: (SteamChatSticker) -> Unit
) {
    val filtered = remember(query, stickers) {
        stickers.filter {
            query.isBlank() || it.name.contains(query, true) || it.title.contains(query, true)
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height(380.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filtered, key = { "sticker-${it.name}" }) { sticker ->
            Surface(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onStickerSelected(sticker) },
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
                        modifier = Modifier.weight(1f).fillMaxWidth()
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
}

@Composable
internal fun SteamChatRemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bytes by remember(url) { mutableStateOf<ByteArray?>(null) }
    var drawable by remember(url) { mutableStateOf<Drawable?>(null) }
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bytes = loadSteamRemoteBytes(context, url)
    }
    LaunchedEffect(bytes) {
        val payload = bytes ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            drawable = runCatching {
                withContext(Dispatchers.Default) {
                    ImageDecoder.decodeDrawable(
                        ImageDecoder.createSource(ByteBuffer.wrap(payload))
                    )
                }
            }.getOrNull()
        } else {
            image = withContext(Dispatchers.Default) {
                android.graphics.BitmapFactory.decodeByteArray(payload, 0, payload.size)
                    ?.asImageBitmap()
            }
        }
    }
    val animated = drawable as? AnimatedImageDrawable
    if (animated != null) {
        AndroidView(
            factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE } },
            modifier = modifier,
            update = { view ->
                if (view.drawable !== animated) view.setImageDrawable(animated)
                if (!animated.isRunning) animated.start()
            }
        )
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && drawable != null) {
        AndroidView(
            factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE } },
            modifier = modifier,
            update = { view -> if (view.drawable !== drawable) view.setImageDrawable(drawable) }
        )
        return
    }
    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.EmojiEmotions, contentDescription = contentDescription)
        }
    }
}

private enum class RichPickerPage(val labelRes: Int) {
    EMOJI(R.string.steam_chat_rich_picker_emoji),
    EMOTICON(R.string.steam_chat_rich_picker_emoticons),
    STICKER(R.string.steam_chat_rich_picker_stickers),
    EFFECT(R.string.steam_chat_rich_picker_effects)
}
