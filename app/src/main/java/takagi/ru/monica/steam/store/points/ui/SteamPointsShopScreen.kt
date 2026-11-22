package takagi.ru.monica.steam.store.points.ui

import takagi.ru.monica.steam.foundation.ui.SteamRefreshIconButton
import takagi.ru.monica.steam.foundation.ui.SteamListFilterChip as FilterChip

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassScaffold as Scaffold
import androidx.activity.compose.BackHandler
import takagi.ru.monica.steam.navigation.steamPageTransition
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.util.Locale
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.foundation.ui.SteamExpressivePullToRefresh
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.store.points.domain.SteamPointsShopCategory
import takagi.ru.monica.steam.store.points.domain.SteamPointsShopItem
import takagi.ru.monica.steam.store.points.presentation.SteamPointsShopViewModel
import takagi.ru.monica.steam.store.ui.SteamStoreImage
import takagi.ru.monica.ui.components.ExpressiveTopBar

@Composable
internal fun SteamPointsShopScreen(
    account: SteamAccount?,
    onBack: () -> Unit,
    onOpenOfficial: (String) -> Unit,
    modifier: Modifier = Modifier,
    appId: Int? = null,
    initialItem: SteamPointsShopItem? = null,
    viewModel: SteamPointsShopViewModel = viewModel(key = "steam-points-${appId ?: 0}",
        factory = SteamPointsShopViewModel.factory(LocalContext.current, appId)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dockClearance = LocalSteamDockContentClearance.current
    var selectedItem by remember(appId, initialItem?.definitionId, account?.id) { mutableStateOf(initialItem) }
    LaunchedEffect(account?.id, account?.accessToken, account?.steamLoginSecure) {
        viewModel.attachAccount(account)
    }

    val reduceAnimations = takagi.ru.monica.ui.LocalReduceAnimations.current
    BackHandler(enabled = selectedItem != null) { selectedItem = null }
    AnimatedContent(
        targetState = selectedItem,
        modifier = modifier,
        transitionSpec = {
            steamPageTransition(reduceAnimations, forward = targetState != null)
        },
        label = "SteamPointsRewardDetail"
    ) { detailItem ->
        if (detailItem != null) {
            SteamPointsRewardDetailScreen(
                item = detailItem,
                onBack = { selectedItem = null },
                onOpenOfficial = { onOpenOfficial(detailItem.officialUrl) },
                modifier = Modifier.fillMaxSize()
            )
            return@AnimatedContent
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            topBar = {
                ExpressiveTopBar(
                    title = stringResource(R.string.steam_points_shop_title),
                    searchQuery = "",
                    onSearchQueryChange = {},
                    isSearchExpanded = false,
                    onSearchExpandedChange = {},
                    modifier = Modifier.statusBarsPadding(),
                    collapsedTitleEndPadding = 72.dp,
                    actions = { SteamRefreshIconButton(state.loading, { viewModel.load(force = true) }) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            SteamExpressivePullToRefresh(
                showIndicator = false,
                refreshing = state.loading,
                onRefresh = { viewModel.load(force = true) },
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 154.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = dockClearance + 20.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SteamPointsBalanceCard(
                        balance = state.pointsBalance,
                        signedIn = state.signedIn
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(SteamPointsShopCategory.entries) { category ->
                            FilterChip(
                                selected = state.category == category,
                                onClick = { viewModel.selectCategory(category) },
                                label = { Text(pointsCategoryLabel(category)) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }
                if (state.error != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SteamPointsMessage(
                            message = state.error.orEmpty(),
                            onRetry = { viewModel.load(force = true) }
                        )
                    }
                }
                if (state.loading && state.items.isEmpty()) {
                    items(6) {
                        Card(
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.78f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                } else if (state.items.isEmpty() && state.error == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SteamPointsMessage(stringResource(R.string.steam_points_shop_empty))
                    }
                } else {
                    items(state.items, key = SteamPointsShopItem::definitionId) { item ->
                        SteamPointsRewardCard(item) { selectedItem = item }
                    }
                }
                if (state.hasMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Button(
                                onClick = { viewModel.load(force = false, loadMore = true) },
                                enabled = !state.loadingMore,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                if (state.loadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.steam_store_load_more))
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SteamPointsBalanceCard(balance: Long?, signedIn: Boolean) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                Icons.Default.Stars,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.steam_points_shop_balance),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = when {
                        balance != null -> NumberFormat.getIntegerInstance().format(balance)
                        signedIn -> stringResource(R.string.steam_points_shop_balance_unavailable)
                        else -> stringResource(R.string.steam_points_shop_sign_in_hint)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SteamPointsRewardCard(item: SteamPointsShopItem, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.fillMaxWidth()) {
            SteamStoreImage(
                url = item.imageUrl,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Fit,
                contentDescription = item.title
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.title.ifBlank { stringResource(R.string.steam_points_shop_reward) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.steam_points_shop_cost,
                            NumberFormat.getIntegerInstance(Locale.getDefault()).format(item.pointCost)
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SteamPointsMessage(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
        onRetry?.let {
            Button(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.steam_store_retry))
            }
        }
    }
}

@Composable
private fun pointsCategoryLabel(category: SteamPointsShopCategory): String = stringResource(
    when (category) {
        SteamPointsShopCategory.FEATURED -> R.string.steam_points_category_featured
        SteamPointsShopCategory.BACKGROUNDS -> R.string.steam_points_category_backgrounds
        SteamPointsShopCategory.EMOTICONS -> R.string.steam_points_category_emoticons
        SteamPointsShopCategory.STICKERS -> R.string.steam_points_category_stickers
        SteamPointsShopCategory.PROFILE -> R.string.steam_points_category_profile
        SteamPointsShopCategory.CHAT_EFFECTS -> R.string.steam_points_category_chat_effects
    }
)
