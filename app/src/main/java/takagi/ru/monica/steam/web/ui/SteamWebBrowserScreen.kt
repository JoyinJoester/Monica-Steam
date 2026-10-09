package takagi.ru.monica.steam.web.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.security.SecureRandom
import takagi.ru.monica.R
import takagi.ru.monica.steam.web.data.clearSteamCookies
import takagi.ru.monica.steam.web.data.replaceSteamCookies
import takagi.ru.monica.steam.web.domain.SteamWebAccountSessionPolicy
import takagi.ru.monica.steam.web.domain.SteamWebBrowserState
import takagi.ru.monica.steam.web.domain.SteamWebClientMode
import takagi.ru.monica.steam.web.domain.SteamWebFailureKind
import takagi.ru.monica.steam.web.domain.SteamFamilyViewCookieSourcePolicy
import takagi.ru.monica.steam.web.domain.SteamFamilyViewSessions
import takagi.ru.monica.steam.web.domain.SteamWebNavigationCommand
import takagi.ru.monica.steam.web.domain.SteamWebNavigationPolicy
import takagi.ru.monica.steam.web.domain.SteamWebPageAutomation
import takagi.ru.monica.steam.web.domain.SteamWebPageFailure
import takagi.ru.monica.steam.web.domain.SteamWebSessionCookiePolicy

private const val MAX_RENDERER_AUTO_RECOVERIES = 2

private data class SteamWebSessionScopeKey(
    val initialUrl: String,
    val expectedSteamId: String,
    val loginCookieHash: Int,
    val requireAuthenticatedSession: Boolean,
    val clientMode: SteamWebClientMode,
)

