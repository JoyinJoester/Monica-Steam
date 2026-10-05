package takagi.ru.monica.steam.navigation

import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Collections
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.navigation.liquidglass.render.rememberSteamLiquidGlassBackdrop
import takagi.ru.monica.steam.navigation.liquidglass.render.steamLiquidGlassBackdropSource
import takagi.ru.monica.steam.navigation.liquidglass.render.isSteamLiquidGlassRuntimeSupported
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamLiquidGlassDock
import takagi.ru.monica.ui.theme.MonicaTheme

/** Repeatable offline transition workload; no accounts, disk or network in the frame loop. */
class SteamDockFrameTest {
    @Test fun repeatedDockSwitches() {
        val frames = Collections.synchronizedList(mutableListOf<Long>())
        var selected by mutableStateOf(SteamDockTab.STORE)
        val order = SteamDockTab.entries.toList()
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        lateinit var activity: ComponentActivity
        scenario.onActivity { host ->
        activity = host
        host.setContent {
            MonicaTheme {
                val backdrop = rememberSteamLiquidGlassBackdrop()
                Box(Modifier.fillMaxSize()) {
                    AnimatedContent(selected, modifier = Modifier.fillMaxSize().steamLiquidGlassBackdropSource(backdrop, isSteamLiquidGlassRuntimeSupported()),
                        transitionSpec = { steamDockPageTransition(order, initialState, targetState, false, false) }, label = "frame-test") { page ->
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
                            items(100, key = { it }) { i -> ListItem(headlineContent = { Text("${page.name} · $i") }, supportingContent = { Text("Repeated offline page switch") }) }
                        }
                    }
                    SteamLiquidGlassDock(order, selected, backdrop, { selected = it }, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        }
        Thread.sleep(1000)
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ -> frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION) }
        scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper())) }
        try {
            repeat(20) { index ->
                scenario.onActivity { selected = order[index % order.size] }
                Thread.sleep(450)
            }
        } finally {
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            scenario.close()
        }
        val measured = frames.toList().sorted()
        assertTrue("No frames recorded", measured.size > 30)
        fun percentile(p: Double) = measured[((measured.size - 1) * p).toInt()] / 1_000_000.0
        val summary = "frames=${measured.size},p50_ms=${percentile(.5)},p90_ms=${percentile(.9)},p95_ms=${percentile(.95)},over_32ms=${measured.count { it > 32_000_000 }}"
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "dock-frames.txt").writeText(summary)
        println(summary)
    }
}
