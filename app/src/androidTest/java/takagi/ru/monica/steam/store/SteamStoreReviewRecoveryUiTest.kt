package takagi.ru.monica.steam.store

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.store.data.SteamStoreReviewService
import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.store.ui.SteamStoreReviewsSection
import takagi.ru.monica.ui.theme.MonicaTheme

class SteamStoreReviewRecoveryUiTest {
    @get:Rule val compose = createAndroidComposeRule<SteamComposeFixtureActivity>()

    @Test fun failedFirstPageIsVisibleAndRetryRestoresNativeReviews() {
        var reviews by mutableStateOf(SteamStoreReviews(pageLoadFailed = true))
        var retries = 0
        compose.setContent { MonicaTheme { Surface { Column {
            SteamStoreReviewsSection(620, reviews, SteamReviewFilterSelection(), false, null,
                onFiltersChanged = {}, onLoadMore = {
                    retries++
                    reviews = SteamStoreReviews(items = listOf(SteamUserReview("fixture", body = "Recovered review body", votedUp = true)))
                }, onOpenAuthor = {})
        } } } }
        compose.onNodeWithText(compose.activity.getString(R.string.steam_store_reviews_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.steam_store_reviews_empty)).assertDoesNotExist()
        capture("failed")
        compose.onNodeWithText(compose.activity.getString(R.string.steam_store_reviews_retry)).performClick()
        compose.onNodeWithText("Recovered review body").assertIsDisplayed()
        assertEquals(1, retries)
        capture("recovered")
        compose.onNodeWithText(compose.activity.getString(R.string.steam_store_reviews_unavailable)).assertDoesNotExist()
    }

    @Test fun successfulEmptyPageShowsEmptyStateWithoutRetry() {
        compose.setContent { MonicaTheme { Surface { Column {
            SteamStoreReviewsSection(620, SteamStoreReviews(), SteamReviewFilterSelection(), false, null,
                onFiltersChanged = {}, onLoadMore = { error("No next page") }, onOpenAuthor = {})
        } } } }
        compose.onNodeWithText(compose.activity.getString(R.string.steam_store_reviews_empty)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.steam_store_reviews_retry)).assertDoesNotExist()
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("reviewCapture") != "true") return
        val directory = java.io.File(compose.activity.getExternalFilesDir(null), "review-recovery").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(directory, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** Explicit public, signed-out endpoint check; routine device tests stay offline. */
    @Test fun publicReviewServiceReturnsBodiesAndNextPage() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSteamReviews") == "true")
        val service = SteamStoreReviewService(OkHttpClient.Builder()
            .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build())
        val first = service.fetchPage(620)
        assertTrue(first.items.isNotEmpty())
        val second = service.fetchPage(620, cursor = requireNotNull(first.nextCursor))
        assertTrue(second.items.isNotEmpty())
        assertTrue(first.items.map { it.recommendationId }.intersect(second.items.map { it.recommendationId }.toSet()).isEmpty())
        println("PUBLIC_REVIEWS app=620 first=${first.items.size} second=${second.items.size} disjoint=true")
    }
}