private data class PendingSteamWebPermission(
    val request: PermissionRequest,
    val webResources: Array<String>,
    val androidPermissions: Array<String>,
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SteamWebBrowserScreen(
    url: String,
    steamLoginSecure: String?,
    expectedSteamId: String? = null,
    title: String? = null,
    requireAuthenticatedSession: Boolean = false,
    temporarySession: Boolean = false,
    clientMode: SteamWebClientMode = SteamWebClientMode.DEFAULT,
    automationFactory: ((String) -> SteamWebPageAutomation)? = null,
    onDownloadRequested: ((String) -> Unit)? = null,
    onPlatformViewVisibilityChanged: (Boolean) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val initialBackground = when (clientMode) {
        SteamWebClientMode.COMMUNITY_DESKTOP -> Color(0xFF1B2838)
        SteamWebClientMode.DEFAULT -> MaterialTheme.colorScheme.background
    }
    val sessionDecision = remember(expectedSteamId, steamLoginSecure, requireAuthenticatedSession, temporarySession) {
        SteamWebAccountSessionPolicy.decide(
            expectedSteamId = expectedSteamId,
            steamLoginSecure = steamLoginSecure,
            requireAuthenticatedSession = requireAuthenticatedSession,
            temporarySession = temporarySession,
        )
    }
    val sessionScopeKey = remember(
        url,
        expectedSteamId,
        steamLoginSecure,
        requireAuthenticatedSession,
        clientMode
    ) {
        SteamWebSessionScopeKey(
            initialUrl = url,
            expectedSteamId = expectedSteamId.orEmpty(),
            loginCookieHash = steamLoginSecure.hashCode(),
            requireAuthenticatedSession = requireAuthenticatedSession,
            clientMode = clientMode,
        )
    }
    val initialUrlAllowed = remember(url) { SteamWebNavigationPolicy.isAllowed(url) }
    val controller = remember(sessionScopeKey) { SteamWebBrowserController() }
    val sessionId = remember(sessionScopeKey) { randomSteamWebSessionId() }
    val pageAutomation = remember(sessionScopeKey, automationFactory) {
        automationFactory?.invoke(sessionId)
    }
    var browserState by remember(sessionScopeKey) {
        mutableStateOf(
            SteamWebBrowserState(
                currentUrl = url,
                pageTitle = title
            )
        )
    }
    var platformViewReady by remember(sessionScopeKey) { mutableStateOf(false) }
    var platformViewSignaled by remember(sessionScopeKey) { mutableStateOf(false) }
    var rendererGeneration by remember(sessionScopeKey) { mutableIntStateOf(0) }
    var savedWebViewState by remember(sessionScopeKey) { mutableStateOf<Bundle?>(null) }
    var pendingFileCallback by remember(sessionScopeKey) {
        mutableStateOf<ValueCallback<Array<Uri>>?>(null)
    }
    var pendingPermission by remember(sessionScopeKey) {
        mutableStateOf<PendingSteamWebPermission?>(null)
    }
    var customView by remember(sessionScopeKey) { mutableStateOf<View?>(null) }
    var customViewCallback by remember(sessionScopeKey) {
        mutableStateOf<WebChromeClient.CustomViewCallback?>(null)
    }
    val platformViewVisibilityCallback by rememberUpdatedState(
        onPlatformViewVisibilityChanged
    )
    val downloadRequestCallback by rememberUpdatedState(onDownloadRequested)
    val closeCallback by rememberUpdatedState(onClose)
    val externalUnavailableMessage = stringResource(R.string.steam_web_external_unavailable)
    val fileChooserUnavailableMessage = stringResource(R.string.steam_web_file_chooser_unavailable)

    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        if (callback == null) return@rememberLauncherForActivityResult
        val selected = if (result.resultCode == Activity.RESULT_OK) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
                ?.filter(::isSafeChosenFileUri)
                ?.toTypedArray()
                ?.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        callback.onReceiveValue(selected)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val pending = pendingPermission
        pendingPermission = null
        if (pending == null) return@rememberLauncherForActivityResult
        val granted = pending.androidPermissions.all { permission ->
            result[permission] == true || context.hasPermission(permission)
        }
        runCatching {
            if (granted) pending.request.grant(pending.webResources) else pending.request.deny()
        }
    }

    fun hideCustomView() {
        val callback = customViewCallback
        customView = null
        customViewCallback = null
        runCatching { callback?.onCustomViewHidden() }
    }

    fun captureFamilyViewSession(pageUrl: String?) {
        val accountSteamId = expectedSteamId?.trim()?.takeIf(String::isNotBlank) ?: return
        val allowedUrl = pageUrl?.takeIf(SteamFamilyViewCookieSourcePolicy::isAllowed) ?: return
        SteamFamilyViewSessions.capture(
            accountSteamId = accountSteamId,
            cookieHeader = CookieManager.getInstance().getCookie(allowedUrl),
        )
    }

    fun closeBrowser() {
        captureFamilyViewSession(controller.webView?.url ?: browserState.currentUrl)
        closeCallback()
    }

    fun handleBack() {
        when {
            customView != null -> hideCustomView()
            browserState.canGoBack -> controller.goBack()
            else -> closeBrowser()
        }
    }

    fun showExternalFailure() {
        Toast.makeText(context, externalUnavailableMessage, Toast.LENGTH_SHORT).show()
    }

    fun openExternal(rawUrl: String, browserOnly: Boolean): Boolean {
        val opened = context.openExternalUri(rawUrl, browserOnly)
        if (!opened) showExternalFailure()
        return opened
    }

    fun retry() {
        browserState = browserState.copy(failure = null, loading = true, progress = 1)
        if (controller.attached) {
            controller.reload()
        } else {
            browserState = browserState.copy(
                rendererRecoveryCount = 0,
                contentVisible = false
            )
            rendererGeneration += 1
        }
    }

    BackHandler(onBack = ::handleBack)

    LaunchedEffect(sessionScopeKey, sessionDecision.canLoad, initialUrlAllowed) {
        platformViewReady = false
        platformViewVisibilityCallback(true)
        platformViewSignaled = true
        if (sessionDecision.canLoad && initialUrlAllowed) {
            withFrameNanos { }
            platformViewReady = true
        } else if (sessionDecision.canLoad) {
            browserState = browserState.copy(
                failure = SteamWebPageFailure(
                    kind = SteamWebFailureKind.UNSAFE_NAVIGATION,
                    failingUrl = url
                )
            )
        }
    }

    LaunchedEffect(sessionScopeKey, sessionDecision.canLoad) {
        if (!sessionDecision.canLoad) CookieManager.getInstance().clearSteamCookies()
    }

    DisposableEffect(customView != null, context) {
        val activity = context.findActivity()
        val fullscreen = customView != null
        if (fullscreen && activity != null) {
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (fullscreen && activity != null) {
                WindowCompat.getInsetsController(
                    activity.window,
                    activity.window.decorView
                ).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(lifecycleOwner, controller.webView) {
        val attachedView = controller.webView
        if (attachedView == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> attachedView.onResume()
                    Lifecycle.Event.ON_PAUSE -> attachedView.onPause()
                    Lifecycle.Event.ON_STOP -> {
                        attachedView.onPause()
                        saveSteamWebViewState(attachedView)?.let { savedWebViewState = it }
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(initialBackground)
    ) {
        when {
            !sessionDecision.canLoad -> SteamWebSessionError(sessionDecision.problem)
            !initialUrlAllowed -> browserState.failure?.let { failure ->
                SteamWebFailureContent(failure, onRetry = ::retry, onClose = ::closeBrowser)
            }
            !platformViewReady -> Surface(
                modifier = Modifier.fillMaxSize(),
                color = initialBackground
            ) {}
            else -> key(sessionScopeKey, rendererGeneration) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    factory = { factoryContext ->
                        WebView(factoryContext).apply steamWebView@{
                            controller.attach(this)
                            configureForSteam(
                                clientMode = clientMode,
                                backgroundColor = initialBackground.toArgb()
                            )
                            setDownloadListener { downloadUrl, _, _, _, _ ->
                                val target = downloadUrl?.takeIf(String::isNotBlank) ?: return@setDownloadListener
                                val callback = downloadRequestCallback
                                if (callback != null) {
                                    runCatching { callback(target) }
                                        .onFailure { showExternalFailure() }
                                } else if (SteamWebNavigationPolicy.isSafeExternal(target)) {
                                    openExternal(target, browserOnly = true)
                                } else {
                                    showExternalFailure()
                                }
                            }
                            webChromeClient = SteamBrowserWebChromeClient(
                                onProgressChangedCallback = progressChanged@{ progress ->
                                    if (controller.webView !== this@steamWebView) return@progressChanged
                                    browserState = browserState.copy(
                                        progress = progress,
                                        loading = progress in 0..99
                                    )
                                },
                                onTitleChangedCallback = { pageTitle ->
                                    if (controller.webView === this@steamWebView && pageTitle != null) {
                                        browserState = browserState.copy(pageTitle = pageTitle)
                                    }
                                },
                                onFileChooserCallback = fileChooser@{ callback, params ->
                                    if (!SteamWebNavigationPolicy.isAllowed(this@steamWebView.url.orEmpty())) {
                                        callback.onReceiveValue(null)
                                        return@fileChooser true
                                    }
                                    pendingFileCallback?.onReceiveValue(null)
                                    pendingFileCallback = callback
                                    val chooserIntent = runCatching { params.createIntent() }.getOrNull()
                                    if (chooserIntent == null) {
                                        pendingFileCallback = null
                                        callback.onReceiveValue(null)
                                        Toast.makeText(
                                            context,
                                            fileChooserUnavailableMessage,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        runCatching { fileChooserLauncher.launch(chooserIntent) }
                                            .onFailure {
                                                pendingFileCallback = null
                                                callback.onReceiveValue(null)
                                                Toast.makeText(
                                                    context,
                                                    fileChooserUnavailableMessage,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                    }
                                    true
                                },
                                onPermissionRequestCallback = permissionRequest@{ request ->
                                    val origin = request.origin?.toString().orEmpty()
                                    if (!SteamWebNavigationPolicy.isAllowed(origin)) {
                                        request.deny()
                                        return@permissionRequest
                                    }
                                    val resources = request.resources.distinct()
                                    val knownResources = setOf(
                                        PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                                        PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                    )
                                    if (resources.isEmpty() || resources.any { it !in knownResources }) {
                                        request.deny()
                                        return@permissionRequest
                                    }
                                    val permissions = resources.map { resource ->
                                        when (resource) {
                                            PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                                                Manifest.permission.RECORD_AUDIO
                                            PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                                                Manifest.permission.CAMERA
                                            else -> error("Unknown WebView permission resource")
                                        }
                                    }.distinct().toTypedArray()
                                    if (permissions.all(context::hasPermission)) {
                                        request.grant(resources.toTypedArray())
                                    } else {
                                        runCatching { pendingPermission?.request?.deny() }
                                        pendingPermission = PendingSteamWebPermission(
                                            request = request,
                                            webResources = resources.toTypedArray(),
                                            androidPermissions = permissions
                                        )
                                        runCatching { permissionLauncher.launch(permissions) }
                                            .onFailure {
                                                pendingPermission = null
                                                request.deny()
                                            }
                                    }
                                },
                                onPermissionRequestCanceledCallback = { request ->
                                    if (pendingPermission?.request === request) pendingPermission = null
                                },
                                onShowCustomViewCallback = { view, callback ->
                                    hideCustomView()
                                    (view.parent as? ViewGroup)?.removeView(view)
                                    customView = view
                                    customViewCallback = callback
                                },
                                onHideCustomViewCallback = ::hideCustomView
                            )
                            webViewClient = SteamBrowserWebViewClient(
                                openExternal = { target ->
                                    openExternal(
                                        rawUrl = target,
                                        browserOnly = target.startsWith("http", ignoreCase = true)
                                    )
                                },
                                onPageStartedCallback = pageStarted@{ view, pageUrl ->
                                    if (controller.webView !== view) return@pageStarted
                                    browserState = browserState.copy(
                                        currentUrl = pageUrl,
                                        progress = 1,
                                        loading = true,
                                        canGoBack = view.canGoBack(),
                                        canGoForward = view.canGoForward(),
                                        failure = null
                                    )
                                },
                                onPageCommitVisibleCallback = pageCommit@{ view, pageUrl ->
                                    if (controller.webView !== view) return@pageCommit
                                    browserState = browserState.copy(
                                        currentUrl = pageUrl,
                                        contentVisible = true,
                                        canGoBack = view.canGoBack(),
                                        canGoForward = view.canGoForward()
                                    )
                                },
                                onPageFinishedCallback = pageFinished@{ view, pageUrl ->
                                    if (controller.webView !== view) return@pageFinished
                                    val pageFailure = browserState.failure
                                    browserState = browserState.copy(
                                        currentUrl = pageUrl,
                                        progress = 100,
                                        loading = false,
                                        contentVisible = true,
                                        canGoBack = view.canGoBack(),
                                        canGoForward = view.canGoForward(),
                                        failure = pageFailure,
                                        rendererRecoveryCount = if (pageFailure == null) {
                                            0
                                        } else {
                                            browserState.rendererRecoveryCount
                                        }
                                    )
                                    CookieManager.getInstance().flush()
                                    captureFamilyViewSession(pageUrl)
                                    view.postInvalidate()
                                    saveSteamWebViewState(view)?.let { savedWebViewState = it }
                                    if (pageFailure == null) {
                                        runCatching {
                                            pageAutomation?.onPageFinished(pageUrl)
                                        }.getOrNull()?.let { command ->
                                            if (!executeSteamWebCommand(view, command)) {
                                                browserState = browserState.copy(
                                                    failure = SteamWebPageFailure(
                                                        kind = SteamWebFailureKind.UNSAFE_NAVIGATION,
                                                        failingUrl = command.url
                                                    )
                                                )
                                            }
                                        }
                                    }
                                },
                                onHistoryChangedCallback = { view, pageUrl ->
                                    if (controller.webView === view) {
                                        browserState = browserState.copy(
                                            currentUrl = pageUrl.ifBlank { browserState.currentUrl },
                                            canGoBack = view.canGoBack(),
                                            canGoForward = view.canGoForward()
                                        )
                                    }
                                },
                                onFailureCallback = { failure ->
                                    if (controller.webView === this@steamWebView) {
                                        browserState = browserState.copy(
                                            loading = false,
                                            progress = 0,
                                            failure = failure
                                        )
                                    }
                                },
                                onRendererGoneCallback = rendererGone@{ view, _ ->
                                    if (controller.webView !== view) return@rendererGone
                                    val recoveryCount = browserState.rendererRecoveryCount + 1
                                    val lastUrl = view.url.orEmpty().ifBlank {
                                        browserState.currentUrl
                                    }
                                    controller.detach(view)
                                    destroySteamWebView(view)
                                    browserState = browserState.copy(
                                        currentUrl = lastUrl,
                                        progress = 0,
                                        loading = false,
                                        contentVisible = false,
                                        canGoBack = false,
                                        canGoForward = false,
                                        failure = SteamWebPageFailure(
                                            kind = SteamWebFailureKind.RENDERER,
                                            failingUrl = lastUrl
                                        ),
                                        rendererRecoveryCount = recoveryCount
                                    )
                                    if (recoveryCount <= MAX_RENDERER_AUTO_RECOVERIES) {
                                        rendererGeneration += 1
                                    }
                                }
                            )

                            CookieManager.getInstance().apply { setAcceptCookie(true) }
                                .replaceSteamCookies(
                                    SteamWebSessionCookiePolicy.cookieWrites(
                                        steamLoginSecure = steamLoginSecure.takeIf {
                                            sessionDecision.installAuthenticatedCookie
                                        },
                                        sessionId = sessionId,
                                        clientMode = clientMode,
                                        steamParentalCookie = expectedSteamId
                                            ?.let(SteamFamilyViewSessions::cookieFor),
                                    )
                                ) {
                                    if (controller.webView !== this@steamWebView) return@replaceSteamCookies
                                    val restored = savedWebViewState?.let { state ->
                                        runCatching {
                                            restoreState(Bundle(state)) != null
                                        }.getOrDefault(false)
                                    } == true
                                    if (restored) {
                                        alpha = 1f
                                        browserState = browserState.copy(
                                            currentUrl = this@steamWebView.url.orEmpty().ifBlank {
                                                browserState.currentUrl
                                            },
                                            progress = 100,
                                            loading = false,
                                            contentVisible = true,
                                            canGoBack = canGoBack(),
                                            canGoForward = canGoForward(),
                                            failure = null
                                        )
                                        postInvalidate()
                                    } else {
                                        val target = browserState.currentUrl.ifBlank { url }
                                        if (SteamWebNavigationPolicy.isAllowed(target)) {
                                            loadUrl(target)
                                        } else {
                                            browserState = browserState.copy(
                                                failure = SteamWebPageFailure(
                                                    kind = SteamWebFailureKind.UNSAFE_NAVIGATION,
                                                    failingUrl = target
                                                )
                                            )
                                        }
                                    }
                                }
                        }
                    }
                )
            }
        }

        val autoRecoveringRenderer = browserState.failure?.kind == SteamWebFailureKind.RENDERER &&
            browserState.rendererRecoveryCount in 1..MAX_RENDERER_AUTO_RECOVERIES
        if (
            sessionDecision.canLoad &&
            initialUrlAllowed &&
            platformViewReady &&
            !browserState.contentVisible &&
            (browserState.failure == null || autoRecoveringRenderer)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f),
                color = initialBackground
            ) {}
        }
        if (
            browserState.loading &&
            browserState.progress in 1..99 &&
            customView == null
        ) {
            LinearProgressIndicator(
                progress = { browserState.normalizedProgress },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .zIndex(3f)
            )
        }
        browserState.failure
            ?.takeUnless { autoRecoveringRenderer }
            ?.takeIf { sessionDecision.canLoad && initialUrlAllowed }
            ?.let { failure ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f),
                    color = initialBackground
                ) {
                    SteamWebFailureContent(
                        failure = failure,
                        onRetry = ::retry,
                        onClose = ::closeBrowser
                    )
                }
            }
        customView?.let { fullscreenView ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(5f),
                factory = {
                    (fullscreenView.parent as? ViewGroup)?.removeView(fullscreenView)
                    fullscreenView
                }
            )
        }
        if (customView == null && initialUrlAllowed) {
            SteamWebBrowserActionBar(
                state = browserState,
                controller = controller,
                onShare = {
                    context.shareSteamWebPage(
                        title = browserState.pageTitle ?: title,
                        url = browserState.currentUrl
                    )
                },
                onOpenExternal = {
                    openExternal(browserState.currentUrl, browserOnly = true)
                },
                onClose = ::closeBrowser,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .zIndex(4f)
            )
        }
    }

    DisposableEffect(sessionScopeKey) {
        onDispose {
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = null
            runCatching { pendingPermission?.request?.deny() }
            pendingPermission = null
            val callback = customViewCallback
            customView = null
            customViewCallback = null
            runCatching { callback?.onCustomViewHidden() }
            controller.webView?.let { view ->
                captureFamilyViewSession(view.url ?: browserState.currentUrl)
                saveSteamWebViewState(view)
                controller.detach(view)
                destroySteamWebView(view)
            }
            CookieManager.getInstance().flush()
            if (platformViewSignaled) platformViewVisibilityCallback(false)
        }
    }
}

private val SteamWebNavigationCommand.url: String
    get() = when (this) {
        is SteamWebNavigationCommand.LoadUrl -> url
        is SteamWebNavigationCommand.PostUrl -> url
    }

private fun executeSteamWebCommand(
    webView: WebView,
    command: SteamWebNavigationCommand
): Boolean {
    if (!SteamWebNavigationPolicy.isAllowed(command.url)) return false
    when (command) {
        is SteamWebNavigationCommand.LoadUrl -> webView.loadUrl(command.url)
        is SteamWebNavigationCommand.PostUrl -> webView.postUrl(command.url, command.body)
    }
    return true
}

private fun saveSteamWebViewState(webView: WebView): Bundle? = runCatching {
    Bundle().takeIf { webView.saveState(it) != null }
}.getOrNull()

private fun destroySteamWebView(webView: WebView) {
    runCatching { (webView.parent as? ViewGroup)?.removeView(webView) }
    runCatching { webView.stopLoading() }
    runCatching { webView.onPause() }
    runCatching { webView.setDownloadListener(null) }
    runCatching { webView.webChromeClient = null }
    runCatching { webView.webViewClient = WebViewClient() }
    runCatching { webView.removeAllViews() }
    runCatching { webView.destroy() }
}

private fun isSafeChosenFileUri(uri: Uri): Boolean =
    uri.scheme.equals("content", ignoreCase = true)

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.shareSteamWebPage(title: String?, url: String) {
    if (url.isBlank()) return
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title ?: url)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching {
        startActivity(
            Intent.createChooser(sendIntent, getString(R.string.steam_web_share_chooser)).apply {
                if (this@shareSteamWebPage !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

private fun Context.openExternalUri(rawUrl: String, browserOnly: Boolean): Boolean {
    if (!SteamWebNavigationPolicy.isSafeExternal(rawUrl)) return false
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        if (browserOnly) {
            selector = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_BROWSER)
            }
        }
        if (this@openExternalUri !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        startActivity(intent)
        true
    }.getOrDefault(false)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun randomSteamWebSessionId(): String {
    val bytes = ByteArray(12).also(SecureRandom()::nextBytes)
    return bytes.joinToString("") { "%02x".format(it) }
}
