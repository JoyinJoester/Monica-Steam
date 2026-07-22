package takagi.ru.monica.steam.market

import kotlin.math.floor
import kotlin.math.roundToInt

class SteamMarketFees(
    private val wallet: SteamWalletInfo
) {
    fun toValidMarketPrice(price: Int): Int {
        val minimum = wallet.marketMinimum
        val increment = wallet.currencyIncrement.coerceAtLeast(1)
        if (price <= minimum) return minimum
        if (price <= increment) return increment
        if (increment > 1) return (price.toDouble() / increment).roundToInt() * increment
        return price
    }

    fun buyerPays(receive: Int, publisherFeePercent: Double? = null): Int {
        val publisherPercent = publisherFeePercent ?: wallet.publisherFeePercent
        return totalWithFees(receive, publisherPercent)
    }

    fun receiveFromTotal(total: Int, publisherFeePercent: Double? = null): Int {
        val publisherPercent = publisherFeePercent ?: wallet.publisherFeePercent
        val increment = wallet.currencyIncrement.coerceAtLeast(1)
        val minimum = wallet.marketMinimum
        val initialGuess = floor(
            total / (1.0 + publisherPercent + wallet.steamFeePercent)
        ).toInt()
        val maxBase = total - (2 * minimum)
        var base = toValidMarketPrice(minOf(initialGuess, maxBase))
        for (attempt in 0 until 3) {
            val calculated = totalWithFees(base, publisherPercent)
            when {
                calculated == total -> return base
                calculated < total -> base += increment
                else -> {
                    base -= increment
                    break
                }
            }
        }
        return base.coerceAtLeast(minimum)
    }

    fun breakdown(receive: Int, publisherFeePercent: Double? = null): SteamMarketFeeBreakdown {
        val publisherPercent = publisherFeePercent ?: wallet.publisherFeePercent
        val steamFee = fee(receive, wallet.steamFeePercent)
        val publisherFee = fee(receive, publisherPercent)
        return SteamMarketFeeBreakdown(
            receive = receive,
            steamFee = steamFee,
            publisherFee = publisherFee,
            buyerPays = toValidMarketPrice(receive) + steamFee + publisherFee
        )
    }

    private fun totalWithFees(receive: Int, publisherPercent: Double): Int {
        return toValidMarketPrice(receive) +
            fee(receive, publisherPercent) +
            fee(receive, wallet.steamFeePercent)
    }

    private fun fee(baseAmount: Int, percent: Double): Int {
        return if (percent > 0.0) {
            toValidMarketPrice(floor(baseAmount * percent).toInt())
        } else {
            0
        }
    }
}
