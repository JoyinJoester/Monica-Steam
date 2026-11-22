package takagi.ru.monica.steam.store

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.io.File
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.store.ui.SteamNativeCartScreen

class SteamNativeCartDesignDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val tab = mutableStateOf(SteamStoreCollectionTab.CART)
    private val items = mutableStateOf(listOf(SteamCartItem(431960, 109874, "Wallpaper Engine", finalPriceCents = 2200)))
    private var checkout = 0
    private var wishlistOpened = 0
    private fun text(id: Int) = compose.activity.getString(id)
    private fun mount(liquid: Boolean, large: Boolean) {
        compose.setContent { MaterialTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.5f else 1f),
                LocalSteamDesignStyle provides if (liquid) SteamDockStyle.LIQUID_GLASS else SteamDockStyle.FIXED) {
                SteamNativeCartScreen(items.value, listOf(SteamWishlistItem(620, "Portal 2")), tab.value,
                    false, false, null, { tab.value = it }, {}, {}, {}, {}, { checkout++ }, {}, { wishlistOpened = it })
            }
        } }
    }
    private fun frame(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Compose idleness precedes the Activity window's compositor transition.
        Thread.sleep(350)
        val committed = java.util.concurrent.CountDownLatch(1)
        instrumentation.runOnMainSync {
            val root = compose.activity.window.decorView
            root.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            root.invalidate()
        }
        assertTrue("Cart screenshot must use a committed window frame", committed.await(5, java.util.concurrent.TimeUnit.SECONDS))
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(instrumentation.targetContext.filesDir, "cart-design/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    private fun verify(liquid: Boolean, large: Boolean) {
        mount(liquid, large)
        val style = if (liquid) "glass" else "m3e"
        val size = if (large) "large" else "normal"
        compose.onAllNodesWithTag("steam-cart-checkout").assertCountEquals(1)
        compose.onNodeWithText("Wallpaper Engine").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.steam_store_checkout)).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(text(R.string.steam_store_security_note)).assertIsDisplayed()
        frame("$style-$size-cart")
        compose.onNodeWithText(text(R.string.steam_store_checkout)).performClick()
        assertEquals(1, checkout)
        compose.onNodeWithText(compose.activity.getString(R.string.steam_store_wishlist_tab, 1)).performClick()
        compose.onNodeWithTag("steam-cart-checkout").assertDoesNotExist()
        compose.onNodeWithText("Portal 2").assertIsDisplayed().performClick()
        assertEquals(620, wishlistOpened)
        frame("$style-$size-wishlist")
        compose.onNodeWithText(compose.activity.getString(R.string.steam_store_cart_tab, 1)).performClick()
        compose.onNodeWithTag("steam-cart-checkout").assertIsDisplayed()
        compose.runOnIdle { items.value = emptyList() }
        compose.onNodeWithTag("steam-cart-checkout").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.steam_store_cart_empty_title)).assertIsDisplayed()
    }
    @Test fun m3eNormal() = verify(false, false)
    @Test fun m3eLarge() = verify(false, true)
    @Test fun glassNormal() = verify(true, false)
    @Test fun glassLarge() = verify(true, true)
    @Test fun unresolvedPackageCannotCheckout() {
        items.value = listOf(SteamCartItem(620, name = "Synthetic unresolved package", finalPriceCents = 2200))
        mount(true, false)
        compose.onNodeWithText(text(R.string.steam_store_checkout)).assertIsNotEnabled()
        assertEquals(0, checkout)
    }
}
