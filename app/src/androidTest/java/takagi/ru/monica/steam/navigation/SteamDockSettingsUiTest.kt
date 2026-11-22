package takagi.ru.monica.steam.navigation

import android.graphics.Bitmap
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.SteamFixedBottomBar
import takagi.ru.monica.ui.screens.SteamDesignStyleScreen
import takagi.ru.monica.ui.screens.SteamDockOrderScreen
import takagi.ru.monica.ui.theme.MonicaTheme
import java.io.File

class SteamDockSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<SteamComposeFixtureActivity>()

    @Test
    fun designStyleOffersOnlyTwoChoicesAndSwitchesBetweenThem() {
        var style by mutableStateOf(SteamDockStyle.fromStoredValue(null))
        compose.setContent {
            MonicaTheme {
                Surface(Modifier.fillMaxSize().testTag("design-style-settings-test-root")) {
                    Box(Modifier.fillMaxSize()) {
                        SteamDesignStyleScreen(
                            style = style,
                            onStyleChange = { style = it },
                            onNavigateBack = {},
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
        val m3e = compose.activity.getString(R.string.steam_dock_style_fixed)
        val glass = compose.activity.getString(R.string.steam_dock_style_liquid_glass)
        assertEquals("M3E", m3e)
        compose.onNodeWithText(m3e).assertIsDisplayed()
        compose.onNodeWithTag("steam-design-style-FIXED").assertIsSelected()
        compose.onNodeWithTag("steam-design-style-TELEGRAM").assertDoesNotExist()
        compose.onNodeWithText(glass).performClick()
        compose.runOnIdle { assertEquals(SteamDockStyle.LIQUID_GLASS, style) }
        compose.onNodeWithTag("steam-design-style-LIQUID_GLASS").assertIsSelected()
        compose.onNodeWithText(m3e).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SteamDockStyle.FIXED, style) }
        compose.onNodeWithTag("steam-design-style-FIXED").assertIsSelected()
        val bitmap = compose.onNodeWithTag("design-style-settings-test-root")
            .captureToImage().asAndroidBitmap()
        File(compose.activity.filesDir, "design-style-m3e.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun bottomNavigationPageOnlyOrdersTheCurrentStyle() {
        compose.setContent {
            MonicaTheme {
                Surface(Modifier.fillMaxSize()) {
                    SteamDockOrderScreen(
                        onNavigateBack = {},
                        style = SteamDockStyle.LIQUID_GLASS,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        val activity = compose.activity
        compose.onNodeWithText(activity.getString(R.string.steam_dock_order_description)).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.steam_dock_token)).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.steam_dock_style_fixed)).assertDoesNotExist()
        compose.onNodeWithText(activity.getString(R.string.steam_dock_style_liquid_glass)).assertDoesNotExist()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(activity.filesDir, "dock-order.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun fixedNavigationPageShowsCustomOrderAndAllFiveDestinations() {
        compose.setContent {
            MonicaTheme {
                Surface(Modifier.fillMaxSize()) {
                    SteamDockOrderScreen(
                        onNavigateBack = {},
                        style = SteamDockStyle.FIXED,
                        fixedOrder = listOf(SteamDockTab.SETTINGS, SteamDockTab.CHAT,
                            SteamDockTab.TOKEN, SteamDockTab.STORE, SteamDockTab.LIBRARY),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        listOf(R.string.settings_title, R.string.steam_chat_title, R.string.steam_dock_token,
            R.string.steam_store_title, R.string.steam_library_title).forEach {
            compose.onNodeWithText(compose.activity.getString(it)).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText(compose.activity.getString(R.string.steam_dock_style_fixed)).assertDoesNotExist()
    }
}
