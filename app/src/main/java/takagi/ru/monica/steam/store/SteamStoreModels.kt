package takagi.ru.monica.steam.store

import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data class SteamStoreItem(
    val appId: Int,
    val name: String,
    val imageUrl: String = "",
    val headerImageUrl: String = "",
    val currency: String = "CNY",
    val initialPriceCents: Int? = null,
    val finalPriceCents: Int? = null,
    val discountPercent: Int = 0,
    val windows: Boolean = false,
    val mac: Boolean = false,
    val linux: Boolean = false,
    val metascore: Int? = null
) {
    val isFree: Boolean get() = finalPriceCents == 0
    val formattedFinalPrice: String get() = formatSteamPrice(finalPriceCents, currency)
    val formattedInitialPrice: String get() = formatSteamPrice(initialPriceCents, currency)
}

@Serializable
data class SteamStoreHome(
    val specials: List<SteamStoreItem> = emptyList(),
    val topSellers: List<SteamStoreItem> = emptyList(),
    val newReleases: List<SteamStoreItem> = emptyList(),
    val comingSoon: List<SteamStoreItem> = emptyList(),
    val fetchedAt: Long = System.currentTimeMillis()
)

@Serializable
data class SteamStoreDetail(
    val appId: Int,
    val name: String,
    val type: String = "game",
    val shortDescription: String = "",
    val about: String = "",
    val headerImageUrl: String = "",
    val backgroundImageUrl: String = "",
    val screenshots: List<String> = emptyList(),
    val developers: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val releaseDate: String = "",
    val currency: String = "CNY",
    val initialPriceCents: Int? = null,
    val finalPriceCents: Int? = null,
    val discountPercent: Int = 0,
    val isFree: Boolean = false,
    val windows: Boolean = false,
    val mac: Boolean = false,
    val linux: Boolean = false,
    val packageId: Int? = null
) {
    val formattedFinalPrice: String
        get() = if (isFree) "免费" else formatSteamPrice(finalPriceCents, currency)
    val formattedInitialPrice: String get() = formatSteamPrice(initialPriceCents, currency)
    val storeUrl: String get() = "https://store.steampowered.com/app/$appId/"
}

@Serializable
data class SteamCartItem(
    val appId: Int,
    val packageId: Int? = null,
    val name: String,
    val imageUrl: String = "",
    val currency: String = "CNY",
    val initialPriceCents: Int? = null,
    val finalPriceCents: Int? = null,
    val discountPercent: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
) {
    val formattedPrice: String get() = formatSteamPrice(finalPriceCents, currency)
}

@Serializable
data class SteamWishlistItem(
    val appId: Int,
    val name: String,
    val imageUrl: String = "",
    val packageId: Int? = null,
    val discountPercent: Int = 0,
    val formattedInitialPrice: String = "",
    val formattedFinalPrice: String = "",
    val priority: Int = 0,
    val addedAtEpochSeconds: Long = 0L
)

@Serializable
data class SteamWishlistSnapshot(
    val items: List<SteamWishlistItem> = emptyList(),
    val fetchedAt: Long = System.currentTimeMillis()
)

enum class SteamStoreCollectionTab {
    CART,
    WISHLIST
}

internal fun SteamStoreDetail.toWishlistItem(nowEpochSeconds: Long = System.currentTimeMillis() / 1000L) =
    SteamWishlistItem(
        appId = appId,
        name = name,
        imageUrl = headerImageUrl,
        packageId = packageId,
        discountPercent = discountPercent,
        formattedInitialPrice = formattedInitialPrice,
        formattedFinalPrice = formattedFinalPrice,
        addedAtEpochSeconds = nowEpochSeconds
    )

internal fun steamCartCheckoutPackageIds(items: List<SteamCartItem>): List<Int> =
    items.mapNotNull { it.packageId }.distinct()

internal fun steamCartTotalCents(items: List<SteamCartItem>): Int =
    items.mapNotNull { it.finalPriceCents }.sum()

internal fun formatSteamPrice(value: Int?, currency: String): String {
    if (value == null) return "—"
    if (value == 0) return "免费"
    val amount = value / 100.0
    return when (currency.uppercase(Locale.US)) {
        "CNY" -> String.format(Locale.CHINA, "¥%.2f", amount)
        "USD" -> String.format(Locale.US, "$%.2f", amount)
        "EUR" -> String.format(Locale.US, "€%.2f", amount)
        "GBP" -> String.format(Locale.UK, "£%.2f", amount)
        "TWD" -> String.format(Locale.TAIWAN, "NT$%.2f", amount)
        "HKD" -> String.format(Locale.US, "HK$%.2f", amount)
        "SGD" -> String.format(Locale.US, "S$%.2f", amount)
        "CAD" -> String.format(Locale.CANADA, "C$%.2f", amount)
        "AUD" -> String.format(Locale.US, "A$%.2f", amount)
        "JPY" -> String.format(Locale.JAPAN, "¥%.0f", amount)
        "KRW" -> String.format(Locale.KOREA, "₩%.0f", amount)
        "RUB" -> String.format(Locale.US, "₽%.2f", amount)
        "INR" -> String.format(Locale.US, "₹%.2f", amount)
        "BRL" -> String.format(Locale.US, "R$%.2f", amount)
        "PLN" -> String.format(Locale.US, "%.2f zł", amount)
        "TRY" -> String.format(Locale.US, "₺%.2f", amount)
        "UAH" -> String.format(Locale.US, "₴%.2f", amount)
        "THB" -> String.format(Locale.US, "฿%.2f", amount)
        "VND" -> String.format(Locale.US, "₫%.0f", amount)
        "IDR" -> String.format(Locale.US, "Rp %.0f", amount)
        "MYR" -> String.format(Locale.US, "RM %.2f", amount)
        "PHP" -> String.format(Locale.US, "₱%.2f", amount)
        "MXN" -> String.format(Locale.US, "Mex$%.2f", amount)
        "CLP" -> String.format(Locale.US, "CLP$%.0f", amount)
        "COP" -> String.format(Locale.US, "COL$%.0f", amount)
        "PEN" -> String.format(Locale.US, "S/ %.2f", amount)
        else -> String.format(Locale.US, "%s %.2f", currency, amount)
    }
}
