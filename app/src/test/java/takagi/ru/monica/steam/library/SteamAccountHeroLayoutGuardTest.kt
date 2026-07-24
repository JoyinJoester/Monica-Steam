package takagi.ru.monica.steam.library

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamAccountHeroLayoutGuardTest {
    @Test
    fun heroUsesTwoLineIdentityAndContentDrivenHeight() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val hero = screen
            .substringAfter("private fun SteamAccountHeroCard(")
            .substringBefore("private fun SteamAccountDetail(")
        val tokens = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryLayoutTokens.kt"
        ).readText()

        assertTrue(tokens.contains("OverviewHeroMinHeight = 184.dp"))
        assertTrue(hero.contains("SteamLibraryLayoutTokens.OverviewHeroMinHeight"))
        assertTrue(hero.contains("compact = true"))
        assertTrue(hero.contains("maxLines = 2"))
        assertTrue(hero.contains("fontFamily = GoogleSansFlexFontFamily"))
        assertFalse(hero.contains(".aspectRatio(1.62f)"))
        assertFalse(hero.contains("style = MaterialTheme.typography.headlineSmall"))
    }

    @Test
    fun heroKeepsIdentityAndMetricsInSeparateLayoutGroups() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val hero = screen
            .substringAfter("private fun SteamAccountHeroCard(")
            .substringBefore("private fun SteamAccountDetail(")
        val metric = screen
            .substringAfter("private fun HeroMetric(")
            .substringBefore("private fun SteamGameSectionHeader(")

        assertTrue(hero.contains("verticalArrangement = Arrangement.SpaceBetween"))
        assertTrue(hero.contains("HeroMetric("))
        assertTrue(metric.contains("fontFeatureSettings = \"tnum\""))
        assertTrue(metric.contains("overflow = TextOverflow.Ellipsis"))
    }

    private fun projectFile(path: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, path)
    }
}
