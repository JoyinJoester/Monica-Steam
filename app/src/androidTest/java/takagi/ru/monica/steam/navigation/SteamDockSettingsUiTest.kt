package takagi.ru.monica.steam.navigation

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.SteamFixedBottomBar
import takagi.ru.monica.ui.screens.SteamDockOrderScreen
import takagi.ru.monica.ui.theme.MonicaTheme
import java.io.File

class SteamDockSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fixedIsSelectedAndOnlyTwoStylesAreAvailable() {
        var style by mutableStateOf(SteamDockStyle.fromStoredValue("M3E"))
        compose.setContent {
            MonicaTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        SteamDockOrderScreen(
                            onNavigateBack = {},
                            style = style,
                            onStyleChange = { style = it },
                            modifier = Modifier.fillMaxSize()
                        )
                        SteamFixedBottomBar(
                            order = SteamDockTab.FIXED_DEFAULT_ORDER,
                            selected = SteamDockTab.SETTINGS,
                            onSelected = {},
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        val fixed = compose.activity.getString(R.string.steam_dock_style_fixed)
        val glass = compose.activity.getString(R.string.steam_dock_style_liquid_glass)
        compose.onNodeWithText(fixed).assertIsSelected().assertIsDisplayed()
        compose.onNodeWithText("M3E").assertDoesNotExist()
        compose.onNodeWithText(glass).performClick()
        compose.runOnIdle { assertEquals(SteamDockStyle.LIQUID_GLASS, style) }
        compose.onNodeWithText(glass).assertIsSelected()
        compose.onNodeWithText(fixed).performClick()
        compose.onNodeWithText(fixed).assertIsSelected()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.filesDir, "dock-fixed.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
