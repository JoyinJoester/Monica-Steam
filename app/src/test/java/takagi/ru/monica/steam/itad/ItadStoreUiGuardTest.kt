package takagi.ru.monica.steam.itad

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.itad.domain.ItadMoney
import takagi.ru.monica.steam.itad.ui.formatItadMoney

class ItadStoreUiGuardTest {
    @Test
    fun legacyPriceQueryIsReplacedByKeylessSteamDb() {
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText() + projectFile("app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreRegionalPrices.kt").readText()
        val card = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/steamdb/ui/SteamDbSection.kt"
        ).readText()
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()

        assertFalse(store.contains("item(key = \"itad_history_low_"))
        assertTrue(store.contains("AnimatedVisibility("))
        assertTrue(store.contains("SteamDbQuery(appId, price.countryCode, price.currency"))
        assertTrue(store.contains("historyCountryCode = detail.accountCountryCode"))
        assertTrue(store.contains("SteamDbPriceSection("))
        assertFalse(store.contains("ItadHistoryLowSection("))
        assertFalse(store.contains("onOpenItadSettings"))
        assertTrue(card.contains("R.string.steamdb_price_source"))
        assertTrue(card.contains("https://steamdb.info/app/"))
        assertTrue(settings.contains("SteamDbSettingsScreen("))
        assertFalse(settings.contains("ItadSettingsScreen("))
    }

    @Test
    fun moneyFormattingKeepsItadCurrencyAndAmountWithoutConversion() {
        val formatted = formatItadMoney(
            ItadMoney(amount = 9.99, amountInt = 999, currency = "CNY"),
            Locale.US
        )

        assertEquals("CNY 9.99", formatted)
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, path)
    }
}
