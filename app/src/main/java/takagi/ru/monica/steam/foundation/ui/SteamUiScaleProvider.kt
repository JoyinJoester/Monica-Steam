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

private val LocalSteamUiScale = staticCompositionLocalOf {
    SteamUiScaleOption.DEFAULT
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
    val scale by preferences.scale.collectAsState(initial = SteamUiScaleOption.DEFAULT)
    val baseDensity = LocalDensity.current
    val appDensity = remember(baseDensity.density, baseDensity.fontScale, scale) {
        Density(
            density = calculateSteamUiDensity(baseDensity.density, scale),
            fontScale = baseDensity.fontScale
        )
    }

    CompositionLocalProvider(
        LocalDensity provides appDensity,
        LocalSteamUiScale provides scale
    ) {
        content()
    }
}

/**
 * Keeps high-density page content readable when the large scale is selected.
 * Navigation surfaces intentionally stay outside this provider so their touch
 * targets can retain the user's preferred larger size.
 */
@Composable
internal fun ProvideSteamContentDensity(content: @Composable () -> Unit) {
    val scale = LocalSteamUiScale.current
    val appDensity = LocalDensity.current
    val contentDensity = remember(appDensity.density, appDensity.fontScale, scale) {
        Density(
            density = calculateSteamContentDensity(appDensity.density, scale),
            fontScale = appDensity.fontScale
        )
    }

    CompositionLocalProvider(LocalDensity provides contentDensity) {
        content()
    }
}

internal fun calculateSteamContentDensity(
    scaledDensity: Float,
    scale: SteamUiScaleOption
): Float {
    val contentFactor = scale.factor.coerceAtMost(1f)
    return (scaledDensity / scale.factor * contentFactor).coerceAtLeast(0.1f)
}
