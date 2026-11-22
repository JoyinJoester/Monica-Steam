package takagi.ru.monica.steam.store.purchase.domain

import takagi.ru.monica.steam.store.domain.SteamStoreDetail

internal data class SteamPurchaseOffer(val priceCents: Int?, val discountPercent: Int, val endEpochSeconds: Long?) {
    fun expired(nowSeconds: Long): Boolean = endEpochSeconds?.let { it <= nowSeconds } == true
}

/** Match the displayed game price, never a free pass or an unrelated edition. */
internal fun steamPurchaseOffer(detail: SteamStoreDetail): SteamPurchaseOffer {
    val matching = detail.packageOptions.filter {
        !it.isFreeLicense && !it.canGetFreeLicense && it.priceCents == detail.finalPriceCents &&
            it.discountPercent == detail.discountPercent
    }
    val expiry = matching.mapNotNull { it.discountEndEpochSeconds }.distinct().singleOrNull()
    return SteamPurchaseOffer(detail.finalPriceCents, detail.discountPercent, expiry)
}

internal data class SteamDiscountRemaining(val unit: Unit, val amount: Long) {
    enum class Unit { DAYS, HOURS, MINUTES }
}
internal fun steamDiscountRemaining(endSeconds: Long?, nowSeconds: Long): SteamDiscountRemaining? {
    val remaining = (endSeconds ?: return null) - nowSeconds
    if (remaining <= 0) return null
    return when {
        remaining >= 86400 -> SteamDiscountRemaining(SteamDiscountRemaining.Unit.DAYS, remaining / 86400)
        remaining >= 3600 -> SteamDiscountRemaining(SteamDiscountRemaining.Unit.HOURS, remaining / 3600)
        else -> SteamDiscountRemaining(SteamDiscountRemaining.Unit.MINUTES, (remaining + 59) / 60)
    }
}
