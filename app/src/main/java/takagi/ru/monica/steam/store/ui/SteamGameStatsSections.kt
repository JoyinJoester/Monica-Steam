package takagi.ru.monica.steam.store.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import takagi.ru.monica.ui.LocalReduceAnimations
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.SteamRefreshActionIcon
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteImage
import takagi.ru.monica.ui.password.MonicaTopActionsDropdownMenu

@Composable
internal fun SteamGameStatsImage(url: String, label: String?, modifier: Modifier, crop: Boolean = true) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, url) {
        value = null
        if (url.isNotBlank()) value = loadSteamRemoteImage(context, url)
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (image != null) Image(requireNotNull(image), label, Modifier.fillMaxSize(),
            contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
        else if (label != null) Text(label.take(1).uppercase(), style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SteamGameStatsHero(name: String, account: String, avatar: String, artwork: String,
    refreshing: Boolean, onRefresh: () -> Unit, onChoose: () -> Unit,
    onGameData: (() -> Unit)?, onScreenshots: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))) {
        SteamGameStatsImage(artwork, null, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .12f), Color.Black.copy(alpha = .82f)))))
        Column(Modifier.fillMaxWidth().heightIn(min = 144.dp).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Box {
                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("game_stats_menu")) {
                            SteamRefreshActionIcon(refreshing, Icons.Default.MoreVert, stringResource(R.string.more_options))
                        }
                    }
                    MonicaTopActionsDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.steam_switch_account)) },
                            onClick = { menu = false; onChoose() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.refresh)) }, enabled = !refreshing,
                            onClick = { menu = false; onRefresh() })
                        onGameData?.let { DropdownMenuItem(text = { Text(stringResource(R.string.steam_game_stats_game_data)) },
                            onClick = { menu = false; it() }) }
                        onScreenshots?.let { DropdownMenuItem(text = { Text(stringResource(R.string.steam_game_stats_screenshots)) },
                            onClick = { menu = false; it() }) }
                    }
                }
            }
            Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SteamGameStatsImage(avatar, account, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).testTag("game_stats_avatar"))
                Text(account, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun SteamGameFriendsComparison(extras: SteamGameStatsExtras, ownMinutes: Int?, open: (String) -> Unit) {
    val activity = extras.friends
    val own = activity?.own?.minutes ?: ownMinutes
    val average = activity?.averageMinutes
    val maximum = maxOf(own?.toDouble() ?: 0.0, activity?.friends?.mapNotNull { it.minutes }?.maxOrNull()?.toDouble() ?: 0.0)
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.steam_game_stats_friends), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (activity == null) ExtraStatus("friends", extras)
        else if (average == null) Text(stringResource(R.string.steam_game_stats_no_friend_times), style = MaterialTheme.typography.bodyMedium)
        else {
            val ownLabel = stringResource(R.string.steam_game_stats_you)
            val averageLabel = stringResource(R.string.steam_game_stats_friend_average)
            val ownText = own?.let { stringResource(R.string.steam_game_stats_hours, it / 60.0) } ?: stringResource(R.string.steam_game_stats_unknown)
            val averageText = stringResource(R.string.steam_game_stats_hours, average / 60.0)
            val primary = MaterialTheme.colorScheme.primary
            val secondary = MaterialTheme.colorScheme.tertiary
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            val surface = MaterialTheme.colorScheme.background
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(ownLabel, color = primary, style = MaterialTheme.typography.labelMedium)
                    Text(ownText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text(averageLabel, color = secondary, style = MaterialTheme.typography.labelMedium)
                    Text(averageText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
            // One shared time axis. Marker positions are never displaced to avoid overlap:
            // equal values use concentric colors, preserving the actual measurement.
            Canvas(Modifier.fillMaxWidth().height(26.dp).testTag("game_friend_time_axis").semantics {
                contentDescription = "$ownLabel: $ownText; $averageLabel: $averageText"
            }) {
                val inset = 9.dp.toPx(); val width = size.width - 2 * inset
                val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
                fun x(value: Double) = inset + width * steamGamePlaytimeFraction(value, maximum).let { if (rtl) 1f - it else it }
                val y = size.height / 2
                drawLine(track, Offset(inset, y), Offset(size.width - inset, y), 8.dp.toPx(), StrokeCap.Round)
                own?.let { drawLine(primary.copy(alpha = .7f), Offset(x(0.0), y), Offset(x(it.toDouble()), y), 8.dp.toPx(), StrokeCap.Round) }
                activity.friends.mapNotNull { it.minutes }.distinct().forEach { minutes ->
                    drawCircle(secondary.copy(alpha = .6f), 2.dp.toPx(), Offset(x(minutes.toDouble()), y))
                }
                own?.let { drawCircle(primary, 8.dp.toPx(), Offset(x(it.toDouble()), y)) }
                drawCircle(surface, 7.dp.toPx(), Offset(x(average), y))
                drawCircle(secondary, 5.dp.toPx(), Offset(x(average), y))
            }
            Text(stringResource(R.string.steam_game_stats_readable_friends, activity.friends.count { it.minutes != null }),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (activity?.friends?.isNotEmpty() == true) LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(activity.friends.take(12), key = { it.steamId }) { friend ->
                Column(Modifier.width(72.dp).clip(RoundedCornerShape(12.dp)).clickable { open("https://steamcommunity.com/profiles/${friend.steamId}/") }
                    .padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SteamGameStatsImage(friend.avatarUrl, friend.name, Modifier.size(40.dp).clip(CircleShape))
                    Text(friend.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                    Text(friend.minutes?.let { stringResource(R.string.steam_game_stats_hours, it / 60.0) }
                        ?: stringResource(R.string.steam_game_stats_unknown), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
internal fun SteamGameStatsCollectibles(extras: SteamGameStatsExtras, appId: Int, steamId: String, open: (String) -> Unit,
    onOpenPointsShop: () -> Unit = {}, onOpenReward: (takagi.ru.monica.steam.store.points.domain.SteamPointsShopItem) -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatsSectionTitle(stringResource(R.string.steam_game_stats_badges), { open("https://steamcommunity.com/profiles/$steamId/gamecards/$appId/") }, appId > 0 && steamId.isNotBlank())
        val badge = extras.badges
        if (badge == null) ExtraStatus("badges", extras)
        else {
            if (badge.name.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SteamGameStatsImage(badge.iconUrl, badge.name, Modifier.size(48.dp), crop = false)
                Column(Modifier.weight(1f)) {
                    Text(badge.name, style = MaterialTheme.typography.titleSmall)
                    if (badge.description.isNotBlank()) Text(badge.description, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (badge.cards.isEmpty() && badge.name.isBlank()) Text(stringResource(R.string.steam_game_stats_no_badges))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(badge.cards, key = { it.imageUrl }) { card ->
                    Column(Modifier.width(88.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SteamGameStatsImage(card.imageUrl, card.name, Modifier.size(88.dp, 112.dp).clip(RoundedCornerShape(8.dp)), crop = false)
                        Text(card.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(if (card.owned) R.string.steam_game_stats_card_owned else R.string.steam_game_stats_card_missing),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        StatsSectionTitle(stringResource(R.string.steam_points_shop_title), onOpenPointsShop, appId > 0)
        val points = extras.points
        if (points == null) ExtraStatus("points", extras)
        else if (points.items.isEmpty()) Text(stringResource(R.string.steam_game_stats_no_points))
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(points.items, key = { it.definitionId }) { reward ->
                Column(Modifier.width(104.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpenReward(reward) }.padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SteamGameStatsImage(reward.imageUrl, reward.title, Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)), crop = false)
                    Text(reward.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.steam_game_stats_points_cost, reward.pointCost), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun StatsSectionTitle(title: String, open: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (enabled) TextButton(onClick = open) { Text(stringResource(R.string.steam_game_stats_view_all)) }
    }
}

@Composable
private fun ExtraStatus(key: String, extras: SteamGameStatsExtras) {
    Text(stringResource(if (key in extras.loading) R.string.loading else R.string.steam_game_stats_section_unavailable),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A thicker, quiet wave for achievement completion; this is determinate, not refreshing. */
@Composable
internal fun SteamGameAchievementProgress(progress: Float) {
    val target = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val value by animateFloatAsState(target, tween(if (LocalReduceAnimations.current) 0 else 220), label = "game_achievement_completion")
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.fillMaxWidth().height(22.dp).testTag("game_achievement_wave").semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
    }) {
        val inset = 4.dp.toPx(); val width = (size.width - inset * 2).coerceAtLeast(0f)
        val y = size.height / 2; val extent = width * value
        val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
        fun x(offset: Float) = if (rtl) size.width - inset - offset else inset + offset
        if (value < 1f) drawLine(track, Offset(x((extent + 8.dp.toPx()).coerceAtMost(width)), y),
            Offset(x(width), y), 6.dp.toPx(), StrokeCap.Round)
        if (extent > 0) {
            val path = Path()
            val samples = (extent / 2.dp.toPx()).toInt().coerceAtLeast(1)
            for (i in 0..samples) {
                val offset = extent * i / samples
                val py = y + sin(offset / 24.dp.toPx() * 2 * PI).toFloat() * 2.dp.toPx()
                if (i == 0) path.moveTo(x(offset), py) else path.lineTo(x(offset), py)
            }
            drawPath(path, color, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
