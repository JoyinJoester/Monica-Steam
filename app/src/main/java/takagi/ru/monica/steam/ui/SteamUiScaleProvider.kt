package takagi.ru.monica.steam.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

internal fun ComponentActivity.setSteamUiScaledContent(content: @Composable () -> Unit) {
    setContent {
        ProvideSteamUiScale(content)
    }
}

@Composable
internal fun ProvideSteamUiScale(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { SteamUiScalePreferences(context) }
    val scale by preferences.scale.collectAsState(initial = SteamUiScaleOption.DEFAULT)
    val baseDensity = LocalDensity.current
    val appDensity = remember(baseDensity.density, baseDensity.fontScale, scale) {
        Density(
            density = calculateSteamUiDensity(baseDensity.density, scale),
            fontScale = baseDensity.fontScale
        )
    }

    CompositionLocalProvider(LocalDensity provides appDensity) {
        content()
    }
}
