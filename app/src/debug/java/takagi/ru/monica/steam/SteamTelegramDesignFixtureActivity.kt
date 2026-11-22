package takagi.ru.monica.steam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.ColorScheme
import takagi.ru.monica.steam.design.telegram.*
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle

/** Offline chat controls only; no global design selection, account or network. */
class SteamTelegramDesignFixtureActivity : ComponentActivity() {
    var fixtureDesignStyle by mutableStateOf(SteamDockStyle.LIQUID_GLASS)
    var fixtureFontScale by mutableFloatStateOf(1f)
    var searchExpanded by mutableStateOf(false)
    var redrawVersion by mutableIntStateOf(0)
    var probeMode by mutableStateOf(false)
    var probeVariant by mutableIntStateOf(0)
    var contentMounts = 0
    var contentUnmounts = 0
    var searchSubmissions = 0
    var submittedQuery = ""
    var observedDraft = ""
    var observedRedrawVersion = -1
    var observedProbeVariant = -1
    var basePalette: androidx.compose.material3.ColorScheme? = null
    var actualPalette: androidx.compose.material3.ColorScheme? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fixtureFontScale),
                LocalSteamDesignStyle provides fixtureDesignStyle) {
                MonicaTheme(darkTheme = false, colorScheme = ColorScheme.FOREST_GREEN) {
                    val hostPalette = MaterialTheme.colorScheme
                    SideEffect { basePalette = hostPalette }
                    SteamTelegramTheme { FixtureBody() }
                }
            }
        }
    }

    @Composable private fun FixtureBody() {
        val palette = MaterialTheme.colorScheme
        SideEffect { actualPalette = palette }
        SteamTelegramGlassHost(
            modifier = Modifier.fillMaxSize().background(palette.background),
            content = {
                var draft by rememberSaveable { mutableStateOf("") }
                val currentDraft = draft
                val currentVariant = probeVariant
                val currentRedraw = redrawVersion
                DisposableEffect(Unit) { contentMounts++; onDispose { contentUnmounts++ } }
                SideEffect {
                    observedDraft = currentDraft
                    observedRedrawVersion = currentRedraw
                    observedProbeVariant = currentVariant
                }
                if (probeMode) {
                    Canvas(Modifier.fillMaxSize()) {
                        val first = if (currentVariant == 0) Color.Red else Color.Green
                        val second = if (currentVariant == 0) Color.Blue else Color.Yellow
                        drawRect(first, size = Size(size.width / 2f, size.height))
                        drawRect(second, topLeft = Offset(size.width / 2f, 0f), size = Size(size.width / 2f, size.height))
                    }
                } else {
                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        SteamTelegramTopBar("Offline chat $currentRedraw")
                        if (searchExpanded) TelegramSearchField(currentDraft, { draft = it },
                            "Search synthetic conversations", Modifier.padding(12.dp), autoFocus = true,
                            onSearch = { searchSubmissions++; submittedQuery = draft })
                    }
                }
            },
            overlay = {
                if (probeMode) SteamTelegramGlassSurface(
                    Modifier.align(Alignment.Center).size(220.dp, 120.dp), cornerRadius = 24.dp) {}
            }
        )
    }
}
