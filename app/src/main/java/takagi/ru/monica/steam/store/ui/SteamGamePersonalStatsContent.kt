package takagi.ru.monica.steam.store.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import takagi.ru.monica.R
import takagi.ru.monica.steam.library.ui.AchievementItem
import takagi.ru.monica.steam.library.ui.libraryFailureLabel
import takagi.ru.monica.steam.library.ui.SteamAchievementSetSelector
import takagi.ru.monica.steam.library.ui.SteamAchievementStatusSelector
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassHost
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassRole
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassSurface
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SteamGamePersonalStatsContent(
    name: String, accountName: String?, state: SteamGamePersonalStats,
    listState: LazyListState = rememberLazyListState(), onChooseAccount: () -> Unit,
    onRetry: () -> Unit,
    onOpenGameData: (() -> Unit)?, onOpenScreenshots: (() -> Unit)?,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    extras: SteamGameStatsExtras = SteamGameStatsExtras(), heroImageUrl: String = "",
    appId: Int = 0, steamId: String = "", onOpenWeb: (String) -> Unit = {},
    onOpenPointsShop: () -> Unit = {},
    onOpenReward: (takagi.ru.monica.steam.store.points.domain.SteamPointsShopItem) -> Unit = {}
) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var setId by rememberSaveable { mutableStateOf<Long?>(null) }
    val achievements = state.achievements
    val sets = achievements?.selectableSets.orEmpty()
    val effectiveSet = setId.takeIf { value -> sets.any { it.groupId == value } }
    val entries = remember(achievements, filter, effectiveSet) {
        achievements?.achievementsInSet(effectiveSet).orEmpty().filter {
            filter == 0 || (filter == 1 && it.achieved) || (filter == 2 && !it.achieved)
        }.sortedByDescending { it.achieved }
    }
    val unknown = stringResource(R.string.steam_game_stats_unknown)
    val game = state.game
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    SteamGlassHost(Modifier.fillMaxSize(), backdropColor = MaterialTheme.colorScheme.background, content = {}, overlay = {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("game_personal_list"),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topPadding + 8.dp,
            bottom = LocalSteamDockContentClearance.current + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (accountName == null) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.AccountCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.steam_game_stats_sign_in), style = MaterialTheme.typography.titleLarge)
                    Button(onClick = onChooseAccount) { Text(stringResource(R.string.steam_switch_account)) }
                }
            }
            return@LazyColumn
        }
        item(key = "player") {
            SteamGameStatsHero(name, accountName, extras.avatarUrl, heroImageUrl,
                state.loading || extras.loading.isNotEmpty(), onRetry, onChooseAccount,
                onOpenGameData, onOpenScreenshots)
        }
        if (state.activityCached || state.achievementsCached) item(key = "cached") {
            Text(stringResource(R.string.steam_game_stats_cached), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item(key = "playtime") {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(game?.let { hours(it.playtimeForeverMinutes) } ?: unknown,
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("game_total_playtime"))
                    Text(stringResource(R.string.steam_library_total_playtime), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatValue(stringResource(R.string.steam_library_recent_playtime),
                    if (game != null && !state.recentUnavailable) hours(game.playtimeRecentMinutes) else unknown)
                StatValue(stringResource(R.string.steam_game_stats_last_played),
                    game?.lastPlayedAt?.takeIf { it > 0 }?.let {
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it * 1000L))
                    } ?: unknown)
            }
        }
        state.activityFailure?.let { failure -> item(key = "activity_error") {
            Text(libraryFailureLabel(failure), color = MaterialTheme.colorScheme.error)
        } }
        if (!state.loading && game == null && state.activityFailure == null) item(key = "no_activity") {
            Text(stringResource(R.string.steam_game_stats_no_activity), style = MaterialTheme.typography.bodyMedium)
        }
        item(key = "achievement_summary") {
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.steam_store_achievements), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    if (achievements != null) Text("${achievements.completed.size} / ${achievements.achievements.size} · ${(achievements.completionRate * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                }
                if (achievements != null) SteamGameAchievementProgress(achievements.completionRate)
            }
        }
        item(key = "friends") {
            SteamGameFriendsComparison(extras, game?.playtimeForeverMinutes, onOpenWeb)
        }
        item(key = "collectibles") {
            SteamGameStatsCollectibles(extras, appId, steamId, onOpenWeb, onOpenPointsShop, onOpenReward)
        }
        state.achievementFailure?.let { failure -> item(key = "achievement_error") {
            Text(libraryFailureLabel(failure), color = MaterialTheme.colorScheme.error)
        } }
        if (!state.loading && (state.activityFailure != null || state.achievementFailure != null)) item(key = "retry") {
            FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.steam_library_retry)) }
        }
        if (sets.isNotEmpty()) item(key = "sets") {
            SteamAchievementSetSelector(sets, effectiveSet, { setId = it }, requireNotNull(achievements)::setProgress)
        }
        if (achievements != null && achievements.achievements.isNotEmpty()) {
            item(key = "filters") {
                SteamAchievementStatusSelector(filter, { filter = it })
            }
            itemsIndexed(entries, key = { index, entry -> "${entry.groupId}:${entry.apiName}:$index" }) { _, entry ->
                AchievementItem(entry, achievements.appId, compact = true)
            }
            if (entries.isEmpty()) item(key = "no_filtered_achievements") {
                Text(stringResource(if (filter == 1) R.string.steam_library_no_completed_achievements else R.string.steam_library_no_incomplete_achievements))
            }
        } else if (achievements != null) item(key = "no_achievements") {
            Text(stringResource(R.string.steam_library_no_achievements))
        }
    }
    })
    }
}

@Composable
private fun StatValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun hours(minutes: Int): String = stringResource(R.string.steam_game_stats_hours, minutes / 60.0)
