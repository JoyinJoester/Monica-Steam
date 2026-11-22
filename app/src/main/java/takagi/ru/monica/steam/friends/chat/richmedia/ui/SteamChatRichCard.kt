package takagi.ru.monica.steam.friends.chat.richmedia.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.rememberSteamCardPreviewAction
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteBytes
import takagi.ru.monica.steam.foundation.ui.isSafeSteamRemoteImagePayload
import takagi.ru.monica.steam.foundation.ui.decodeSteamRemoteBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.friends.chat.telegram.TelegramActionMenu
import takagi.ru.monica.steam.friends.chat.telegram.TelegramMenuAction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal data class SteamChatCardAction(
    val label: String,
    val icon: ImageVector? = null,
    val prominent: Boolean = false,
    val destructive: Boolean = false,
    val run: () -> Unit
)

@Composable
internal fun steamChatCopyLink(title: String, url: String?): SteamChatCardAction? {
    val context = LocalContext.current
    return url?.let {
        SteamChatCardAction(stringResource(R.string.steam_card_copy_link), icon = Icons.Rounded.ContentCopy) {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(title, it))
        }
    }
}

@Composable
internal fun SteamChatRichCard(
    kind: String,
    title: String,
    icon: ImageVector,
    artwork: String?,
    metadata: String?,
    description: String?,
    primary: SteamChatCardAction?,
    actions: List<SteamChatCardAction>,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    featuredArtwork: Boolean = false
) {
    var showActions by remember(kind, title) { mutableStateOf(false) }
    val context = LocalContext.current
    // One decoded cover stays with this card and its preview, so the first
    // expansion frame does not replace a visible cover with a loading icon.
    val featuredImage = if (featuredArtwork) key(artwork) {
        produceState<ImageBitmap?>(null) {
            val payload = artwork?.takeIf(String::isNotBlank)?.let { loadSteamRemoteBytes(context, it) }
            if (payload != null && isSafeSteamRemoteImagePayload(payload)) {
                value = withContext(Dispatchers.Default) {
                    runCatching { decodeSteamRemoteBitmap(payload)?.asImageBitmap() }.getOrNull()
                }
            }
        }
    } else null
    val (anchor, show) = rememberSteamCardPreviewAction(
        fallback = { showActions = true },
        cardContent = if (featuredArtwork) { close, dismiss, fraction ->
            SteamChatFeaturedGameContent(kind, title, icon, artwork, featuredImage?.value, description,
                metadata = metadata, primary = primary, actions = actions,
                onAction = close, onDismiss = dismiss, fraction = fraction)
        } else null
    ) { close ->
        if (!featuredArtwork) {
            Text(kind, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        if (artwork != null) {
            SteamChatRemoteImage(
                url = artwork,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(2.15f).clip(RoundedCornerShape(16.dp)),
                mode = SteamChatRemoteImageMode.ARTWORK,
                fallbackIcon = icon
            )
        }
        if (featuredArtwork) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(kind, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        metadata?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        description?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (primary != null) {
            Button(onClick = { close(); primary.run() }, modifier = Modifier.fillMaxWidth()) { Text(primary.label) }
        }
        actions.forEach { action ->
            TextButton(onClick = { close(); action.run() }, modifier = Modifier.fillMaxWidth()) { Text(action.label) }
        }
    }
    val shape = RoundedCornerShape(if (featuredArtwork) 28.dp else 18.dp)
    val previewLabel = stringResource(R.string.steam_preview_open)
    Surface(
        modifier = modifier.widthIn(min = 210.dp, max = 310.dp).then(anchor).clip(shape)
            .then(if (featuredArtwork) Modifier.testTag("steam-chat-game-card") else Modifier)
            .combinedClickable(role = Role.Button, onClickLabel = primary?.label.takeIf { onTap != null } ?: previewLabel,
                onClick = onTap ?: show, onLongClickLabel = previewLabel, onLongClick = show),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (featuredArtwork) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        if (featuredArtwork) {
            Box {
                SteamChatFeaturedGameContent(kind, title, icon, artwork, featuredImage?.value, description)
                if (showActions) TelegramActionMenu(
                    actions = (listOfNotNull(primary) + actions).map {
                        TelegramMenuAction(it.label, 0) { showActions = false; it.run() }
                    }, onDismiss = { showActions = false })
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(start = 10.dp, top = 8.dp, end = 2.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val artShape = RoundedCornerShape(11.dp)
                    Surface(
                        modifier = Modifier.size(width = if (artwork == null) 42.dp else 74.dp, height = 44.dp),
                        shape = artShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        if (artwork != null) SteamChatRemoteImage(
                            url = artwork, contentDescription = null, modifier = Modifier.fillMaxSize(),
                            mode = SteamChatRemoteImageMode.ARTWORK, fallbackIcon = icon
                        ) else Box(contentAlignment = Alignment.Center) {
                            Icon(icon, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        metadata?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Box {
                        IconButton(onClick = show) { Icon(Icons.Default.MoreHoriz, stringResource(R.string.more_options)) }
                        if (showActions) {
                            TelegramActionMenu(
                                actions = (listOfNotNull(primary) + actions).map {
                                    TelegramMenuAction(it.label, 0) { showActions = false; it.run() }
                                },
                                onDismiss = { showActions = false }
                            )
                        }
                    }
                }
                description?.takeIf(String::isNotBlank)?.let {
                    Text(it, Modifier.padding(top = 6.dp, end = 10.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The collapsed and expanded card share the same artwork, type and spacing. */
@Composable
private fun SteamChatFeaturedGameContent(
    kind: String,
    title: String,
    icon: ImageVector,
    artwork: String?,
    artworkImage: ImageBitmap?,
    description: String?,
    metadata: String? = null,
    primary: SteamChatCardAction? = null,
    actions: List<SteamChatCardAction> = emptyList(),
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    fraction: Float = 0f
) {
    val expanded = onDismiss != null
    val reveal = ((fraction - 0.2f) / 0.6f).coerceIn(0f, 1f)
    val scroll = rememberScrollState()
    var returningScroll by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(fraction) {
        if (fraction >= 1f) returningScroll = null
        else if (expanded) {
            val start = returningScroll ?: scroll.value.also { returningScroll = it }
            scroll.scrollTo((start * fraction).toInt())
        }
    }
    Box {
        Column(Modifier.fillMaxWidth().then(
            if (expanded) Modifier.verticalScroll(scroll) else Modifier
        )) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().testTag("steam-chat-game-artwork")) {
                val cover = if (artwork.isNullOrBlank()) Modifier.fillMaxWidth().height(88.dp)
                    else Modifier.fillMaxWidth().aspectRatio(
                        takagi.ru.monica.steam.friends.chat.gameinvite.domain.STEAM_GAME_HEADER_ASPECT_RATIO)
                if (artworkImage != null) Image(bitmap = artworkImage, contentDescription = null,
                    modifier = cover, contentScale = ContentScale.Crop)
                else Box(cover, contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, Modifier.testTag("steam-chat-game-title"),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(kind, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (expanded) metadata?.let {
                        Text(it, Modifier.graphicsLayer { alpha = reveal },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                description?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                }
                if (expanded) SteamChatGameActions(primary, actions,
                    onAction = { onAction?.invoke() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).graphicsLayer { alpha = reveal })
            }
        }
        if (onDismiss != null) FilledTonalIconButton(onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).graphicsLayer { alpha = reveal }) {
            Icon(Icons.Rounded.Close, stringResource(R.string.steam_preview_close))
        }
    }
}
