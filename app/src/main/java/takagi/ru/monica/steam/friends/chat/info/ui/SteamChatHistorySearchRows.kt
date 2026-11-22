package takagi.ru.monica.steam.friends.chat.info.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentKind
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteImage
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryItem

/**
 * Result row. Material's list anatomy: a leading avatar carries identity, the sender is the
 * primary line, the message is supporting text, and a thumbnail sits on the trailing edge.
 * Picking a hit paints the row in a tonal container instead of a flat tint.
 */
@Composable
internal fun HistoryRow(
    item: SteamChatHistoryItem,
    query: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val spec = spring<Color>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = spec,
        label = "historyRowContainer"
    )
    val primaryText by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = spec,
        label = "historyRowPrimary"
    )
    val secondaryText by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .78f)
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spec,
        label = "historyRowSecondary"
    )

    Surface(
        color = container,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        Row(
            Modifier.clickable(onClick = onClick)
                // The floating match rail is anchored to the right edge of the list, so every row
                // keeps the same gutter — a plain text row used to run underneath the rail.
                .padding(start = 10.dp, end = 56.dp, top = 10.dp, bottom = 10.dp)
                // A 48dp thumbnail makes media rows taller; without a floor the plain rows came out
                // short and the list read as unevenly spaced bands.
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Picture and initial share one leading slot: with the thumbnail on the trailing edge
            // its row moved the timestamp out of the column every other row lines up on.
            HistoryLeading(item, LeadingSlot)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.senderName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Timestamps stay on the trailing edge so they scan as one column.
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatHistoryClock(item.timestamp),
                        Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryText
                    )
                }
                Text(
                    highlighted(item.body, query, MaterialTheme.colorScheme.primary.copy(alpha = .24f)),
                    Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = secondaryText
                )
            }
        }
    }
}

/** One width for every leading element so the text column and its timestamps never shift. */
private val LeadingSlot = 40.dp

/**
 * The leading slot: the picture when it resolves, otherwise the sender's initial. A media message
 * whose URL never loads used to leave a blank hole where every other row has something to look at.
 */
@Composable
private fun HistoryLeading(item: SteamChatHistoryItem, size: Dp) {
    val bitmap = rememberRemoteThumb(item.mediaUrl.takeUnless { item.spoiler })
    if (bitmap != null) {
        Image(
            bitmap,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        SenderBadge(item.senderName, size = size)
    }
}

@Composable
private fun rememberRemoteThumb(url: String?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = url?.takeIf { it.isNotBlank() }?.let { loadSteamRemoteImage(context, it) }
    }
    return bitmap
}

/**
 * Deterministic tonal disc so the same sender keeps the same colour across the list. Only the
 * primary and tertiary containers are used: the selected row is painted in the secondary
 * container, and a grey disc read as "disabled" next to the toned ones.
 */
@Composable
private fun SenderBadge(name: String, size: Dp) {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer
    )
    val (container, content) = palette[(name.hashCode() and Int.MAX_VALUE) % palette.size]
    Surface(shape = CircleShape, color = container, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                name.trim().take(1).uppercase().ifEmpty { "?" },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = content
            )
        }
    }
}

/** One square cell of the media wall the bottom bar switches this page to. */
@Composable
internal fun HistoryMediaCell(item: SteamChatHistoryItem, onClick: () -> Unit) {
    val video = item.attachmentKind == SteamChatAttachmentKind.VIDEO
    val type = stringResource(if (video) R.string.steam_chat_content_video else R.string.steam_chat_content_image)
    val description = stringResource(R.string.steam_thread_open_shared_message,
        "${if (item.spoiler) stringResource(R.string.steam_chat_content_spoiler) else item.body} · ${item.senderName} · ${chatContentTimestamp(item.timestamp)}")
    val corner = if (LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS) 4.dp else 12.dp
    val shape = RoundedCornerShape(corner)
    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .semantics { contentDescription = description }.clickable(onClick = onClick)
        .testTag("chat-content-media-${item.id}")) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(if (item.spoiler) Icons.Default.VisibilityOff else if (video) Icons.Default.PlayArrow else Icons.Default.Image,
                contentDescription = if (item.spoiler) stringResource(R.string.steam_chat_content_spoiler) else type,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        }
        if (!item.spoiler) RemoteSteamThumbnail(
            url = item.mediaUrl.orEmpty(), modifier = Modifier.fillMaxSize(), corner = corner)
        if (video && item.mediaUrl != null && !item.spoiler) {
            Surface(color = Color.Black.copy(alpha = .45f), shape = CircleShape,
                modifier = Modifier.align(Alignment.Center).size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, type, tint = Color.White) }
            }
        }
        Surface(color = Color.Black.copy(alpha = .55f), shape = RoundedCornerShape(6.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)) {
            Text((if (video) "$type · " else "") + formatHistoryClock(item.timestamp),
                Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = Color.White)
        }
    }
}

@Composable
private fun RemoteSteamThumbnail(url: String, modifier: Modifier, corner: Dp = 12.dp) {
    val bitmap = rememberRemoteThumb(url)
    Box(modifier) {
        // No placeholder fill: a sticker whose image never resolves used to leave a pale tile
        // floating at the row edge, which read as a rendering bug.
        bitmap?.let {
            Image(
                it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(corner)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
internal fun HistoryDayHeader(label: String) {
    Text(
        label,
        Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

/** Marks every occurrence of [query] so a hit is visible inside a long message. */
internal fun highlighted(text: String, query: String, background: Color): AnnotatedString {
    val needle = query.trim()
    if (needle.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var index = text.indexOf(needle, ignoreCase = true)
        while (index >= 0) {
            addStyle(SpanStyle(background = background), index, index + needle.length)
            index = text.indexOf(needle, startIndex = index + needle.length, ignoreCase = true)
        }
    }
}
