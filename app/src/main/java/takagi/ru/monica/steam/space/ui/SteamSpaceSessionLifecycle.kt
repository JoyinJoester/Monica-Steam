package takagi.ru.monica.steam.space.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.space.presentation.SteamSpaceViewModel

/** Stop is synchronous; activation waits for composition to supply the latest account. */
@Composable
internal fun SteamSpaceSessionLifecycle(
    model: SteamSpaceViewModel,
    account: SteamAccount?,
    onRefreshFriends: () -> Unit
) {
    val owner = LocalLifecycleOwner.current
    val session = remember(model, owner) { SpaceLifecycleSession(model) }
    val refreshFriends by rememberUpdatedState(onRefreshFriends)
    var startRevision by remember(owner) { mutableIntStateOf(0) }

    DisposableEffect(session, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // A revision also handles stop/start occurring between two Compose frames.
                Lifecycle.Event.ON_START -> startRevision++
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> session.stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            session.stop()
        }
    }
    LaunchedEffect(session, account, startRevision) {
        // Account/token updates while stopped must not resurrect a request.
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            session.apply(account, refreshFriends)
        }
    }
}

private class SpaceLifecycleSession(private val model: SteamSpaceViewModel) {
    private var active = false
    private var selected: SteamAccount? = null

    fun apply(account: SteamAccount?, refreshFriends: () -> Unit) {
        val refreshPresence = !active || selected?.id != account?.id || selected?.steamId != account?.steamId
        if (!active || selected != account) {
            active = true
            selected = account
            model.selectAccount(account)
        }
        if (refreshPresence && account?.hasRealSteamId == true) refreshFriends()
    }

    fun stop() {
        active = false
        selected = null
        model.deactivate()
    }
}
