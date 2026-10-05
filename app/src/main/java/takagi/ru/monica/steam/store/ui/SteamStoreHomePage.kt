package takagi.ru.monica.steam.store.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.SteamExpressivePullToRefresh
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.rememberSteamAdaptiveLayout
import takagi.ru.monica.steam.navigation.ui.steamWindowTopPadding
import takagi.ru.monica.steam.navigation.ui.steamDockActionClearance
import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.store.filters.domain.resolveSteamStoreTagLabels
import takagi.ru.monica.steam.store.filters.ui.SteamStoreActiveFilterSummary
import takagi.ru.monica.steam.store.hints.domain.SteamStoreHintKind
import takagi.ru.monica.steam.store.presentation.SteamStoreUiState
import takagi.ru.monica.ui.LocalReduceAnimations

internal data class SteamStoreHomeActions(
    val query: (String) -> Unit,
    val search: () -> Unit,
    val filter: (SteamStoreBrowseFilter) -> Unit,
    val clearFilters: () -> Unit,
    val advancedFilters: () -> Unit,
    val refresh: () -> Unit,
    val loadMore: () -> Unit,
    val game: (SteamStoreItem) -> Unit,
    val event: (String) -> Unit,
    val account: () -> Unit,
    val cart: () -> Unit,
    val wishlist: () -> Unit,
    val freebies: () -> Unit,
    val points: () -> Unit,
    val activateProduct: () -> Unit,
    val workshopImport: () -> Unit,
    val notifications: () -> Unit,
    val settings: () -> Unit,
    val unlockFamilyView: (() -> Unit)? = null,
    val back: (() -> Unit)? = null
)

