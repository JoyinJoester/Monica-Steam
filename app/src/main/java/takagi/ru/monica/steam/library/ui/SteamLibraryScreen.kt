package takagi.ru.monica.steam.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.library.SteamAchievement
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameAchievements
import takagi.ru.monica.steam.library.SteamGamePrice
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamLibrarySnapshot
import takagi.ru.monica.steam.library.SteamLibraryViewModel
import takagi.ru.monica.steam.library.SteamRegionalPrice
import takagi.ru.monica.steam.library.sortedRegionalPricesForDisplay
import takagi.ru.monica.steam.profile.SteamMiniProfileDecor
import takagi.ru.monica.steam.profile.SteamMiniProfileDecorRepository
import takagi.ru.monica.steam.profile.SteamRemoteImageCache
import takagi.ru.monica.steam.foundation.ui.SteamAvatarImage
import takagi.ru.monica.steam.profile.ui.SteamMiniProfileBackgroundLayer
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit
import takagi.ru.monica.ui.theme.GoogleSansFlexFontFamily
import takagi.ru.monica.utils.SettingsManager

private sealed interface SteamLibraryDestination {
    data object Overview : SteamLibraryDestination
    data object Account : SteamLibraryDestination
    data class Game(val appId: Int) : SteamLibraryDestination
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamLibraryScreen(
    onNavigateBack: () -> Unit,
    showNavigationBack: Boolean = true,
    onLoadingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: SteamLibraryViewModel = viewModel(
        factory = remember(context) { SteamLibraryViewModel.factory(context) }
    )
    val state by viewModel.uiState.collectAsState()
    val selectedGame = state.selectedGame
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var showAccountSheet by rememberSaveable { mutableStateOf(false) }
    var showAccountDetails by rememberSaveable { mutableStateOf(false) }
    var showRegionalPriceSheet by rememberSaveable { mutableStateOf(false) }
    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }
        ?: state.accounts.firstOrNull()
    val accountDetailsVisible = showAccountDetails && selectedAccount != null
    val libraryDestination = when {
        selectedGame != null -> SteamLibraryDestination.Game(selectedGame.appId)
        accountDetailsVisible -> SteamLibraryDestination.Account
        else -> SteamLibraryDestination.Overview
    }
    BackHandler(enabled = selectedGame != null || accountDetailsVisible) {
        if (selectedGame != null) viewModel.closeGame() else showAccountDetails = false
    }
    LaunchedEffect(state.loadingLibrary, libraryDestination) {
        onLoadingChange(
            state.loadingLibrary && libraryDestination == SteamLibraryDestination.Overview
        )
    }

