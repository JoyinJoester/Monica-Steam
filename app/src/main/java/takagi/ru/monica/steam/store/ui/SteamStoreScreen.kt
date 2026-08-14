package takagi.ru.monica.steam.store.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.steam.foundation.ui.SteamAccountSwitcherSheet
import takagi.ru.monica.steam.foundation.ui.SteamExpressivePullToRefresh
import takagi.ru.monica.steam.foundation.ui.SteamPageOverflowMenu
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamRegionalPrice
import takagi.ru.monica.steam.library.isSteamSouthAsiaPriceCountry
import takagi.ru.monica.steam.itad.ui.ItadHistoryLowSection
import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.store.interest.ui.SteamStoreIgnoreButton
import takagi.ru.monica.steam.store.interest.domain.SteamStoreIgnoreSyncState
import takagi.ru.monica.steam.store.freebie.ui.SteamFreebieScreen
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimResult
import takagi.ru.monica.steam.store.filters.domain.resolveSteamStoreTagLabels
import takagi.ru.monica.steam.store.filters.domain.findTagId
import takagi.ru.monica.steam.store.filters.ui.SteamStoreActiveFilterSummary
import takagi.ru.monica.steam.store.filters.ui.SteamStoreAdvancedFilterSheet
import takagi.ru.monica.steam.store.filters.ui.SteamStoreTagBadges
import takagi.ru.monica.steam.store.hints.data.SteamStoreHintPreferences
import takagi.ru.monica.steam.store.hints.domain.SteamStoreHintKind
import takagi.ru.monica.steam.store.hints.domain.SteamStoreHintSettings
import takagi.ru.monica.steam.store.hints.domain.resolveSteamStoreDetailHints
import takagi.ru.monica.steam.store.hints.domain.resolveSteamStoreItemHints
import takagi.ru.monica.steam.store.hints.ui.SteamStoreHintBadges
import takagi.ru.monica.steam.store.gift.ui.SteamStoreGiftPurchaseSplitButton
import takagi.ru.monica.steam.store.gift.ui.SteamStoreGiftRecipientSheet
import takagi.ru.monica.steam.store.gift.data.steamStoreCheckoutAutomationFactory
import takagi.ru.monica.steam.store.presentation.SteamStoreViewModel
import takagi.ru.monica.steam.store.points.ui.SteamPointsShopScreen
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreOwnershipStatus
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePackageOption
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContext
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContextFailure
import takagi.ru.monica.steam.store.purchase.ui.SteamStorePurchaseContextSection
import takagi.ru.monica.steam.store.purchase.ui.SteamStoreFreeLicenseButton
import takagi.ru.monica.steam.store.requirements.ui.SteamStoreSystemRequirementsSection
import takagi.ru.monica.steam.store.related.ui.SteamStoreRelatedContentSection
import takagi.ru.monica.steam.store.share.domain.SteamStoreGameShare
import takagi.ru.monica.steam.store.share.domain.toGameShare
import takagi.ru.monica.steam.store.share.ui.SteamStoreGameShareSheet
import takagi.ru.monica.steam.store.bundle.ui.SteamStoreBundleSection
import takagi.ru.monica.steam.store.ui.gallery.SteamStoreScreenshotViewer
import takagi.ru.monica.steam.store.activation.domain.SteamStoreProductActivation
import takagi.ru.monica.steam.library.sortedRegionalPricesForDisplay
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.steamDockActionClearance
import takagi.ru.monica.steam.navigation.ui.steamWindowBottomPadding
import takagi.ru.monica.steam.navigation.ui.steamWindowTopPadding
import takagi.ru.monica.steam.profile.SteamRemoteImageCache
import takagi.ru.monica.steam.web.ui.SteamWebBrowserScreen
import takagi.ru.monica.steam.web.domain.SteamWebNavigationPolicy
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit
import java.util.Locale
import kotlinx.coroutines.launch

private sealed interface SteamStoreDestination {
    data object Home : SteamStoreDestination
    data object Cart : SteamStoreDestination
    data object PointsShop : SteamStoreDestination
    data object Freebies : SteamStoreDestination
    data class Detail(val appId: Int) : SteamStoreDestination
    data class Web(val url: String) : SteamStoreDestination
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamStoreScreen(
    showNavigationBack: Boolean = true,
    onNavigateBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onAddSteamAccount: () -> Unit = {},
    onOpenChatShare: (String, SteamStoreGameShare) -> Unit = { _, _ -> },
    initialAppId: Int? = null,
    onInitialAppIdConsumed: () -> Unit = {},
    initialWebUrl: String? = null,
    onInitialWebUrlConsumed: () -> Unit = {},
    onPlatformViewVisibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SteamStoreViewModel = viewModel(factory = SteamStoreViewModel.factory(LocalContext.current))
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hintPreferences = remember(context) { SteamStoreHintPreferences(context) }
    val hintSettings by hintPreferences.settings.collectAsState(
        initial = SteamStoreHintSettings()
    )
    val wishlistAppIds = remember(state.wishlist) {
        state.wishlist.mapTo(linkedSetOf()) { it.appId }
    }
    val itemHints: (Int) -> List<SteamStoreHintKind> = remember(
        hintSettings,
        state.ownedAppIds,
        state.familySharedAppIds,
        wishlistAppIds
    ) {
        { appId ->
            resolveSteamStoreItemHints(
                appId = appId,
                settings = hintSettings,
                ownedAppIds = state.ownedAppIds,
                familySharedAppIds = state.familySharedAppIds,
                wishlistAppIds = wishlistAppIds
            )
        }
    }
    val reduceAnimations = LocalReduceAnimations.current
    val dockContentClearance = LocalSteamDockContentClearance.current
    val storeRefreshing = state.loadingHome || state.loadingCatalog || state.searching
    val refreshStore = {
        viewModel.refreshHintSources()
        viewModel.loadStoreFilterMetadata(force = true)
        if (state.query.isNotBlank()) {
            viewModel.search()
        } else if (state.browseFilter == SteamStoreBrowseFilter.ALL &&
            !state.storeFilters.isActive
        ) {
            viewModel.loadHome(force = true)
        } else {
            viewModel.loadCatalog(force = true)
        }
    }
    var showAccounts by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }
    var freebiesOpen by rememberSaveable { mutableStateOf(false) }
    var pendingGameShare by remember { mutableStateOf<SteamStoreGameShare?>(null) }
    var lastDetail by remember { mutableStateOf<SteamStoreDetail?>(null) }
    LaunchedEffect(state.detail) {
        state.detail?.let { lastDetail = it }
    }
    LaunchedEffect(initialAppId) {
        initialAppId?.let { appId ->
            viewModel.openDetail(appId)
            onInitialAppIdConsumed()
        }
    }
    LaunchedEffect(initialWebUrl) {
        initialWebUrl?.let { url ->
            viewModel.openStoreWeb(url)
            onInitialWebUrlConsumed()
        }
    }
    LaunchedEffect(state.selectedAccountId, state.storageSource) {
        viewModel.loadWishlist()
        viewModel.loadStoreFilterMetadata()
    }
    val webUrl = state.webUrl
    val detailAppId = state.detailAppId
    val selectedStoreAccount = viewModel.selectedAccount()
    val checkoutAutomationFactory = remember(state.checkoutLines) {
        steamStoreCheckoutAutomationFactory(state.checkoutLines)
    }
    val storeDestination = when {
        webUrl != null -> SteamStoreDestination.Web(webUrl)
        detailAppId != null -> SteamStoreDestination.Detail(detailAppId)
        state.cartOpen -> SteamStoreDestination.Cart
        state.pointsShopOpen -> SteamStoreDestination.PointsShop
        freebiesOpen -> SteamStoreDestination.Freebies
        else -> SteamStoreDestination.Home
    }

