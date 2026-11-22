package takagi.ru.monica.steam.library.screenshots.ui

import takagi.ru.monica.steam.foundation.ui.SteamRefreshActionIcon
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassScaffold as Scaffold
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.foundation.media.SteamFullscreenImageViewer
import takagi.ru.monica.steam.foundation.media.steamImageThumbnail
import takagi.ru.monica.steam.foundation.media.SteamImageViewerStrings
import takagi.ru.monica.steam.foundation.ui.SteamExpressivePullToRefresh
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteImage
import takagi.ru.monica.steam.library.screenshots.domain.SteamGameScreenshot
import takagi.ru.monica.steam.library.screenshots.domain.SteamGameScreenshotsPage
import takagi.ru.monica.steam.library.screenshots.presentation.SteamGameScreenshotsViewModel
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.ui.components.ExpressiveTopBar

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SteamGameScreenshotsScreen(
    page: SteamGameScreenshotsPage,
    account: SteamAccount,
    gameName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModelKey = remember(page.steamId, page.appId) {
        "steam_game_screenshots_${page.steamId}_${page.appId}"
    }
    val screenshotsViewModel: SteamGameScreenshotsViewModel = viewModel(
        key = viewModelKey,
        factory = remember { SteamGameScreenshotsViewModel.factory() }
    )
    val state by screenshotsViewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val dockClearance = LocalSteamDockContentClearance.current
    var selectedScreenshotId by rememberSaveable(page.steamId, page.appId) {
        mutableStateOf<String?>(null)
    }
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            state.hasMore && !state.loadingMore && !state.loadMoreFailed &&
                state.screenshots.isNotEmpty() &&
                lastVisibleIndex >= state.screenshots.lastIndex - LOAD_MORE_THRESHOLD
        }
    }

    LaunchedEffect(account, page) {
        screenshotsViewModel.attach(account, page)
    }
    LaunchedEffect(shouldLoadMore, state.screenshots.size) {
        if (shouldLoadMore) screenshotsViewModel.loadMore()
    }
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.steam_library_screenshots_title),
                searchQuery = "",
                onSearchQueryChange = {},
                isSearchExpanded = false,
                onSearchExpandedChange = {},
                modifier = Modifier.statusBarsPadding(),
                collapsedTitleEndPadding = 72.dp,
                compact = true,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = screenshotsViewModel::refresh,
                        enabled = !state.loading && !state.refreshing && !state.loadingMore
                    ) {
                        SteamRefreshActionIcon(state.loading || state.refreshing, Icons.Default.Refresh, stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { padding ->
        SteamExpressivePullToRefresh(
            showIndicator = false,
            refreshing = state.refreshing,
            onRefresh = screenshotsViewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.loading && state.screenshots.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator(modifier = Modifier.size(56.dp))
                    }
                }
                state.loadFailed && state.screenshots.isEmpty() -> {
                    SteamGameScreenshotsStateMessage(
                        icon = Icons.Default.BrokenImage,
                        message = stringResource(R.string.steam_library_screenshots_error),
                        onRetry = screenshotsViewModel::refresh,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 8.dp,
                            end = 12.dp,
                            bottom = dockClearance + 20.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.loadFailed) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SteamGameScreenshotsInlineError(
                                    message = stringResource(
                                        R.string.steam_library_screenshots_error
                                    ),
                                    onRetry = screenshotsViewModel::refresh
                                )
                            }
                        }
                        if (state.screenshots.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SteamGameScreenshotsStateMessage(
                                    icon = Icons.Default.PhotoLibrary,
                                    message = stringResource(
                                        R.string.steam_library_screenshots_empty
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 320.dp)
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = state.screenshots,
                                key = { _, screenshot -> screenshot.publishedFileId }
                            ) { index, screenshot ->
                                SteamGameScreenshotCard(
                                    screenshot = screenshot,
                                    index = index,
                                    onClick = {
                                        selectedScreenshotId = screenshot.publishedFileId
                                    }
                                )
                            }
                        }
                        if (state.loadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LoadingIndicator(modifier = Modifier.size(40.dp))
                                }
                            }
                        } else if (state.loadMoreFailed) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SteamGameScreenshotsInlineError(
                                    message = stringResource(
                                        R.string.steam_library_screenshots_load_more_error
                                    ),
                                    onRetry = screenshotsViewModel::loadMore
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val selectedIndex = state.screenshots.indexOfFirst { screenshot ->
        screenshot.publishedFileId == selectedScreenshotId
    }
    if (selectedIndex >= 0) {
        val screenshots = state.screenshots
        SteamFullscreenImageViewer(
            title = gameName.ifBlank {
                stringResource(R.string.steam_library_screenshots_title)
            },
            images = screenshots.map(SteamGameScreenshot::imageUrl),
            initialIndex = selectedIndex,
            fileStemForIndex = { index ->
                val screenshot = screenshots[index.coerceIn(screenshots.indices)]
                "${gameName.ifBlank { "steam_${page.appId}" }}_${screenshot.publishedFileId}"
            },
            strings = steamGameScreenshotViewerStrings(),
            onDismiss = { selectedScreenshotId = null }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SteamGameScreenshotCard(
    screenshot: SteamGameScreenshot,
    index: Int,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val thumbnailState by produceState<SteamGameScreenshotThumbnailState>(
        initialValue = SteamGameScreenshotThumbnailState.Loading,
        key1 = screenshot.thumbnailUrl
    ) {
        value = loadSteamRemoteImage(context.applicationContext, screenshot.thumbnailUrl)
            ?.let(SteamGameScreenshotThumbnailState::Loaded)
            ?: SteamGameScreenshotThumbnailState.Failed
    }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(SCREENSHOT_CARD_ASPECT_RATIO),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            when (val thumbnail = thumbnailState) {
                SteamGameScreenshotThumbnailState.Loading -> LoadingIndicator(
                    modifier = Modifier.size(34.dp)
                )
                SteamGameScreenshotThumbnailState.Failed -> Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is SteamGameScreenshotThumbnailState.Loaded -> Image(
                    bitmap = thumbnail.image,
                    contentDescription = stringResource(
                        R.string.steam_store_screenshot_description,
                        index + 1
                    ),
                    modifier = Modifier.fillMaxSize().steamImageThumbnail(screenshot.imageUrl, thumbnail.image, ContentScale.Crop),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun SteamGameScreenshotsStateMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 14.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onRetry != null) {
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.steam_library_retry))
            }
        }
    }
}

