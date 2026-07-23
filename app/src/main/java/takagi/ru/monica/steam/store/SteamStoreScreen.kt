package takagi.ru.monica.steam.store

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamRegionalPrice
import takagi.ru.monica.steam.library.sortedRegionalPricesForDisplay
import takagi.ru.monica.steam.profile.SteamRemoteImageCache
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit
import java.util.Locale

private sealed interface SteamStoreDestination {
    data object Home : SteamStoreDestination
    data object Cart : SteamStoreDestination
    data class Detail(val appId: Int) : SteamStoreDestination
    data class Web(val url: String) : SteamStoreDestination
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamStoreScreen(
    showNavigationBack: Boolean = true,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SteamStoreViewModel = viewModel(factory = SteamStoreViewModel.factory(LocalContext.current))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAccounts by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var lastDetail by remember { mutableStateOf<SteamStoreDetail?>(null) }
    LaunchedEffect(state.detail) {
        state.detail?.let { lastDetail = it }
    }
    val webUrl = state.webUrl
    val detailAppId = state.detailAppId
    val storeDestination = when {
        webUrl != null -> SteamStoreDestination.Web(webUrl)
        detailAppId != null -> SteamStoreDestination.Detail(detailAppId)
        state.cartOpen -> SteamStoreDestination.Cart
        else -> SteamStoreDestination.Home
    }

    BackHandler(
        enabled = state.regionalPriceSheetOpen ||
            state.webUrl != null || state.cartOpen || state.detailAppId != null
    ) {
        when {
            state.regionalPriceSheetOpen -> viewModel.closeRegionalPrices()
            state.webUrl != null -> viewModel.closeStoreWeb()
            state.detailAppId != null -> viewModel.closeDetail()
            state.cartOpen -> viewModel.closeCart()
        }
    }

    AnimatedContent(
        targetState = storeDestination,
        modifier = modifier,
        transitionSpec = {
            easyNotesScreenEnter().togetherWith(easyNotesScreenExit())
        },
        label = "SteamStoreNavigation"
    ) { destination ->
        when (destination) {
            is SteamStoreDestination.Web -> SteamStoreWebScreen(
                url = destination.url,
                steamLoginSecure = viewModel.selectedAccount()?.steamLoginSecure,
                checkoutPackageIds = state.checkoutPackageIds,
                onClose = viewModel::closeStoreWeb,
                modifier = Modifier.fillMaxSize()
            )
            SteamStoreDestination.Cart -> SteamNativeCartScreen(
                cartItems = state.cart,
                wishlistItems = state.wishlist,
                selectedTab = state.collectionTab,
                loadingWishlist = state.loadingWishlist,
                wishlistFromCache = state.wishlistFromCache,
                wishlistError = state.wishlistError,
                onTabSelected = viewModel::selectCollectionTab,
                onBack = viewModel::closeCart,
                onRemove = viewModel::removeFromCart,
                onClear = viewModel::clearCart,
                onCheckout = viewModel::checkout,
                onRefreshWishlist = { viewModel.loadWishlist(force = true) },
                onOpenWishlistItem = viewModel::openDetail,
                modifier = Modifier.fillMaxSize()
            )
            is SteamStoreDestination.Detail -> {
                val detail = state.detail ?: lastDetail?.takeIf { it.appId == destination.appId }
                if (detail == null) {
                    SteamStoreDetailUnavailableContent(
                        loading = state.loadingDetail,
                        error = state.error,
                        onBack = viewModel::closeDetail,
                        onRetry = { viewModel.openDetail(destination.appId) },
                        onOpenOfficial = {
                            viewModel.openStoreWeb(
                                "https://store.steampowered.com/app/${destination.appId}/"
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SteamStoreDetailContent(
                        detail = detail,
                        loading = state.loadingDetail,
                        cached = state.detailFromCache,
                        onBack = viewModel::closeDetail,
                        onOpenOfficial = { viewModel.openStoreWeb(detail.storeUrl) },
                        inCart = state.cart.any { it.appId == detail.appId },
                        inWishlist = state.wishlist.any { it.appId == detail.appId },
                        wishlistAvailable = viewModel.selectedAccount()?.hasRealSteamId == true,
                        wishlistMutating = detail.appId in state.wishlistMutatingAppIds,
                        wishlistError = state.wishlistError,
                        regionalPrices = state.regionalPrices,
                        regionalPricesFromCache = state.regionalPricesFromCache,
                        loadingRegionalPrices = state.loadingRegionalPrices,
                        regionalPriceFailure = state.regionalPriceFailure,
                        showRegionalPrices = state.regionalPriceSheetOpen,
                        onToggleCart = {
                            if (state.cart.any { it.appId == detail.appId }) viewModel.removeFromCart(detail.appId)
                            else viewModel.addDetailToCart(detail)
                        },
                        onToggleWishlist = { viewModel.toggleWishlist(detail) },
                        onOpenRegionalPrices = { viewModel.openRegionalPrices(detail.appId) },
                        onCloseRegionalPrices = viewModel::closeRegionalPrices,
                        onRetryRegionalPrices = {
                            viewModel.loadRegionalPrices(detail.appId, force = true)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            SteamStoreDestination.Home -> Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                topBar = {
                    ExpressiveTopBar(
                        title = stringResource(R.string.steam_store_title),
                        searchQuery = state.query,
                        onSearchQueryChange = viewModel::updateQuery,
                        isSearchExpanded = searchExpanded,
                        onSearchExpandedChange = { expanded ->
                            searchExpanded = expanded
                            if (!expanded) viewModel.updateQuery("")
                        },
                        searchHint = stringResource(R.string.steam_store_search_hint),
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
                                onClick = { showAccounts = true },
                                enabled = state.accounts.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.SwitchAccount,
                                    contentDescription = stringResource(R.string.steam_store_account)
                                )
                            }
                            IconButton(onClick = { searchExpanded = true }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.steam_store_search)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.loadHome(force = true) },
                                enabled = !state.loadingHome
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.refresh)
                                )
                            }
                        }
                    )
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = viewModel::openCart,
                        icon = {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(
                                    R.string.steam_store_cart_tab,
                                    state.cart.size
                                )
                            )
                        }
                    )
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.searching) {
                        item {
                            androidx.compose.material3.LinearProgressIndicator(
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            )
                        }
                    }
                    if (state.error != null) {
                        item {
                            StoreMessage(
                                state.error.orEmpty(),
                                onRetry = {
                                    if (state.query.isBlank()) viewModel.loadHome(force = true)
                                    else viewModel.search()
                                }
                            )
                        }
                    }
                    if (state.query.isNotBlank() && !state.searching) {
                        if (state.searchResults.isEmpty()) {
                            item { StoreMessage(stringResource(R.string.steam_store_empty)) }
                        } else {
                            itemsIndexed(state.searchResults, key = ::steamStoreLazyKey) { _, item ->
                                SearchResultCard(item, onClick = { viewModel.openDetail(item.appId) })
                            }
                        }
                    } else {
                        if (state.homeFromCache) item { CachedNotice() }
                        if (state.loadingHome && state.home == null) item { StoreHeroSkeleton() }
                        state.home?.let { home ->
                            home.specials.firstOrNull()?.let { featured ->
                                item { StoreFeaturedHero(featured) { viewModel.openDetail(featured.appId) } }
                            }
                            item {
                                StoreSection(
                                    stringResource(R.string.steam_store_specials),
                                    home.specials,
                                    viewModel::openDetail
                                )
                            }
                            item {
                                StoreSection(
                                    stringResource(R.string.steam_store_top_sellers),
                                    home.topSellers,
                                    viewModel::openDetail
                                )
                            }
                            item {
                                StoreSection(
                                    stringResource(R.string.steam_store_new_releases),
                                    home.newReleases,
                                    viewModel::openDetail
                                )
                            }
                            if (home.comingSoon.isNotEmpty()) {
                                item {
                                    StoreSection(
                                        stringResource(R.string.steam_store_coming_soon),
                                        home.comingSoon,
                                        viewModel::openDetail
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAccounts) {
        AccountSheet(
            accounts = state.accounts,
            selectedId = state.selectedAccountId,
            onSelected = { viewModel.selectAccount(it); showAccounts = false },
            onDismiss = { showAccounts = false }
        )
    }
}

@Composable
private fun SteamStoreDetailUnavailableContent(
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenOfficial: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.steam_store_open_detail)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (loading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.steam_store_detail_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.steam_store_detail_unavailable),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                error?.takeIf(String::isNotBlank)?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.steam_store_retry))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenOfficial,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.Storefront, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.steam_store_open_official))
                }
            }
        }
    }
}

