package takagi.ru.monica.steam.store.domain

import kotlinx.serialization.Serializable

enum class SteamReviewSentimentFilter(val apiValue: String) {
    ALL("all"),
    POSITIVE("positive"),
    NEGATIVE("negative")
}

enum class SteamReviewTimeFilter(
    val apiFilter: String,
    val dayRangeDays: Int? = null
) {
    ALL_TIME("all"),
    RECENT_30_DAYS("recent", dayRangeDays = 30)
}

data class SteamReviewFilterSelection(
    val sentiment: SteamReviewSentimentFilter = SteamReviewSentimentFilter.ALL,
    val time: SteamReviewTimeFilter = SteamReviewTimeFilter.ALL_TIME
) {
    val isDefault: Boolean
        get() = sentiment == SteamReviewSentimentFilter.ALL &&
            time == SteamReviewTimeFilter.ALL_TIME
}

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
    val items: List<SteamUserReview> = emptyList(),
    val nextCursor: String? = null,
    val pageLoadFailed: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis()
)

@Serializable
data class SteamUserReview(
    val recommendationId: String,
    val authorSteamId: String = "",
    val body: String,
    val votedUp: Boolean,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val playtimeForeverMinutes: Int = 0,
    val playtimeAtReviewMinutes: Int = 0,
    val playtimeLastTwoWeeksMinutes: Int = 0,
    val votesUp: Int = 0,
    val votesFunny: Int = 0,
    val steamPurchase: Boolean = false,
    val receivedForFree: Boolean = false,
    val writtenDuringEarlyAccess: Boolean = false,
    val language: String = ""
)

data class SteamReviewPage(
    val summary: SteamReviewSummary? = null,
    val items: List<SteamUserReview> = emptyList(),
    val nextCursor: String? = null
)

internal fun SteamStoreDetail.preserveCachedReviews(cached: SteamStoreDetail?): SteamStoreDetail {
    if (cached == null) return this
    var result = if (
        relatedDlc.isEmpty() && dlcAppIds.isNotEmpty() && cached.relatedDlc.isNotEmpty()
    ) {
        copy(relatedDlc = cached.relatedDlc.filter { it.appId in dlcAppIds })
    } else {
        this
    }
    if (result.bundles.isEmpty() && cached.bundles.isNotEmpty()) {
        result = result.copy(bundles = cached.bundles)
    }
    if (result.packageOptions.isNotEmpty() && cached.packageOptions.isNotEmpty()) {
        val cachedPackages = cached.packageOptions.associateBy { it.packageId }
        result = result.copy(
            packageOptions = result.packageOptions.map { option ->
                val cachedOption = cachedPackages[option.packageId]
                if (option.imageUrl.isBlank() && cachedOption?.imageUrl?.isNotBlank() == true) {
                    option.copy(imageUrl = cachedOption.imageUrl)
                } else {
                    option
                }
            }
        )
    }
    val cachedReviews = cached.reviews ?: return result
    val freshReviews = result.reviews ?: return result.copy(reviews = cachedReviews)
    val mergedReviews = freshReviews.copy(
        overall = freshReviews.overall ?: cachedReviews.overall,
        recent = freshReviews.recent ?: cachedReviews.recent,
        items = if (freshReviews.pageLoadFailed) cachedReviews.items else freshReviews.items,
        nextCursor = if (freshReviews.pageLoadFailed) cachedReviews.nextCursor else freshReviews.nextCursor
    )
    return if (mergedReviews == freshReviews) result else result.copy(reviews = mergedReviews)
}

internal fun SteamStoreReviews.mergePage(page: SteamReviewPage): SteamStoreReviews = copy(
    overall = overall ?: page.summary,
    items = (items + page.items).distinctBy(SteamUserReview::recommendationId),
    nextCursor = page.nextCursor,
    pageLoadFailed = false,
    fetchedAt = System.currentTimeMillis()
)

internal fun steamReviewScore(positive: Int, negative: Int): Int {
    val total = positive + negative
    if (total <= 0) return 0
    val percent = positive.toDouble() / total.toDouble() * 100.0
    return when {
        percent >= 95.0 && total >= 500 -> 9
        percent >= 80.0 -> 8
        percent >= 70.0 -> 6
        percent >= 40.0 -> 5
        percent >= 20.0 -> 4
        total < 50 -> 3
        total >= 500 -> 1
        else -> 2
    }
}
