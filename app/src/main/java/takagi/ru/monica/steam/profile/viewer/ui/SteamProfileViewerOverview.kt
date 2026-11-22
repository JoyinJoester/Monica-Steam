package takagi.ru.monica.steam.profile.viewer.ui

import takagi.ru.monica.steam.foundation.ui.SteamRefreshIconButton
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.LocalSteamAvatarShape
import takagi.ru.monica.steam.friends.domain.SteamPersonaState
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileGameDataVisibility
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileGameScope
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerFailureReason
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerSnapshot
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerTarget
import takagi.ru.monica.steam.profile.viewer.domain.gamesForScope
import takagi.ru.monica.steam.profile.viewer.presentation.SteamProfileViewerUiState
import takagi.ru.monica.steam.profile.ui.SteamMiniProfileBackgroundLayer
import takagi.ru.monica.steam.token.identity.ui.SteamIdentityInfoCard
import takagi.ru.monica.ui.theme.GoogleSansFlexFontFamily

@Composable
internal fun SteamProfileViewerOverview(
    state: SteamProfileViewerUiState,
    target: SteamProfileViewerTarget,
    animatedBackgroundEnabled: Boolean,
    allowBackgroundMotion: Boolean,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenPerfectGames: () -> Unit,
    onOpenGame: (SteamGame) -> Unit,
    modifier: Modifier = Modifier
) {
    val dockClearance = LocalSteamDockContentClearance.current
    val snapshot = state.snapshot
    var scopeName by rememberSaveable(target.steamId) {
        mutableStateOf(SteamProfileGameScope.ALL.name)
    }
    val scope = SteamProfileGameScope.entries.firstOrNull { it.name == scopeName }
        ?: SteamProfileGameScope.ALL
    val visibleGames = snapshot?.gamesForScope(if (snapshot.isSelf) {
        SteamProfileGameScope.ALL
    } else {
        scope
    }).orEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = dockClearance + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "profile_hero") {
            SteamProfileViewerHero(
                snapshot = snapshot,
                target = target,
                loading = state.loading,
                animatedBackgroundEnabled = animatedBackgroundEnabled,
                allowBackgroundMotion = allowBackgroundMotion,
                onRefresh = onRefresh,
                onNavigateBack = onNavigateBack
            )
        }
        if (state.snapshotFromCache && snapshot != null) {
            item(key = "profile_cache") {
                SteamProfileInfoBanner(
                    text = stringResource(R.string.steam_profile_cached_data),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        if (snapshot == null) {
            item(key = "profile_state") {
                SteamProfileViewerStateCard(
                    loading = state.loading,
                    failure = state.failure,
                    onRetry = onRefresh,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            return@LazyColumn
        }
        state.failure?.let { failure ->
            item(key = "profile_refresh_failure") {
                SteamProfileViewerStateCard(
                    loading = false,
                    failure = failure,
                    onRetry = onRefresh,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        item(key = "profile_metrics") {
            SteamProfileCommunityMetrics(
                snapshot = snapshot,
                onOpenBadges = onOpenBadges,
                onOpenFriends = onOpenFriends,
                onOpenGroups = onOpenGroups,
                onOpenPerfectGames = onOpenPerfectGames,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item(key = "profile_information") {
            SteamProfileInformationCard(
                snapshot = snapshot,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (snapshot.badges.isNotEmpty()) {
            item(key = "profile_badges") {
                SteamProfileBadgePreview(
                    snapshot = snapshot,
                    onOpenBadges = onOpenBadges,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        if (!snapshot.isSelf && snapshot.gameDataVisibility == SteamProfileGameDataVisibility.AVAILABLE) {
            item(key = "profile_scope") {
                SteamProfileGameScopeSelector(
                    selected = scope,
                    onSelected = { scopeName = it.name },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        when (snapshot.gameDataVisibility) {
            SteamProfileGameDataVisibility.PRIVATE -> {
                item(key = "profile_private_games") {
                    SteamProfileGameVisibilityCard(
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.steam_profile_games_private),
                        body = stringResource(R.string.steam_profile_games_private_description),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            SteamProfileGameDataVisibility.UNAVAILABLE -> {
                item(key = "profile_unavailable_games") {
                    SteamProfileGameVisibilityCard(
                        icon = Icons.Default.SportsEsports,
                        title = stringResource(R.string.steam_profile_games_unavailable),
                        body = stringResource(R.string.steam_profile_games_unavailable_description),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            SteamProfileGameDataVisibility.AVAILABLE -> {
                item(key = "profile_games_title") {
                    Text(
                        text = if (snapshot.isSelf) {
                            stringResource(R.string.steam_profile_games_title)
                        } else {
                            stringResource(
                                R.string.steam_profile_games_title_count,
                                visibleGames.size
                            )
                        },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (visibleGames.isEmpty()) {
                    item(key = "profile_empty_games") {
                        SteamProfileGameVisibilityCard(
                            icon = Icons.Default.SportsEsports,
                            title = stringResource(R.string.steam_profile_no_games),
                            body = stringResource(R.string.steam_profile_no_games_description),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    items(
                        items = visibleGames,
                        key = { game -> "profile_game_${game.appId}" }
                    ) { game ->
                        SteamProfileGameRow(
                            game = game,
                            onClick = { onOpenGame(game) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SteamProfileViewerHero(
    snapshot: SteamProfileViewerSnapshot?,
    target: SteamProfileViewerTarget,
    loading: Boolean,
    animatedBackgroundEnabled: Boolean,
    allowBackgroundMotion: Boolean,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val summary = snapshot?.target
    val displayName = summary?.displayName ?: target.fallbackName.ifBlank { target.steamId }
    val avatarUrl = summary?.avatarUrl?.ifBlank { target.fallbackAvatarUrl }
        ?: target.fallbackAvatarUrl
    val avatar = rememberSteamProfileViewerImage(avatarUrl)
    val pageBackground = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(244.dp)
            .clipToBounds()
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        SteamMiniProfileBackgroundLayer(
            steamId = summary?.steamId ?: target.steamId,
            enabled = animatedBackgroundEnabled,
            allowMotion = allowBackgroundMotion,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                        0.52f to pageBackground.copy(alpha = 0.34f),
                        0.82f to pageBackground.copy(alpha = 0.9f),
                        1f to pageBackground
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
        SteamRefreshIconButton(loading, onRefresh, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp))
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(82.dp),
                    shape = LocalSteamAvatarShape.current,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    if (avatar != null) {
                        Image(
                            bitmap = avatar,
                            contentDescription = displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = GoogleSansFlexFontFamily
                        ),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        summary?.let {
                            SteamProfileStatusPill(it.personaState, it.isPlaying)
                            it.steamLevel?.let { level ->
                                SteamProfileSmallPill(
                                    stringResource(R.string.steam_profile_level, level)
                                )
                            }
                        }
                    }
                }
            }
            summary?.currentGameName?.takeIf(String::isNotBlank)?.let { gameName ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(gameName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun SteamProfileInformationCard(
    snapshot: SteamProfileViewerSnapshot,
    modifier: Modifier = Modifier
) {
    val summary = snapshot.target
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.steam_friend_profile_information),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            SteamIdentityInfoCard(steamId64 = summary.steamId, embedded = true)
            if (summary.countryCode.isNotBlank()) {
                SteamProfileInformationRow(
                    icon = Icons.Default.Public,
                    label = stringResource(R.string.steam_friend_location),
                    value = summary.countryCode
                )
            }
            if (summary.timeCreated > 0L) {
                SteamProfileInformationRow(
                    icon = Icons.Default.Schedule,
                    label = stringResource(R.string.steam_profile_member_since),
                    value = formatSteamProfileDate(summary.timeCreated)
                )
            }
        }
    }
}

@Composable
private fun SteamProfileInformationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SteamProfileGameScopeSelector(
    selected: SteamProfileGameScope,
    onSelected: (SteamProfileGameScope) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        SteamProfileGameScope.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index, SteamProfileGameScope.entries.size),
                label = {
                    Text(
                        text = stringResource(
                            when (option) {
                                SteamProfileGameScope.ALL -> R.string.steam_profile_scope_all
                                SteamProfileGameScope.COMMON -> R.string.steam_profile_scope_common
                                SteamProfileGameScope.TARGET_ONLY -> R.string.steam_profile_scope_target_only
                            }
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
internal fun SteamProfileGameRow(
    game: SteamGame,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val image = rememberSteamProfileViewerImage(game.headerImageUrl)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 88.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(width = 108.dp, height = 61.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = game.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SportsEsports, contentDescription = null)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatSteamProfilePlaytime(game.playtimeForeverMinutes.toLong()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val total = game.achievementTotalCount
                    if (total != null && total > 0) {
                        Text(
                            text = stringResource(
                                R.string.steam_profile_achievement_short,
                                game.achievementUnlockedCount ?: 0,
                                total
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (game.isPerfectAchievementGame) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SteamProfileViewerStateCard(
    loading: Boolean,
    failure: SteamProfileViewerFailureReason?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loading) CircularProgressIndicator()
            Text(
                text = if (loading) {
                    stringResource(R.string.steam_profile_loading)
                } else {
                    steamProfileFailureMessage(failure)
                },
                style = MaterialTheme.typography.bodyLarge
            )
            if (!loading) {
                FilledTonalButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.steam_library_retry))
                }
            }
        }
    }
}

@Composable
private fun SteamProfileGameVisibilityCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SteamProfileInfoBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SteamProfileStatusPill(state: SteamPersonaState, playing: Boolean) {
    val text = when {
        playing -> stringResource(R.string.steam_profile_playing)
        state.isOnline -> stringResource(R.string.steam_friend_online)
        else -> stringResource(R.string.steam_friend_offline)
    }
    SteamProfileSmallPill(text)
}

@Composable
private fun SteamProfileSmallPill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
internal fun steamProfileFailureMessage(reason: SteamProfileViewerFailureReason?): String =
    stringResource(
        when (reason) {
            SteamProfileViewerFailureReason.ACCOUNT_REQUIRED -> R.string.steam_profile_account_required
            SteamProfileViewerFailureReason.SESSION_REQUIRED -> R.string.steam_profile_session_required
            SteamProfileViewerFailureReason.PRIVATE_PROFILE -> R.string.steam_profile_private
            SteamProfileViewerFailureReason.GAME_DATA_PRIVATE -> R.string.steam_profile_games_private
            SteamProfileViewerFailureReason.RATE_LIMITED -> R.string.steam_profile_rate_limited
            SteamProfileViewerFailureReason.INVALID_RESPONSE -> R.string.steam_profile_invalid_response
            SteamProfileViewerFailureReason.NETWORK,
            null -> R.string.steam_profile_network_error
        }
    )

internal fun formatSteamProfilePlaytime(minutes: Long): String {
    if (minutes < 60L) return "${minutes.coerceAtLeast(0L)}m"
    val hours = minutes.coerceAtLeast(0L) / 60.0
    return if (hours >= 100.0) {
        String.format(Locale.getDefault(), "%.0fh", hours)
    } else {
        String.format(Locale.getDefault(), "%.1fh", hours)
    }
}

internal fun formatSteamProfileDate(timestampSeconds: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestampSeconds * 1_000L))
