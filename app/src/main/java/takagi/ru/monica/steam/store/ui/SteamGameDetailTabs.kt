package takagi.ru.monica.steam.store.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import takagi.ru.monica.steam.navigation.liquidglass.render.SteamLiquidGlassBackdrop
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamGlassBackdrop
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamPopupBackdrop
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.library.gamedata.domain.steamGameDataPage
import takagi.ru.monica.steam.library.gamedata.ui.SteamGameDataWebScreen
import takagi.ru.monica.steam.library.screenshots.domain.steamGameScreenshotsPage
import takagi.ru.monica.steam.library.screenshots.ui.SteamGameScreenshotsScreen
import takagi.ru.monica.steam.workshop.SteamWorkshopScreen
import takagi.ru.monica.steam.workshop.SteamWorkshopService
import kotlinx.coroutines.CancellationException
import takagi.ru.monica.ui.LocalReduceAnimations

/** Both entry points keep one game identity and independent saved content states. */
@Composable
internal fun SteamGameDetailTabs(
    appId: Int, name: String, initialPersonalStats: Boolean,
    account: SteamAccount?, source: SteamStorageSource, onBack: () -> Unit,
    onChooseAccount: () -> Unit, onOpenWeb: (String) -> Unit,
    onShare: (() -> Unit)?,
    onPlatformViewVisibilityChanged: (Boolean) -> Unit = {},
    statsGateway: SteamGameStatsGateway? = null,
    heroImageUrl: String = "",
    workshopSupport: (suspend (Int) -> Boolean)? = null,
    workshopContent: (@Composable (SteamGameSectionHeader) -> Unit)? = null,
    pointsShopContent: (@Composable (Int, takagi.ru.monica.steam.store.points.domain.SteamPointsShopItem?, () -> Unit) -> Unit)? = null,
    bottomBar: (@Composable (() -> Unit) -> Unit)? = null,
    storeContent: @Composable (LazyListState, @Composable ((() -> Unit)?, (() -> Unit)?) -> Unit) -> Unit
) {
    var section by rememberSaveable(appId) { mutableStateOf(
        if (initialPersonalStats) SteamGameDetailSection.STATS else SteamGameDetailSection.STORE) }
    val personal = section == SteamGameDetailSection.STATS
    val workshopService = remember { SteamWorkshopService() }
    var workshopAvailable by remember(appId) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(appId, section) {
        if (workshopAvailable == null) {
            try { workshopAvailable = workshopSupport?.invoke(appId) ?: workshopService.supportsWorkshop(appId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Unknown support retries on the next section visit. */ }
        }
    }
    val storeScroll = rememberLazyListState()
    val statsScroll = rememberLazyListState()
    val saved = rememberSaveableStateHolder()
    val context = LocalContext.current
    val gateway = statsGateway ?: remember(context) { AndroidSteamGameStatsGateway(context) }
    var stats by remember(account?.id, account?.steamId, source, appId) { mutableStateOf(SteamGamePersonalStats()) }
    var extras by remember(account?.id, account?.steamId, source, appId) { mutableStateOf(SteamGameStatsExtras()) }
    var extrasLoaded by remember(account?.id, account?.steamId, source, appId) { mutableStateOf(false) }
    var loaded by remember(account?.id, account?.steamId, source, appId) { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var webPage by rememberSaveable(appId) { mutableStateOf<String?>(null) }
    var pointsOpen by remember(account?.id, account?.steamId, appId) { mutableStateOf(false) }
    var pointsReward by remember(account?.id, account?.steamId, appId) { mutableStateOf<takagi.ru.monica.steam.store.points.domain.SteamPointsShopItem?>(null) }
    val dataPage = steamGameDataPage(account?.steamId, appId)
    val screenshots = steamGameScreenshotsPage(account?.steamId, appId)
    LaunchedEffect(account?.id, account?.steamId, source, appId, personal, refresh) {
        if (account != null && personal && !loaded) {
            stats = stats.copy(loading = true)
            loadSteamGamePersonalStats(gateway, account, appId, name) { stats = it }
            loaded = true
        }
    }
    LaunchedEffect(account?.id, account?.steamId, source, appId, personal, refresh) {
        if (account != null && personal && !extrasLoaded) {
            loadSteamGameStatsExtras(gateway, account, appId) { extras = it }
            extrasLoaded = true
        }
    }
    if (webPage == "data" && account != null && dataPage != null) {
        SteamGameDataWebScreen(page = dataPage, account = account, onClose = { webPage = null },
            onPlatformViewVisibilityChanged = onPlatformViewVisibilityChanged,
            modifier = Modifier.fillMaxSize())
        return
    }
    if (webPage == "screenshots" && account != null && screenshots != null) {
        BackHandler { webPage = null }
        SteamGameScreenshotsScreen(page = screenshots, account = account, gameName = name,
            onBack = { webPage = null }, modifier = Modifier.fillMaxSize())
        return
    }
    if (pointsOpen) {
        BackHandler { pointsOpen = false; pointsReward = null }
        val close = { pointsOpen = false; pointsReward = null }
        if (pointsShopContent != null) pointsShopContent(appId, pointsReward, close)
        else takagi.ru.monica.steam.store.points.ui.SteamPointsShopScreen(account,
            onBack = close, onOpenOfficial = onOpenWeb,
            appId = appId, initialItem = pointsReward, modifier = Modifier.fillMaxSize())
        return
    }
    SteamGamePurchaseHost(visible = section == SteamGameDetailSection.STORE,
        bottomBar = bottomBar?.let { bar -> { bar { section = SteamGameDetailSection.STORE } } }) {
    SteamGameDetailTabLayout(appId, section, { section = it }, workshopAvailable == true,
        storeScroll, onBack, onShare, onOpenWeb, storeContent,
        workshopContent = { header ->
            if (workshopContent != null) workshopContent(header)
            else SteamWorkshopScreen(appId, name, account, source, onBack,
                modifier = Modifier.fillMaxSize(), gameHeader = header)
        }) { topPadding ->
        saved.SaveableStateProvider("stats") {
            SteamGamePersonalStatsContent(
                name = name, accountName = account?.let { it.displayName.ifBlank { it.accountName } },
                state = stats, listState = statsScroll,
                onChooseAccount = onChooseAccount,
                onRetry = { loaded = false; extrasLoaded = false; refresh++ },
                onOpenGameData = dataPage?.let { { webPage = "data" } },
                onOpenScreenshots = screenshots?.let { { webPage = "screenshots" } },
                topPadding = topPadding, extras = extras, heroImageUrl = heroImageUrl,
                appId = appId, steamId = account?.steamId.orEmpty(), onOpenWeb = onOpenWeb,
                onOpenPointsShop = { pointsReward = null; pointsOpen = true },
                onOpenReward = { pointsReward = it; pointsOpen = true }
            )
        }
    }
    }
}

@Composable
internal fun SteamGameDetailTabLayout(
    appId: Int, section: SteamGameDetailSection, onSectionChanged: (SteamGameDetailSection) -> Unit,
    workshopAvailable: Boolean, storeScroll: LazyListState,
    onBack: () -> Unit, onShare: (() -> Unit)?, onOpenWeb: (String) -> Unit,
    storeContent: @Composable (LazyListState, @Composable ((() -> Unit)?, (() -> Unit)?) -> Unit) -> Unit,
    workshopContent: @Composable (SteamGameSectionHeader) -> Unit,
    statsContent: @Composable (androidx.compose.ui.unit.Dp) -> Unit
) {
    val reduced = LocalReduceAnimations.current
    val saved = rememberSaveableStateHolder()
    val headers = remember { mutableStateMapOf<SteamGameDetailSection, SteamGameHeaderPlacement>() }
    val density = LocalDensity.current
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var headerHeight by remember(density, inset) { mutableStateOf(inset + 64.dp) }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(targetState = section, modifier = Modifier.fillMaxSize(),
            transitionSpec = { fadeIn(tween(if (reduced) 0 else 160)) togetherWith fadeOut(tween(if (reduced) 0 else 90)) },
            label = "Game detail section") { visibleSection ->
            val header: SteamGameSectionHeader = { back ->
                SteamGameDetailHeaderPlaceholder(visibleSection, headerHeight, back, headers)
            }
            saved.SaveableStateProvider(visibleSection.name) {
                when (visibleSection) {
                    SteamGameDetailSection.STORE -> storeContent(storeScroll) { _, _ -> header(onBack) }
                    SteamGameDetailSection.STATS -> SteamGameDetailSectionHost(header = { header(onBack) },
                        toolbar = {
                            SteamStoreDetailActionToolbar(null,
                                { onOpenWeb("https://store.steampowered.com/app/$appId/") }, null, onShare)
                        }, content = statsContent)
                    SteamGameDetailSection.WORKSHOP -> workshopContent(header)
                }
            }
        }
        // The navigation stays outside the page crossfade; each page still owns its
        // backdrop, content padding and (for Workshop) nested back action.
        val placement = headers[section]
        CompositionLocalProvider(LocalSteamGlassBackdrop provides placement?.backdrop,
            LocalSteamPopupBackdrop provides placement?.backdrop) {
            Box(Modifier.fillMaxWidth()
                .then(if (placement == null) Modifier.clearAndSetSemantics {} else Modifier)
                .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    // Nested Workshop pages own their chrome. Unplaced navigation is not
                    // drawn or hit-tested, but retains its motion/scroll state on return.
                    if (placement != null) placeable.placeRelative(0, 0)
                }
            }.onSizeChanged { headerHeight = with(density) { it.height.toDp() } }) {
                SteamGameDetailNavigation(section, workshopAvailable, onSectionChanged,
                    placement?.back?.value ?: onBack)
            }
        }
    }
}

private class SteamGameHeaderPlacement(
    val back: State<() -> Unit>,
    val backdrop: SteamLiquidGlassBackdrop?
)

@Composable
private fun SteamGameDetailHeaderPlaceholder(
    section: SteamGameDetailSection,
    height: Dp,
    onBack: () -> Unit,
    headers: MutableMap<SteamGameDetailSection, SteamGameHeaderPlacement>
) {
    val back = rememberUpdatedState(onBack)
    val backdrop = LocalSteamGlassBackdrop.current
    val placement = remember(backdrop) { SteamGameHeaderPlacement(back, backdrop) }
    DisposableEffect(section, placement) {
        headers[section] = placement
        onDispose { if (headers[section] === placement) headers.remove(section) }
    }
    Spacer(Modifier.fillMaxWidth().height(height))
}
