package takagi.ru.monica.steam.foundation.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import takagi.ru.monica.data.InterfaceScale
import takagi.ru.monica.ui.scale.ProvideMonicaInterfaceScale

internal val LocalSteamUiChromeDensity = staticCompositionLocalOf<Density?> {
    null
}

internal fun ComponentActivity.setSteamUiScaledContent(content: @Composable () -> Unit) {
    setContent {
        ProvideSteamUiScale(content)
    }
}

@Composable
internal fun ProvideSteamUiScale(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { SteamUiScalePreferences(context) }
    val scalePercent by preferences.scale.collectAsState(
        initial = InterfaceScale.DEFAULT_PERCENT
    )
    ProvideMonicaInterfaceScale(scalePercent) {
        val appDensity = LocalDensity.current
        CompositionLocalProvider(LocalSteamUiChromeDensity provides appDensity) {
            ProvideSteamAvatarShape(content)
        }
    }
}