@Composable
private fun StoreFeaturedHero(game: SteamStoreItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(460f / 215f)
            ) {
                SteamStoreImage(
                    game.headerImageUrl.ifBlank { game.imageUrl },
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        stringResource(R.string.steam_store_specials),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    game.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                PriceRow(
                    game.discountPercent,
                    game.formattedInitialPrice,
                    game.formattedFinalPrice,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StoreHeroSkeleton() {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(460f / 215f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {}
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                Surface(
                    modifier = Modifier.weight(1f).height(150.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {}
            }
        }
    }
}

@Composable
private fun StoreSection(title: String, games: List<SteamStoreItem>, onOpen: (Int) -> Unit) {
    if (games.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${games.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(games, key = ::steamStoreLazyKey) { _, game ->
                StoreGameCard(game) { onOpen(game.appId) }
            }
        }
    }
}

internal fun steamStoreLazyKey(index: Int, item: SteamStoreItem): String =
    "${item.appId}-$index"

internal fun steamStoreRegionalPriceLazyKey(index: Int, price: SteamRegionalPrice): String =
    "${price.countryCode.uppercase(Locale.ROOT)}-$index"

@Composable
private fun StoreGameCard(game: SteamStoreItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(224.dp).height(290.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        SteamStoreImage(
            game.imageUrl.ifBlank { game.headerImageUrl },
            Modifier.fillMaxWidth().height(126.dp)
        )
        Column(
            Modifier.fillMaxWidth().height(164.dp).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.TopStart) {
                Text(game.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            PriceRow(game.discountPercent, game.formattedInitialPrice, game.formattedFinalPrice)
        }
    }
}

@Composable
private fun SearchResultCard(game: SteamStoreItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SteamStoreImage(
                    game.imageUrl.ifBlank { game.headerImageUrl },
                    Modifier.width(120.dp).aspectRatio(460f / 215f).clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    game.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            PriceRow(
                game.discountPercent,
                game.formattedInitialPrice,
                game.formattedFinalPrice,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(accounts: List<SteamAccount>, selectedId: Long?, onSelected: (Long) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.steam_store_account), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.steam_store_security_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (accounts.isEmpty()) Text(stringResource(R.string.steam_store_no_account), color = MaterialTheme.colorScheme.onSurfaceVariant)
            accounts.forEach { account ->
                val selected = account.id == selectedId
                Surface(
                    onClick = { onSelected(account.id) },
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = if (selected) 2.dp else 0.dp,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.padding(8.dp).size(32.dp),
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(account.displayName.ifBlank { account.accountName }, style = MaterialTheme.typography.titleMedium)
                            Text(account.visibleSteamId.ifBlank { account.accountName }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (account.steamLoginSecure.isNullOrBlank()) Text(stringResource(R.string.steam_store_no_session), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (selected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteamStoreDetailContent(
    detail: SteamStoreDetail,
    loading: Boolean,
    cached: Boolean,
    onBack: () -> Unit,
    onOpenOfficial: () -> Unit,
    inCart: Boolean,
    inWishlist: Boolean,
    wishlistAvailable: Boolean,
    wishlistMutating: Boolean,
    wishlistError: String?,
    regionalPrices: List<SteamRegionalPrice>,
    regionalPricesFromCache: Boolean,
    loadingRegionalPrices: Boolean,
    regionalPriceFailure: SteamLibraryFailureReason?,
    showRegionalPrices: Boolean,
    onToggleCart: () -> Unit,
    onToggleWishlist: () -> Unit,
    onOpenRegionalPrices: () -> Unit,
    onCloseRegionalPrices: () -> Unit,
    onRetryRegionalPrices: () -> Unit,
    modifier: Modifier
) {
    val heroBackgroundUrl = detail.backgroundImageUrl.ifBlank { detail.headerImageUrl }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(390.dp)) {
                SteamStoreImage(
                    url = heroBackgroundUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.24f
                )
                SteamStoreImage(
                    url = detail.headerImageUrl,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .aspectRatio(460f / 215f),
                    contentScale = ContentScale.Fit
                )
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.43f to Color.Transparent,
                            0.72f to MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                            1f to MaterialTheme.colorScheme.background
                        )
                    )
                )
                if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                Surface(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 12.dp, top = 8.dp)
                        .size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 3.dp
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.back)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        detail.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        onClick = onOpenRegionalPrices,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                PriceRow(
                                    detail.discountPercent,
                                    detail.formattedInitialPrice,
                                    detail.formattedFinalPrice,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = stringResource(R.string.steam_store_regional_price_description),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.AutoMirrored.Filled.CompareArrows,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.steam_store_regional_price_action),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (detail.windows) AssistChip(onClick = {}, label = { Text("Windows") })
                        if (detail.mac) AssistChip(onClick = {}, label = { Text("macOS") })
                        if (detail.linux) AssistChip(onClick = {}, label = { Text("Linux") })
                    }
                }
            }
        }
        if (cached) item { CachedNotice() }
        item {
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SteamStorePurchaseActions(
                    inCart = inCart,
                    inWishlist = inWishlist,
                    wishlistAvailable = wishlistAvailable,
                    wishlistMutating = wishlistMutating,
                    wishlistError = wishlistError,
                    onToggleCart = onToggleCart,
                    onToggleWishlist = onToggleWishlist,
                    onOpenOfficial = onOpenOfficial,
                    modifier = Modifier.fillMaxWidth()
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            stringResource(R.string.steam_store_security_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (detail.shortDescription.isNotBlank()) {
            item {
                DetailTextSection(
                    stringResource(R.string.steam_store_about),
                    detail.shortDescription
                )
            }
        }
        if (detail.screenshots.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.steam_store_screenshots),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(detail.screenshots) {
                            SteamStoreImage(
                                it,
                                Modifier
                                    .width(280.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        }
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.steam_store_information),
                        style = MaterialTheme.typography.titleLarge
                    )
                    DetailLine(
                        stringResource(R.string.steam_store_developer),
                        detail.developers.joinToString()
                    )
                    DetailLine(
                        stringResource(R.string.steam_store_publisher),
                        detail.publishers.joinToString()
                    )
                    DetailLine(stringResource(R.string.steam_store_release_date), detail.releaseDate)
                    if (detail.genres.isNotEmpty()) DetailLine("类型", detail.genres.joinToString())
                }
            }
        }
    }
    if (showRegionalPrices) {
        SteamStoreRegionalPriceSheet(
            gameName = detail.name,
            prices = regionalPrices,
            loading = loadingRegionalPrices,
            fromCache = regionalPricesFromCache,
            failure = regionalPriceFailure,
            onRetry = onRetryRegionalPrices,
            onDismiss = onCloseRegionalPrices
        )
    }
}

@Composable
private fun SteamStorePurchaseActions(
    inCart: Boolean,
    inWishlist: Boolean,
    wishlistAvailable: Boolean,
    wishlistMutating: Boolean,
    wishlistError: String?,
    onToggleCart: () -> Unit,
    onToggleWishlist: () -> Unit,
    onOpenOfficial: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onToggleCart,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.ShoppingCart, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (inCart) {
                    stringResource(R.string.steam_store_cart_remove)
                } else {
                    stringResource(R.string.steam_store_add_cart)
                }
            )
        }
        FilledTonalButton(
            onClick = onToggleWishlist,
            enabled = wishlistAvailable && !wishlistMutating,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            if (wishlistMutating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = if (inWishlist) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (inWishlist) {
                            R.string.steam_store_remove_wishlist
                        } else {
                            R.string.steam_store_add_wishlist
                        }
                    )
                )
            }
        }
        OutlinedButton(
            onClick = onOpenOfficial,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.Storefront, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.steam_store_buy)
            )
        }
        if (wishlistError != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = wishlistError,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteamStoreRegionalPriceSheet(
    gameName: String,
    prices: List<SteamRegionalPrice>,
    loading: Boolean,
    fromCache: Boolean,
    failure: SteamLibraryFailureReason?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val sortedPrices = remember(prices) { sortedRegionalPricesForDisplay(prices) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.steam_library_regional_prices),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = gameName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (loading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            if (fromCache && sortedPrices.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.steam_store_cached),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (failure != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = storeRegionalPriceFailureLabel(failure),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
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
            }
            if (!loading && sortedPrices.isEmpty() && failure == null) {
                item {
                    Text(
                        text = stringResource(R.string.steam_library_regional_prices_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                }
            }
            itemsIndexed(sortedPrices, key = ::steamStoreRegionalPriceLazyKey) { _, price ->
                SteamStoreRegionalPriceCard(price)
            }
            item {
                Text(
                    text = stringResource(R.string.steam_library_regional_price_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SteamStoreRegionalPriceCard(price: SteamRegionalPrice) {
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
        else -> formatStoreRegionalPrice(price.currency, price.finalPriceMinor)
    }
    val localOriginal = if (price.isAvailable) {
        formatStoreRegionalPrice(price.currency, price.originalPriceMinor)
    } else {
        unavailable
    }
    val cnyFinal = price.cnyFinalPriceMinor?.let {
        formatStoreRegionalPrice("CNY", it)
    } ?: unavailable
    val cnyOriginal = price.cnyOriginalPriceMinor?.let {
        formatStoreRegionalPrice("CNY", it)
    } ?: unavailable

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = regionalCountryName(price.countryCode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = price.currency,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (discount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.steam_library_regional_discount,
                                discount
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SteamStoreRegionalPriceColumn(
                    label = stringResource(R.string.steam_library_regional_local_price),
                    finalPrice = localFinal,
                    originalPrice = localOriginal,
                    discounted = discount > 0,
                    modifier = Modifier.weight(1f)
                )
                SteamStoreRegionalPriceColumn(
                    label = stringResource(R.string.steam_library_regional_cny_price),
                    finalPrice = cnyFinal,
                    originalPrice = cnyOriginal,
                    discounted = discount > 0,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SteamStoreRegionalPriceColumn(
    label: String,
    finalPrice: String,
    originalPrice: String,
    discounted: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = finalPrice,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.steam_store_regional_original_price, originalPrice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = if (discounted) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun storeRegionalPriceFailureLabel(failure: SteamLibraryFailureReason): String {
    return stringResource(
        when (failure) {
            SteamLibraryFailureReason.SESSION_REQUIRED -> R.string.steam_library_session_required
            SteamLibraryFailureReason.PRIVATE_PROFILE -> R.string.steam_library_private_profile
            SteamLibraryFailureReason.RATE_LIMITED -> R.string.steam_library_rate_limited
            SteamLibraryFailureReason.NETWORK -> R.string.steam_library_network_error
            SteamLibraryFailureReason.INVALID_RESPONSE -> R.string.steam_library_unavailable
        }
    )
}

private fun regionalCountryName(countryCode: String): String {
    return Locale("", countryCode).getDisplayCountry(Locale.getDefault())
        .ifBlank { countryCode }
}

private fun formatStoreRegionalPrice(currency: String, minor: Long): String {
    val cents = minor.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    return formatSteamPrice(cents, currency)
}

@Composable private fun DetailTextSection(title: String, text: String) = Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable private fun DetailLine(label: String, value: String) { if (value.isNotBlank()) Row(Modifier.fillMaxWidth()) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(92.dp)); Text(value, modifier = Modifier.weight(1f)) } }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PriceRow(discount: Int, initial: String, final: String, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (discount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "-$discount%",
                    Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        if (discount > 0) {
            Text(
                initial,
                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            final,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable private fun CachedNotice() { Text(stringResource(R.string.steam_store_cached), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp)) }

@Composable
private fun StoreMessage(message: String, onRetry: (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message)
            if (onRetry != null) FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.steam_store_retry)) }
        }
    }
}

@Composable
internal fun SteamStoreImage(
    url: String,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f
) {
    val context = LocalContext.current
    val cache = remember(context) { SteamRemoteImageCache.get(context.applicationContext) }
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        value = url.takeIf(String::isNotBlank)?.let { cache.load(it)?.asImageBitmap() }
    }
    val loadedImage = image
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        if (loadedImage != null) Image(
            loadedImage,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            alpha = alpha
        )
        else Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
