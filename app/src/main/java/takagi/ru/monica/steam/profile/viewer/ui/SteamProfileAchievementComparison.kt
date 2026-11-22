package takagi.ru.monica.steam.profile.viewer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.ui.SteamAchievementSetSelector
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.profile.viewer.domain.SteamAchievementComparisonEntry
import takagi.ru.monica.steam.profile.viewer.domain.SteamAchievementComparisonFilter
import takagi.ru.monica.steam.profile.viewer.domain.filtered
import takagi.ru.monica.steam.profile.viewer.presentation.SteamProfileViewerUiState

@Composable
internal fun SteamProfileAchievementComparisonScreen(
    state: SteamProfileViewerUiState,
    game: SteamGame,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dockClearance = LocalSteamDockContentClearance.current
    val comparison = state.achievementComparison
    val selfProfile = comparison?.viewerSteamId == comparison?.targetSteamId ||
        state.snapshot?.isSelf == true
    val targetName = state.snapshot?.target?.displayName
        ?: state.target?.fallbackName.orEmpty().ifBlank {
            stringResource(R.string.steam_profile_other_player)
        }
    var filterName by rememberSaveable(game.appId) {
        mutableStateOf(SteamAchievementComparisonFilter.ALL.name)
    }
    val filter = SteamAchievementComparisonFilter.entries.firstOrNull { it.name == filterName }
        ?: SteamAchievementComparisonFilter.ALL
    var selectedSetId by rememberSaveable(game.appId) { mutableStateOf<Long?>(null) }
    val effectiveSetId = selectedSetId.takeIf { id -> comparison?.targetSets?.any { it.groupId == id } == true }
    val visible = comparison?.filtered(filter).orEmpty()
        .filter { effectiveSetId == null || it.groupId == effectiveSetId }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = dockClearance + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "comparison_hero") {
            SteamProfileAchievementHero(
                game = game,
                selfProfile = selfProfile,
                onNavigateBack = onNavigateBack
            )
        }
        if (state.achievementComparisonFromCache && comparison != null) {
            item(key = "comparison_cache") {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.steam_profile_cached_achievements),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
        if (comparison == null) {
            item(key = "comparison_state") {
                SteamProfileAchievementStateCard(
                    loading = state.loadingAchievementComparison,
                    failureText = state.achievementFailure?.let { steamProfileFailureMessage(it) },
                    onRetry = onRetry,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            return@LazyColumn
        }
        item(key = "comparison_metrics") {
            SteamProfileAchievementMetrics(
                viewerCompleted = comparison.viewerCompleted,
                targetCompleted = comparison.targetCompleted,
                total = comparison.total,
                targetName = targetName,
                selfProfile = selfProfile,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item(key = "comparison_filter") {
            SteamProfileAchievementFilterSplitButton(
                selectedFilter = filter,
                onSelectFilter = { filterName = it.name },
                selfProfile = selfProfile,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (comparison.targetSets.isNotEmpty()) item(key = "comparison_sets") {
            SteamAchievementSetSelector(comparison.targetSets, effectiveSetId, { selectedSetId = it },
                progress = { set ->
                    val members = comparison.achievements.filter { it.groupId == set.groupId && !it.archived }
                    val total = set.completionAchievements ?: members.size
                    (set.completedAchievements ?: members.count { it.targetAchieved }).coerceIn(0, total) to total
                }, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (comparison.total == 0) {
            item(key = "comparison_empty") {
                SteamProfileAchievementStateCard(
                    loading = false,
                    failureText = stringResource(R.string.steam_profile_game_has_no_achievements),
                    onRetry = onRetry,
                    showRetry = false,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else if (visible.isEmpty()) {
            item(key = "comparison_filter_empty") {
                Text(
                    text = stringResource(R.string.steam_profile_no_matching_achievements),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(
                items = visible,
                key = { achievement -> achievement.apiName }
            ) { achievement ->
                SteamProfileAchievementComparisonRow(
                    achievement = achievement,
                    appId = game.appId,
                    targetName = targetName,
                    selfProfile = selfProfile,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SteamProfileAchievementHero(
    game: SteamGame,
    selfProfile: Boolean,
    onNavigateBack: () -> Unit
) {
    val image = rememberSteamProfileViewerImage(game.headerImageUrl)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(238.dp)
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = game.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                    )
                )
            )
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 8.dp)
                .size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = game.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(
                    if (selfProfile) R.string.steam_profile_achievements
                    else R.string.steam_profile_achievement_comparison
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SteamProfileAchievementMetrics(
    viewerCompleted: Int,
    targetCompleted: Int,
    total: Int,
    targetName: String,
    selfProfile: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SteamProfileAchievementMetric(
                value = "$viewerCompleted / $total",
                label = stringResource(R.string.steam_profile_current_account),
                modifier = Modifier.weight(1f)
            )
            if (!selfProfile) {
                SteamProfileAchievementMetric(
                    value = "$targetCompleted / $total",
                    label = targetName,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SteamProfileAchievementMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SteamProfileAchievementComparisonRow(
    achievement: SteamAchievementComparisonEntry,
    appId: Int,
    targetName: String,
    selfProfile: Boolean,
    modifier: Modifier = Modifier
) {
    val displayName = if (achievement.hidden && !achievement.viewerAchieved && !achievement.targetAchieved) {
        stringResource(R.string.steam_achievement_hidden)
    } else achievement.displayName
    val revealDetails = !achievement.hidden || achievement.viewerAchieved || achievement.targetAchieved
    val rawIcon = if (achievement.viewerAchieved || achievement.targetAchieved) achievement.iconUrl
        else achievement.lockedIconUrl ?: achievement.iconUrl
    val image = rememberSteamProfileViewerImage(if (revealDetails)
        takagi.ru.monica.steam.space.data.SteamSpaceLinks.achievementIcon(appId, rawIcon) else null)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    if (revealDetails && image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    achievement.description.takeIf { revealDetails && it.isNotBlank() }?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SteamProfileAchievementStatus(
                    label = stringResource(R.string.steam_profile_current_account),
                    achieved = achievement.viewerAchieved,
                    modifier = Modifier.weight(1f)
                )
                if (!selfProfile) {
                    SteamProfileAchievementStatus(
                        label = targetName,
                        achieved = achievement.targetAchieved,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SteamProfileAchievementStatus(
    label: String,
    achieved: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (achieved) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (achieved) Icons.Default.CheckCircle
                else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        if (achieved) R.string.steam_profile_achievement_completed
                        else R.string.steam_profile_achievement_incomplete
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SteamProfileAchievementStateCard(
    loading: Boolean,
    failureText: String?,
    onRetry: () -> Unit,
    showRetry: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loading) CircularProgressIndicator()
            Text(
                text = if (loading) stringResource(R.string.steam_profile_loading_achievements)
                else failureText ?: stringResource(R.string.steam_profile_network_error),
                style = MaterialTheme.typography.bodyLarge
            )
            if (!loading && showRetry) {
                FilledTonalButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.steam_library_retry))
                }
            }
        }
    }
}
