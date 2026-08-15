package takagi.ru.monica.steam.web.ui

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import takagi.ru.monica.steam.web.domain.SteamWebFailureKind
import takagi.ru.monica.steam.web.domain.SteamWebNavigationPolicy
import takagi.ru.monica.steam.web.domain.SteamWebPageFailure

internal class SteamBrowserWebViewClient(
    private val openExternal: (String) -> Boolean,
    private val onPageStartedCallback: (WebView, String) -> Unit,
    private val onPageCommitVisibleCallback: (WebView, String) -> Unit,
    private val onPageFinishedCallback: (WebView, String) -> Unit,
    private val onHistoryChangedCallback: (WebView, String) -> Unit,
    private val onFailureCallback: (SteamWebPageFailure) -> Unit,
    private val onRendererGoneCallback: (WebView, Boolean) -> Unit,
) : WebViewClient() {
    private var hasCommittedContent = false

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        if (!hasCommittedContent) view.alpha = 0f
        onPageStartedCallback(view, url)
    }

    override fun onPageCommitVisible(view: WebView, url: String) {
        hasCommittedContent = true
        view.alpha = 1f
        view.applySteamStoreMenuScrollFix(url)
        onPageCommitVisibleCallback(view, url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        hasCommittedContent = true
        view.alpha = 1f
        view.applySteamStoreMenuScrollFix(url)
        onPageFinishedCallback(view, url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        onHistoryChangedCallback(view, url.orEmpty())
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val target = request.url?.toString().orEmpty()
        if (SteamWebNavigationPolicy.isAllowed(target)) return false
        if (!request.isForMainFrame) return true
        if (SteamWebNavigationPolicy.isSafeExternal(target) && openExternal(target)) return true
        onFailureCallback(
            SteamWebPageFailure(
                kind = SteamWebFailureKind.UNSAFE_NAVIGATION,
                failingUrl = target
            )
        )
        return true
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        if (!request.isForMainFrame) return
        onFailureCallback(
            SteamWebPageFailure(
                kind = SteamWebFailureKind.NETWORK,
                description = error.description?.toString(),
                failingUrl = request.url?.toString()
            )
        )
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        if (!request.isForMainFrame || errorResponse.statusCode < 400) return
        onFailureCallback(
            SteamWebPageFailure(
                kind = SteamWebFailureKind.HTTP,
                description = errorResponse.reasonPhrase,
                failingUrl = request.url?.toString(),
                statusCode = errorResponse.statusCode
            )
        )
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError
    ) {
        handler.cancel()
        view.stopLoading()
        onFailureCallback(
            SteamWebPageFailure(
                kind = SteamWebFailureKind.SSL,
                failingUrl = error.url
            )
        )
    }

    override fun onReceivedHttpAuthRequest(
        view: WebView,
        handler: HttpAuthHandler,
        host: String?,
        realm: String?
    ) {
        handler.cancel()
        onFailureCallback(
            SteamWebPageFailure(
                kind = SteamWebFailureKind.HTTP,
                failingUrl = view.url,
                statusCode = 401
            )
        )
    }

    override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
        dontResend.sendToTarget()
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail
    ): Boolean {
        onRendererGoneCallback(view, detail.didCrash())
        return true
    }
}

internal class SteamBrowserWebChromeClient(
    private val onProgressChangedCallback: (Int) -> Unit,
    private val onTitleChangedCallback: (String?) -> Unit,
    private val onFileChooserCallback: (
        ValueCallback<Array<Uri>>,
        FileChooserParams
    ) -> Boolean,
    private val onPermissionRequestCallback: (PermissionRequest) -> Unit,
    private val onPermissionRequestCanceledCallback: (PermissionRequest) -> Unit,
    private val onShowCustomViewCallback: (View, CustomViewCallback) -> Unit,
    private val onHideCustomViewCallback: () -> Unit,
) : WebChromeClient() {
    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgressChangedCallback(newProgress.coerceIn(0, 100))
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        onTitleChangedCallback(title?.trim()?.takeIf(String::isNotBlank))
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        if (filePathCallback == null || fileChooserParams == null) return false
        return onFileChooserCallback(filePathCallback, fileChooserParams)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        onPermissionRequestCallback(request)
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest) {
        onPermissionRequestCanceledCallback(request)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        onShowCustomViewCallback(view, callback)
    }

    override fun onHideCustomView() {
        onHideCustomViewCallback()
    }
}

private fun WebView.applySteamStoreMenuScrollFix(pageUrl: String) {
    val host = runCatching { Uri.parse(pageUrl).host.orEmpty() }.getOrDefault("")
    if (!host.equals("store.steampowered.com", ignoreCase = true)) return
    evaluateJavascript(STEAM_STORE_MENU_SCROLL_FIX_SCRIPT, null)
}

private val STEAM_STORE_MENU_SCROLL_FIX_SCRIPT = """
    (() => {
        const styleId = 'monica-steam-store-menu-scroll-fix';
        const fixedClass = 'monica-steam-store-menu-scroll-target';
        if (!document.getElementById(styleId)) {
            const style = document.createElement('style');
            style.id = styleId;
            style.textContent = `
            .${'$'}{fixedClass} {
                position: relative !important;
                top: auto !important;
                height: auto !important;
                overflow: visible !important;
            }
            `;
            (document.head || document.documentElement).appendChild(style);
        }

        const markMenu = () => {
            const menuRoot = document.querySelector('[data-featuretarget="store-menu-v7"]');
            const placeholder = menuRoot?.querySelector(':scope > .PlaceholderInner');
            const container = placeholder?.nextElementSibling;
            if (!container) return false;
            const menu = Array.from(container.children).find((element) => {
                const position = window.getComputedStyle(element).position;
                return (position === 'sticky' || position === 'fixed') &&
                    element.querySelector('a[href*="/wishlist"]');
            });
            if (!menu) return false;
            menu.classList.add(fixedClass);
            return true;
        };

        if (markMenu()) return;
        const observer = new MutationObserver(() => {
            if (markMenu()) observer.disconnect();
        });
        observer.observe(document.documentElement, { childList: true, subtree: true });
        window.setTimeout(() => observer.disconnect(), 5000);
    })();
""".trimIndent()
