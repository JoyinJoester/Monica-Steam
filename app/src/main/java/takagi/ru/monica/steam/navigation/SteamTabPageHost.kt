package takagi.ru.monica.steam.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

/** A bounded group of visited tabs; other routes keep the normal single-page transition.
 * Hidden pages keep their recorded layers; only the active page draws or exposes semantics.
 * Their lifecycle is stopped and focus is cleared when leaving the current page.
 * Only the current page survives backgrounding, window overlays or a platform-view route.
 */
@Composable
internal fun <T : Any> SteamTabPageHost(
    targetPage: T,
    retainedPages: Set<T>,
    retentionEnabled: Boolean,
    modifier: Modifier = Modifier,
    label: String = "steam_tab_page",
    transitionSpec: (T, T) -> ContentTransform,
    content: @Composable (T) -> Unit
) {
    var lastGroupedPage by remember { mutableStateOf(targetPage) }
    SideEffect { if (targetPage in retainedPages) lastGroupedPage = targetPage }
    // All grouped tabs share one transition key. Switching a warm tab doesn't recreate it.
    AnimatedContent(
        targetState = targetPage.takeUnless { it in retainedPages },
        modifier = modifier,
        label = label,
        contentKey = { it },
        transitionSpec = {
            transitionSpec(initialState ?: lastGroupedPage, targetState ?: targetPage)
        }
    ) { page ->
        if (page != null) {
            content(page)
        } else {
            SteamVisitedTabGroup(
                selected = targetPage.takeIf { it in retainedPages } ?: lastGroupedPage,
                retainedPages = retainedPages,
                retentionEnabled = retentionEnabled,
                content = content
            )
        }
    }
}

@Composable
private fun <T : Any> SteamVisitedTabGroup(
    selected: T,
    retainedPages: Set<T>,
    retentionEnabled: Boolean,
    content: @Composable (T) -> Unit
) {
    val parent = LocalLifecycleOwner.current
    val parentState by parent.lifecycle.currentStateFlow.collectAsState()
    val focused = LocalWindowInfo.current.isWindowFocused
    val focusManager = LocalFocusManager.current
    DisposableEffect(selected, focusManager) {
        // A cached search field must not keep receiving keyboard input after its tab leaves.
        onDispose { focusManager.clearFocus(force = true) }
    }
    val visited = remember { mutableStateListOf<T>() }
    DisposableEffect(parent, visited) {
        val observer = LifecycleEventObserver { _, event ->
            // Recomposition can be paused with the Activity. Clear the revisit set now,
            // so resuming cannot restore an inactive page from the previous foreground run.
            if (event == Lifecycle.Event.ON_STOP) visited.clear()
        }
        parent.lifecycle.addObserver(observer)
        onDispose { parent.lifecycle.removeObserver(observer) }
    }
    val keepVisited = retentionEnabled && focused && parentState.isAtLeast(Lifecycle.State.STARTED)
    val pages = if (keepVisited) (visited.filter { it in retainedPages } + selected).distinct()
        else listOf(selected)
    SideEffect {
        visited.removeAll { it !in pages }
        if (selected !in visited) visited.add(selected)
    }
    Layout(modifier = Modifier.fillMaxSize(), content = {
        for (page in pages) key(page) {
            val active = page == selected
            val owner = remember(parent) { SteamTabLifecycleOwner(parent, active) }
            SideEffect { owner.setActive(active) }
            DisposableEffect(owner) {
                owner.attach()
                onDispose { owner.dispose() }
            }
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val hidden = Modifier.graphicsLayer { alpha = if (active) 1f else 0f; clip = true }
                    .then(if (active) Modifier else Modifier.clearAndSetSemantics {})
                Box(Modifier.fillMaxSize().then(hidden)) { content(page) }
            }
        }
    }) { measurables, constraints ->
        // Unplacing an inbox invalidates its recorded backdrop on every return. Keep the
        // bounded Dock pages placed behind transparent layers, with the active page on top.
        val placed = measurables.map { it.measure(constraints) }
        val visible = placed[pages.indexOf(selected)]
        layout(visible.width, visible.height) {
            for (index in placed.indices) {
                placed[index].place(0, 0, zIndex = if (pages[index] == selected) 1f else 0f)
            }
        }
    }
}

private class SteamTabLifecycleOwner(
    private val parent: LifecycleOwner,
    private var active: Boolean
) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    private val observer = LifecycleEventObserver { _, _ -> update() }
    init { registry.currentState = Lifecycle.State.CREATED }
    fun attach() { parent.lifecycle.addObserver(observer); update() }
    fun setActive(value: Boolean) { active = value; update() }
    private fun update() {
        val state = parent.lifecycle.currentState
        registry.currentState = if (active || state < Lifecycle.State.CREATED) state
            else Lifecycle.State.CREATED
    }
    fun dispose() {
        parent.lifecycle.removeObserver(observer)
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
