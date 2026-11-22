package takagi.ru.monica.steam.space.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import takagi.ru.monica.steam.space.data.SteamSpaceLinks

/** Choose a browser rather than re-entering Monica's own Steam app-link handler. */
internal fun steamSpaceOriginalBrowserIntent(url: String): Intent? {
    if (!SteamSpaceLinks.isAllowedSourceUrl(url)) return null
    return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        selector = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
    }
}

internal fun openSteamSpaceOriginalInBrowser(context: Context, url: String): Boolean {
    val intent = steamSpaceOriginalBrowserIntent(url) ?: return false
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // No unrestricted ACTION_VIEW fallback: it could resolve to Monica again.
    return runCatching { context.startActivity(intent) }.isSuccess
}
