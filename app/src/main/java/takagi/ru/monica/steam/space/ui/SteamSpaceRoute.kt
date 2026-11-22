package takagi.ru.monica.steam.space.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.presentation.SteamFriendsUiState
import takagi.ru.monica.steam.space.presentation.*
import takagi.ru.monica.steam.space.data.SteamSpaceLinks
import takagi.ru.monica.steam.web.domain.SteamWebAccountSessionPolicy
import takagi.ru.monica.steam.web.ui.SteamWebBrowserScreen

@Composable
internal fun SteamSpaceRoute(
    account: SteamAccount?,
    friendsState: SteamFriendsUiState,
    onRefreshFriends: () -> Unit,
    onBack: () -> Unit,
    onOpenGame: (Int) -> Unit,
    onMessage: (String) -> Unit,
    onManageAccounts: () -> Unit,
    onPlatformViewVisibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val model: SteamSpaceViewModel = viewModel(factory = remember(context) { SteamSpaceViewModel.factory(context) })
    val state by model.uiState.collectAsState()
    val settingsModel: SteamSpaceContentSettingsViewModel = viewModel(
        factory = remember(context) { SteamSpaceContentSettingsViewModel.factory(context) }
    )
    val settings by settingsModel.state.collectAsState()
    SteamSpaceSessionLifecycle(model, account, onRefreshFriends)
    // Effects run after composition: never draw the previous account during that gap.
    val visible = if (account == null || !account.hasRealSteamId) SteamSpaceUiState(failure = SteamSpaceFailureReason.ACCOUNT_REQUIRED)
        else if (state.accountId == account.id && state.accountSteamId == account.steamId) state
        // Activation restores memory synchronously; do not flash a spinner before that effect.
        // Stopped routes also remain empty instead of republishing the retained cache.
        else SteamSpaceUiState(accountId = account.id, accountSteamId = account.steamId)
    val visibleFriends = friendsState.takeIf { it.accountId == account?.id } ?: SteamFriendsUiState(loading = account != null)
    val loginCookie = account?.steamLoginSecure?.takeIf(String::isNotBlank)
        ?: account?.accessToken?.takeIf(String::isNotBlank)?.let { "${account.steamId}||$it" }
    val canOpenActivity = account?.hasRealSteamId == true &&
        SteamWebAccountSessionPolicy.decide(account.steamId, loginCookie,
            requireAuthenticatedSession = true, temporarySession = account.isTemporary).canLoad
    var publishing by remember(account?.id, account?.steamId, account?.isTemporary) { mutableStateOf(false) }
    var publishingWeb by remember(account?.id, account?.steamId, account?.isTemporary) { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        SteamSpaceScreen(
            state = if (settings.ready) visible else visible.copy(items = emptyList(), loaded = false, loading = true),
            friendsState = visibleFriends, onBack = onBack,
            onRefresh = { model.refresh(); onRefreshFriends() }, onLoadMore = model::loadMore,
            onOpenGame = onOpenGame, onMessage = onMessage, onManageAccounts = onManageAccounts,
            onPublish = { if (account?.hasRealSteamId == true && !account.accessToken.isNullOrBlank()) publishing = true else onManageAccounts() },
            onOpenOriginal = { url ->
                val opened = openSteamSpaceOriginalInBrowser(context, url)
                if (!opened) Toast.makeText(context, R.string.steam_space_open_failed, Toast.LENGTH_SHORT).show()
            }, modifier = if (publishing) Modifier.clearAndSetSemantics { hideFromAccessibility() } else Modifier,
            hiddenKinds = settings.hiddenKinds,
            onSetKindVisible = settingsModel::setKindVisible,
            onShowAllKinds = settingsModel::showAllKinds,
            onRetryContentSettings = settingsModel::retrySave,
            contentSettingsSaving = !settings.ready || settings.saving,
            contentSettingsFailed = settings.failed
        )
        if (publishing && account != null) {
            SteamSpaceComposePage(account = account,
                onClose = { publishing = false },
                onPublished = {
                    publishing = false
                    Toast.makeText(context, R.string.steam_space_post_success, Toast.LENGTH_SHORT).show()
                    model.refresh()
                },
                onOpenWeb = if (canOpenActivity) ({ publishingWeb = true }) else null,
                modifier = if (publishingWeb) Modifier.clearAndSetSemantics { hideFromAccessibility() } else Modifier)
        }
        if (publishingWeb && canOpenActivity && account != null) {
            SteamWebBrowserScreen(
                url = SteamSpaceLinks.HOME,
                title = stringResource(R.string.steam_space_publish),
                steamLoginSecure = loginCookie,
                expectedSteamId = account.steamId,
                requireAuthenticatedSession = true,
                temporarySession = account.isTemporary,
                onPlatformViewVisibilityChanged = onPlatformViewVisibilityChanged,
                onClose = { publishingWeb = false; model.refresh() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SteamSpaceComposePage(account: SteamAccount, onClose: () -> Unit, onPublished: () -> Unit,
    onOpenWeb: (() -> Unit)?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val model: SteamSpaceComposeViewModel = viewModel(factory = remember(context) { SteamSpaceComposeViewModel.factory(context) })
    val state by model.state.collectAsState()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(model, account) { model.selectAccount(account) }
    DisposableEffect(model, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.stop()
            if (event == Lifecycle.Event.ON_DESTROY) model.clear()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); model.clear() }
    }
    val visible = if (state.accountId == account.id && state.steamId == account.steamId) state else SteamSpaceComposeState()
    LaunchedEffect(visible.phase) { if (visible.phase == SteamSpaceComposePhase.SUCCEEDED) onPublished() }
    SteamSpaceComposeScreen(account, visible, model::updateText, model::publish, onClose, onOpenWeb, modifier)
}

/** External navigation never carries the account cookie, token, or a third-party scheme. */
internal fun isSteamSpaceSourceUrl(url: String): Boolean = SteamSpaceLinks.isAllowedSourceUrl(url)
