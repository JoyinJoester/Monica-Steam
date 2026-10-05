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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import takagi.ru.monica.steam.workshop.SteamWorkshopEntry
import takagi.ru.monica.steam.workshop.SteamWorkshopScreen
import takagi.ru.monica.steam.workshop.WorkshopImportCodeDialog
import takagi.ru.monica.steam.workshop.WorkshopShareCode
import takagi.ru.monica.R
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.steam.foundation.ui.SteamAccountSwitcherSheet
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamRegionalPrice
import takagi.ru.monica.steam.library.isSteamSouthAsiaPriceCountry
import takagi.ru.monica.steam.steamdb.domain.SteamDbQuery
import takagi.ru.monica.steam.steamdb.ui.SteamDbDetailsEntry
import takagi.ru.monica.steam.steamdb.ui.SteamDbPriceSection
import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.store.interest.ui.SteamStoreIgnoreButton
import takagi.ru.monica.steam.store.interest.domain.SteamStoreIgnoreSyncState
import takagi.ru.monica.steam.store.freebie.ui.SteamFreebieScreen
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimResult
import takagi.ru.monica.steam.store.filters.domain.findTagId
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
import takagi.ru.monica.steam.navigation.ui.steamWindowBottomPadding
import takagi.ru.monica.steam.navigation.ui.steamWindowTopPadding
import takagi.ru.monica.steam.profile.SteamRemoteImageCache
import takagi.ru.monica.steam.web.ui.SteamWebBrowserScreen
import takagi.ru.monica.steam.web.domain.SteamWebNavigationPolicy
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit
import java.util.Locale
import kotlinx.coroutines.launch

