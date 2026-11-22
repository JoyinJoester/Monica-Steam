package takagi.ru.monica.steam.friends.chat.richmedia.ui

import android.view.View
import com.monica.telegramchat.TelegramGlassView
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassHost
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassSelectionRow
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassSupported
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassRole
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.friends.chat.telegram.LocalTelegramSourceView
import androidx.compose.ui.graphics.toArgb
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassSurface
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEffect
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatSticker
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.steam.friends.chat.telegram.TelegramColors
import takagi.ru.monica.steam.friends.chat.telegram.telegramChatColors

/**
 * Single Telegram-shaped surface for every Steam rich media page.
 *
 * Telegram keeps one panel with rounded top corners, a category strip, and a
 * floating page pill that stays put while the user switches between emoji,
 * stickers and GIFs. Rebuilding the Steam pages around that same skeleton is
 * what keeps "表情 / Steam / 贴纸 / 特效" from falling back to Material cards.
 */
@Composable
internal fun SteamChatRichMediaPickerPanel(
    state: SteamChatRichMediaUiState,
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit,
    onEmoticonSelected: (SteamChatEmoticon) -> Unit,
    onStickerSelected: (SteamChatSticker) -> Unit,
    onEffectSelected: (SteamChatEffect) -> Unit,
    onRefresh: () -> Unit,
    sourceView: View? = LocalTelegramSourceView.current
) {
    var page by remember { mutableStateOf(RichPickerPage.EMOJI) }
    var query by remember { mutableStateOf("") }
    val colors = telegramChatColors()
    val frosted = LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS && steamGlassSupported()
    val panelShape = RoundedCornerShape(topStart = PANEL_RADIUS, topEnd = PANEL_RADIUS)

    SteamGlassHost(
        modifier = Modifier.fillMaxWidth().height(PANEL_HEIGHT).clip(panelShape),
        backdropColor = if (frosted) Color.Transparent else Color(colors.native.menuBackground),
        content = {
        if (frosted) AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { TelegramGlassView(it) },
            update = { view ->
                view.setSourceView(sourceView)
                view.setLiquidStyle(false)
                view.setFrostedPanelStyle(true)
                view.setStrokeEnabled(false)
                view.setTintColor(Color(colors.native.menuBackground)
                    .copy(alpha = if (colors.native.dark) .48f else .60f).toArgb())
            },
            onRelease = { it.setSourceView(null) }
        )
        if (page == RichPickerPage.EMOJI) {
            // The original Telegram emoji grid owns its category strip, so it is
            // simply inset from the floating pill owned by this composable.
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { com.monica.telegramchat.TelegramEmojiPickerView(it) },
                update = { view ->
                    view.setTransparentBackground(frosted)
                    view.setColors(colors.native)
                    view.setCallbacks(onEmojiSelected, onDismiss)
                },
                onRelease = { it.setCallbacks({}, {}) }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                SteamRichPickerSearchRow(
                    query = query,
                    onQueryChange = { query = it },
                    loading = state.catalogLoading,
                    onRefresh = onRefresh
                )
                if (state.catalogFailure) {
                    SteamRichPickerFailureNotice(onRefresh = onRefresh)
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (page) {
                        RichPickerPage.EMOTICON -> EmoticonGrid(query, state.emoticons, onEmoticonSelected)
                        RichPickerPage.STICKER -> StickerGrid(query, state.stickers, onStickerSelected)
                        RichPickerPage.EFFECT -> EffectList(query, state.effects, onEffectSelected)
                        RichPickerPage.EMOJI -> Unit
                    }
                }
            }
        }
        }, overlay = {
        // Fades the grid out under the floating pill so labels stay readable.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(GRADIENT_HEIGHT)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(colors.native.menuBackground)
                            .copy(alpha = if (frosted) .22f else 1f))
                    )
                )
        )
        SteamRichPickerBottomBar(
            selectedPage = page,
            onSelectPage = {
                if (it != page) {
                    page = it
                    query = ""
                }
            },
            onDismiss = onDismiss,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    })
}

@Composable
private fun SteamRichPickerSearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    loading: Boolean,
    onRefresh: () -> Unit
) {
    val colors = telegramChatColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        takagi.ru.monica.steam.foundation.ui.SteamListSearchField(
            query, onQueryChange, stringResource(R.string.steam_chat_rich_picker_search), Modifier.weight(1f))
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.secondary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.steam_chat_rich_picker_refresh),
                    tint = colors.secondary,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onRefresh)
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun SteamRichPickerFailureNotice(onRefresh: () -> Unit) {
    val colors = telegramChatColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.tgOverlay(.06f))
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.steam_chat_rich_picker_catalog_unavailable),
            color = colors.secondary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.steam_chat_rich_picker_refresh),
            color = colors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onRefresh)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