@Composable
private fun SteamGameScreenshotsInlineError(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(onClick = onRetry) {
                Text(stringResource(R.string.steam_library_retry))
            }
        }
    }
}

private fun steamGameScreenshotViewerStrings() = SteamImageViewerStrings(
    imageDescription = R.string.steam_store_screenshot_description,
    close = R.string.steam_store_screenshot_close,
    previous = R.string.steam_store_screenshot_previous,
    next = R.string.steam_store_screenshot_next,
    position = R.string.steam_store_screenshot_position,
    download = R.string.steam_store_screenshot_download,
    downloading = R.string.steam_store_screenshot_downloading,
    downloadSuccess = R.string.steam_store_screenshot_download_success,
    downloadFailed = R.string.steam_store_screenshot_download_failed,
    downloadUnsupported = R.string.steam_store_screenshot_download_unsupported,
    downloadTooLarge = R.string.steam_store_screenshot_download_too_large,
    permissionDenied = R.string.steam_store_screenshot_permission_denied,
    invalidSource = R.string.steam_store_screenshot_invalid_source,
    loadFailed = R.string.steam_store_screenshot_load_failed
)

private sealed interface SteamGameScreenshotThumbnailState {
    data object Loading : SteamGameScreenshotThumbnailState
    data object Failed : SteamGameScreenshotThumbnailState
    data class Loaded(val image: ImageBitmap) : SteamGameScreenshotThumbnailState
}

private const val LOAD_MORE_THRESHOLD = 4
private const val SCREENSHOT_CARD_ASPECT_RATIO = 16f / 10f