    BackHandler(
        enabled = state.webUrl == null && (
            state.regionalPriceSheetOpen || state.cartOpen || state.detailAppId != null ||
                state.pointsShopOpen || freebiesOpen
            )
    ) {
        when {
            state.regionalPriceSheetOpen -> viewModel.closeRegionalPrices()
            state.detailAppId != null -> viewModel.closeDetail()
            state.cartOpen -> viewModel.closeCart()
            state.pointsShopOpen -> viewModel.closePointsShop()
            freebiesOpen -> freebiesOpen = false
        }
    }

    AnimatedContent(
        targetState = storeDestination,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        transitionSpec = {
            if (initialState is SteamStoreDestination.Web ||
                targetState is SteamStoreDestination.Web
            ) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                easyNotesScreenEnter(reduceAnimations)
                    .togetherWith(easyNotesScreenExit(reduceAnimations))
            }
        },
        label = "SteamStoreNavigation"
    ) { destination ->
        when (destination) {
            is SteamStoreDestination.Web -> SteamWebBrowserScreen(
                url = destination.url,
                title = if (destination.url == SteamStoreProductActivation.REGISTER_KEY_URL) {
                    stringResource(R.string.steam_store_activate_product_code)
                } else {
                    null
                },
                steamLoginSecure = selectedStoreAccount?.steamLoginSecure
                    ?: selectedStoreAccount?.accessToken?.let { token ->
                        "${selectedStoreAccount.steamId}||$token"
                    },
                expectedSteamId = selectedStoreAccount?.steamId,
                automationFactory = checkoutAutomationFactory,
                requireAuthenticatedSession = state.webRequiresAuthenticatedSession,
                onPlatformViewVisibilityChanged = onPlatformViewVisibilityChanged,
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
                onEditGiftRecipient = viewModel::editGiftRecipient,
                onClear = viewModel::clearCart,
                onCheckout = viewModel::checkout,
                onRefreshWishlist = { viewModel.loadWishlist(force = true) },
                onOpenWishlistItem = viewModel::openDetail,
                modifier = Modifier.fillMaxSize()
            )
            SteamStoreDestination.PointsShop -> SteamPointsShopScreen(
                account = viewModel.selectedAccount(),
                onBack = viewModel::closePointsShop,
                onOpenOfficial = viewModel::openStoreWeb,
                modifier = Modifier.fillMaxSize()
            )
            SteamStoreDestination.Freebies -> SteamFreebieScreen(
                onBack = { freebiesOpen = false },
                onOpenDetail = viewModel::openDetail,
                onOpenOfficial = viewModel::openAuthenticatedStoreWeb,
                onAddSteamAccount = onAddSteamAccount,
                modifier = Modifier.fillMaxSize()
            )
            is SteamStoreDestination.Detail -> {
                val detail = state.detail ?: lastDetail?.takeIf { it.appId == destination.appId }
                if (detail == null) {
                    SteamStoreDetailUnavailableContent(
                        loading = state.loadingDetail,
                        error = state.error,
                        familyViewUnlockRequired = state.familyViewUnlockRequired,
                        onBack = viewModel::closeDetail,
                        onRetry = viewModel::retryDetail,
                        onUnlockFamilyView = viewModel::openFamilyViewUnlock,
                        onOpenOfficial = {
                            viewModel.openStoreWeb(
                                "https://store.steampowered.com/app/${destination.appId}/"
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val detailHints = resolveSteamStoreDetailHints(
                        detail = detail,
                        settings = hintSettings,
                        owned = state.purchaseContext?.ownership ==
                            SteamStoreOwnershipStatus.OWNED || detail.appId in state.ownedAppIds,
                        familyShared = detail.appId in state.familySharedAppIds,
                        inWishlist = detail.appId in wishlistAppIds
                    )
                    val filterableDetailTags = remember(detail.tags, state.filterMetadata) {
                        detail.tags.filterTo(linkedSetOf()) { label ->
                            state.filterMetadata?.findTagId(label) != null
                        }
                    }
                    SteamStoreDetailContent(
                        detail = detail,
                        hints = detailHints,
                        showTags = hintSettings.storeTagsEnabled,
                        filterableTags = filterableDetailTags,
                        loading = state.loadingDetail,
                        cached = state.detailFromCache,
                        purchaseContext = state.purchaseContext,
                        purchaseContextFromCache = state.purchaseContextFromCache,
                        loadingPurchaseContext = state.loadingPurchaseContext,
                        purchaseContextFailure = state.purchaseContextFailure,
                        alreadyOwned = state.purchaseContext?.ownership ==
                            SteamStoreOwnershipStatus.OWNED || detail.appId in state.ownedAppIds,
                        freeLicenseOption = detail.freeLicenseOption.takeIf {
                            detail.availableInAccountRegion != false
                        },
                        freeLicenseClaiming = detail.appId in state.freeLicenseClaimingAppIds,
                        freeLicenseClaimResult = state.freeLicenseClaimResults[detail.appId],
                        onBack = viewModel::closeDetail,
                        onOpenOfficial = { viewModel.openStoreWeb(detail.storeUrl) },
                        onOpenOfficialReviews = {
                            viewModel.openStoreWeb(detail.reviewsUrl)
                        },
                        onShare = {
                            pendingGameShare = detail.toGameShare()
                            viewModel.prepareShareFriends()
                        },
                        onOpenWebsite = { rawUrl ->
                            val normalizedUrl = normalizeSteamStoreWebsiteUrl(rawUrl)
                            when {
                                normalizedUrl == null -> showStoreWebsiteOpenFailure(context)
                                SteamWebNavigationPolicy.isAllowed(normalizedUrl) ->
                                    viewModel.openStoreWeb(normalizedUrl)
                                else -> openExternalStoreWebsite(context, normalizedUrl)
                            }
                        },
                        reviewFilters = state.reviewFilters,
                        loadingMoreReviews = state.loadingMoreReviews,
                        reviewLoadError = state.reviewLoadError,
                        onReviewFiltersChanged = viewModel::updateReviewFilters,
                        onLoadMoreReviews = viewModel::loadMoreReviews,
                        cartItem = state.cart.firstOrNull { it.appId == detail.appId },
                        inWishlist = state.wishlist.any { it.appId == detail.appId },
                        wishlistAvailable = viewModel.selectedAccount()?.hasRealSteamId == true,
                        wishlistMutating = detail.appId in state.wishlistMutatingAppIds,
                        wishlistError = state.wishlistError,
                        ignored = detail.ignored,
                        ignoreAvailable = viewModel.selectedAccount()?.hasRealSteamId == true,
                        ignoreMutating = detail.appId in state.ignoredMutatingAppIds,
                        ignoreSyncState = state.ignoredSyncStates[detail.appId],
                        ignoredError = state.ignoredError,
                        regionalPrices = state.regionalPrices,
                        regionalPricesFromCache = state.regionalPricesFromCache,
                        loadingRegionalPrices = state.loadingRegionalPrices,
                        regionalPriceFailure = state.regionalPriceFailure,
                        showRegionalPrices = state.regionalPriceSheetOpen,
                        onAddToCart = { packageOption ->
                            viewModel.addDetailToCart(detail, packageOption)
                        },
                        onAddAsGift = { packageOption ->
                            viewModel.beginGiftPurchase(detail, packageOption)
                        },
                        onClaimFreeLicense = {
                            if (selectedStoreAccount == null) {
                                showAccounts = true
                            } else {
                                viewModel.openAuthenticatedStoreWeb(detail.storeUrl)
                            }
                        },
                        onRemoveFromCart = { viewModel.removeFromCart(detail.appId) },
                        onOpenCart = viewModel::openCart,
                        onToggleWishlist = { viewModel.toggleWishlist(detail) },
                        onToggleIgnored = { viewModel.toggleIgnored(detail) },
                        onOpenRegionalPrices = { viewModel.openRegionalPrices(detail.appId) },
                        onCloseRegionalPrices = viewModel::closeRegionalPrices,
                        onRetryRegionalPrices = {
                            viewModel.loadRegionalPrices(detail.appId, force = true)
                        },
                        onOpenRelatedApp = viewModel::openRelatedDetail,
                        onOpenBundle = viewModel::openStoreWeb,
                        onFilterByTag = viewModel::filterByDetailTag,
                        onOpenItadSettings = onOpenSettings,
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
                        modifier = Modifier.steamWindowTopPadding(),
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
                            SteamStoreBrowseMenu(
                                selectedFilter = state.browseFilter,
                                activeFilterCount = state.storeFilters.activeCount,
                                onSelectFilter = viewModel::selectBrowseFilter,
                                onOpenAdvancedFilters = {
                                    showAdvancedFilters = true
                                    viewModel.loadStoreFilterMetadata()
                                },
                                onOpenFreebies = { freebiesOpen = true },
                                onOpenPointsShop = viewModel::openPointsShop,
                                onOpenProductActivation = {
                                    viewModel.openAuthenticatedStoreWeb(
                                        SteamStoreProductActivation.REGISTER_KEY_URL
                                    )
                                }
                            )
                            IconButton(
                                onClick = { showAccounts = true },
                                enabled = state.accounts.isNotEmpty() ||
                                    state.mdbxDatabases.isNotEmpty()
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
                            SteamPageOverflowMenu(
                                refreshing = storeRefreshing,
                                onRefresh = refreshStore,
                                onOpenNotifications = onOpenNotifications,
                                onOpenSettings = onOpenSettings
                            )
                        }
                    )
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = viewModel::openCart,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .steamDockActionClearance(),
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
                SteamExpressivePullToRefresh(
                    refreshing = storeRefreshing,
                    onRefresh = refreshStore,
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = dockContentClearance + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                    if (state.storeFilters.isActive) {
                        item(key = "store_active_filters") {
                            SteamStoreActiveFilterSummary(
                                selection = state.storeFilters,
                                metadata = state.filterMetadata,
                                onClear = viewModel::clearStoreFilters
                            )
                        }
                    }
                    if (state.searching) {
                        item {
                            androidx.compose.material3.LinearProgressIndicator(
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            )
                        }
                    }
                        if (state.error != null || state.catalogError != null) {
                        item {
                            StoreMessage(
                                message = state.catalogError ?: state.error.orEmpty(),
                                onRetry = {
                                        if (state.query.isBlank() &&
                                            (state.browseFilter != SteamStoreBrowseFilter.ALL ||
                                                state.storeFilters.isActive)
                                        ) {
                                            viewModel.loadCatalog(force = true)
                                        } else if (state.query.isBlank()) viewModel.loadHome(force = true)
                                        else viewModel.search()
                                },
                                onUnlockFamilyView = if (state.familyViewUnlockRequired) {
                                    viewModel::openFamilyViewUnlock
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    if (state.query.isNotBlank() && !state.searching) {
                        if (state.searchResults.isEmpty()) {
                            item { StoreMessage(stringResource(R.string.steam_store_empty)) }
                        } else {
                            itemsIndexed(state.searchResults, key = ::steamStoreLazyKey) { _, item ->
                                SearchResultCard(
                                    game = item,
                                    hints = itemHints(item.appId),
                                    tagLabels = resolveSteamStoreTagLabels(
                                        tagIds = item.tagIds,
                                        metadata = state.filterMetadata,
                                        enabled = hintSettings.storeTagsEnabled
                                    ),
                                    onClick = { viewModel.openDetail(item) }
                                )
                            }
                        }
                    } else if (state.browseFilter != SteamStoreBrowseFilter.ALL ||
                        state.storeFilters.isActive
                    ) {
                        if (state.catalogFromCache) item { CachedNotice() }
                        if (state.loadingCatalog && state.catalogPage == null) {
                            item { StoreHeroSkeleton() }
                        }
                        val catalogItems = state.catalogPage?.items.orEmpty()
                        if (!state.loadingCatalog && catalogItems.isEmpty() && state.catalogError == null) {
                            item { StoreMessage(stringResource(R.string.steam_store_filter_empty)) }
                        } else {
                            itemsIndexed(catalogItems, key = ::steamStoreLazyKey) { _, item ->
                                SearchResultCard(
                                    game = item,
                                    hints = itemHints(item.appId),
                                    tagLabels = resolveSteamStoreTagLabels(
                                        tagIds = item.tagIds,
                                        metadata = state.filterMetadata,
                                        enabled = hintSettings.storeTagsEnabled
                                    ),
                                    onClick = { viewModel.openDetail(item) }
                                )
                            }
                            if (state.catalogPage?.hasMore == true) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FilledTonalButton(
                                            onClick = { viewModel.loadCatalog(loadMore = true) },
                                            enabled = !state.loadingMoreCatalog,
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            if (state.loadingMoreCatalog) {
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
                    } else {
                        if (state.homeFromCache) item { CachedNotice() }
                        if (state.loadingHome && state.home == null) item { StoreHeroSkeleton() }
                        state.home?.let { home ->
                            item {
                                SteamStoreDiscoveryContent(
                                    home = home,
                                    selectedFilter = state.browseFilter,
                                    itemHints = itemHints,
                                    onOpenGame = viewModel::openDetail,
                                    onOpenEvent = viewModel::openStoreWeb
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
        SteamAccountSwitcherSheet(
            accounts = state.accounts,
            selectedAccountId = state.selectedAccountId,
            storageSource = state.storageSource,
            mdbxDatabases = state.mdbxDatabases,
            loading = state.accountsLoading,
            errorMessage = state.accountSourceError,
            onSelectStorageSource = viewModel::selectStorageSource,
            onSelectAccount = {
                viewModel.selectAccount(it)
                showAccounts = false
            },
            onAddAccount = onAddSteamAccount,
            onRefresh = viewModel::refreshAccountSource,
            onDismiss = { showAccounts = false }
        )
    }
    if (showAdvancedFilters) {
        SteamStoreAdvancedFilterSheet(
            selection = state.storeFilters,
            metadata = state.filterMetadata,
            loading = state.loadingFilterMetadata,
            error = state.filterMetadataError,
            onRetry = { viewModel.loadStoreFilterMetadata(force = true) },
            onApply = { selection ->
                viewModel.applyStoreFilters(selection)
                showAdvancedFilters = false
            },
            onDismiss = { showAdvancedFilters = false }
        )
    }
    if (state.gift.pickerOpen) {
        SteamStoreGiftRecipientSheet(
            state = state.gift,
            onSelect = viewModel::selectGiftRecipient,
            onRefresh = viewModel::refreshGiftFriends,
            onDismiss = viewModel::dismissGiftRecipientPicker
        )
    }
    pendingGameShare?.let { share ->
        SteamStoreGameShareSheet(
            share = share,
            friendsState = state.gift,
            onOpenChat = { friend ->
                pendingGameShare = null
                onOpenChatShare(friend.steamId, share)
            },
            onShareExternal = {
                shareSteamStoreGame(context, share)
            },
            onRefresh = viewModel::refreshGiftFriends,
            onDismiss = {
                pendingGameShare = null
            }
        )
    }
}

@Composable
private fun SteamStoreDetailUnavailableContent(
    loading: Boolean,
    error: String?,
    familyViewUnlockRequired: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUnlockFamilyView: () -> Unit,
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
                if (familyViewUnlockRequired) {
                    Button(
                        onClick = onUnlockFamilyView,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.steam_store_family_view_unlock))
                    }
                    Spacer(Modifier.height(8.dp))
                }
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
internal fun StoreFeaturedHero(
    game: SteamStoreItem,
    hints: List<SteamStoreHintKind> = emptyList(),
    onClick: () -> Unit
) {
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
                SteamStoreHintBadges(
                    hints = hints,
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                    compact = true
                )
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
internal fun StoreSection(
    title: String,
    games: List<SteamStoreItem>,
    itemHints: (Int) -> List<SteamStoreHintKind> = { emptyList() },
    onOpen: (Int) -> Unit
) {
    if (games.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${games.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(games, key = ::steamStoreLazyKey) { _, game ->
                StoreGameCard(
                    game = game,
                    hints = itemHints(game.appId),
                    onClick = { onOpen(game.appId) }
                )
            }
        }
    }
}

internal fun steamStoreLazyKey(index: Int, item: SteamStoreItem): String =
    "${item.appId}-$index"

internal fun steamStoreRegionalPriceLazyKey(index: Int, price: SteamRegionalPrice): String =
    "${price.countryCode.uppercase(Locale.ROOT)}-$index"

@Composable
private fun StoreGameCard(
    game: SteamStoreItem,
    hints: List<SteamStoreHintKind>,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(SteamStoreLayoutTokens.GameCardWidth)
            .height(SteamStoreLayoutTokens.GameCardHeight),
        shape = RoundedCornerShape(SteamStoreLayoutTokens.CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Box(Modifier.fillMaxWidth().height(SteamStoreLayoutTokens.GameImageHeight)) {
            SteamStoreImage(
                game.imageUrl.ifBlank { game.headerImageUrl },
                Modifier.fillMaxSize()
            )
            SteamStoreHintBadges(
                hints = hints,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                compact = true
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .height(SteamStoreLayoutTokens.GameBodyHeight)
                .padding(SteamStoreLayoutTokens.GameCardPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.TopStart) {
                Text(game.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            PriceRow(game.discountPercent, game.formattedInitialPrice, game.formattedFinalPrice)
        }
    }
}

@Composable
private fun SearchResultCard(
    game: SteamStoreItem,
    hints: List<SteamStoreHintKind>,
    tagLabels: List<String>,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(SteamStoreLayoutTokens.SearchCardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SteamStoreImage(
                    game.imageUrl.ifBlank { game.headerImageUrl },
                    Modifier
                        .width(SteamStoreLayoutTokens.SearchImageWidth)
                        .aspectRatio(460f / 215f)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    game.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            SteamStoreHintBadges(hints = hints, compact = true)
            SteamStoreTagBadges(labels = tagLabels)
            if (game.availableInAccountRegion == false) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = stringResource(R.string.steam_store_unavailable_account_region),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                game.priceCountryCode?.let { countryCode ->
                    Text(
                        text = stringResource(
                            R.string.steam_store_reference_region_price,
                            regionalCountryName(countryCode)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
private fun SteamStoreDetailContent(
    detail: SteamStoreDetail,
    hints: List<SteamStoreHintKind>,
    showTags: Boolean,
    filterableTags: Set<String>,
    loading: Boolean,
    cached: Boolean,
    purchaseContext: SteamStorePurchaseContext?,
    purchaseContextFromCache: Boolean,
    loadingPurchaseContext: Boolean,
    purchaseContextFailure: SteamStorePurchaseContextFailure?,
    alreadyOwned: Boolean,
    freeLicenseOption: SteamStorePackageOption?,
    freeLicenseClaiming: Boolean,
    freeLicenseClaimResult: SteamFreebieClaimResult?,
    onBack: () -> Unit,
    onOpenOfficial: () -> Unit,
    onOpenOfficialReviews: () -> Unit,
    onShare: () -> Unit,
    onOpenWebsite: (String) -> Unit,
    reviewFilters: SteamReviewFilterSelection,
    loadingMoreReviews: Boolean,
    reviewLoadError: String?,
    onReviewFiltersChanged: (SteamReviewFilterSelection) -> Unit,
    onLoadMoreReviews: () -> Unit,
    cartItem: SteamCartItem?,
    inWishlist: Boolean,
    wishlistAvailable: Boolean,
    wishlistMutating: Boolean,
    wishlistError: String?,
    ignored: Boolean,
    ignoreAvailable: Boolean,
    ignoreMutating: Boolean,
    ignoreSyncState: SteamStoreIgnoreSyncState?,
    ignoredError: String?,
    regionalPrices: List<SteamRegionalPrice>,
    regionalPricesFromCache: Boolean,
    loadingRegionalPrices: Boolean,
    regionalPriceFailure: SteamLibraryFailureReason?,
    showRegionalPrices: Boolean,
    onAddToCart: (SteamStorePackageOption?) -> Unit,
    onAddAsGift: (SteamStorePackageOption?) -> Unit,
    onClaimFreeLicense: () -> Unit,
    onRemoveFromCart: () -> Unit,
    onOpenCart: () -> Unit,
    onToggleWishlist: () -> Unit,
    onToggleIgnored: () -> Unit,
    onOpenRegionalPrices: () -> Unit,
    onCloseRegionalPrices: () -> Unit,
    onRetryRegionalPrices: () -> Unit,
    onOpenRelatedApp: (Int) -> Unit,
    onOpenBundle: (String) -> Unit,
    onFilterByTag: (String) -> Boolean,
    onOpenItadSettings: () -> Unit,
    modifier: Modifier
) {
    val dockContentClearance = LocalSteamDockContentClearance.current
    val reduceAnimations = LocalReduceAnimations.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val heroBackgroundUrl = detail.backgroundImageUrl.ifBlank { detail.headerImageUrl }
    val heroViewerUrl = detail.headerImageUrl.ifBlank { heroBackgroundUrl }
    val aboutText = detail.about.ifBlank { detail.shortDescription }
    var showHeroViewer by rememberSaveable(detail.appId) { mutableStateOf(false) }
    var selectedScreenshotIndex by rememberSaveable(detail.appId) {
        mutableStateOf<Int?>(null)
    }
    var selectedPackageId by rememberSaveable(detail.appId) {
        mutableStateOf(detail.packageId)
    }
    val packageIds = remember(detail.packageOptions) {
        detail.packageOptions.map(SteamStorePackageOption::packageId)
    }
    LaunchedEffect(detail.appId, packageIds) {
        if (selectedPackageId !in packageIds) {
            selectedPackageId = detail.packageId ?: packageIds.firstOrNull()
        }
    }
    val selectedPackage = detail.packageOptions.firstOrNull {
        it.packageId == selectedPackageId
    }
    val hasReviews = detail.reviews?.let { reviews ->
        reviews.overall != null || reviews.recent != null || reviews.items.isNotEmpty()
    } == true
    val purchaseSectionIndex = 1 + listOf(
        showTags && detail.tags.isNotEmpty(),
        hints.isNotEmpty(),
        cached,
        freeLicenseOption != null
    ).count { it }
    val reviewSectionIndex = purchaseSectionIndex + 3 + listOf(
        detail.fullGame != null || detail.demos.isNotEmpty() || detail.relatedDlc.isNotEmpty(),
        detail.bundles.isNotEmpty(),
        aboutText.isNotBlank(),
        detail.systemRequirements.hasContent,
        detail.screenshots.isNotEmpty()
    ).count { it }
    val scrollToSection: (Int) -> Unit = { index ->
        scope.launch {
            if (reduceAnimations) {
                listState.scrollToItem(index)
            } else {
                listState.animateScrollToItem(index)
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = dockContentClearance + 32.dp),
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
                        .statusBarsPadding()
                        .clickable(enabled = heroViewerUrl.isNotBlank()) {
                            showHeroViewer = true
                        }
                        .aspectRatio(460f / 215f),
                    contentScale = ContentScale.Fit,
                    contentDescription = stringResource(
                        R.string.steam_store_header_image_description
                    )
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
                    SelectionContainer {
                        Text(
                            detail.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                                if (detail.availableInAccountRegion == false) {
                                    Text(
                                        text = stringResource(
                                            R.string.steam_store_unavailable_account_region
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    detail.priceCountryCode?.let { countryCode ->
                                        Text(
                                            text = stringResource(
                                                R.string.steam_store_reference_region_price,
                                                regionalCountryName(countryCode)
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
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
        if (showTags && detail.tags.isNotEmpty()) {
            item(key = "store_tags_${detail.appId}") {
                SteamStoreDetailTags(
                    labels = detail.tags,
                    filterableLabels = filterableTags,
                    onTagClick = onFilterByTag,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        if (hints.isNotEmpty()) {
            item(key = "store_hints_${detail.appId}") {
                SteamStoreHintBadges(
                    hints = hints,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        if (cached) item { CachedNotice() }
        if (freeLicenseOption != null) {
            item(key = "store_free_license_${detail.appId}") {
                SteamStoreFreeLicenseButton(
                    alreadyOwned = alreadyOwned,
                    claiming = freeLicenseClaiming,
                    result = freeLicenseClaimResult,
                    onOpenOfficial = onClaimFreeLicense,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
        }
        item {
            SteamStorePurchaseContextSection(
                detail = detail,
                context = purchaseContext,
                contextFromCache = purchaseContextFromCache,
                loadingContext = loadingPurchaseContext,
                contextFailure = purchaseContextFailure,
                selectedPackageId = selectedPackageId,
                onSelectPackage = { selectedPackageId = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
        item {
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SteamStorePurchaseActions(
                    cartItem = cartItem,
                    inWishlist = inWishlist,
                    purchaseAvailable = detail.availableInAccountRegion != false,
                    alreadyOwned = alreadyOwned,
                    hasPurchasablePackage = selectedPackage?.let { option ->
                        !option.isFreeLicense &&
                            !option.canGetFreeLicense &&
                            (option.priceCents?.let { it > 0 } ?: !detail.isFree)
                    } == true,
                    wishlistAvailable = wishlistAvailable,
                    wishlistMutating = wishlistMutating,
                    wishlistError = wishlistError,
                    ignored = ignored,
                    ignoreAvailable = ignoreAvailable,
                    ignoreMutating = ignoreMutating,
                    ignoreSyncState = ignoreSyncState,
                    ignoredError = ignoredError,
                    onAddForSelf = { onAddToCart(selectedPackage) },
                    onAddAsGift = { onAddAsGift(selectedPackage) },
                    onRemoveFromCart = onRemoveFromCart,
                    onOpenCart = onOpenCart,
                    onToggleWishlist = onToggleWishlist,
                    onToggleIgnored = onToggleIgnored,
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
        if (detail.fullGame != null || detail.demos.isNotEmpty() || detail.relatedDlc.isNotEmpty()) {
            item(key = "store_related_${detail.appId}") {
                SteamStoreRelatedContentSection(
                    fullGame = detail.fullGame,
                    demos = detail.demos,
                    relatedDlc = detail.relatedDlc,
                    onOpenApp = onOpenRelatedApp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        if (detail.bundles.isNotEmpty()) {
            item(key = "store_bundles_${detail.appId}") {
                SteamStoreBundleSection(
                    bundles = detail.bundles,
                    currency = detail.currency,
                    onOpenApp = onOpenRelatedApp,
                    onOpenBundle = onOpenBundle,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
        if (aboutText.isNotBlank()) {
            item {
                DetailTextSection(
                    stringResource(R.string.steam_store_about),
                    aboutText
                )
            }
        }
        if (detail.systemRequirements.hasContent) {
            item(key = "store_system_requirements_${detail.appId}") {
                SteamStoreSystemRequirementsSection(
                    requirements = detail.systemRequirements,
                    modifier = Modifier.padding(horizontal = 16.dp)
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
                        itemsIndexed(detail.screenshots) { index, screenshot ->
                            Card(
                                onClick = { selectedScreenshotIndex = index },
                                modifier = Modifier
                                    .width(280.dp)
                                    .aspectRatio(16f / 9f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    SteamStoreImage(
                                        url = screenshot,
                                        modifier = Modifier.fillMaxSize(),
                                        contentDescription = stringResource(
                                            R.string.steam_store_screenshot_description,
                                            index + 1
                                        )
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                        tonalElevation = 2.dp
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ZoomIn,
                                            contentDescription = null,
                                            modifier = Modifier.padding(8.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
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
                    if (detail.categories.isNotEmpty()) {
                        DetailLine(
                            stringResource(R.string.steam_store_categories),
                            detail.categories.joinToString()
                        )
                    }
                    if (detail.supportedLanguages.isNotBlank()) {
                        DetailLine(
                            stringResource(R.string.steam_store_supported_languages),
                            detail.supportedLanguages
                        )
                    }
                    if (detail.controllerSupport.isNotBlank()) {
                        DetailLine(
                            stringResource(R.string.steam_store_controller_support),
                            detail.controllerSupport
                        )
                    }
                    detail.recommendationCount?.let {
                        DetailLine(stringResource(R.string.steam_store_recommendations), it.toString())
                    }
                    detail.achievementCount?.let {
                        DetailLine(stringResource(R.string.steam_store_achievements), it.toString())
                    }
                    if (detail.website.isNotBlank()) {
                        SteamStoreWebsiteButton(
                            onClick = { onOpenWebsite(detail.website) }
                        )
                    }
                }
            }
        }
        detail.reviews?.let { reviews ->
            if (reviews.overall != null || reviews.recent != null || reviews.items.isNotEmpty()) {
                item(key = "store_reviews_${detail.appId}") {
                    SteamStoreReviewsSection(
                        appId = detail.appId,
                        reviews = reviews,
                        filters = reviewFilters,
                        loadingMore = loadingMoreReviews,
                        loadError = reviewLoadError,
                        onFiltersChanged = onReviewFiltersChanged,
                        onLoadMore = onLoadMoreReviews,
                        onOpenAuthor = { steamId ->
                            onOpenWebsite("https://steamcommunity.com/profiles/$steamId/")
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
        }
        SteamStoreDetailActionToolbar(
            onOpenPurchaseOptions = { scrollToSection(purchaseSectionIndex) },
            onOpenOfficialStore = onOpenOfficial,
            onOpenReviews = {
                if (hasReviews) scrollToSection(reviewSectionIndex)
                else onOpenOfficialReviews()
            },
            onShare = onShare,
            modifier = Modifier
                .fillMaxSize()
                .steamWindowTopPadding()
                .steamWindowBottomPadding()
                .padding(bottom = dockContentClearance)
        )
    }
    if (showHeroViewer && heroViewerUrl.isNotBlank()) {
        SteamStoreScreenshotViewer(
            gameName = detail.name,
            screenshots = listOf(heroViewerUrl),
            initialIndex = 0,
            onDismiss = { showHeroViewer = false }
        )
    }
    selectedScreenshotIndex?.let { initialIndex ->
        SteamStoreScreenshotViewer(
            gameName = detail.name,
            screenshots = detail.screenshots,
            initialIndex = initialIndex,
            onDismiss = { selectedScreenshotIndex = null }
        )
    }
    if (showRegionalPrices) {
        SteamStoreRegionalPriceSheet(
            appId = detail.appId,
            gameName = detail.name,
            historyCountryCode = detail.accountCountryCode ?: detail.priceCountryCode,
            prices = regionalPrices,
            loading = loadingRegionalPrices,
            fromCache = regionalPricesFromCache,
            failure = regionalPriceFailure,
            onRetry = onRetryRegionalPrices,
            onOpenItadSettings = onOpenItadSettings,
            onDismiss = onCloseRegionalPrices
        )
    }
}

@Composable
private fun SteamStorePurchaseActions(
    cartItem: SteamCartItem?,
    inWishlist: Boolean,
    purchaseAvailable: Boolean,
    alreadyOwned: Boolean,
    hasPurchasablePackage: Boolean,
    wishlistAvailable: Boolean,
    wishlistMutating: Boolean,
    wishlistError: String?,
    ignored: Boolean,
    ignoreAvailable: Boolean,
    ignoreMutating: Boolean,
    ignoreSyncState: SteamStoreIgnoreSyncState?,
    ignoredError: String?,
    onAddForSelf: () -> Unit,
    onAddAsGift: () -> Unit,
    onRemoveFromCart: () -> Unit,
    onOpenCart: () -> Unit,
    onToggleWishlist: () -> Unit,
    onToggleIgnored: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!purchaseAvailable) {
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
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Text(
                        text = stringResource(R.string.steam_store_locked_purchase_disabled),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        if (hasPurchasablePackage) {
            SteamStoreGiftPurchaseSplitButton(
                cartItem = cartItem,
                canAdd = purchaseAvailable && !alreadyOwned,
                alreadyOwned = alreadyOwned,
                onAddForSelf = onAddForSelf,
                onAddAsGift = onAddAsGift,
                onOpenCart = onOpenCart,
                onRemove = onRemoveFromCart,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = onToggleWishlist,
                enabled = (purchaseAvailable || inWishlist) && wishlistAvailable && !wishlistMutating,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
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
            SteamStoreIgnoreButton(
                ignored = ignored,
                enabled = ignoreAvailable,
                mutating = ignoreMutating,
                onClick = onToggleIgnored,
                modifier = Modifier.weight(1f)
            )
        }
        if (ignoreSyncState == SteamStoreIgnoreSyncState.PENDING ||
            ignoreSyncState == SteamStoreIgnoreSyncState.LOCAL_ONLY
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (ignoreSyncState == SteamStoreIgnoreSyncState.PENDING) {
                            Icons.Default.Sync
                        } else {
                            Icons.Default.CloudOff
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(
                            if (ignoreSyncState == SteamStoreIgnoreSyncState.PENDING) {
                                R.string.steam_store_ignore_sync_pending
                            } else {
                                R.string.steam_store_ignore_local_only
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        val actionError = ignoredError ?: wishlistError
        if (actionError != null) {
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
                        text = actionError,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SteamStoreDetailTags(
    labels: List<String>,
    filterableLabels: Set<String>,
    onTagClick: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val distinctLabels = remember(labels) {
        labels.map(String::trim).filter(String::isNotBlank).distinct()
    }
    if (distinctLabels.isEmpty()) return
    var tagsExpanded by rememberSaveable(distinctLabels.joinToString("\u0000")) {
        mutableStateOf(false)
    }
    val canExpand = distinctLabels.size > DETAIL_TAGS_COLLAPSED_COUNT

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Label,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.steam_store_filter_tags),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (canExpand) {
                TextButton(onClick = { tagsExpanded = !tagsExpanded }) {
                    Text(
                        stringResource(
                            if (tagsExpanded) {
                                R.string.steam_store_filter_collapse_tags
                            } else {
                                R.string.steam_store_filter_expand_tags
                            }
                        )
                    )
                    Icon(
                        imageVector = if (tagsExpanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = null
                    )
                }
            }
        }
        AnimatedContent(
            targetState = tagsExpanded,
            label = "steam_store_detail_tags_expansion"
        ) { expanded ->
            val visibleLabels = if (expanded || !canExpand) {
                distinctLabels
            } else {
                distinctLabels.take(DETAIL_TAGS_COLLAPSED_COUNT)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                visibleLabels.forEach { label ->
                    FilterChip(
                        selected = false,
                        onClick = { onTagClick(label) },
                        enabled = label in filterableLabels,
                        label = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
    }
}

private const val DETAIL_TAGS_COLLAPSED_COUNT = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteamStoreRegionalPriceSheet(
    appId: Int,
    gameName: String,
    historyCountryCode: String?,
    prices: List<SteamRegionalPrice>,
    loading: Boolean,
    fromCache: Boolean,
    failure: SteamLibraryFailureReason?,
    onRetry: () -> Unit,
    onOpenItadSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val sortedPrices = remember(prices) { sortedRegionalPricesForDisplay(prices) }
    val preferredCountryCode = remember(historyCountryCode) {
        historyCountryCode.orEmpty().trim().uppercase(Locale.ROOT)
    }
    var expandedCountryCode by rememberSaveable(appId) { mutableStateOf<String?>(null) }
    var initialCountryApplied by rememberSaveable(appId) { mutableStateOf(false) }
    LaunchedEffect(appId, sortedPrices, preferredCountryCode) {
        if (!initialCountryApplied && sortedPrices.isNotEmpty()) {
            expandedCountryCode = sortedPrices.firstOrNull {
                it.countryCode.equals(preferredCountryCode, ignoreCase = true)
            }?.countryCode
            initialCountryApplied = true
        }
    }
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
                SteamStoreRegionalPriceCard(
                    appId = appId,
                    price = price,
                    expanded = expandedCountryCode == price.countryCode,
                    onToggleExpanded = {
                        expandedCountryCode = if (expandedCountryCode == price.countryCode) {
                            null
                        } else {
                            price.countryCode
                        }
                    },
                    onOpenItadSettings = onOpenItadSettings
                )
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
private fun SteamStoreRegionalPriceCard(
    appId: Int,
    price: SteamRegionalPrice,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenItadSettings: () -> Unit
) {
    val reduceAnimations = LocalReduceAnimations.current
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
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(16.dp),
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
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = stringResource(
                            if (expanded) R.string.collapse else R.string.expand
                        ),
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
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
            AnimatedVisibility(
                visible = expanded,
                enter = if (reduceAnimations) {
                    EnterTransition.None
                } else {
                    fadeIn() + expandVertically()
                },
                exit = if (reduceAnimations) {
                    ExitTransition.None
                } else {
                    fadeOut() + shrinkVertically()
                }
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                    ItadHistoryLowSection(
                        appId = appId,
                        countryCode = price.countryCode,
                        expectedCurrency = price.currency,
                        currentSteamPriceMinor = price.finalPriceMinor.takeIf {
                            price.isAvailable
                        },
                        onOpenSettings = onOpenItadSettings,
                        modifier = Modifier.padding(14.dp)
                    )
                }
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

@Composable
private fun regionalCountryName(countryCode: String): String {
    return if (isSteamSouthAsiaPriceCountry(countryCode)) {
        stringResource(R.string.steam_region_south_asia)
    } else {
        Locale("", countryCode).getDisplayCountry(Locale.getDefault())
            .ifBlank { countryCode }
    }
}

private fun formatStoreRegionalPrice(currency: String, minor: Long): String {
    val cents = minor.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    return formatSteamPrice(cents, currency)
}

@Composable
private fun DetailTextSection(title: String, text: String) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    val collapsible = remember(text) {
        text.length > 280 || text.lineSequence().count() > 6
    }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (collapsible) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    stringResource(
                        if (expanded) {
                            R.string.steam_store_about_collapse
                        } else {
                            R.string.steam_store_about_expand
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String
) {
    if (value.isNotBlank()) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(92.dp)
            )
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(value)
            }
        }
    }
}

@Composable
private fun SteamStoreWebsiteButton(onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(Icons.Default.Language, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.steam_store_website),
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
    }
}

private fun openExternalStoreWebsite(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (runCatching { context.startActivity(intent) }.isFailure) {
        showStoreWebsiteOpenFailure(context)
    }
}

private fun showStoreWebsiteOpenFailure(context: Context) {
    android.widget.Toast.makeText(
        context,
        R.string.steam_store_website_open_failed,
        android.widget.Toast.LENGTH_LONG
    ).show()
}

private fun shareSteamStoreGame(context: Context, share: SteamStoreGameShare) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, share.name)
        putExtra(Intent.EXTRA_TEXT, share.messageBody)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(
                sendIntent,
                context.getString(R.string.steam_store_share_chooser)
            ).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

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
private fun StoreMessage(
    message: String,
    onRetry: (() -> Unit)? = null,
    onUnlockFamilyView: (() -> Unit)? = null,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message)
            if (onUnlockFamilyView != null) {
                FilledTonalButton(onClick = onUnlockFamilyView) {
                    Icon(Icons.Default.LockOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.steam_store_family_view_unlock))
                }
            }
            if (onRetry != null) FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.steam_store_retry)) }
        }
    }
}

@Composable
internal fun SteamStoreImage(
    url: String,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f,
    contentDescription: String? = null
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
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            alpha = alpha
        )
        else Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
