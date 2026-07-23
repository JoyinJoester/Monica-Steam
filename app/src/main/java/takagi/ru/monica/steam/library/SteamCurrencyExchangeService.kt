package takagi.ru.monica.steam.library

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

internal data class SteamCnyExchangeRates(
    val unitsPerCny: Map<String, Double>,
    val fetchedAt: Long
)

class SteamCurrencyExchangeService(
    private val client: OkHttpClient = OkHttpClient(),
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    internal fun fetchCnyRates(): SteamCnyExchangeRates {
        val url = endpoint.toHttpUrl()
        require(url.scheme == "https" && url.host == "open.er-api.com") {
            "Invalid currency exchange endpoint"
        }
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "Monica-Steam/1.0")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Currency exchange request failed: ${response.code}" }
            return parseCnyRates(response.body?.string().orEmpty(), json)
        }
    }

    companion object {
        private const val DEFAULT_ENDPOINT = "https://open.er-api.com/v6/latest/CNY"

        internal fun parseCnyRates(
            raw: String,
            json: Json = Json { ignoreUnknownKeys = true }
        ): SteamCnyExchangeRates {
            val root = json.parseToJsonElement(raw).jsonObject
            require(root["result"]?.jsonPrimitive?.content == "success") {
                "Currency exchange response was not successful"
            }
            require(root["base_code"]?.jsonPrimitive?.content == "CNY") {
                "Currency exchange response used an unexpected base"
            }
            val rates = root["rates"]?.jsonObject.orEmpty()
                .mapNotNull { (currency, value) ->
                    val rate = value.jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                    if (!rate.isFinite() || rate <= 0.0) return@mapNotNull null
                    currency.uppercase() to rate
                }
                .toMap()
                .toMutableMap()
                .apply { put("CNY", 1.0) }
            require(rates.size > 1) { "Currency exchange response contained no rates" }
            val fetchedAt = root["time_last_update_unix"]?.jsonPrimitive?.longOrNull
                ?.times(1_000L)
                ?: System.currentTimeMillis()
            return SteamCnyExchangeRates(rates, fetchedAt)
        }
    }
}
