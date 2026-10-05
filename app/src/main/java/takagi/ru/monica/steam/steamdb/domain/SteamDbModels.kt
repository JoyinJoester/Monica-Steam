package takagi.ru.monica.steam.steamdb.domain

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

data class SteamDbQuery(val appId: Int, val country: String?, val currency: String?, val free: Boolean = false) {
    val priceRegion: String? get() = steamDbPriceRegion(country, currency)
}

data class SteamDbAppInfo(
    val currentPlayers: Long?, val peakToday: Long?, val peakAll: Long?,
    val followers: Long?, val updatedAt: Long?
)

data class SteamDbLowestPrice(
    val price: String, val discount: Int?, val twoYearLow: String?, val occurrences: Long?, val lastAt: Long?
)

data class SteamDbRating(val positive: Long, val negative: Long) {
    val total: Long get() = positive + negative
    val score: Double? get() {
        if (positive < 0 || negative < 0 || total <= 0) return null
        val average = positive.toDouble() / total
        return 100 * (average - (average - 0.5) * 2.0.pow(-log10(total.toDouble() + 1)))
    }
}

enum class SteamDbFailure { NETWORK, SERVICE, RATE_LIMITED, NO_DATA, UNKNOWN_REGION, FREE }

sealed interface SteamDbResult<out T> {
    data object Loading : SteamDbResult<Nothing>
    data class Ready<T>(val value: T, val fetchedAt: Long, val cached: Boolean = false, val stale: Boolean = false) : SteamDbResult<T>
    data class Failed(val reason: SteamDbFailure, val retryAt: Long? = null, val statusCode: Int? = null) : SteamDbResult<Nothing>
}

data class SteamDbState(
    val info: SteamDbResult<SteamDbAppInfo> = SteamDbResult.Loading,
    val players: SteamDbResult<Long> = SteamDbResult.Loading,
    val rating: SteamDbResult<SteamDbRating> = SteamDbResult.Loading,
    val price: SteamDbResult<SteamDbLowestPrice> = SteamDbResult.Loading
)

// Regional USD markets are distinct Steam price histories. Never infer them from device language.
// Mapping and rating formula adapted from Millennium; see assets/licenses/millennium.txt.
fun steamDbPriceRegion(country: String?, currency: String?): String? {
    val region = country?.trim()?.uppercase(Locale.ROOT)?.takeIf { it in countries } ?: return null
    val code = currency?.trim()?.uppercase(Locale.ROOT)?.takeIf { it in currencies } ?: return null
    if (code != "USD") return code
    return usdRegions.entries.firstOrNull { region in it.value }?.key ?: "USD"
}

private val countries = Locale.getISOCountries().toSet()
private val currencies = ("USD GBP EUR CHF RUB PLN BRL JPY NOK IDR MYR PHP SGD THB VND KRW TRY UAH " +
    "MXN CAD AUD NZD CNY INR CLP PEN COP ZAR HKD TWD SAR AED SEK ARS ILS BYN KZT KWD QAR CRC UYU " +
    "BGN HRK CZK DKK HUF RON").split(' ').toSet()
private val usdRegions = mapOf(
    "USD-CIS" to "AZ AM BY GE KG MD TJ TM UZ".split(' '),
    "USD-SASIA" to "BD BT NP PK LK".split(' '),
    "USD-LATAM" to "AR BO BZ EC GT GY HN NI PA PY SR SV VE".split(' '),
    "USD-MENA" to "BH DZ EG IQ JO LB LY MA OM PS SD TN TR YE".split(' ')
)
