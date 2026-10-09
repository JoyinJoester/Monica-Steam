package takagi.ru.monica.steam.store.domain

import java.util.Locale
import java.math.BigDecimal
import java.text.NumberFormat
import takagi.ru.monica.steam.library.SteamRegionalPrice

internal fun normalizedStoreCountry(country: String?): String? = country?.trim()
    ?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 && it.all { char -> char in 'A'..'Z' } }

internal fun storeRegionFlag(country: String): String {
    val code = when (val normalized = normalizedStoreCountry(country)) {
        "HK", "TW" -> "CN"
        null -> return ""
        else -> normalized
    }
    return code.map { String(Character.toChars(0x1F1E6 + it.code - 'A'.code)) }.joinToString("")
}

/** Steam amounts use hundredths even for zero-decimal display currencies. */
internal fun storeLocalPriceLabel(currency: String, minor: Long, locale: Locale = Locale.getDefault()): String {
    val code = currency.uppercase(Locale.ROOT)
    val digits = if (code in setOf("JPY", "KRW", "VND", "IDR", "CLP", "COP")) 0 else 2
    val amount = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(BigDecimal.valueOf(minor.coerceAtLeast(0), 2))
    return "$code $amount"
}

internal fun storeRegionalPriceCountries(currentCountry: String?, defaults: List<String>): List<String> =
    (listOfNotNull(normalizedStoreCountry(currentCountry)) + defaults.mapNotNull(::normalizedStoreCountry)).distinct()

internal fun storePricesContainCurrentRegion(prices: List<SteamRegionalPrice>, currentCountry: String?): Boolean =
    normalizedStoreCountry(currentCountry)?.let { current ->
        prices.any { normalizedStoreCountry(it.countryCode) == current }
    } ?: true

/** Account region first, followed by comparable prices, then unavailable regions. */
internal fun sortedStoreRegionalPrices(prices: List<SteamRegionalPrice>, currentCountry: String?): List<SteamRegionalPrice> {
    val current = normalizedStoreCountry(currentCountry)
    return prices.sortedWith(compareBy<SteamRegionalPrice> {
        if (current != null && normalizedStoreCountry(it.countryCode) == current) 0 else 1
    }.thenBy { if (it.isAvailable) 0 else 1 }
        .thenBy { if (it.isAvailable) it.cnyFinalPriceMinor ?: Long.MAX_VALUE else Long.MAX_VALUE }
        .thenBy { normalizedStoreCountry(it.countryCode).orEmpty() })
}
