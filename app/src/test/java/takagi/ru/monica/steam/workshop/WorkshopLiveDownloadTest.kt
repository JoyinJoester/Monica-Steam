package takagi.ru.monica.steam.workshop

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.*
import takagi.ru.monica.steam.workshop.download.*

class WorkshopLiveDownloadTest {
    @Test fun publicWallpaperAccessProbe() = runBlocking {
        assumeTrue(System.getenv("MONICA_LIVE_WORKSHOP") == "1")
        val root = Files.createTempDirectory("monica-wallpaper-live").toFile()
        val client = WorkshopDownloadClient()
        val source = client.details(WALLPAPER_ENGINE_APP_ID, "3815558506")
        assertTrue(source.item.size in 1..(20 * 1024 * 1024))
        assertTrue(source.manifest.toULong() > 0uL)
        val files = try { client.download(source, root) { _, _ -> } }
        catch (denied: WorkshopDownloadAccessDenied) {
            println("Live anonymous access refused by Steam: EResult=${denied.result}; no download claimed.")
            assertTrue(root.listFiles().isNullOrEmpty())
            return@runBlocking
        }
        assertTrue(files.isNotEmpty())
        assertTrue(files.any { it.name == "project.json" })
        assertTrue(files.sumOf { it.length() } > 0)
        println("Wallpaper sample: ${files.size} files, ${files.sumOf { it.length() }} bytes, saved to $root")
    }
}
