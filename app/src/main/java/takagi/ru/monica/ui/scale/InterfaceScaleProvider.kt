package takagi.ru.monica.ui.scale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import takagi.ru.monica.data.InterfaceScale

@Composable
fun ProvideMonicaInterfaceScale(
    scalePercent: Int,
    content: @Composable () -> Unit
) {
    val baseDensity = LocalDensity.current
    val normalizedPercent = InterfaceScale.normalizePercent(scalePercent)
    val appDensity = remember(
        baseDensity.density,
        baseDensity.fontScale,
        normalizedPercent
    ) {
        Density(
            density = InterfaceScale.calculateDensity(
                baseDensity = baseDensity.density,
                percent = normalizedPercent
            ),
            fontScale = baseDensity.fontScale
        )
    }

    CompositionLocalProvider(LocalDensity provides appDensity) {
        content()
    }
}
