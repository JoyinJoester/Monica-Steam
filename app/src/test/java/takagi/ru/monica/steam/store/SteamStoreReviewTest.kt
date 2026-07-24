package takagi.ru.monica.steam.store

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.data.SteamStoreReviewParser
import takagi.ru.monica.steam.store.domain.SteamReviewSummary
import takagi.ru.monica.steam.store.domain.SteamStoreDetail
import takagi.ru.monica.steam.store.domain.SteamStoreReviews
import takagi.ru.monica.steam.store.domain.SteamReviewPage
import takagi.ru.monica.steam.store.domain.SteamUserReview
import takagi.ru.monica.steam.store.domain.mergePage
import takagi.ru.monica.steam.store.domain.preserveCachedReviews
import takagi.ru.monica.steam.store.domain.steamReviewScore

class SteamStoreReviewTest {
    @Test
    fun parsesNativeReviewBodiesAuthorMetadataAndCursor() {
        val page = SteamStoreReviewParser.parsePage(
            """{
              "query_summary": {
                "review_score": 8,
                "total_positive": 80,
                "total_negative": 20,
                "total_reviews": 100
              },
              "reviews": [{
                "recommendationid": "98765",
                "author": {
                  "steamid": "76561198000000001",
                  "playtime_forever": 720,
                  "playtime_at_review": 600,
                  "playtime_last_two_weeks": 120
                },
                "language": "schinese",
                "review": "内容很扎实，值得推荐。",
                "timestamp_created": 1700000000,
                "timestamp_updated": 1700000100,
                "voted_up": true,
                "votes_up": 18,
                "votes_funny": 2,
                "steam_purchase": true,
                "received_for_free": false,
                "written_during_early_access": true
              }],
              "cursor": "next-page-token"
            }""".trimIndent()
        )

        assertEquals(8, page.summary?.score)
        assertEquals("next-page-token", page.nextCursor)
        val review = page.items.single()
        assertEquals("98765", review.recommendationId)
        assertEquals("内容很扎实，值得推荐。", review.body)
        assertTrue(review.votedUp)
        assertEquals(720, review.playtimeForeverMinutes)
        assertEquals(120, review.playtimeLastTwoWeeksMinutes)
        assertEquals(18, review.votesUp)
        assertTrue(review.steamPurchase)
        assertTrue(review.writtenDuringEarlyAccess)
    }

    @Test
    fun reviewPagesMergeWithoutDuplicatingRecommendations() {
        val first = SteamUserReview("1", body = "First", votedUp = true)
        val updated = first.copy(body = "First updated")
        val second = SteamUserReview("2", body = "Second", votedUp = false)
        val merged = SteamStoreReviews(items = listOf(first), nextCursor = "page-2")
            .mergePage(SteamReviewPage(items = listOf(updated, second), nextCursor = "page-3"))

        assertEquals(listOf("1", "2"), merged.items.map { it.recommendationId })
        assertEquals("First", merged.items.first().body)
        assertEquals("page-3", merged.nextCursor)
    }

    @Test
    fun parsesLifetimeSummaryAndRecentHistogramSeparately() {
        val overall = SteamStoreReviewParser.parseOverall(
            """{"query_summary":{"review_score":6,"total_positive":161869,"total_negative":43575,"total_reviews":205444}}"""
        )
        val recent = SteamStoreReviewParser.parseRecent(
            """{"results":{"recent":[{"recommendations_up":12,"recommendations_down":3},{"recommendations_up":13,"recommendations_down":2}]}}"""
        )

        assertEquals(6, overall?.score)
        assertEquals(79, overall?.positivePercent)
        assertEquals(25, recent?.positive)
        assertEquals(5, recent?.negative)
        assertEquals(83, recent?.positivePercent)
        assertEquals(8, recent?.score)
    }

    @Test
    fun oldDetailCacheRemainsCompatibleAndCachedReviewsSurvivePartialRefresh() {
        val oldDetail = Json { ignoreUnknownKeys = true }.decodeFromString<SteamStoreDetail>(
            """{"appId":620,"name":"Portal 2"}"""
        )
        assertNull(oldDetail.reviews)

        val reviews = SteamStoreReviews(
            overall = SteamReviewSummary(score = 8, positive = 80, negative = 20, total = 100)
        )
        val cached = oldDetail.copy(reviews = reviews)
        val refreshed = oldDetail.copy(name = "Portal 2 refreshed")
            .preserveCachedReviews(cached)
        assertSame(reviews, refreshed.reviews)
        assertSame(reviews, cached.preserveCachedReviews(refreshed).reviews)

        val recent = SteamReviewSummary(score = 6, positive = 70, negative = 30, total = 100)
        val partialRefresh = oldDetail.copy(reviews = SteamStoreReviews(recent = recent))
            .preserveCachedReviews(cached)
        assertSame(reviews.overall, partialRefresh.reviews?.overall)
        assertSame(recent, partialRefresh.reviews?.recent)
    }

    @Test
    fun scoreBandsAndUiRemainCompactAndSemantic() {
        assertEquals(9, steamReviewScore(950, 50))
        assertEquals(5, steamReviewScore(55, 45))
        assertEquals(2, steamReviewScore(10, 90))

        val reviewService = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/data/SteamStoreReviewService.kt"
        ).readText()
        val reviewUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreReviewSummary.kt"
        ).readText()
        val detailUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val reviewListUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreReviewList.kt"
        ).readText()

        assertTrue(reviewService.contains("runCatching(request)"))
        assertTrue(reviewService.contains("appreviewhistogram"))
        assertTrue(reviewUi.contains("BoxWithConstraints"))
        assertTrue(reviewUi.contains("surfaceContainerLow"))
        assertTrue(reviewUi.contains("LinearProgressIndicator"))
        assertTrue(detailUi.contains("SteamStoreReviewsSection("))
        assertTrue(reviewListUi.contains("SteamStoreReviewSummarySection("))
        assertTrue(reviewListUi.contains("SteamStoreReviewCard("))
        assertTrue(reviewListUi.contains("heightIn(min = 48.dp)"))
    }

    @Test
    fun nativeReviewContentIsTheLastStoreDetailSection() {
        val detailUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()

        val informationIndex = detailUi.indexOf("R.string.steam_store_information")
        val reviewsIndex = detailUi.lastIndexOf("SteamStoreReviewsSection(")
        assertTrue(informationIndex >= 0)
        assertTrue(reviewsIndex > informationIndex)
        assertFalse(detailUi.contains("openStoreWeb(detail.reviewsUrl)"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