private sealed interface SteamStoreDestination {
    data object Home : SteamStoreDestination
    data object Cart : SteamStoreDestination
    data object PointsShop : SteamStoreDestination
    data object Freebies : SteamStoreDestination
    data class Workshop(val appId: Int) : SteamStoreDestination
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
    initialWorkshopShare: String? = null,
    onInitialWorkshopShareConsumed: () -> Unit = {},
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
    val homeListState = rememberLazyListState()
    LaunchedEffect(state.query, state.browseFilter, state.storeFilters) {
        homeListState.scrollToItem(0)
    }
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }
    var workshopAppId by rememberSaveable { mutableStateOf<Int?>(null) }
    var workshopShareCode by rememberSaveable { mutableStateOf<String?>(null) }
    var showWorkshopImport by rememberSaveable { mutableStateOf(false) }
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
    LaunchedEffect(initialWorkshopShare) {
        initialWorkshopShare?.let { code ->
            runCatching { WorkshopShareCode.decode(code) }.getOrNull()?.let { share ->
                workshopShareCode = WorkshopShareCode.encode(share)
                workshopAppId = share.appId
            }
            onInitialWorkshopShareConsumed()
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
        workshopAppId != null -> SteamStoreDestination.Workshop(requireNotNull(workshopAppId))
        webUrl != null -> SteamStoreDestination.Web(webUrl)
        detailAppId != null -> SteamStoreDestination.Detail(detailAppId)
        state.cartOpen -> SteamStoreDestination.Cart
        state.pointsShopOpen -> SteamStoreDestination.PointsShop
        freebiesOpen -> SteamStoreDestination.Freebies
        else -> SteamStoreDestination.Home
    }

    BackHandler(
        enabled = state.webUrl == null && (
            workshopAppId != null || state.regionalPriceSheetOpen || state.cartOpen || state.detailAppId != null ||
                state.pointsShopOpen || freebiesOpen
            )
    ) {
        when {
            workshopAppId != null -> workshopAppId = null
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
            is SteamStoreDestination.Workshop -> SteamWorkshopScreen(
                appId = destination.appId,
                gameName = state.detail?.takeIf { it.appId == destination.appId }?.name.orEmpty(),
                account = selectedStoreAccount,
                source = state.storageSource,
                onBack = { workshopAppId = null; workshopShareCode = null },
                initialShareCode = workshopShareCode,
                onInitialShareConsumed = { workshopShareCode = null }
            )
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
                        onOpenWorkshop = { workshopAppId = detail.appId },
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
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            SteamStoreDestination.Home -> SteamStoreHomePage(
                state = state,
                listState = homeListState,
                accountName = selectedStoreAccount?.displayName?.ifBlank { selectedStoreAccount.accountName },
                itemHints = itemHints,
                showTags = hintSettings.storeTagsEnabled,
                actions = SteamStoreHomeActions(
                    query = viewModel::updateQuery,
                    search = viewModel::search,
                    filter = { filter -> viewModel.updateQuery(""); viewModel.selectBrowseFilter(filter) },
                    clearFilters = viewModel::clearStoreFilters,
                    advancedFilters = { showAdvancedFilters = true; viewModel.loadStoreFilterMetadata() },
                    refresh = refreshStore,
                    loadMore = { viewModel.loadCatalog(loadMore = true) },
                    game = viewModel::openDetail,
                    event = viewModel::openStoreWeb,
                    account = { showAccounts = true },
                    cart = viewModel::openCart,
                    wishlist = { viewModel.openCart(); viewModel.selectCollectionTab(SteamStoreCollectionTab.WISHLIST) },
                    freebies = { freebiesOpen = true },
                    points = viewModel::openPointsShop,
                    activateProduct = { viewModel.openAuthenticatedStoreWeb(SteamStoreProductActivation.REGISTER_KEY_URL) },
                    workshopImport = { showWorkshopImport = true },
                    notifications = onOpenNotifications,
                    settings = onOpenSettings,
                    unlockFamilyView = if (state.familyViewUnlockRequired) viewModel::openFamilyViewUnlock else null,
                    back = if (showNavigationBack) onNavigateBack else null
                )
            )
        }
    }

    if (showWorkshopImport) WorkshopImportCodeDialog({ showWorkshopImport = false }) { share ->
        showWorkshopImport = false
        workshopShareCode = WorkshopShareCode.encode(share)
        workshopAppId = share.appId
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
internal fun StoreHeroSkeleton() {
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
    onOpen: (Int) -> Unit,
    onSeeAll: (() -> Unit)? = null
) {
    if (games.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            onSeeAll?.let { action ->
                TextButton(onClick = action) { Text(stringResource(R.string.store_home_see_all)) }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

internal fun androidx.compose.foundation.lazy.LazyListScope.storeAdaptiveItems(
    games: List<SteamStoreItem>,
    columns: Int,
    content: @Composable (SteamStoreItem) -> Unit
) {
    if (columns <= 1) {
        itemsIndexed(games, key = ::steamStoreLazyKey) { _, game -> content(game) }
        return
    }

    items(
        items = games.chunked(columns),
        key = { row -> row.joinToString("_") { game -> game.appId.toString() } }
    ) { row ->
        Row(modifier = Modifier.fillMaxWidth()) {
            row.forEach { game ->
                Box(modifier = Modifier.weight(1f)) {
                    content(game)
                }
            }
            repeat(columns - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

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
            .heightIn(min = SteamStoreLayoutTokens.GameCardHeight),
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
                .heightIn(min = SteamStoreLayoutTokens.GameBodyHeight)
                .padding(SteamStoreLayoutTokens.GameCardPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(game.name, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            StoreCompactPrice(game)
        }
    }
}

@Composable
internal fun SearchResultCard(
    game: SteamStoreItem,
    hints: List<SteamStoreHintKind>,
    tagLabels: List<String>,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
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
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(game.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    StoreCompactPrice(game)
                }
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

        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreCompactPrice(game: SteamStoreItem) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(game.formattedFinalPrice, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        if (game.discountPercent > 0) Text("−${game.discountPercent}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamStoreDetailContent(
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
    onOpenWorkshop: () -> Unit,
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
        hints.isNotEmpty(),
        cached,
        freeLicenseOption != null
    ).count { it }
    val reviewSectionIndex = purchaseSectionIndex + 4 + listOf(
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
    Column(modifier = modifier.fillMaxSize()) {
        SteamStoreDetailTopBar(detail.name, onBack, onShare,
            onPurchase = { scrollToSection(purchaseSectionIndex) },
            onReviews = { if (hasReviews) scrollToSection(reviewSectionIndex) else onOpenOfficialReviews() },
            onOfficial = onOpenOfficial)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(bottom = dockContentClearance + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            SteamStoreDetailHeader(detail, loading, onOpenRegionalPrices,
                onImage = { showHeroViewer = heroViewerUrl.isNotBlank() },
                modifier = Modifier.padding(horizontal = 12.dp),
                tags = {
                    if (showTags && detail.tags.isNotEmpty()) SteamStoreDetailTags(
                        labels = detail.tags, filterableLabels = filterableTags, onTagClick = onFilterByTag)
                })
        }
        if (hints.isNotEmpty()) {
            item(key = "store_hints_${detail.appId}") {
                SteamStoreHintBadges(
                    hints = hints,
                    modifier = Modifier.padding(horizontal = 12.dp)
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
        }
        item {
            Column(
                Modifier.padding(horizontal = 12.dp),
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
                Text(stringResource(R.string.steam_store_security_note),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            }
        }
        if (detail.fullGame != null || detail.demos.isNotEmpty() || detail.relatedDlc.isNotEmpty()) {
            item(key = "store_related_${detail.appId}") {
                SteamStoreRelatedContentSection(
                    fullGame = detail.fullGame,
                    demos = detail.demos,
                    relatedDlc = detail.relatedDlc,
                    onOpenApp = onOpenRelatedApp,
                    modifier = Modifier.padding(horizontal = 12.dp)
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
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
        if (detail.screenshots.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.steam_store_screenshots),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(detail.screenshots) { index, screenshot ->
                            Card(
                                onClick = { selectedScreenshotIndex = index },
                                modifier = Modifier
                                    .width(240.dp)
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
        item(key = "store_steamdb_${detail.appId}") {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SteamWorkshopEntry(detail.appId, onOpenWorkshop)
            SteamDbDetailsEntry(
                query = SteamDbQuery(detail.appId, detail.priceCountryCode,
                    detail.currency.takeIf { detail.finalPriceCents != null }, detail.isFree),
                gameName = detail.name,
                modifier = Modifier.fillMaxWidth()
            )
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
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
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
        }
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
            TextButton(
                onClick = onToggleWishlist,
                enabled = (purchaseAvailable || inWishlist) && wishlistAvailable && !wishlistMutating,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
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
                modifier = Modifier.weight(1f),
                compact = true
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

@Composable
private fun SteamStoreDetailTags(labels: List<String>, filterableLabels: Set<String>,
    onTagClick: (String) -> Boolean, modifier: Modifier = Modifier) {
    val distinctLabels = remember(labels) { labels.map(String::trim).filter(String::isNotBlank).distinct() }
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(distinctLabels, key = { it }) { label ->
            SuggestionChip(onClick = { onTagClick(label) }, enabled = label in filterableLabels,
                label = { Text(label, maxLines = 1) })
        }
    }
}

@Composable
internal fun storeRegionalPriceFailureLabel(failure: SteamLibraryFailureReason): String {
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
internal fun regionalCountryName(countryCode: String): String {
    when (normalizedStoreCountry(countryCode)) {
        "HK" -> return stringResource(R.string.store_region_hong_kong)
        "TW" -> return stringResource(R.string.store_region_taiwan)
    }
    return if (isSteamSouthAsiaPriceCountry(countryCode)) {
        stringResource(R.string.steam_region_south_asia)
    } else {
        Locale("", countryCode).getDisplayCountry(Locale.getDefault())
            .ifBlank { countryCode }
    }
}

internal fun formatStoreRegionalPrice(currency: String, minor: Long): String {
    val cents = minor.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    val formatted = formatSteamPrice(cents, currency)
    return if (currency.equals("CNY", ignoreCase = true) && '.' in formatted) {
        formatted.trimEnd('0').trimEnd('.')
    } else formatted
}

@Composable
private fun DetailTextSection(title: String, text: String) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    val collapsible = remember(text) {
        text.length > 200 || text.lineSequence().count() > 4
    }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
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

@Composable internal fun CachedNotice() { Text(stringResource(R.string.steam_store_cached), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp)) }

@Composable
internal fun StoreMessage(
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
