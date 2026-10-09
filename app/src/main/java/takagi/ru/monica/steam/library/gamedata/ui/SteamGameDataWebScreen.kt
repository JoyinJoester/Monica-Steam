package takagi.ru.monica.steam.library.gamedata.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.library.gamedata.domain.SteamGameDataPage
import takagi.ru.monica.steam.library.gamedata.domain.SteamReplayBrowserPolicy
import takagi.ru.monica.steam.web.domain.SteamWebClientMode
import takagi.ru.monica.steam.web.ui.SteamWebBrowserScreen

@Composable
internal fun SteamGameDataWebScreen(
    page: SteamGameDataPage,
    account: SteamAccount,
    onPlatformViewVisibilityChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloadFailureMessage = stringResource(R.string.steam_library_replay_browser_failed)
    SteamWebBrowserScreen(
        url = page.url,
        steamLoginSecure = account.steamLoginSecure
            ?: account.accessToken?.let { token -> "${account.steamId}||$token" },
        expectedSteamId = account.steamId,
        temporarySession = account.isTemporary,
        title = stringResource(R.string.steam_library_game_data_title),
        requireAuthenticatedSession = true,
        clientMode = SteamWebClientMode.COMMUNITY_DESKTOP,
        onDownloadRequested = { url ->
            if (!openSteamReplayInBrowser(context, url)) {
                Toast.makeText(context, downloadFailureMessage, Toast.LENGTH_SHORT).show()
            }
        },
        onPlatformViewVisibilityChanged = onPlatformViewVisibilityChanged,
        onClose = onClose,
        modifier = modifier
    )
}

internal fun openSteamReplayInBrowser(context: Context, rawUrl: String?): Boolean {
    val url = SteamReplayBrowserPolicy.normalizedUrl(rawUrl) ?: return false
    val uri = Uri.parse(url)
    val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        selector = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_BROWSER)
        }
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (runCatching { context.startActivity(browserIntent) }.isSuccess) return true
    val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(fallback) }.isSuccess
}
