package takagi.ru.monica.steam.store

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.data.SteamStoreReviewParser
import takagi.ru.monica.steam.store.domain.SteamReviewSummary
import takagi.ru.monica.steam.store.domain.SteamStoreDetail
import takagi.ru.monica.steam.store.domain.SteamStoreReviews
import takagi.ru.monica.steam.store.domain.preserveCachedReviews
import takagi.ru.monica.steam.store.domain.steamReviewScore

class SteamStoreReviewTest {
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

        assertTrue(reviewService.contains("runCatching(request)"))
        assertTrue(reviewService.contains("appreviewhistogram"))
        assertTrue(reviewUi.contains("BoxWithConstraints"))
        assertTrue(reviewUi.contains("surfaceContainerLow"))
        assertTrue(reviewUi.contains("LinearProgressIndicator"))
        assertTrue(detailUi.contains("SteamStoreReviewSummarySection("))
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
