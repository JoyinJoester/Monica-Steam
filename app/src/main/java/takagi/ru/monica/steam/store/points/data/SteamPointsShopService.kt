package takagi.ru.monica.steam.store.points.data

import okhttp3.OkHttpClient
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamHttpClientProvider
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.store.data.effectiveSteamStoreAccessToken
import takagi.ru.monica.steam.store.points.domain.SteamPointsShopCategory
import takagi.ru.monica.steam.store.points.domain.SteamPointsShopPage

internal class SteamPointsShopService(
    client: OkHttpClient = SteamHttpClientProvider.client,
    private val api: SteamApiClient = SteamApiClient(client)
) {
    fun page(
        category: SteamPointsShopCategory,
        cursor: String? = null,
        language: String = "schinese",
        count: Int = 24,
        appId: Int? = null
    ): SteamPointsShopPage = parseSteamPointsShopPage(
        response = api.callProtobuf(
            iface = "ILoyaltyRewardsService",
            method = "BatchedQueryRewardItems",
            request = buildSteamPointsShopQuery(category, language, count, cursor, appId),
            useGet = true
        ),
        category = category
    )

    fun balance(account: SteamAccount): Long? {
        if (!account.hasRealSteamId) return null
        val token = effectiveSteamStoreAccessToken(account.accessToken, account.steamLoginSecure)
            ?: return null
        val request = SteamProtoWriter().apply {
            writeFixed64(1, account.steamId.toLong())
        }
        return parseSteamPointsBalance(
            api.callProtobuf(
                iface = "ILoyaltyRewardsService",
                method = "GetSummary",
                request = request,
                accessToken = token,
                useGet = true
            )
        )
    }
}
