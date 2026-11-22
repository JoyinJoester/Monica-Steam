package takagi.ru.monica.steam.store

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.library.SteamAchievement
import takagi.ru.monica.steam.library.ui.AchievementItem
import takagi.ru.monica.steam.profile.SteamMiniProfileDecor
import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.store.ui.*
import takagi.ru.monica.ui.theme.MonicaTheme

class SteamReviewAuthorIconsUiTest {
    @get:Rule val compose = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val id = "76561198000000031"
    private val other = "76561198000000032"
    private val avatarUrl = "https://avatars.steamstatic.com/monica-author-fixture.jpg"
    private var oldConfig: Configuration? = null
    private val fixtureFiles = mutableListOf<File>()
    private var pending: CompletableDeferred<Unit>? = null
    @After fun cleanup() {
        pending?.complete(Unit)
        fixtureFiles.forEach { it.delete() }
        oldConfig?.let { config -> compose.activityRule.scenario.onActivity {
            @Suppress("DEPRECATION")
            it.resources.updateConfiguration(config, it.resources.displayMetrics)
        } }
    }
    private fun launch(large: Boolean = false, locale: Locale = Locale.ENGLISH, content: @Composable () -> Unit) {
        compose.activityRule.scenario.onActivity {
            oldConfig = Configuration(it.resources.configuration)
            @Suppress("DEPRECATION")
            it.resources.updateConfiguration(Configuration(it.resources.configuration).apply {
                setLocale(locale); fontScale = if (large) 1.5f else 1f
            }, it.resources.displayMetrics)
        }
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(
                d.density * (compose.activity.resources.displayMetrics.widthPixels / d.density / 360f), if (large) 1.5f else 1f)) {
                MonicaTheme(darkTheme = large) { Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(12.dp)) { content() } } }
            }
        }
    }
    private fun cache(url: String, color: Int) {
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(compose.activity.cacheDir, "steam_library_images/$hash.img").apply { parentFile!!.mkdirs() }
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle(); fixtureFiles += file
    }
    private fun waitColor(tag: String, color: Int) {
        compose.waitUntil(5000) {
            val b = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
            val actual = b.getPixel(b.width / 2, b.height / 2)
            kotlin.math.abs(Color.red(actual) - Color.red(color)) < 12 &&
                kotlin.math.abs(Color.green(actual) - Color.green(color)) < 12 &&
                kotlin.math.abs(Color.blue(actual) - Color.blue(color)) < 12
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle(); android.os.SystemClock.sleep(350)
        val b = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "author-icons/$name.png").apply {
            parentFile!!.mkdirs(); outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }; b.recycle()
    }
    @Test fun cachedPublicAuthorNameAndAvatarOpenTheSameProfile() {
        cache(avatarUrl, Color.rgb(220, 95, 40))
        compose.activity.getSharedPreferences("steam_mini_profile_decor_metadata", 0).edit()
            .putLong("${id}_fetched_at", System.currentTimeMillis()).putString("${id}_persona_name", "Alex fixture")
            .putString("${id}_avatar_url", avatarUrl).commit()
        var opened = ""
        launch(locale = Locale.SIMPLIFIED_CHINESE) {
            SteamStoreReviewsSection(620, SteamStoreReviews(items = listOf(SteamUserReview("1", id,
                "合作解谜很有趣，每个关卡都值得和朋友一起尝试。", true,
                createdAt = 1793836800L, playtimeForeverMinutes = 13701, playtimeAtReviewMinutes = 10548))),
                SteamReviewFilterSelection(), false, null, {}, {}, { opened = it })
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Alex fixture").fetchSemanticsNodes().isNotEmpty() }
        waitColor("review_avatar_$id", Color.rgb(220, 95, 40))
        capture("author-light")
        compose.onNodeWithText("Alex fixture").performClick(); assertEquals(id, opened)
        opened = ""; compose.onNodeWithContentDescription("Alex fixture", useUnmergedTree = true).performClick(); assertEquals(id, opened)
    }
    @Test fun unavailableIdentityStillOpensTheCorrectAuthor() {
        var opened = ""
        launch { SteamReviewAuthor(id, { opened = it }, loader = { null }) }
        compose.onNodeWithText(id).performClick(); assertEquals(id, opened)
    }
    @Test fun invalidAuthorDoesNotFetchOrNavigate() {
        var calls = 0
        launch { SteamReviewAuthor("bad/id", { calls++ }, loader = { calls++; null }) }
        compose.onNodeWithTag("review_author_bad/id").assertIsNotEnabled(); assertEquals(0, calls)
    }
    @Test fun latePreviousAuthorCannotReplaceTheNewIdentity() {
        var current by mutableStateOf(id)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); pending = release
        launch { SteamReviewAuthor(current, {}, loader = {
            if (it == id) withContext(NonCancellable) { entered.complete(Unit); release.await(); SteamMiniProfileDecor("Old author", null, null) }
            else SteamMiniProfileDecor("New author", null, null)
        }) }
        compose.waitUntil(5000) { entered.isCompleted }
        compose.runOnIdle { current = other }
        compose.onNodeWithText("New author").assertIsDisplayed()
        compose.runOnIdle { release.complete(Unit) }; compose.waitForIdle()
        compose.onNodeWithText("Old author").assertDoesNotExist(); compose.onNodeWithText("New author").assertIsDisplayed()
    }
    @Test fun largeReviewCardKeepsNameAvatarBodyAndProfileClick() {
        cache(avatarUrl, Color.rgb(60, 140, 200))
        var opened = ""
        launch(large = true) {
            SteamStoreReviewsSection(620, SteamStoreReviews(items = listOf(SteamUserReview("1", id,
                "Cooperative experiments are excellent.", false, createdAt = 1793836800L,
                playtimeForeverMinutes = 13701, playtimeAtReviewMinutes = 10548))), SteamReviewFilterSelection(), false, null,
                {}, {}, { opened = it }, authorLoader = { SteamMiniProfileDecor("Alex cooperative adventures", null, null, avatarUrl) })
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Alex cooperative adventures").fetchSemanticsNodes().isNotEmpty() }
        waitColor("review_avatar_$id", Color.rgb(60, 140, 200))
        compose.onNodeWithText("Cooperative experiments are excellent.").assertIsDisplayed(); capture("review-dark-large")
        compose.onNodeWithText("Alex cooperative adventures").performClick(); assertEquals(id, opened)
    }
    @Test fun achievementFilenamesAndMissingGrayRenderActualImages() {
        val base = "https://community.cloudflare.steamstatic.com/steamcommunity/public/images/apps/620/"
        cache(base + "normal.jpg", Color.rgb(210, 130, 20)); cache(base + "gray.jpg", Color.rgb(50, 160, 210))
        launch {
            AchievementItem(SteamAchievement("done", "Completed experiment", "A real icon", true, null, "normal.jpg", "gray.jpg"), 620)
            AchievementItem(SteamAchievement("locked", "Next experiment", "Gray icon", false, null, "normal.jpg", "gray.jpg"), 620)
            AchievementItem(SteamAchievement("fallback", "Another experiment", "Normal image fallback", false, null, "normal.jpg", null), 620)
        }
        waitColor("achievement_icon_0_done", Color.rgb(210, 130, 20))
        compose.onNodeWithContentDescription("Next experiment", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Another experiment", useUnmergedTree = true).assertExists()
        capture("achievements-light")
    }
}
