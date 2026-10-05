package takagi.ru.monica.ui.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.MdbxEngineType
import takagi.ru.monica.ui.theme.MonicaTheme

class SteamMdbxManagementUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun createAndOpenStayVisibleAfterScrollingLargeTextGrid() {
        val actions = mutableListOf<String>()
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            val base = LocalContext.current
            val config = Configuration(base.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }
            CompositionLocalProvider(LocalContext provides base.createConfigurationContext(config), LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MonicaTheme {
                    MdbxSourceManagementPage(MdbxManagerSource.LOCAL,
                        (1..14).map { LocalMdbxDatabase(id = it.toLong(), name = "Steam 账号库 $it", filePath = "fixture", sourceType = "LOCAL_INTERNAL", engineType = if (it == 1) MdbxEngineType.KOTLIN_MDBX1.name else MdbxEngineType.RUST_MDBX2.name) },
                        emptyMap(), emptyMap(), { actions += "create" }, { actions += "open" }, { actions += "vault:${it.id}" })
                }
            }
        }
        screenshot("mdbx-grid-normal")
        compose.runOnIdle { fontScale = 1.5f }
        screenshot("mdbx-grid")
        compose.onNodeWithTag("mdbx_database_grid").performScrollToIndex(13)
        compose.onNodeWithTag("mdbx_open").assertIsDisplayed().performClick()
        compose.onNodeWithTag("mdbx_create").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("open", "create"), actions) }
    }
    @Test fun retiredPageOnlyOffersUpgradeAndRemoval() {
        val actions = mutableListOf<String>()
        compose.setContent { MonicaTheme { MdbxRetiredVaultPage({ actions += "upgrade" }, { actions += "remove" }) } }
        compose.onNodeWithTag("mdbx_legacy_unavailable").assertIsDisplayed()
        screenshot("mdbx-legacy")
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Activity window transitions are outside Compose's test clock.
        Thread.sleep(500)
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(instrumentation.targetContext.filesDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
