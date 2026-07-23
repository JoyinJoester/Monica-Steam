package takagi.ru.monica.steam.trade

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamTradeOfferIntegrationGuardTest {
    @Test
    fun productionUsesAuthenticatedProtobufAndCommunityActions() {
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/trade/SteamTradeOfferService.kt"
        ).readText()

        assertTrue(service.contains("iface = \"IEconService\""))
        assertTrue(service.contains("method = \"GetTradeOffers\""))
        assertTrue(service.contains("useGet = true"))
        assertTrue(service.contains("/tradeoffer/${'$'}{offer.id}/accept"))
        assertTrue(service.contains("/tradeoffer/${'$'}{offer.id}/decline"))
        assertTrue(service.contains("/tradeoffer/${'$'}{offer.id}/cancel"))
        assertTrue(service.contains("SteamInventoryService.marketCookies"))
        assertFalse(service.contains("Web API Key"))
    }

    @Test
    fun tradeOffersHaveProductionSectionDetailsAndIdentityVerification() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val content = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/trade/ui/SteamTradeOffersContent.kt"
        ).readText()

        assertTrue(screen.contains("TRADE_OFFERS("))
        assertTrue(screen.contains("SteamTradeOffersContent("))
        assertTrue(screen.contains("pendingProtectedTradeOfferAction"))
        assertTrue(screen.contains("M3IdentityVerifyDialog("))
        assertTrue(screen.contains("viewModel.respondTradeOffer("))
        assertTrue(content.contains("SteamTradeOfferDetailSheet("))
        assertTrue(content.contains("SteamTradeOfferAction.ACCEPT"))
        assertTrue(content.contains("SteamTradeOfferAction.DECLINE"))
        assertTrue(content.contains("SteamTradeOfferAction.CANCEL"))
        assertTrue(content.contains("heightIn(min = 52.dp)"))
        assertFalse(content.contains("LazyRow("))
    }

    private fun projectFile(path: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, path)
    }
}
