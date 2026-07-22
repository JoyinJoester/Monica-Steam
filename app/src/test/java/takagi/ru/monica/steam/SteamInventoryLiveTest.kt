package takagi.ru.monica.steam

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.market.SteamInventoryService
import takagi.ru.monica.steam.network.SteamApiClient

class SteamInventoryLiveTest {
    @Test
    fun realMaFileEligibilityCheckReturnsInventoryOverview() {
        val maFilePath = System.getenv("STEAM_LIVE_MAFILE").orEmpty()
        val maFile = File(maFilePath)
        assumeTrue("STEAM_LIVE_MAFILE is required for this live test", maFile.isFile)

        val root = Json.parseToJsonElement(maFile.readText()).jsonObject
        val session = root["Session"] as? JsonObject
        val steamId = root.stringValue("steamid").ifBlank {
            session.stringValue("SteamID")
        }
        val accessToken = root.stringValue("access_token").ifBlank {
            session.stringValue("AccessToken")
        }
        val refreshToken = root.stringValue("refresh_token").ifBlank {
            session.stringValue("RefreshToken")
        }
        val steamLoginSecure = root.stringValue("steamLoginSecure").ifBlank {
            session.stringValue("SteamLoginSecure")
        }
        val account = SteamAccount(
            id = 1L,
            steamId = steamId,
            accountName = "live-test",
            displayName = "live-test",
            deviceId = root.stringValue("device_id"),
            sharedSecret = root.stringValue("shared_secret"),
            identitySecret = root.stringValue("identity_secret").takeIf { it.isNotBlank() },
            revocationCode = root.stringValue("revocation_code").takeIf { it.isNotBlank() },
            tokenGid = root.stringValue("token_gid").takeIf { it.isNotBlank() },
            accessToken = accessToken.takeIf { it.isNotBlank() },
            refreshToken = refreshToken.takeIf { it.isNotBlank() },
            steamLoginSecure = steamLoginSecure.takeIf { it.isNotBlank() },
            rawSteamGuardJson = "",
            selected = true,
            sortOrder = 0,
            createdAt = 0L,
            updatedAt = 0L
        )

        assertTrue("maFile must contain a real Steam ID", account.hasRealSteamId)
        assertTrue("maFile must contain steamLoginSecure", !account.steamLoginSecure.isNullOrBlank())
        val html = SteamApiClient().communityGetText(
            path = "/market/eligibilitycheck/",
            query = mapOf("goto" to "/profiles/${account.steamId}/inventory/"),
            cookies = SteamInventoryService.marketCookies(
                account = account,
                sessionId = SteamInventoryService.newSessionId()
            )
        )
        val overview = SteamInventoryService.parseOverviewHtml(html)
        val directOverview = SteamInventoryService().fetchOverview(account)
        assertTrue("inventory HTML must contain app context data", html.contains("g_rgAppContextData"))
        assertTrue("wallet currency must be available", overview.wallet.currency > 0)
        assertTrue("direct inventory overview must load", directOverview.wallet.currency > 0)
        println("Steam live inventory reads completed: games=${directOverview.games.size}")
    }

    private fun JsonObject?.stringValue(key: String): String {
        return this?.get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
    }
}