/** Telegram's floating page switcher: segmented pill on the left, close circle right. */
@Composable
internal fun SteamRichPickerBottomBar(
    selectedPage: RichPickerPage,
    onSelectPage: (RichPickerPage) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = telegramChatColors()
    val liquid = LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS
    val shellBackdrop = rememberLayerBackdrop()
    val shellShape = RoundedCornerShape(24.dp)
    val shell = Modifier.steamGlassSurface(shellShape, Color(colors.native.menuBackground),
        role = SteamGlassRole.FILTER)
    Row(
        modifier = modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .padding(start = 14.dp, end = 10.dp, bottom = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            // Record a sibling shell; clipping the row would cut off the held glass lens.
            Box(Modifier.matchParentSize().layerBackdrop(shellBackdrop).then(shell))
            SteamGlassSelectionRow(
                options = RichPickerPage.entries,
                selected = selectedPage,
                onSelect = onSelectPage,
                indicatorColor = colors.tgOverlay(.18f),
                modifier = Modifier.fillMaxWidth(),
                dragFromAnyOption = true,
                surfaceBackdrop = shellBackdrop,
                indicatorInset = 6.dp
            ) { page, active, itemModifier, select ->
                Box(
                    modifier = itemModifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .selectable(active, role = Role.Tab, interactionSource = null,
                            indication = null, onClick = select)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(page.tabLabelRes),
                        color = when {
                            active && liquid -> MaterialTheme.colorScheme.primary
                            active -> colors.text
                            else -> colors.secondary.copy(alpha = .9f)
                        },
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .steamGlassSurface(CircleShape, Color(colors.native.menuBackground))
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.steam_chat_close),
                tint = colors.secondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun EmoticonGrid(
    query: String,
    emoticons: List<SteamChatEmoticon>,
    onEmoticonSelected: (SteamChatEmoticon) -> Unit
) {
    val filtered = remember(query, emoticons) {
        emoticons.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }
    SteamRichPickerGrid(
        empty = filtered.isEmpty(),
        columns = GridCells.Adaptive(54.dp)
    ) {
        items(filtered, key = { "steam-${it.name}" }) { emoticon ->
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onEmoticonSelected(emoticon) },
                contentAlignment = Alignment.Center
            ) {
                // 54dp cell - 2dp padding keeps Steam's native large emoticon
                // resolution without a second resampling pass.
                SteamChatRemoteImage(
                    url = emoticon.imageUrl,
                    contentDescription = emoticon.name,
                    modifier = Modifier.padding(2.dp),
                    mode = SteamChatRemoteImageMode.EMOTICON
                )
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
    val colors = telegramChatColors()
    val filtered = remember(query, stickers) {
        stickers.filter {
            query.isBlank() || it.name.contains(query, true) || it.title.contains(query, true)
        }
    }
    SteamRichPickerGrid(
        empty = filtered.isEmpty(),
        columns = GridCells.Fixed(3)
    ) {
        items(filtered, key = { "sticker-${it.name}" }) { sticker ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.94f)
                    .clip(RoundedCornerShape(10.dp))
                    .combinedClickable(
                        onClick = { onStickerSelected(sticker) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            previewSticker = sticker
                        }
                    )
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SteamChatRemoteImage(
                    url = sticker.imageUrl,
                    contentDescription = sticker.title,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    mode = SteamChatRemoteImageMode.STICKER
                )
                Text(
                    text = sticker.title,
                    modifier = Modifier.widthIn(max = 100.dp).padding(top = 2.dp),
                    color = colors.secondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
                shape = RoundedCornerShape(24.dp),
                color = Color(colors.native.menuBackground),
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
private fun EffectList(
    query: String,
    effects: List<SteamChatEffect>,
    onEffectSelected: (SteamChatEffect) -> Unit
) {
    val colors = telegramChatColors()
    val filtered = remember(query, effects) {
        effects.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }
    if (filtered.isEmpty()) {
        SteamRichPickerEmptyState()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = BOTTOM_BAR_HEIGHT)
    ) {
        items(filtered, key = { "effect-${it.name}" }) { effect ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onEffectSelected(effect) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = colors.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = effect.name,
                    color = colors.text,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SteamRichPickerGrid(
    empty: Boolean,
    columns: GridCells,
    content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit
) {
    if (empty) {
        SteamRichPickerEmptyState()
        return
    }
    LazyVerticalGrid(
        columns = columns,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = BOTTOM_BAR_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

@Composable
private fun SteamRichPickerEmptyState() {
    val colors = telegramChatColors()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.steam_chat_rich_picker_empty),
            color = colors.secondary.copy(alpha = .8f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = BOTTOM_BAR_HEIGHT)
        )
    }
}

/** Telegram tints panel chrome with a neutral scrim instead of a Material container. */
private fun TelegramColors.tgOverlay(alpha: Float): Color =
    if (native.dark) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha * .75f)

private val PANEL_RADIUS = 20.dp
private val PANEL_HEIGHT = 320.dp
private val SEARCH_HEIGHT = 38.dp

/** Matches [com.monica.telegramchat.TelegramEmojiPickerView]'s bottom grid inset. */
private val BOTTOM_BAR_HEIGHT = 68.dp
private val GRADIENT_HEIGHT = 76.dp
