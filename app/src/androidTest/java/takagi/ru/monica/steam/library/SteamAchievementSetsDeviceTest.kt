package takagi.ru.monica.steam.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.core.RustSteamCoreNative
import takagi.ru.monica.steam.library.ui.SteamGameDetail
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.R
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import takagi.ru.monica.steam.profile.viewer.domain.buildSteamAchievementComparison
import takagi.ru.monica.steam.profile.viewer.presentation.SteamProfileViewerUiState
import takagi.ru.monica.steam.profile.viewer.ui.SteamProfileAchievementComparisonScreen
import java.io.File

class SteamAchievementSetsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()

    private fun setTab(name: String) = rule.onNode(hasText(name, substring = true) and
        SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected))

    @Test fun nativeBridgePreservesMembershipArchiveAndHiddenFlags() {
        val definitions = SteamProtoWriter().apply { writeMessage(1, SteamProtoWriter().apply {
            writeString(1, "ARCHIVED"); writeString(2, "Old task"); writeVarint(8, 1)
            writeVarint(11, 0xffffffffL); writeBool(12, true); writeBool(6, true)
        }) }
        val bytes = requireNotNull(RustSteamCoreNative.parseAchievementDetailsOrNull(definitions.toByteArray(), byteArrayOf()))
        assertEquals("MSA2", bytes.take(4).toByteArray().toString(Charsets.US_ASCII))
        val item = requireNotNull(RustAchievementDetailsParser.decodeForTest(bytes)).single()
        assertEquals(0xffffffffL, item.groupId)
        assertTrue(item.archived); assertTrue(item.hidden)
    }

    @Test fun realGameDetailCombinesCategoryAndStatusFilters() {
        val base = SteamAchievement("base", "Base task", "", true, null, null, null)
        val dlc = base.copy(apiName = "dlc", displayName = "DLC task", achieved = false, groupId = 7)
        val data = SteamGameAchievements(1, 730, "Fixture", listOf(base, dlc), 0,
            listOf(SteamAchievementSet(0, "Base", isCompleted = true), SteamAchievementSet(7, "Expansion", dlcAppId = 731)))
        rule.setContent { MaterialTheme {
            SteamGameDetail(SteamGame(730, "Fixture", 0, 0), "US", null, null, data,
                false, false, null, {}, {}, {}, {}, {}, {}, {}, Modifier.fillMaxSize())
        } }
        setTab("Expansion").performScrollTo().performClick().assertIsSelected()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("DLC task"))
        rule.onNodeWithText("DLC task").assertIsDisplayed()
        rule.onNodeWithText("Base task").assertDoesNotExist()
        val changeFilter = rule.activity.getString(R.string.steam_library_change_filter)
        val completed = rule.activity.getString(R.string.steam_library_completed)
        val all = rule.activity.getString(R.string.steam_library_achievement_all)
        rule.onNodeWithContentDescription(changeFilter).performScrollTo().performClick()
        rule.onNodeWithText(completed, substring = false).performClick()
        rule.onNodeWithText("DLC task").assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.steam_library_no_completed_achievements))
            .performScrollTo().assertIsDisplayed()
        setTab("Base").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithText("Base task").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("DLC task").assertDoesNotExist()
        rule.onNodeWithContentDescription(changeFilter).performScrollTo().performClick()
        rule.onNodeWithText(all, substring = false).performClick()
        setTab("Expansion").performScrollTo().performClick()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("DLC task"))
        rule.onNodeWithText("DLC task").assertIsDisplayed()
    }

    @Test fun largeFontComparisonFiltersSetsAndConcealsLockedHiddenDetails() {
        val hidden = SteamAchievement("secret", "Secret title", "Secret description", false, null, null, null, hidden = true)
        val dlc = hidden.copy(apiName = "dlc", displayName = "Expansion task", hidden = false, groupId = 7)
        val data = SteamGameAchievements(1, 730, "Fixture", listOf(hidden, dlc), 0,
            listOf(SteamAchievementSet(0, "Base"), SteamAchievementSet(7, "Expansion", dlcAppId = 731)))
        val comparison = buildSteamAchievementComparison("1", "2", data, data)
        rule.setContent { MaterialTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                SteamProfileAchievementComparisonScreen(SteamProfileViewerUiState(achievementComparison = comparison),
                    SteamGame(730, "Fixture", 0, 0), {}, {})
                }
            }
        } }
        setTab("Expansion").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithText("Expansion task").performScrollTo().assertIsDisplayed()
        setTab("Base").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithText(rule.activity.getString(R.string.steam_achievement_hidden)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Secret title", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Secret description", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Secret title", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Expansion task").assertDoesNotExist()
        setTab("Base").performScrollTo().assertIsDisplayed()
        rule.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(rule.activity.getExternalFilesDir(null), "setglass-performance/achievement-sets-large.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
