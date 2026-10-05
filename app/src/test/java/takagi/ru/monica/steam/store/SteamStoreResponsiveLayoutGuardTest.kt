package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreResponsiveLayoutGuardTest {

    @Test
    fun priceTokensWrapBetweenItemsInsteadOfInsideCurrencyText() {
        val priceRow = storeSource()
            .substringAfter("private fun PriceRow(")
            .substringBefore("@Composable private fun CachedNotice")

        assertTrue(priceRow.contains("FlowRow("))
        assertTrue(priceRow.contains("softWrap = false"))
        assertTrue(priceRow.contains("maxLines = 1"))
    }

    private fun storeSource(): String = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
    ).readText()

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
