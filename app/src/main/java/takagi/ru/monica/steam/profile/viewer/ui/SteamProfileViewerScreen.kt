package takagi.ru.monica.steam.profile.viewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.foundation.ui.SteamExpressivePullToRefresh
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerTarget
import takagi.ru.monica.steam.profile.viewer.presentation.SteamProfileViewerViewModel
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.steam.navigation.steamPageTransition
import takagi.ru.monica.utils.SettingsManager

private sealed interface SteamProfileViewerDestination {
    data object Overview : SteamProfileViewerDestination
    data object Badges : SteamProfileViewerDestination
    data object Friends : SteamProfileViewerDestination
    data object Groups : SteamProfileViewerDestination
    data object PerfectGames : SteamProfileViewerDestination
    data class Achievement(val appId: Int) : SteamProfileViewerDestination
    data class FriendProfile(val steamId: String) : SteamProfileViewerDestination
    data class GroupDetail(val groupId: String) : SteamProfileViewerDestination
}

@Composable
fun SteamProfileViewerScreen(
    viewerAccount: SteamAccount,
    target: SteamProfileViewerTarget,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    knownSelfGames: List<SteamGame> = emptyList()
) {
    val context = LocalContext.current
    val reduceAnimations = LocalReduceAnimations.current
    val settingsManager = remember(context) { SettingsManager(context.applicationContext) }
    val appSettings by settingsManager.settingsFlow.collectAsState(initial = AppSettings())
    val profileViewModel: SteamProfileViewerViewModel = viewModel(
        key = "steam_profile_${viewerAccount.id}_${target.steamId}",
        factory = remember(context) { SteamProfileViewerViewModel.factory(context) }
    )
    val state by profileViewModel.uiState.collectAsState()
    val selectedGame = state.selectedGame
    var showBadges by rememberSaveable(target.steamId) { mutableStateOf(false) }
    var showFriends by rememberSaveable(target.steamId) { mutableStateOf(false) }
    var showGroups by rememberSaveable(target.steamId) { mutableStateOf(false) }
    var showPerfectGames by rememberSaveable(target.steamId) { mutableStateOf(false) }
    var selectedFriendSteamId by remember(target.steamId) { mutableStateOf<String?>(null) }
    var selectedGroupId by remember(target.steamId) { mutableStateOf<String?>(null) }

    LaunchedEffect(
        viewerAccount.id,
        viewerAccount.steamId,
        viewerAccount.accessToken,
        target.steamId,
        target.fallbackName,
        target.fallbackAvatarUrl,
        knownSelfGames
    ) {
        profileViewModel.load(
            viewer = viewerAccount,
            target = target,
            knownSelfGames = knownSelfGames
        )
    }

    val destination: SteamProfileViewerDestination = when {
        selectedFriendSteamId != null -> SteamProfileViewerDestination.FriendProfile(
            requireNotNull(selectedFriendSteamId)
        )
        selectedGroupId != null -> SteamProfileViewerDestination.GroupDetail(
            requireNotNull(selectedGroupId)
        )
        selectedGame != null -> SteamProfileViewerDestination.Achievement(selectedGame.appId)
        showBadges -> SteamProfileViewerDestination.Badges
        showFriends -> SteamProfileViewerDestination.Friends
        showGroups -> SteamProfileViewerDestination.Groups
        showPerfectGames -> SteamProfileViewerDestination.PerfectGames
        else -> SteamProfileViewerDestination.Overview
    }

    BackHandler(enabled = true) {
        when {
            selectedFriendSteamId != null -> selectedFriendSteamId = null
            selectedGroupId != null -> selectedGroupId = null
            selectedGame != null -> profileViewModel.closeGame()
            showBadges -> showBadges = false
            showFriends -> showFriends = false
            showGroups -> showGroups = false
            showPerfectGames -> showPerfectGames = false
            else -> onNavigateBack()
        }
    }

    AnimatedContent(
        targetState = destination,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            steamPageTransition(reduceAnimations, forward = targetState != SteamProfileViewerDestination.Overview)
        },
        contentKey = { animatedDestination -> animatedDestination },
        label = "SteamProfileViewerNavigation"
    ) { animatedDestination ->
        when (animatedDestination) {
            SteamProfileViewerDestination.Overview -> {
                SteamExpressivePullToRefresh(
                    showIndicator = false,
                    refreshing = state.loading,
                    onRefresh = profileViewModel::refresh,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxSize()
                ) {
                    SteamProfileViewerOverview(
                        state = state,
                        target = target,
                        animatedBackgroundEnabled = appSettings.steamMiniProfileBackgroundEnabled,
                        allowBackgroundMotion = !reduceAnimations,
                        onNavigateBack = onNavigateBack,
                        onRefresh = profileViewModel::refresh,
                        onOpenBadges = { showBadges = true },
                        onOpenFriends = {
                            showFriends = true
                            profileViewModel.loadFriends()
                        },
                        onOpenGroups = {
                            showGroups = true
                            profileViewModel.loadGroups()
                        },
                        onOpenPerfectGames = { showPerfectGames = true },
                        onOpenGame = profileViewModel::openGame,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            SteamProfileViewerDestination.Badges -> {
                state.snapshot?.let { snapshot ->
                    SteamProfileBadgesScreen(
                        snapshot = snapshot,
                        onNavigateBack = { showBadges = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            SteamProfileViewerDestination.Friends -> {
                SteamProfileFriendsScreen(
                    friends = state.friends,
                    loading = state.loadingFriends,
                    failure = state.friendsFailure,
                    onNavigateBack = { showFriends = false },
                    onRefresh = { profileViewModel.loadFriends(force = true) },
                    onOpenFriend = { friend -> selectedFriendSteamId = friend.steamId },
                    modifier = Modifier.fillMaxSize()
                )
            }
            SteamProfileViewerDestination.Groups -> {
                SteamProfileGroupsScreen(
                    groups = state.groups,
                    loading = state.loadingGroups,
                    failure = state.groupsFailure,
                    onNavigateBack = { showGroups = false },
                    onRefresh = { profileViewModel.loadGroups(force = true) },
                    onOpenGroup = { group -> selectedGroupId = group.groupId },
                    modifier = Modifier.fillMaxSize()
                )
            }
            SteamProfileViewerDestination.PerfectGames -> {
                state.snapshot?.let { snapshot ->
                    SteamProfilePerfectGamesScreen(
                        snapshot = snapshot,
                        onNavigateBack = { showPerfectGames = false },
                        onOpenGame = profileViewModel::openGame,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            is SteamProfileViewerDestination.Achievement -> {
                selectedGame?.let { game ->
                    SteamProfileAchievementComparisonScreen(
                        state = state,
                        game = game,
                        onNavigateBack = profileViewModel::closeGame,
                        onRetry = { profileViewModel.openGame(game) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            is SteamProfileViewerDestination.FriendProfile -> {
                val friend = state.friends.firstOrNull {
                    it.steamId == animatedDestination.steamId
                }
                SteamProfileViewerScreen(
                    viewerAccount = viewerAccount,
                    target = SteamProfileViewerTarget(
                        steamId = animatedDestination.steamId,
                        fallbackName = friend?.displayName.orEmpty(),
                        fallbackAvatarUrl = friend?.avatarUrl.orEmpty(),
                        fallbackProfileUrl = friend?.profileUrl.orEmpty()
                    ),
                    onNavigateBack = { selectedFriendSteamId = null },
                    modifier = Modifier.fillMaxSize()
                )
            }
            is SteamProfileViewerDestination.GroupDetail -> {
                state.groups.firstOrNull { it.groupId == animatedDestination.groupId }?.let { group ->
                    SteamProfileGroupDetailScreen(
                        group = group,
                        onNavigateBack = { selectedGroupId = null },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
