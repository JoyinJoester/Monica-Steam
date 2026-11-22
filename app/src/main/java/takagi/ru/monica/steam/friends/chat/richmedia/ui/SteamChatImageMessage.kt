package takagi.ru.monica.steam.friends.chat.richmedia.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.media.SteamFullscreenImageViewer
import takagi.ru.monica.steam.foundation.media.SteamImageDownloadPolicy
import takagi.ru.monica.steam.friends.chat.richmedia.domain.*
import takagi.ru.monica.steam.friends.chat.telegram.telegramChatColors
import takagi.ru.monica.ui.LocalReduceAnimations

/** Full-bleed media bubble; metadata is over the image, or below an actual caption. */
@Composable
internal fun SteamChatImageMessage(
    content: SteamChatRichContent.Attachment,
    modifier: Modifier = Modifier,
    outgoing: Boolean = false,
    groupedWithPrevious: Boolean = false,
    groupedWithNext: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    metadata: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val colors = telegramChatColors()
    val reduced = LocalReduceAnimations.current
    val haptics = LocalHapticFeedback.current
    val sources = remember(content) { content.images.ifEmpty { listOf(SteamChatImageSource(content.url, content.label)) }.take(10) }
    val ratios = remember(sources) { mutableStateListOf(*sources.map {
        if (it.width > 0 && it.height > 0) it.width.toFloat() / it.height else 4f / 3f
    }.toTypedArray()) }
    var revealed by remember(content) { mutableStateOf(!content.spoiler) }
    var selectedImage by remember(content) { mutableStateOf<Int?>(null) }
    val longClick: (() -> Unit)? = onLongClick?.let { action -> {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        action()
    } }
    val revealLabel = stringResource(R.string.steam_chat_spoiler_reveal)
    val longClickLabel = stringResource(R.string.steam_preview_message_actions)
    val shape = RoundedCornerShape(
        topStart = if (!outgoing && groupedWithPrevious) 4.dp else 16.dp,
        topEnd = if (outgoing && groupedWithPrevious) 4.dp else 16.dp,
        bottomStart = if (!outgoing && groupedWithNext) 4.dp else 16.dp,
        bottomEnd = if (outgoing && groupedWithNext) 4.dp else 16.dp)
    fun open(index: Int) {
        val source = sources[index]
        if (SteamImageDownloadPolicy.isAllowedUrl(source.url)) selectedImage = index
        else runCatching {
            val uri = Uri.parse(source.url)
            if (uri.scheme == "https") context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
    BoxWithConstraints(modifier.widthIn(max = 324.dp)) {
        val arrangement = steamChatImageLayout(ratios.toList(), maxWidth.value)
        Column(Modifier.width(arrangement.width.dp).clip(shape)
            .background(if (outgoing) colors.outgoing else colors.incoming).testTag("steam-chat-photo-bubble")) {
            Box {
                Crossfade(targetState = revealed, animationSpec = tween(if (reduced) 0 else 180),
                    label = "steam-chat-spoiler-image") { isRevealed ->
                    if (!isRevealed) {
                        Box(Modifier.fillMaxWidth().height(arrangement.height.dp)
                            .combinedClickable(onClick = { revealed = true }, onLongClick = longClick,
                                onLongClickLabel = longClickLabel).testTag("steam-chat-photo-spoiler"),
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(20.dp)) {
                                Icon(Icons.Default.Visibility, null, Modifier.size(28.dp))
                                Text(revealLabel, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    } else {
                        Layout(modifier = Modifier.fillMaxWidth().height(arrangement.height.dp).testTag("steam-chat-photo-grid"),
                            content = {
                                sources.forEachIndexed { index, source ->
                                    SteamChatRemoteImage(source.url, source.label,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp))
                                            .combinedClickable(onClick = { open(index) }, onLongClick = longClick,
                                                onLongClickLabel = longClickLabel).testTag("steam-chat-photo-$index"),
                                        playAnimation = !reduced,
                                        mode = if (sources.size == 1) SteamChatRemoteImageMode.CONTENT else SteamChatRemoteImageMode.ARTWORK,
                                        fallbackIcon = Icons.Default.Image,
                                        onImageSize = { width, height -> ratios[index] = width.toFloat() / height })
                                }
                            }) { measurables, constraints ->
                            val width = constraints.maxWidth
                            val height = constraints.maxHeight
                            val gap = 2.dp.roundToPx()
                            val tiles = arrangement.tiles.map { tile ->
                                val left = (tile.left * width).roundToInt() + if (tile.left > 0f) gap / 2 else 0
                                val top = (tile.top * height).roundToInt() + if (tile.top > 0f) gap / 2 else 0
                                val right = (tile.right * width).roundToInt() - if (tile.right < 1f) (gap + 1) / 2 else 0
                                val bottom = (tile.bottom * height).roundToInt() - if (tile.bottom < 1f) (gap + 1) / 2 else 0
                                Triple(left, top, Constraints.fixed((right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1)))
                            }
                            val children = measurables.mapIndexed { index, measurable -> measurable.measure(tiles[index].third) }
                            layout(width, height) { children.forEachIndexed { index, placeable ->
                                placeable.place(tiles[index].first, tiles[index].second)
                            } }
                        }
                    }
                }
                if (content.caption.isBlank() && metadata != null) Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).testTag("steam-chat-photo-metadata"),
                    color = Color.Black.copy(alpha = .55f), contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp)) {
                    Box(Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) { metadata() }
                }
            }
            if (content.caption.isNotBlank()) Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(content.caption, modifier = if (onLongClick == null) Modifier else Modifier
                    .pointerInput(onLongClick) { detectTapGestures(onLongPress = { longClick?.invoke() }) }
                    .semantics { this.onLongClick(label = longClickLabel) { longClick?.invoke(); true } },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (outgoing) colors.outgoingText else colors.text)
                if (metadata != null) Box(Modifier.align(Alignment.End).testTag("steam-chat-photo-caption-metadata")) { metadata() }
            }
        }
    }
    selectedImage?.let { index ->
        SteamFullscreenImageViewer(title = sources[index].label, images = sources.map { it.url }, initialIndex = index,
            fileStemForIndex = { selected -> SteamImageDownloadPolicy.safeFileStem(
                "steam_chat_${sources[selected].label.substringBeforeLast('.')}", "steam_chat_image") },
            strings = chatImageViewerStrings, onDismiss = { selectedImage = null })
    }
}
