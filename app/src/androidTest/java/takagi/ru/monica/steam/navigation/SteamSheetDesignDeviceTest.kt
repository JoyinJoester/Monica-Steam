package takagi.ru.monica.steam.navigation

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.foundation.ui.SteamAccountSwitcherSheet
import takagi.ru.monica.steam.friends.domain.*
import takagi.ru.monica.steam.friends.ui.SteamFriendAccountSheet
import takagi.ru.monica.steam.library.filter.domain.*
import takagi.ru.monica.steam.library.filter.ui.SteamLibraryFilterSheet
import takagi.ru.monica.steam.navigation.liquidglass.render.SteamLiquidGlassBackdrop
import takagi.ru.monica.steam.navigation.liquidglass.ui.*
import takagi.ru.monica.steam.store.filters.domain.*
import takagi.ru.monica.steam.store.filters.ui.SteamStoreAdvancedFilterSheet
import takagi.ru.monica.steam.store.gift.presentation.SteamStoreGiftUiState
import takagi.ru.monica.steam.store.gift.ui.SteamStoreGiftRecipientSheet
import takagi.ru.monica.steam.store.share.domain.SteamStoreGameShare
import takagi.ru.monica.steam.store.share.ui.SteamStoreGameShareSheet

/** Real production sheets, synthetic data and an offline test application; no account actions. */
class SteamSheetDesignDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private fun back() = instrument.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
    private var source: SteamLiquidGlassBackdrop? = null
    private fun text(id: Int) = rule.activity.getString(id)
    private val accounts = (1..30).map { index -> SteamAccount(
        id = index.toLong(), steamId = "765611980000000${index.toString().padStart(2, '0')}",
        accountName = "account$index", displayName = "Synthetic account $index",
        deviceId = "", sharedSecret = "", identitySecret = null, revocationCode = null, tokenGid = null,
        accessToken = null, refreshToken = null, steamLoginSecure = null, rawSteamGuardJson = "",
        selected = index == 1, sortOrder = index, createdAt = 0, updatedAt = 0)
    }
    private val friends = (1..80).map { index -> SteamFriend(
        steamId = "7656119800000${index.toString().padStart(4, '0')}",
        personaName = "Synthetic friend $index", relationship = SteamFriendRelationship.FRIEND,
        personaState = if (index == 1) SteamPersonaState.ONLINE else SteamPersonaState.OFFLINE)
    }

    private fun mount(dark: Boolean, liquid: Boolean, large: Boolean = false, effects: Boolean = true,
        content: @Composable () -> Unit) {
        rule.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalSteamDesignStyle provides if (liquid) SteamDockStyle.LIQUID_GLASS else SteamDockStyle.FIXED,
                    LocalSteamGlassEffectsEnabled provides effects,
                    LocalDensity provides Density(density.density, if (large) 1.5f else 1f)) {
                    SteamGlassHost(Modifier.fillMaxSize(), content = {
                        Canvas(Modifier.fillMaxSize()) {
                            for (x in 0..9) for (y in 0..15) drawRect(
                                if ((x+y) % 2 == 0) Color(0xFF6EAFE2) else Color(0xFFE5B889),
                                Offset(x*size.width/10, y*size.height/16), Size(size.width/10, size.height/16))
                        }
                    }, overlay = { source = LocalSteamGlassBackdrop.current; content() })
                }
            }
        }
        rule.waitForIdle()
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        SystemClock.sleep(350)
        val bitmap = instrument.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val file = File(rule.activity.filesDir, "sheet-design/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun library(dark: Boolean, liquid: Boolean, large: Boolean = false, effects: Boolean = true) {
        var applied: SteamLibraryFilterSelection? = null
        var dismissed = false
        val opened = mutableStateOf(true)
        mount(dark, liquid, large, effects) {
            if (opened.value) SteamLibraryFilterSheet(SteamLibraryFilterSelection(), 400,
                { if (it.ownership == SteamLibraryOwnershipFilter.OWNED) 128 else 400 },
                { applied = it }, { dismissed = true; opened.value = false })
        }
        rule.onNodeWithText(text(R.string.steam_library_filter_ownership_owned)).performScrollTo().performClick().assertIsSelected()
        rule.runOnIdle { assertNull(applied) }
        rule.onNodeWithText(rule.activity.getString(R.string.steam_library_filter_apply, 128)).assertIsDisplayed()
        capture("library-${dark}-${liquid}-${large}-${effects}")
        rule.onNodeWithText(rule.activity.getString(R.string.steam_library_filter_apply, 128)).performClick()
        rule.runOnIdle { assertEquals(SteamLibraryOwnershipFilter.OWNED, applied!!.ownership) }
        rule.onNodeWithContentDescription(text(R.string.close)).performClick()
        rule.runOnIdle { assertTrue(dismissed) }
        if (effects) rule.waitUntil(5000) { source?.consumers == 0 }
    }
    @Test fun libraryLiquidLight() = library(false, true)
    @Test fun libraryLiquidDark() = library(true, true)
    @Test fun libraryM3eLight() = library(false, false)
    @Test fun libraryM3eDarkLargeText() = library(true, false, true)
    @Test fun libraryFallbackLargeText() = library(false, true, true, false)

    @Test fun libraryResetAndScrollKeepDraftUnapplied() {
        var applied: SteamLibraryFilterSelection? = null
        mount(true, true, true) {
            SteamLibraryFilterSheet(SteamLibraryFilterSelection(), 400, { 400 }, { applied = it }, {})
        }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text(R.string.steam_library_sort_total_playtime)))
        rule.onNodeWithText(text(R.string.steam_library_sort_total_playtime)).performClick()
        rule.onNodeWithText(text(R.string.steam_library_filter_reset)).assertIsDisplayed().performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.steam_library_filter_apply, 400)).performClick()
        rule.runOnIdle { assertEquals(SteamLibraryFilterSelection(), applied) }
    }

    @Test fun storeFilterKeepsPendingChoicesUntilApply() {
        var applied: SteamStoreFilterSelection? = null
        val metadata = SteamStoreFilterMetadata(
            priceOptions = listOf(SteamStoreFilterOption("free", "Synthetic free")),
            languages = listOf(SteamStoreFilterOption("english", "Synthetic English")),
            tags = listOf(SteamStoreTagOption(1, "Synthetic strategy")))
        mount(true, true, true) {
            SteamStoreAdvancedFilterSheet(SteamStoreFilterSelection(), metadata, false, null, {}, { applied = it }, {})
        }
        rule.onNodeWithText("Synthetic free").performClick().assertIsSelected()
        rule.onNodeWithText("Synthetic English").performScrollTo().performClick().assertIsSelected()
        rule.runOnIdle { assertNull(applied) }
        capture("store-filters-large")
        rule.onNodeWithText(text(R.string.steam_store_filter_apply)).assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals("free", applied!!.maxPrice); assertEquals(setOf("english"), applied!!.supportedLanguageIds) }
    }

    private fun share(dark: Boolean, liquid: Boolean, large: Boolean = false) {
        var openedChat: String? = null; var external = 0; var refresh = 0
        mount(dark, liquid, large) {
            SteamStoreGameShareSheet(SteamStoreGameShare(440, "Synthetic game", "https://store.steampowered.com/app/440/"),
                SteamStoreGiftUiState(friends = friends), { openedChat = it.steamId }, { external++ }, { refresh++ }, {})
        }
        capture("share-${dark}-${liquid}-${large}")
        rule.onNodeWithText(text(R.string.steam_store_share_external)).assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, external); assertNull(openedChat) }
        rule.onNode(hasSetTextAction()).performClick().performTextInput("friend 80")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.waitForIdle()
        rule.onNodeWithText("Synthetic friend 80").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(friends.last().steamId, openedChat) }
        rule.onNodeWithContentDescription(text(R.string.refresh)).performClick()
        rule.runOnIdle { assertEquals(1, refresh) }
    }
    @Test fun sharingLiquidLight() = share(false, true)
    @Test fun sharingLiquidDarkLargeText() = share(true, true, true)
    @Test fun sharingM3eLight() = share(false, false)
    @Test fun sharingM3eDark() = share(true, false)

    @Test fun giftSelectionKeepsValidationNoteAndSelectedState() {
        var chosen: SteamFriend? = null
        mount(false, true, true) {
            SteamStoreGiftRecipientSheet(SteamStoreGiftUiState(friends = friends,
                pendingItem = takagi.ru.monica.steam.store.domain.SteamCartItem(440, name = "Synthetic game",
                    giftRecipient = takagi.ru.monica.steam.store.gift.domain.SteamStoreGiftRecipient(
                        friends.first().steamId, 1, friends.first().displayName))), { chosen = it }, {}, {})
        }
        rule.onNodeWithText(text(R.string.steam_store_gift_validation_note)).assertIsDisplayed()
        rule.onNodeWithText(friends.first().displayName).assertIsSelected()
        rule.onNode(hasSetTextAction()).performClick().performTextInput("friend 80")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.waitForIdle()
        capture("gift-large")
        rule.onNodeWithText(friends.last().displayName).performClick()
        rule.runOnIdle { assertEquals(friends.last(), chosen) }
    }

    @Test fun switcherScrollsAndAddAccountRemainsReachable() {
        var selected: Long? = null; var adds = 0; var dismisses = 0
        mount(true, true, true) {
            SteamAccountSwitcherSheet(accounts, 1, SteamStorageSource.Local, emptyList(), false, null,
                {}, { selected = it }, { adds++ }, {}, { dismisses++ })
        }
        rule.onNodeWithText(accounts.first().displayName).assertIsSelected()
        rule.onNodeWithText(text(R.string.steam_add_account_title)).assertIsDisplayed()
        capture("accounts-large")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(accounts.last().displayName))
        rule.onNodeWithText(accounts.last().displayName).performClick()
        rule.runOnIdle { assertEquals(30L, selected) }
        rule.onNodeWithText(text(R.string.steam_add_account_title)).performClick()
        rule.runOnIdle { assertEquals(1, adds); assertEquals(1, dismisses) }
    }

    @Test fun friendAccountPickerVirtualizesAndReachesLastAccount() {
        var selected: Long? = null
        mount(false, false, true) { SteamFriendAccountSheet(accounts, 1, { selected = it }, {}) }
        rule.onNodeWithText(accounts.first().displayName).assertIsSelected()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(accounts.last().displayName))
        rule.onNodeWithText(accounts.last().displayName).performClick()
        rule.runOnIdle { assertEquals(30L, selected) }
    }

}
