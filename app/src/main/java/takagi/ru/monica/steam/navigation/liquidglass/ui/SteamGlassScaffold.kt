package takagi.ru.monica.steam.navigation.liquidglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import takagi.ru.monica.steam.navigation.liquidglass.render.rememberSteamLiquidGlassBackdrop
import takagi.ru.monica.steam.navigation.liquidglass.render.steamLiquidGlassBackdropSource

/** Same Scaffold slots and insets. Only the body is recorded; chrome never captures itself. */
@Composable
internal fun SteamGlassScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit
) {
    val backdrop = rememberSteamLiquidGlassBackdrop()
    val source = backdrop.takeIf { steamGlassSupported() }
    @Composable fun Chrome(block: @Composable () -> Unit) {
        CompositionLocalProvider(LocalSteamGlassBackdrop provides source,
            LocalSteamPopupBackdrop provides source, content = block)
    }
    Scaffold(
        modifier, topBar = { Chrome(topBar) }, bottomBar = { Chrome(bottomBar) },
        snackbarHost = { Chrome(snackbarHost) }, floatingActionButton = { Chrome(floatingActionButton) },
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor, contentColor = contentColor, contentWindowInsets = contentWindowInsets
    ) { padding ->
        Box(Modifier.fillMaxSize().steamLiquidGlassBackdropSource(backdrop,
            source != null && backdrop.consumers > 0).background(containerColor)) {
            // Popups are separate windows, so they can sample this body. In-body controls
            // need their own sibling host (or retain an opaque fallback).
            CompositionLocalProvider(LocalSteamGlassBackdrop provides null,
                LocalSteamPopupBackdrop provides source) { content(padding) }
        }
    }
}
