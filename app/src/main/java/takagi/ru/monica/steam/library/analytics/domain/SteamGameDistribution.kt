package takagi.ru.monica.steam.library.analytics.domain

import takagi.ru.monica.steam.library.SteamGame

enum class SteamGameDistributionMode {
    PLAYTIME,
    PRICE
}

enum class SteamGameDistributionRange {
    UNPLAYED,
    UNDER_ONE_HOUR,
    ONE_TO_THREE_HOURS,
    THREE_TO_TEN_HOURS,
    TEN_TO_THIRTY_HOURS,
    THIRTY_TO_HUNDRED_HOURS,
    OVER_HUNDRED_HOURS,
    FREE,
    PRICE_UNDER_25,
    PRICE_25_TO_50,
    PRICE_50_TO_100,
    PRICE_100_TO_200,
    PRICE_200_TO_400,
    PRICE_OVER_400,
    PRICE_UNKNOWN
}

data class SteamGameDistributionBucket(
    val range: SteamGameDistributionRange,
    val gameCount: Int
)

fun steamGameDistribution(
    games: List<SteamGame>,
    mode: SteamGameDistributionMode
): List<SteamGameDistributionBucket> {
    val ranges = when (mode) {
        SteamGameDistributionMode.PLAYTIME -> listOf(
            SteamGameDistributionRange.UNPLAYED,
            SteamGameDistributionRange.UNDER_ONE_HOUR,
            SteamGameDistributionRange.ONE_TO_THREE_HOURS,
            SteamGameDistributionRange.THREE_TO_TEN_HOURS,
            SteamGameDistributionRange.TEN_TO_THIRTY_HOURS,
            SteamGameDistributionRange.THIRTY_TO_HUNDRED_HOURS,
            SteamGameDistributionRange.OVER_HUNDRED_HOURS
        )
        SteamGameDistributionMode.PRICE -> listOf(
            SteamGameDistributionRange.FREE,
            SteamGameDistributionRange.PRICE_UNDER_25,
            SteamGameDistributionRange.PRICE_25_TO_50,
            SteamGameDistributionRange.PRICE_50_TO_100,
            SteamGameDistributionRange.PRICE_100_TO_200,
            SteamGameDistributionRange.PRICE_200_TO_400,
            SteamGameDistributionRange.PRICE_OVER_400,
            SteamGameDistributionRange.PRICE_UNKNOWN
        )
    }
    val counts = games.groupingBy { game -> rangeFor(game, mode) }.eachCount()
    return ranges.map { range -> SteamGameDistributionBucket(range, counts[range] ?: 0) }
}

private fun rangeFor(game: SteamGame, mode: SteamGameDistributionMode): SteamGameDistributionRange {
    if (mode == SteamGameDistributionMode.PLAYTIME) {
        return when (game.playtimeForeverMinutes) {
            0 -> SteamGameDistributionRange.UNPLAYED
            in 1 until 60 -> SteamGameDistributionRange.UNDER_ONE_HOUR
            in 60 until 180 -> SteamGameDistributionRange.ONE_TO_THREE_HOURS
            in 180 until 600 -> SteamGameDistributionRange.THREE_TO_TEN_HOURS
            in 600 until 1_800 -> SteamGameDistributionRange.TEN_TO_THIRTY_HOURS
            in 1_800 until 6_000 -> SteamGameDistributionRange.THIRTY_TO_HUNDRED_HOURS
            else -> SteamGameDistributionRange.OVER_HUNDRED_HOURS
        }
    }

    val price = game.price?.takeIf { it.isAvailable }?.originalPriceMinor
        ?: return SteamGameDistributionRange.PRICE_UNKNOWN
    return when (price) {
        0L -> SteamGameDistributionRange.FREE
        in 1L until 2_500L -> SteamGameDistributionRange.PRICE_UNDER_25
        in 2_500L until 5_000L -> SteamGameDistributionRange.PRICE_25_TO_50
        in 5_000L until 10_000L -> SteamGameDistributionRange.PRICE_50_TO_100
        in 10_000L until 20_000L -> SteamGameDistributionRange.PRICE_100_TO_200
        in 20_000L until 40_000L -> SteamGameDistributionRange.PRICE_200_TO_400
        else -> SteamGameDistributionRange.PRICE_OVER_400
    }
}
