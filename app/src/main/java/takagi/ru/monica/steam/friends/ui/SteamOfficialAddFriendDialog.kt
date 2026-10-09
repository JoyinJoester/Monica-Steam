package takagi.ru.monica.steam.friends.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.web.ui.SteamWebBrowserScreen

internal const val STEAM_OFFICIAL_ADD_FRIEND_URL =
    "https://steamcommunity.com/my/friends/add"

@Composable
internal fun SteamOfficialAddFriendDialog(
    account: SteamAccount?,
    onPlatformViewVisibilityChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            SteamWebBrowserScreen(
                url = STEAM_OFFICIAL_ADD_FRIEND_URL,
                steamLoginSecure = account?.let { selectedAccount ->
                    selectedAccount.steamLoginSecure
                        ?: selectedAccount.accessToken?.let { token ->
                            "${selectedAccount.steamId}||$token"
                        }
                },
                expectedSteamId = account?.steamId,
                temporarySession = account?.isTemporary == true,
                title = stringResource(R.string.steam_friend_add_on_steam),
                requireAuthenticatedSession = true,
                onPlatformViewVisibilityChanged = onPlatformViewVisibilityChanged,
                onClose = onDismiss,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
