package takagi.ru.monica.steam.foundation.media

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.DialogWindowProvider
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassHost
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

internal data class SteamImageViewerStrings(
    @StringRes val imageDescription: Int,
    @StringRes val close: Int,
    @StringRes val previous: Int,
    @StringRes val next: Int,
    @StringRes val position: Int,
    @StringRes val download: Int,
    @StringRes val downloading: Int,
    @StringRes val downloadSuccess: Int,
    @StringRes val downloadFailed: Int,
    @StringRes val downloadUnsupported: Int,
    @StringRes val downloadTooLarge: Int,
    @StringRes val permissionDenied: Int,
    @StringRes val invalidSource: Int,
    @StringRes val loadFailed: Int
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SteamFullscreenImageViewer(
    title: String,
    images: List<String>,
    initialIndex: Int,
    fileStemForIndex: (Int) -> String,
    strings: SteamImageViewerStrings,
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloader = remember(context) {
        SteamImageDownloader(context.applicationContext)
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(images.indices)
    ) { images.size }
    var downloading by remember { mutableStateOf(false) }
    var pendingPermissionIndex by remember { mutableStateOf<Int?>(null) }
    val root = LocalView.current.rootView
    val origin = remember(images, initialIndex) { touchedSteamImageOrigin(root, images[initialIndex.coerceIn(images.indices)]) }
    DisposableEffect(origin) {
        origin?.anchor?.hidden = true
        onDispose { origin?.anchor?.hidden = false }
    }
    val reduced = LocalReduceAnimations.current
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf<SteamImageOrigin?>(null) }
    val zoomedPages = remember { mutableStateMapOf<Int, Boolean>() }
    var screenOffset by remember { mutableStateOf(Offset.Zero) }
    val dismiss = {
        if (!closing) {
            returning = origin?.anchor?.origin()?.takeIf {
                pagerState.currentPage == initialIndex && zoomedPages[pagerState.currentPage] != true
            }
            closing = true
        }
    }
    LaunchedEffect(closing, reduced) {
        if (closing) {
            if (!reduced) progress.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            onDismiss()
        } else if (reduced) progress.snapTo(1f)
        else progress.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
    }

    val startDownload: (Int) -> Unit = { requestedIndex ->
        if (!downloading) {
            val safeIndex = requestedIndex.coerceIn(images.indices)
            downloading = true
            scope.launch {
                val result = downloader.download(
                    imageUrl = images[safeIndex],
                    fileStem = fileStemForIndex(safeIndex)
                )
                val message = when (result) {
                    is SteamImageDownloadResult.Success -> context.getString(
                        strings.downloadSuccess,
                        result.displayName
                    )
                    SteamImageDownloadResult.PermissionRequired -> context.getString(
                        strings.permissionDenied
                    )
                    SteamImageDownloadResult.InvalidSource -> context.getString(
                        strings.invalidSource
                    )
                    SteamImageDownloadResult.UnsupportedImage -> context.getString(
                        strings.downloadUnsupported
                    )
                    SteamImageDownloadResult.TooLarge -> context.getString(
                        strings.downloadTooLarge
                    )
                    SteamImageDownloadResult.NetworkFailure,
                    SteamImageDownloadResult.StorageFailure -> context.getString(
                        strings.downloadFailed
                    )
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                downloading = false
            }
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val index = pendingPermissionIndex
        pendingPermissionIndex = null
        if (granted && index != null) {
            startDownload(index)
        } else {
            Toast.makeText(context, strings.permissionDenied, Toast.LENGTH_LONG).show()
        }
    }

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialog = LocalView.current.parent as? DialogWindowProvider
        SideEffect { dialog?.window?.setDimAmount(0f); dialog?.window?.setWindowAnimations(0) }
        BackHandler(onBack = dismiss)
        val flight = if (closing) returning else origin
        Box(Modifier.fillMaxSize().testTag("steam-image-viewer")
            .onGloballyPositioned { screenOffset = it.positionOnScreen() }
            .drawBehind { drawRect(Color.Black, alpha = progress.value) }) {
        SteamGlassHost(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = if (flight != null && progress.value < 1f) 0f else progress.value
                if (flight == null) { scaleX = .96f + .04f * progress.value; scaleY = scaleX }
            },
            backdropColor = Color.Transparent,
            content = {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                SteamFullscreenImagePage(
                    url = images[page],
                    contentDescription = stringResource(strings.imageDescription, page + 1),
                    loadFailedText = stringResource(strings.loadFailed),
                    onZoomChanged = { zoomedPages[page] = it }
                )
            }

            }, overlay = {
            ImageViewerTopControls(
                title = title,
                downloading = downloading,
                closeDescription = stringResource(strings.close),
                downloadDescription = stringResource(strings.download),
                downloadingDescription = stringResource(strings.downloading),
                onDismiss = dismiss,
                onDownload = {
                    val currentIndex = pagerState.currentPage
                    if (downloader.requiresLegacyStoragePermission()) {
                        pendingPermissionIndex = currentIndex
                        storagePermissionLauncher.launch(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    } else {
                        startDownload(currentIndex)
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            if (images.size > 1) {
                ImageViewerPageControls(
                    currentIndex = pagerState.currentPage,
                    imageCount = images.size,
                    positionText = stringResource(
                        strings.position,
                        pagerState.currentPage + 1,
                        images.size
                    ),
                    previousDescription = stringResource(strings.previous),
                    nextDescription = stringResource(strings.next),
                    onPrevious = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                (pagerState.currentPage - 1).coerceAtLeast(0)
                            )
                        }
                    },
                    onNext = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                (pagerState.currentPage + 1).coerceAtMost(images.lastIndex)
                            )
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        })
        if (flight != null && progress.value < 1f && !reduced) {
            SteamImageFlight(flight, { progress.value }, { screenOffset })
        }
        }
    }
}