    AnimatedContent(
        targetState = libraryDestination,
        modifier = modifier,
        transitionSpec = {
            easyNotesScreenEnter().togetherWith(easyNotesScreenExit())
        },
        label = "SteamLibraryNavigation"
    ) { destination ->
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (destination is SteamLibraryDestination.Overview) {
                    ExpressiveTopBar(
                        title = stringResource(R.string.steam_library_title),
                        searchQuery = query,
                        onSearchQueryChange = { query = it },
                        isSearchExpanded = searchExpanded,
                        onSearchExpandedChange = { searchExpanded = it },
                        searchHint = stringResource(R.string.steam_library_search_hint),
                        modifier = Modifier.statusBarsPadding(),
                        navigationIcon = if (showNavigationBack) {
                            {
                                IconButton(onClick = onNavigateBack) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back)
                                    )
                                }
                            }
                        } else null,
                        actions = {
                            IconButton(
                                onClick = { showAccountSheet = true },
                                enabled = state.accounts.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.SwitchAccount,
                                    contentDescription = stringResource(R.string.steam_switch_account)
                                )
                            }
                            IconButton(onClick = { searchExpanded = true }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.steam_library_search)
                                )
                            }
                            IconButton(
                                onClick = viewModel::refreshLibrary,
                                enabled = !state.loadingLibrary
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.refresh)
                                )
                            }
                        }
                    )
                }
            }
        ) { padding ->
            when (destination) {
                is SteamLibraryDestination.Game -> {
                    val game = selectedGame
                        ?: state.snapshot?.games?.firstOrNull { it.appId == destination.appId }
                    if (game != null) {
                        SteamGameDetail(
                            game = game,
                            achievements = state.achievements,
                            loading = state.loadingAchievements,
                            fromCache = state.achievementsFromCache,
                            failure = state.achievementFailure,
                            onRetry = { viewModel.openGame(game) },
                            onNavigateBack = viewModel::closeGame,
                            onOpenRegionalPrices = {
                                showRegionalPriceSheet = true
                                viewModel.loadRegionalPrices(game)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                        )
                    }
                }
                SteamLibraryDestination.Account -> {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val settingsManager = remember(context) {
                        SettingsManager(context.applicationContext)
                    }
                    val appSettings by settingsManager.settingsFlow.collectAsState(initial = AppSettings())
                    selectedAccount?.let { account ->
                        SteamAccountDetail(
                            account = account,
                            snapshot = state.snapshot,
                            fromCache = state.snapshotFromCache,
                            loading = state.loadingLibrary,
                            failure = state.libraryFailure,
                            appSettings = appSettings,
                            onNavigateBack = { showAccountDetails = false },
                            onRefresh = viewModel::refreshLibrary,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                        )
                    }
                }
                SteamLibraryDestination.Overview -> SteamLibraryOverview(
                    state = state,
                    query = query,
                    onOpenGame = viewModel::openGame,
                    onOpenAccountDetails = { showAccountDetails = true },
                    onRetry = viewModel::refreshLibrary,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }
    }
    if (showAccountSheet && selectedGame == null && !accountDetailsVisible) {
        SteamAccountSwitcherSheet(
            accounts = state.accounts,
            selectedAccountId = state.selectedAccountId,
            onSelectAccount = { accountId ->
                viewModel.selectAccount(accountId)
                showAccountSheet = false
            },
            onDismiss = { showAccountSheet = false }
        )
    }
    if (showRegionalPriceSheet && selectedGame != null) {
        SteamRegionalPriceSheet(
            game = selectedGame,
            loading = state.loadingRegionalPrices,
            failure = state.regionalPriceFailure,
            onRetry = { viewModel.loadRegionalPrices(selectedGame, force = true) },
            onDismiss = { showRegionalPriceSheet = false }
        )
    }
}

@Composable
private fun SteamLibraryOverview(
    state: takagi.ru.monica.steam.library.SteamLibraryUiState,
    query: String,
    onOpenGame: (SteamGame) -> Unit,
    onOpenAccountDetails: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsManager = remember(context) { SettingsManager(context.applicationContext) }
    val appSettings by settingsManager.settingsFlow.collectAsState(initial = AppSettings())
    var filterName by rememberSaveable { mutableStateOf(SteamLibraryGameFilter.ALL.name) }
    val filter = SteamLibraryGameFilter.entries
        .firstOrNull { it.name == filterName }
        ?: SteamLibraryGameFilter.ALL
    val snapshot = state.snapshot
    val sections = remember(snapshot, query, filter) {
        buildSteamLibrarySections(snapshot?.games.orEmpty(), query, filter)
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 112.dp)) {
        item {
            SteamAccountHeroSwitcher(
                accounts = state.accounts,
                selectedAccountId = state.selectedAccountId,
                snapshot = snapshot,
                appSettings = appSettings,
                onOpenAccountDetails = onOpenAccountDetails
            )
        }
        if (snapshot == null) {
            item {
                if (state.accounts.isEmpty()) {
                    Text(
                        stringResource(R.string.steam_library_no_accounts),
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LibraryStateMessage(
                        failure = state.libraryFailure,
                        loading = state.loadingLibrary,
                        onRetry = onRetry,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            return@LazyColumn
        }
        item {
            SteamLibraryFilterSplitButton(
                selectedFilter = filter,
                onSelectFilter = { filterName = it.name },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        if (sections.all { it.games.isEmpty() }) {
            item {
                SteamLibraryEmptySearch(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                )
            }
        } else {
            sections.forEach { section ->
                if (section.games.isNotEmpty()) {
                    item(key = "section_${section.type.name}") {
                        SteamGameSectionHeader(section)
                    }
                    itemsIndexed(
                        items = section.games,
                        key = { index, game -> steamLibraryGameLazyKey(section.type, index, game) }
                    ) { _, game ->
                        SteamGameLibraryRow(game = game, onClick = { onOpenGame(game) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SteamLibraryFilterSplitButton(
    selectedFilter: SteamLibraryGameFilter,
    onSelectFilter: (SteamLibraryGameFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "steam_library_filter_rotation"
    )
    Box(modifier = modifier.wrapContentSize(Alignment.TopStart)) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.TonalLeadingButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize)
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(steamLibraryFilterLabel(selectedFilter))
                }
            },
            trailingButton = {
                SplitButtonDefaults.TonalTrailingButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it }
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.steam_library_change_filter),
                        modifier = Modifier
                            .size(SplitButtonDefaults.TrailingIconSize)
                            .graphicsLayer { rotationZ = rotation }
                    )
                }
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(220.dp),
            offset = DpOffset(x = 0.dp, y = 10.dp),
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SteamLibraryGameFilter.entries.forEach { option ->
                    SteamLibraryFilterMenuItem(
                        filter = option,
                        selected = option == selectedFilter,
                        onClick = {
                            expanded = false
                            onSelectFilter(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SteamLibraryFilterMenuItem(
    filter: SteamLibraryGameFilter,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
        } else {
            Color.Transparent
        },
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(20.dp))
            }
            Text(
                text = steamLibraryFilterLabel(filter),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SteamAccountHeroSwitcher(
    accounts: List<SteamAccount>,
    selectedAccountId: Long?,
    snapshot: SteamLibrarySnapshot?,
    appSettings: AppSettings,
    onOpenAccountDetails: () -> Unit
) {
    if (accounts.isEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        return
    }
    val account = accounts.firstOrNull { it.id == selectedAccountId } ?: accounts.first()
    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        SteamAccountHeroCard(
            account = account,
            snapshot = snapshot.takeIf { account.id == selectedAccountId },
            appSettings = appSettings,
            onClick = onOpenAccountDetails
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteamAccountSwitcherSheet(
    accounts: List<SteamAccount>,
    selectedAccountId: Long?,
    onSelectAccount: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        Text(
            text = stringResource(R.string.steam_switch_account),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(accounts, key = SteamAccount::id) { account ->
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable { onSelectAccount(account.id) },
                    headlineContent = {
                        Text(
                            text = account.displayName.ifBlank {
                                account.accountName.ifBlank { account.visibleSteamId }
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            text = listOf(account.accountName, account.visibleSteamId)
                                .filter(String::isNotBlank)
                                .distinct()
                                .joinToString(" · "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingContent = {
                        SteamAvatarImage(account = account, size = 48.dp)
                    },
                    trailingContent = {
                        if (account.id == selectedAccountId) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.steam_selected_account_marker),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(modifier = Modifier.padding(start = 80.dp))
            }
        }
    }
}

@Composable
private fun SteamAccountHeroCard(
    account: SteamAccount,
    snapshot: SteamLibrarySnapshot?,
    appSettings: AppSettings,
    onClick: () -> Unit
) {
    val decor = rememberSteamMiniProfileDecor(account)
    val displayName = decor?.personaName?.takeIf(String::isNotBlank)
        ?: account.displayName.ifBlank { account.accountName.ifBlank { account.visibleSteamId } }
    val levelLabel = decor?.level?.let { level ->
        stringResource(R.string.steam_library_level, level)
    }
    val gamesLabel = snapshot?.let {
        stringResource(R.string.steam_library_games_short, it.gameCount)
    }
    val identityFacts = listOfNotNull(levelLabel, gamesLabel).joinToString(" · ")

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SteamLibraryLayoutTokens.OverviewHeroCornerRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SteamLibraryLayoutTokens.OverviewHeroMinHeight)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                )
        ) {
            if (account.hasRealSteamId) {
                SteamMiniProfileBackgroundLayer(
                    steamId = account.steamId,
                    enabled = true,
                    allowMotion = !appSettings.reduceAnimations,
                    modifier = Modifier.matchParentSize()
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.44f),
                                Color.Black.copy(alpha = 0.28f),
                                Color.Black.copy(alpha = 0.80f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SteamLibraryLayoutTokens.OverviewHeroMinHeight)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SteamFramedAvatar(account = account, decor = decor, compact = true)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = displayName.ifBlank { "Steam" },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = GoogleSansFlexFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                lineHeight = 23.sp,
                                letterSpacing = 0.sp
                            ),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (identityFacts.isNotEmpty()) {
                            Text(
                                text = identityFacts,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = GoogleSansFlexFontFamily,
                                    letterSpacing = 0.sp
                                ),
                                color = Color.White.copy(alpha = 0.88f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.steam_library_open_account_details),
                        tint = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroMetric(
                        label = stringResource(R.string.steam_library_total_playtime),
                        value = snapshot?.let { formatHeroPlaytime(it.totalPlaytimeMinutes) } ?: "—",
                        modifier = Modifier.weight(0.95f)
                    )
                    HeroMetric(
                        label = stringResource(R.string.steam_library_inventory_count),
                        value = snapshot?.inventoryItemCount?.toString() ?: "—",
                        modifier = Modifier.weight(0.85f)
                    )
                    HeroMetric(
                        label = stringResource(R.string.steam_library_estimated_value),
                        value = snapshot?.takeIf { it.pricedGameCount > 0 }?.let {
                            formatAccountHeroPrice(it.currency, it.estimatedReplacementValueMinor)
                        } ?: "—",
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SteamAccountDetail(
    account: SteamAccount,
    snapshot: SteamLibrarySnapshot?,
    fromCache: Boolean,
    loading: Boolean,
    failure: SteamLibraryFailureReason?,
    appSettings: AppSettings,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            SteamAccountDetailHero(
                account = account,
                snapshot = snapshot,
                appSettings = appSettings,
                loading = loading,
                onNavigateBack = onNavigateBack,
                onRefresh = onRefresh
            )
        }
        if (snapshot == null) {
            item {
                LibraryStateMessage(
                    failure = failure,
                    loading = loading,
                    onRetry = onRefresh,
                    modifier = Modifier.padding(16.dp)
                )
            }
            return@LazyColumn
        }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.steam_library_account_overview),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = GoogleSansFlexFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        letterSpacing = 0.sp
                    )
                )
                SteamAccountValueCard(
                    label = stringResource(R.string.steam_library_estimated_value),
                    value = if (snapshot.pricedGameCount > 0) {
                        formatPrice(
                            snapshot.currency,
                            snapshot.estimatedReplacementValueMinor
                        )
                    } else {
                        stringResource(R.string.steam_library_price_unavailable)
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SteamAccountDetailMetric(
                        label = stringResource(R.string.steam_library_game_count),
                        value = snapshot.gameCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    SteamAccountDetailMetric(
                        label = stringResource(R.string.steam_library_inventory_count),
                        value = snapshot.inventoryItemCount?.toString() ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SteamAccountDetailMetric(
                        label = stringResource(R.string.steam_library_total_playtime),
                        value = formatPlaytime(snapshot.totalPlaytimeMinutes),
                        modifier = Modifier.weight(1f)
                    )
                    SteamAccountDetailMetric(
                        label = stringResource(R.string.steam_library_recent_playtime),
                        value = formatPlaytime(snapshot.recentPlaytimeMinutes),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SteamAccountDetailMetric(
                        label = stringResource(R.string.steam_library_played_games),
                        value = snapshot.games.count { it.playtimeForeverMinutes > 0 }.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    SteamAccountDetailMetric(
                        label = stringResource(R.string.steam_library_unplayed_games),
                        value = snapshot.games.count { it.playtimeForeverMinutes == 0 }.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = stringResource(
                        R.string.steam_library_price_coverage,
                        snapshot.pricedGameCount,
                        snapshot.gameCount,
                        (snapshot.priceCoverage * 100).toInt()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                snapshot.inventoryFailure?.let {
                    Text(
                        text = stringResource(R.string.steam_library_inventory_cache_kept),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.steam_library_last_updated,
                            formatDateTime(snapshot.fetchedAt)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (fromCache) {
                        Text(
                            text = stringResource(R.string.steam_library_offline_cache),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SteamAccountDetailHero(
    account: SteamAccount,
    snapshot: SteamLibrarySnapshot?,
    appSettings: AppSettings,
    loading: Boolean,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val decor = rememberSteamMiniProfileDecor(account)
    val displayName = decor?.personaName?.takeIf(String::isNotBlank)
        ?: account.displayName.ifBlank { account.accountName.ifBlank { account.visibleSteamId } }
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clipToBounds()
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        if (account.hasRealSteamId) {
            SteamMiniProfileBackgroundLayer(
                steamId = account.steamId,
                enabled = true,
                allowMotion = !appSettings.reduceAnimations,
                modifier = Modifier.matchParentSize()
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.55f to Color.Black.copy(alpha = 0.38f),
                        0.82f to background.copy(alpha = 0.9f),
                        1f to background
                    )
                )
        )
        Surface(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 8.dp, top = 8.dp)
                .size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            tonalElevation = 2.dp
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 8.dp, top = 8.dp)
                .size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            tonalElevation = 2.dp
        ) {
            IconButton(onClick = onRefresh, enabled = !loading) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.refresh)
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SteamFramedAvatar(account = account, decor = decor)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName.ifBlank { "Steam" },
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = GoogleSansFlexFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 30.sp,
                            lineHeight = 34.sp,
                            letterSpacing = 0.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(
                            decor?.level?.let { stringResource(R.string.steam_library_level, it) },
                            snapshot?.let { stringResource(R.string.steam_library_games_short, it.gameCount) }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = account.visibleSteamId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SteamAccountDetailMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 112.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = GoogleSansFlexFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (value.length > 8) 22.sp else 30.sp,
                    lineHeight = if (value.length > 8) 26.sp else 34.sp,
                    letterSpacing = 0.sp,
                    fontFeatureSettings = "tnum"
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = GoogleSansFlexFontFamily,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SteamAccountValueCard(
    label: String,
    value: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 148.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = GoogleSansFlexFontFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp
                )
            )
            Text(
                text = value,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = GoogleSansFlexFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = accountValueTextSize(value),
                    lineHeight = accountValueLineHeight(value),
                    letterSpacing = 0.sp,
                    fontFeatureSettings = "tnum"
                ),
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Clip
            )
        }
    }
}

private fun accountValueTextSize(value: String) = when {
    value.length > 18 -> 24.sp
    value.length > 14 -> 28.sp
    value.length > 10 -> 32.sp
    else -> 36.sp
}

private fun accountValueLineHeight(value: String) = when {
    value.length > 18 -> 29.sp
    value.length > 14 -> 33.sp
    value.length > 10 -> 37.sp
    else -> 41.sp
}

@Composable
private fun rememberSteamMiniProfileDecor(account: SteamAccount): SteamMiniProfileDecor? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val decorRepository = remember(context) {
        SteamMiniProfileDecorRepository.get(context.applicationContext)
    }
    val decor by produceState<SteamMiniProfileDecor?>(
        initialValue = null,
        key1 = account.steamId
    ) {
        value = if (account.hasRealSteamId) decorRepository.load(account.steamId) else null
    }
    return decor
}

@Composable
private fun SteamFramedAvatar(
    account: SteamAccount,
    decor: SteamMiniProfileDecor?,
    compact: Boolean = false
) {
    val frame = rememberSteamRemoteImage(decor?.avatarFrameUrl)
    val frameSize = if (compact) SteamLibraryLayoutTokens.OverviewHeroFrameSize else 82.dp
    val avatarSize = if (compact) SteamLibraryLayoutTokens.OverviewHeroAvatarSize else 68.dp
    Box(modifier = Modifier.size(frameSize), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.88f), CircleShape)
        ) {
            SteamAvatarImage(
                account = account,
                size = avatarSize,
                modifier = Modifier.fillMaxSize()
            )
        }
        frame?.let { image ->
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.steam_library_avatar_frame),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = GoogleSansFlexFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                fontFeatureSettings = "tnum"
            ),
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = GoogleSansFlexFontFamily,
                letterSpacing = 0.sp
            ),
            color = Color.White.copy(alpha = 0.82f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SteamGameSectionHeader(section: SteamLibraryGameSection) {
    Text(
        text = steamLibrarySectionLabel(section.type, section.games.size),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SteamGameLibraryRow(game: SteamGame, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SteamGameBanner(game)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = formatGameHours(game.playtimeForeverMinutes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (game.playtimeRecentMinutes > 0) {
                        Text(
                            text = stringResource(
                                R.string.steam_library_recent_increment,
                                formatGameHours(game.playtimeRecentMinutes)
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.steam_library_open_game_details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        HorizontalDivider(modifier = Modifier.padding(start = 144.dp, end = 16.dp))
    }
}

@Composable
private fun SteamGameBanner(game: SteamGame) {
    val image = rememberSteamRemoteImage(steamGameImageUrls(game))
    Surface(
        modifier = Modifier
            .width(112.dp)
            .height(64.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(10.dp)
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = game.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SportsEsports, contentDescription = game.name)
            }
        }
    }
}

internal fun steamGameImageUrls(game: SteamGame): List<String> {
    val iconUrl = game.iconHash.takeIf(String::isNotBlank)?.let {
        "https://media.steampowered.com/steamcommunity/public/images/apps/${game.appId}/$it.jpg"
    }
    return listOfNotNull(
        game.headerImageUrl.takeIf(String::isNotBlank),
        "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${game.appId}/header.jpg",
        "https://cdn.akamai.steamstatic.com/steam/apps/${game.appId}/header.jpg",
        "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${game.appId}/capsule_231x87.jpg",
        "https://cdn.akamai.steamstatic.com/steam/apps/${game.appId}/capsule_231x87.jpg",
        iconUrl
    ).distinct()
}

@Composable
private fun SteamLibraryEmptySearch(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(
                text = stringResource(R.string.steam_library_no_matching_games),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun steamLibraryFilterLabel(filter: SteamLibraryGameFilter): String {
    return stringResource(
        when (filter) {
            SteamLibraryGameFilter.ALL -> R.string.steam_library_filter_all
            SteamLibraryGameFilter.UNPLAYED -> R.string.steam_library_filter_unplayed
            SteamLibraryGameFilter.RECENT -> R.string.steam_library_filter_recent
            SteamLibraryGameFilter.LONG_PLAYED -> R.string.steam_library_filter_long_played
            SteamLibraryGameFilter.PERFECT -> R.string.steam_library_filter_perfect
        }
    )
}

@Composable
private fun steamLibrarySectionLabel(type: SteamLibraryGameSectionType, count: Int): String {
    return stringResource(
        when (type) {
            SteamLibraryGameSectionType.RECENT -> R.string.steam_library_section_recent
            SteamLibraryGameSectionType.PLAYED -> R.string.steam_library_section_played
            SteamLibraryGameSectionType.UNPLAYED -> R.string.steam_library_section_unplayed
            SteamLibraryGameSectionType.RESULTS -> R.string.steam_library_section_results
        },
        count
    )
}

internal fun steamAchievementLazyKey(index: Int, achievement: SteamAchievement): String =
    "${achievement.apiName.ifBlank { "achievement" }}-$index"

internal fun steamRegionalPriceLazyKey(index: Int, price: SteamRegionalPrice): String =
    "${price.countryCode.uppercase(Locale.ROOT)}-$index"

@Composable
private fun LibrarySummary(snapshot: SteamLibrarySnapshot) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCell(
                label = stringResource(R.string.steam_library_game_count),
                value = snapshot.gameCount.toString(),
                modifier = Modifier.weight(1f)
            )
            SummaryCell(
                label = stringResource(R.string.steam_library_total_playtime),
                value = formatPlaytime(snapshot.totalPlaytimeMinutes),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCell(
                label = stringResource(R.string.steam_library_recent_playtime),
                value = formatPlaytime(snapshot.recentPlaytimeMinutes),
                modifier = Modifier.weight(1f)
            )
            SummaryCell(
                label = stringResource(R.string.steam_library_estimated_value),
                value = if (snapshot.pricedGameCount == 0) "-" else {
                    formatPrice(snapshot.currency, snapshot.estimatedReplacementValueMinor)
                },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            stringResource(
                R.string.steam_library_price_coverage,
                snapshot.pricedGameCount,
                snapshot.gameCount,
                (snapshot.priceCoverage * 100).toInt()
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        snapshot.priceFailure?.let { failure ->
            Text(
                libraryFailureLabel(failure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteamGameDetail(
    game: SteamGame,
    achievements: SteamGameAchievements?,
    loading: Boolean,
    fromCache: Boolean,
    failure: SteamLibraryFailureReason?,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenRegionalPrices: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filterName by rememberSaveable(game.appId) {
        mutableStateOf(SteamAchievementFilter.ALL.name)
    }
    val filter = SteamAchievementFilter.entries
        .firstOrNull { it.name == filterName }
        ?: SteamAchievementFilter.ALL
    val visibleAchievements = remember(achievements, filter) {
        when (filter) {
            SteamAchievementFilter.ALL -> achievements?.achievements.orEmpty()
                .sortedByDescending(SteamAchievement::achieved)
            SteamAchievementFilter.COMPLETED -> achievements?.completed.orEmpty()
            SteamAchievementFilter.INCOMPLETE -> achievements?.incomplete.orEmpty()
        }
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            SteamGameDetailHero(
                game = game,
                achievements = achievements,
                onNavigateBack = onNavigateBack,
                onOpenRegionalPrices = onOpenRegionalPrices
            )
        }
        item {
            if (loading) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.steam_library_loading_achievements))
                }
            }
            if (fromCache) {
                Text(
                    stringResource(R.string.steam_library_achievements_cached),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            failure?.let {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        libraryFailureLabel(it),
                        color = MaterialTheme.colorScheme.error
                    )
                    FilledTonalButton(onClick = onRetry, enabled = !loading) {
                        Text(stringResource(R.string.steam_library_retry))
                    }
                }
            }
        }
        if (achievements != null) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SteamAchievementFilter.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = filter == option,
                                onClick = { filterName = option.name },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = SteamAchievementFilter.entries.size
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                label = { Text(steamAchievementFilterLabel(option)) }
                            )
                        }
                    }
                }
            }
            if (achievements.achievements.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.steam_library_no_achievements),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                if (visibleAchievements.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(
                                if (filter == SteamAchievementFilter.COMPLETED) {
                                    R.string.steam_library_no_completed_achievements
                                } else {
                                    R.string.steam_library_no_incomplete_achievements
                                }
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                itemsIndexed(visibleAchievements, key = ::steamAchievementLazyKey) { _, achievement ->
                    AchievementItem(achievement)
                }
            }
        }
    }
}

@Composable
private fun SteamGameDetailHero(
    game: SteamGame,
    achievements: SteamGameAchievements?,
    onNavigateBack: () -> Unit,
    onOpenRegionalPrices: () -> Unit
) {
    val image = rememberSteamRemoteImage(steamGameImageUrls(game))
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = game.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopCenter
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SportsEsports,
                    contentDescription = game.name,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = 0.48f),
                        0.2f to Color.Transparent,
                        0.5f to background.copy(alpha = 0.12f),
                        0.72f to background.copy(alpha = 0.88f),
                        1f to background
                    )
                )
        )
        Surface(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 8.dp, top = 8.dp)
                .size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            tonalElevation = 2.dp
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = game.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SteamGameDetailMetric(
                    label = stringResource(R.string.steam_library_cn_price),
                    value = game.price?.takeIf(SteamGamePrice::isAvailable)?.let {
                        if (it.finalPriceMinor == 0L) stringResource(R.string.steam_library_free)
                        else formatPrice(it.currency, it.finalPriceMinor)
                    } ?: stringResource(R.string.steam_library_price_unavailable),
                    onClick = onOpenRegionalPrices,
                    modifier = Modifier.weight(1f)
                )
                SteamGameDetailMetric(
                    label = stringResource(R.string.steam_library_total_playtime),
                    value = formatGameHours(game.playtimeForeverMinutes),
                    modifier = Modifier.weight(1f)
                )
                SteamGameDetailMetric(
                    label = stringResource(R.string.steam_library_recent_playtime),
                    value = formatGameHours(game.playtimeRecentMinutes),
                    modifier = Modifier.weight(1f)
                )
            }
            achievements?.let {
                Text(
                    text = stringResource(
                        R.string.steam_library_achievement_progress,
                        it.completed.size,
                        it.achievements.size,
                        (it.completionRate * 100).toInt()
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SteamGameDetailMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (onClick != null) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun steamAchievementFilterLabel(filter: SteamAchievementFilter): String {
    return stringResource(
        when (filter) {
            SteamAchievementFilter.ALL -> R.string.steam_library_achievement_all
            SteamAchievementFilter.COMPLETED -> R.string.steam_library_completed
            SteamAchievementFilter.INCOMPLETE -> R.string.steam_library_incomplete
        }
    )
}

@Composable
private fun AchievementItem(achievement: SteamAchievement) {
    ListItem(
        headlineContent = { Text(achievement.displayName) },
        supportingContent = {
            Column {
                if (achievement.description.isNotBlank()) Text(achievement.description)
                if (achievement.unlockTimeSeconds != null) {
                    Text(
                        stringResource(
                            R.string.steam_library_unlocked_at,
                            formatDateTime(achievement.unlockTimeSeconds * 1_000L)
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        leadingContent = {
            SteamAchievementIcon(achievement)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
}

@Composable
private fun SteamAchievementIcon(achievement: SteamAchievement) {
    val image = rememberSteamRemoteImage(
        if (achievement.achieved) achievement.iconUrl else achievement.lockedIconUrl
    )
    Surface(
        modifier = Modifier
            .size(48.dp)
            .alpha(if (achievement.achieved) 1f else 0.62f),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.small
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = achievement.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    if (achievement.achieved) Icons.Default.CheckCircle else Icons.Default.Help,
                    contentDescription = null,
                    tint = if (achievement.achieved) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteamRegionalPriceSheet(
    game: SteamGame,
    loading: Boolean,
    failure: SteamLibraryFailureReason?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val sortedPrices = remember(game.regionalPrices) {
        sortedRegionalPricesForDisplay(game.regionalPrices)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.steam_library_regional_prices),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = game.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (loading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }
            }
            if (failure != null) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = libraryFailureLabel(failure),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = onRetry,
                            enabled = !loading,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.steam_library_retry))
                        }
                    }
                }
            }
            if (!loading && game.regionalPrices.isEmpty() && failure == null) {
                item {
                    Text(
                        text = stringResource(R.string.steam_library_regional_prices_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
            if (sortedPrices.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.steam_library_regional_region),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.steam_library_regional_local_price),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.25f)
                        )
                        Text(
                            text = stringResource(R.string.steam_library_regional_cny_price),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.25f)
                        )
                    }
                }
            }
            itemsIndexed(sortedPrices, key = ::steamRegionalPriceLazyKey) { _, price ->
                SteamRegionalPriceRow(price)
            }
            item {
                Text(
                    text = stringResource(R.string.steam_library_regional_price_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SteamRegionalPriceRow(price: SteamRegionalPrice) {
    val discount = if (price.originalPriceMinor > price.finalPriceMinor &&
        price.originalPriceMinor > 0L
    ) {
        ((price.originalPriceMinor - price.finalPriceMinor) * 100L /
            price.originalPriceMinor).toInt()
    } else {
        0
    }
    val unavailable = stringResource(R.string.steam_library_price_unavailable)
    val localFinal = when {
        !price.isAvailable -> unavailable
        price.finalPriceMinor == 0L -> stringResource(R.string.steam_library_free)
        else -> formatPrice(price.currency, price.finalPriceMinor)
    }
    val localOriginal = if (price.isAvailable) {
        formatPrice(price.currency, price.originalPriceMinor)
    } else {
        unavailable
    }
    val cnyFinal = price.cnyFinalPriceMinor?.let(::formatCnyPrice) ?: unavailable
    val cnyOriginal = price.cnyOriginalPriceMinor?.let(::formatCnyPrice) ?: unavailable

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = regionalCountryName(price.countryCode),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (discount > 0) {
                    stringResource(R.string.steam_library_regional_discount, discount)
                } else {
                    price.currency
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (discount > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SteamRegionalPriceColumn(
            finalPrice = localFinal,
            originalPrice = localOriginal,
            discounted = discount > 0,
            modifier = Modifier.weight(1.25f)
        )
        SteamRegionalPriceColumn(
            finalPrice = cnyFinal,
            originalPrice = cnyOriginal,
            discounted = discount > 0,
            modifier = Modifier.weight(1.25f)
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
}

@Composable
private fun SteamRegionalPriceColumn(
    finalPrice: String,
    originalPrice: String,
    discounted: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = finalPrice,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = originalPrice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = if (discounted) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun regionalCountryName(countryCode: String): String {
    return Locale("", countryCode).getDisplayCountry(Locale.getDefault())
        .ifBlank { countryCode }
}

private enum class SteamAchievementFilter {
    ALL,
    COMPLETED,
    INCOMPLETE
}

@Composable
private fun GameIcon(game: SteamGame, large: Boolean = false) {
    val iconUrl = game.iconHash.takeIf(String::isNotBlank)?.let {
        "https://media.steampowered.com/steamcommunity/public/images/apps/${game.appId}/$it.jpg"
    }
    val bitmap = rememberSteamRemoteImage(iconUrl)
    Surface(
        modifier = Modifier.size(if (large) 64.dp else 48.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        bitmap?.let { loadedBitmap ->
            Image(
                bitmap = loadedBitmap,
                contentDescription = game.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } ?: run {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.SportsEsports, contentDescription = game.name)
            }
        }
    }
}

@Composable
private fun rememberSteamRemoteImage(url: String?): ImageBitmap? {
    return rememberSteamRemoteImage(listOfNotNull(url))
}

@Composable
private fun rememberSteamRemoteImage(urls: List<String>): ImageBitmap? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cache = remember(context) { SteamRemoteImageCache.get(context.applicationContext) }
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = urls
    ) {
        value = urls.firstNotNullOfOrNull { candidate ->
            cache.load(candidate)?.asImageBitmap()
        }
    }
    return bitmap
}

@Composable
private fun LibraryStateMessage(
    failure: SteamLibraryFailureReason?,
    loading: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (loading) {
            CircularProgressIndicator()
        } else {
            Icon(
                when (failure) {
                    SteamLibraryFailureReason.PRIVATE_PROFILE -> Icons.Default.Warning
                    SteamLibraryFailureReason.SESSION_REQUIRED -> Icons.Default.Error
                    SteamLibraryFailureReason.RATE_LIMITED -> Icons.Default.Warning
                    SteamLibraryFailureReason.NETWORK -> Icons.Default.Help
                    SteamLibraryFailureReason.INVALID_RESPONSE, null -> Icons.Default.Help
                },
                contentDescription = null
            )
            Text(libraryFailureLabel(failure))
            FilledTonalButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Text(
                    text = stringResource(R.string.refresh),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun libraryFailureLabel(failure: SteamLibraryFailureReason?): String {
    return stringResource(
        when (failure) {
            SteamLibraryFailureReason.PRIVATE_PROFILE -> R.string.steam_library_private_profile
            SteamLibraryFailureReason.SESSION_REQUIRED -> R.string.steam_library_session_required
            SteamLibraryFailureReason.RATE_LIMITED -> R.string.steam_library_rate_limited
            SteamLibraryFailureReason.NETWORK -> R.string.steam_library_network_error
            SteamLibraryFailureReason.INVALID_RESPONSE, null -> R.string.steam_library_unavailable
        }
    )
}

private fun formatPlaytime(minutes: Long): String {
    val hours = minutes / 60L
    val remaining = minutes % 60L
    return if (hours == 0L) "${remaining}m" else "${hours}h ${remaining}m"
}

private fun formatPlaytime(minutes: Int): String = formatPlaytime(minutes.toLong())

private fun formatHeroPlaytime(minutes: Long): String {
    val hours = minutes / 60.0
    return if (hours >= 100.0) {
        String.format(Locale.getDefault(), "%.0fh", hours)
    } else {
        String.format(Locale.getDefault(), "%.1fh", hours)
    }
}

private fun formatGameHours(minutes: Int): String {
    val hours = minutes / 60.0
    return if (hours >= 100.0) {
        String.format(Locale.getDefault(), "%.0fh", hours)
    } else {
        String.format(Locale.getDefault(), "%.1fh", hours)
    }
}

private fun formatPrice(currency: String, minor: Long): String {
    return "$currency ${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"
}

private fun formatCnyPrice(minor: Long): String {
    return "¥${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"
}

private fun formatCompactPrice(currency: String, minor: Long): String {
    val number = compactPriceNumber(minor)
    return listOf(currency.takeIf(String::isNotBlank), number).filterNotNull().joinToString(" ")
}

private fun formatAccountHeroPrice(currency: String, minor: Long): String {
    val prefix = when (currency.uppercase(Locale.ROOT)) {
        "CNY", "JPY" -> "¥"
        "USD" -> "\$"
        "EUR" -> "€"
        "GBP" -> "£"
        "KRW" -> "₩"
        else -> currency.uppercase(Locale.ROOT)
    }
    return "$prefix${compactPriceNumber(minor)}"
}

private fun compactPriceNumber(minor: Long): String {
    val major = minor / 100.0
    return when {
        major >= 1_000_000.0 -> String.format(Locale.getDefault(), "%.1fM", major / 1_000_000.0)
        major >= 10_000.0 -> String.format(Locale.getDefault(), "%.1fK", major / 1_000.0)
        else -> String.format(Locale.getDefault(), "%.0f", major)
    }
}

private fun formatDateTime(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
}
