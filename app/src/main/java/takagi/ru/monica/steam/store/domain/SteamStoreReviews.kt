package takagi.ru.monica.steam.store.domain

import kotlinx.serialization.Serializable

@Serializable
data class SteamReviewSummary(
    val score: Int = 0,
    val positive: Int = 0,
    val negative: Int = 0,
    val total: Int = positive + negative
) {
    val positivePercent: Int
        get() = if (total <= 0) {
            0
        } else {
            ((positive.toLong() * 100L + total / 2L) / total).toInt().coerceIn(0, 100)
        }
}

@Serializable
data class SteamStoreReviews(
    val overall: SteamReviewSummary? = null,
    val recent: SteamReviewSummary? = null,
    val fetchedAt: Long = System.currentTimeMillis()
)

internal fun SteamStoreDetail.preserveCachedReviews(cached: SteamStoreDetail?): SteamStoreDetail {
    val cachedReviews = cached?.reviews ?: return this
    val freshReviews = reviews ?: return copy(reviews = cachedReviews)
    return copy(
        reviews = freshReviews.copy(
            overall = freshReviews.overall ?: cachedReviews.overall,
            recent = freshReviews.recent ?: cachedReviews.recent
        )
    )
}

internal fun steamReviewScore(positive: Int, negative: Int): Int {
    val total = positive + negative
    if (total <= 0) return 0
    val percent = positive.toDouble() / total.toDouble() * 100.0
    return when {
        percent >= 95.0 && total >= 500 -> 9
        percent >= 80.0 && total >= 50 -> 8
        percent >= 80.0 -> 7
        percent >= 70.0 -> 6
        percent >= 40.0 -> 5
        percent >= 20.0 -> 4
        total < 50 -> 3
        total >= 500 -> 1
        else -> 2
    }
}