/** Stateless home surface shared by the real route and device interaction tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamStoreHomePage(
    state: SteamStoreUiState,
    listState: LazyListState,
    actions: SteamStoreHomeActions,
    itemHints: (Int) -> List<SteamStoreHintKind> = { emptyList() },
    showTags: Boolean = true,
    accountName: String? = null
) {
    var drawerOpen by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val columns = if (rememberSteamAdaptiveLayout().useTwoPaneLayout) 2 else 1
    val clearance = LocalSteamDockContentClearance.current
    val searching = state.query.isNotBlank()
    val catalog = state.browseFilter != SteamStoreBrowseFilter.ALL || state.storeFilters.isActive
    val refreshing = if (searching) state.searching else if (catalog) state.loadingCatalog else state.loadingHome
    val error = if (searching) state.error else if (catalog) state.catalogError else state.error
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    if (drawerOpen) SteamStoreNavigationDrawer(refreshing, actions) { drawerOpen = false }
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(Modifier.steamWindowTopPadding()) {
                TextField(
                    value = state.query, onValueChange = actions.query,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("store_home_search"),
                    placeholder = { Text(stringResource(R.string.store_home_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        if (actions.back != null) IconButton(onClick = actions.back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        } else IconButton(onClick = { focus.clearFocus(); drawerOpen = true }) {
                            Icon(Icons.Default.Menu, stringResource(R.string.more_options))
                        }
                    },
                    trailingIcon = {
                        if (searching) IconButton(onClick = { actions.query(""); focus.clearFocus() }) {
                            Icon(Icons.Default.Close, stringResource(R.string.store_home_clear_search))
                        } else Row {
                            IconButton(onClick = actions.account, modifier = Modifier.testTag("store_home_account")) {
                                Icon(Icons.Default.AccountCircle, accountName ?: stringResource(R.string.steam_store_account))
                            }
                        }
                    },
                    singleLine = true, shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { actions.search(); focus.clearFocus() })
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    StoreHomeCategories(state.browseFilter, searching, Modifier.weight(1f)) { focus.clearFocus(); actions.filter(it) }
                    IconButton(onClick = { focus.clearFocus(); actions.advancedFilters() }, modifier = Modifier.testTag("store_home_filters")) {
                        BadgedBox(badge = {
                            if (state.storeFilters.activeCount > 0) Badge { Text(state.storeFilters.activeCount.toString()) }
                        }) { Icon(Icons.Default.Tune, stringResource(R.string.steam_store_advanced_filters)) }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!keyboardVisible) ExtendedFloatingActionButton(
                onClick = actions.cart,
                modifier = Modifier.navigationBarsPadding().steamDockActionClearance().testTag("store_home_cart"),
                icon = { Icon(Icons.Default.ShoppingCart, null) },
                text = { Text(stringResource(R.string.steam_store_cart_tab, state.cart.size)) }
            )
        }
    ) { padding ->
        SteamExpressivePullToRefresh(refreshing, actions.refresh, Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("store_home_list"),
                contentPadding = PaddingValues(top = 4.dp, bottom = clearance + 88.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (state.storeFilters.isActive) item(key = "store_active_filters") {
                    SteamStoreActiveFilterSummary(state.storeFilters, state.filterMetadata, actions.clearFilters)
                }
                if (searching) item(key = "store_search_heading") {
                    Text(stringResource(R.string.store_home_search_results), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                }
                if (error != null) item(key = "store_home_error") {
                    StoreHomeFailure(actions.refresh, actions.unlockFamilyView)
                }
                if (searching) {
                    if (state.searching) item(key = "store_search_progress") {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("store_home_search_progress"))
                    } else if (state.searchResults.isEmpty() && error == null) item(key = "store_search_empty") {
                        StoreMessage(stringResource(R.string.steam_store_empty))
                    }
                    storeAdaptiveItems(state.searchResults, columns) { game ->
                        SearchResultCard(game, itemHints(game.appId), resolveSteamStoreTagLabels(game.tagIds, state.filterMetadata, showTags)) { actions.game(game) }
                    }
                } else if (catalog) {
                    if (state.catalogFromCache) item(key = "store_catalog_cache") { CachedNotice() }
                    if (state.loadingCatalog && state.catalogPage == null) item(key = "store_catalog_loading") { StoreHeroSkeleton() }
                    val games = state.catalogPage?.items.orEmpty()
                    if (!state.loadingCatalog && games.isEmpty() && error == null) item(key = "store_catalog_empty") {
                        StoreMessage(stringResource(R.string.steam_store_filter_empty))
                    }
                    storeAdaptiveItems(games, columns) { game ->
                        SearchResultCard(game, itemHints(game.appId), resolveSteamStoreTagLabels(game.tagIds, state.filterMetadata, showTags)) { actions.game(game) }
                    }
                    if (state.catalogPage?.hasMore == true) item(key = "store_catalog_more") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            FilledTonalButton(onClick = actions.loadMore, enabled = !state.loadingMoreCatalog) {
                                if (state.loadingMoreCatalog) CircularProgressIndicator(Modifier.size(18.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                                Text(stringResource(R.string.steam_store_load_more))
                            }
                        }
                    }
                } else {
                    if (state.homeFromCache) item(key = "store_home_cache") { CachedNotice() }
                    if (state.loadingHome && state.home == null) item(key = "store_home_loading") { StoreHeroSkeleton() }
                    state.home?.let { home ->
                        steamStoreDiscoveryItems(home, itemHints, actions.game, actions.event, actions.filter)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreHomeCategories(selected: SteamStoreBrowseFilter, searching: Boolean, modifier: Modifier = Modifier, onSelect: (SteamStoreBrowseFilter) -> Unit) {
    val scroll = rememberLazyListState()
    val reduceMotion = LocalReduceAnimations.current
    val filters = listOf(SteamStoreBrowseFilter.ALL, SteamStoreBrowseFilter.SPECIALS, SteamStoreBrowseFilter.TOP_SELLERS, SteamStoreBrowseFilter.NEW_RELEASES, SteamStoreBrowseFilter.COMING_SOON, SteamStoreBrowseFilter.FREE)
    LaunchedEffect(selected) {
        val index = filters.indexOf(selected)
        if (reduceMotion) scroll.scrollToItem(index) else scroll.animateScrollToItem(index)
    }
    val indicatorColor = MaterialTheme.colorScheme.primary
    LazyRow(state = scroll, modifier = modifier.height(48.dp).testTag("store_home_categories")) {
        items(filters, key = { it.name }) { filter ->
            val active = !searching && selected == filter
            Tab(
                selected = active, onClick = { onSelect(filter) },
                text = { Text(storeHomeCategoryLabel(filter), maxLines = 1) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 52.dp).testTag("store_category_${filter.name}").drawBehind {
                    if (active) drawLine(indicatorColor, Offset(12.dp.toPx(), size.height - 2.dp.toPx()), Offset(size.width - 12.dp.toPx(), size.height - 2.dp.toPx()), 3.dp.toPx())
                }
            )
        }
    }
}

@Composable
private fun storeHomeCategoryLabel(filter: SteamStoreBrowseFilter): String = stringResource(when (filter) {
    SteamStoreBrowseFilter.ALL -> R.string.store_home_discover
    SteamStoreBrowseFilter.SPECIALS -> R.string.store_home_specials
    SteamStoreBrowseFilter.TOP_SELLERS -> R.string.store_home_bestsellers
    SteamStoreBrowseFilter.NEW_RELEASES -> R.string.steam_store_new_releases
    SteamStoreBrowseFilter.COMING_SOON -> R.string.steam_store_coming_soon
    SteamStoreBrowseFilter.FREE -> R.string.store_home_free
})

@Composable
private fun StoreHomeFailure(onRetry: () -> Unit, onUnlock: (() -> Unit)?) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("store_home_error"), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (onUnlock == null) Icons.Default.CloudOff else Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(if (onUnlock == null) R.string.store_home_load_failed else R.string.steam_store_family_view_unlock), style = MaterialTheme.typography.titleMedium)
            if (onUnlock == null) Text(stringResource(R.string.store_home_retry_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onUnlock != null) FilledTonalButton(onClick = onUnlock) { Text(stringResource(R.string.steam_store_family_view_unlock)) }
            else FilledTonalButton(onClick = onRetry, modifier = Modifier.testTag("store_home_retry")) { Text(stringResource(R.string.store_home_retry)) }
        }
    }
}
