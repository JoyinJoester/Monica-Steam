package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreUiPolishGuardTest {
    @Test
    fun storeUsesMonicaExpressiveHierarchyAndImmersiveDetail() {
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val web = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreWebScreen.kt"
        ).readText()

        assertTrue(store.contains("ExpressiveTopBar("))
        assertTrue(store.contains("StoreFeaturedHero("))
        assertTrue(store.contains("StoreHeroSkeleton("))
        assertTrue(store.contains("Brush.verticalGradient("))
        assertTrue(store.contains("height(390.dp)"))
        assertTrue(store.contains("heightIn(min = 52.dp)"))
        assertTrue(store.contains("containerColor = MaterialTheme.colorScheme.background"))
        assertFalse(store.contains("OutlinedTextField("))

        assertTrue(web.contains("shape = RoundedCornerShape(28.dp)"))
        assertTrue(web.contains("statusBarsPadding()"))
        assertTrue(web.contains("heightIn(min = 64.dp)"))
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
