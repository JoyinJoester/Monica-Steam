package takagi.ru.monica.steam.market

import kotlin.math.roundToInt

object SteamBatchPricing {
    fun resolveEntries(
        stacks: List<SteamInventoryItemStack>,
        mode: SteamBatchPriceMode,
        quotes: Map<String, SteamMarketQuote>,
        wallet: SteamWalletInfo,
        manualReceive: Int? = null,
        itemReceiveOverrides: Map<String, Int> = emptyMap()
    ): List<SteamBatchSellEntry> {
        return stacks.mapNotNull { stack ->
            val stackKey = stack.item.stackKey
            val receive = itemReceiveOverrides[stackKey]?.takeIf { it > 0 }
                ?: resolveSellerReceive(
                    mode = mode,
                    quote = quotes[stackKey],
                    wallet = wallet,
                    publisherFeePercent = stack.item.publisherFeePercent,
                    manualReceive = manualReceive
                )
                ?: return@mapNotNull null
            SteamBatchSellEntry(stack = stack, priceReceive = receive, quantity = 1)
        }
    }

    fun resolveSellerReceive(
        mode: SteamBatchPriceMode,
        quote: SteamMarketQuote?,
        wallet: SteamWalletInfo,
        publisherFeePercent: Double?,
        manualReceive: Int? = null
    ): Int? {
        if (mode == SteamBatchPriceMode.MANUAL) {
            return manualReceive?.takeIf { it > 0 }
        }
        val buyerTotal = when (mode) {
            SteamBatchPriceMode.LOWEST_LISTING -> {
                parseLocalizedPriceMinorUnits(quote?.price?.lowestPrice)
            }
            SteamBatchPriceMode.MEDIAN -> {
                parseLocalizedPriceMinorUnits(quote?.price?.medianPrice)
            }
            SteamBatchPriceMode.RECENT_HIGH -> {
                quote?.history?.maxOfOrNull { it.price }?.times(100.0)?.roundToInt()
            }
            SteamBatchPriceMode.RECENT_LOW -> {
                quote?.history?.minOfOrNull { it.price }?.times(100.0)?.roundToInt()
            }
            SteamBatchPriceMode.MANUAL -> null
        } ?: return null
        if (buyerTotal <= 0) return null
        return SteamMarketFees(wallet)
            .receiveFromTotal(buyerTotal, publisherFeePercent)
            .takeIf { it > 0 }
    }

    fun parseLocalizedPriceMinorUnits(raw: String?): Int? {
        val value = raw.orEmpty().trim()
        if (value.isEmpty()) return null
        val numeric = value.filter { it.isDigit() || it == '.' || it == ',' }
        if (numeric.none(Char::isDigit)) return null

        val lastDot = numeric.lastIndexOf('.')
        val lastComma = numeric.lastIndexOf(',')
        val separatorIndex = when {
            lastDot >= 0 && lastComma >= 0 -> maxOf(lastDot, lastComma)
            lastDot >= 0 -> decimalSeparatorIndex(numeric, lastDot)
            lastComma >= 0 -> decimalSeparatorIndex(numeric, lastComma)
            else -> -1
        }
        val wholeDigits: String
        val fractionDigits: String
        if (separatorIndex >= 0) {
            wholeDigits = numeric.substring(0, separatorIndex).filter(Char::isDigit)
            fractionDigits = numeric.substring(separatorIndex + 1).filter(Char::isDigit)
        } else {
            wholeDigits = numeric.filter(Char::isDigit)
            fractionDigits = ""
        }
        val whole = wholeDigits.ifEmpty { "0" }.toLongOrNull() ?: return null
        val fraction = fractionDigits.take(2).padEnd(2, '0').toIntOrNull() ?: 0
        val minor = whole * 100L + fraction
        return minor.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
    }

    private fun decimalSeparatorIndex(value: String, index: Int): Int {
        val digitsAfter = value.substring(index + 1).count(Char::isDigit)
        return if (digitsAfter in 1..2) index else -1
    }
}
