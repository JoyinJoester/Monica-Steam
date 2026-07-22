package takagi.ru.monica.steam.health

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamHealthIntegrationGuardTest {
    @Test
    fun serverTimeUsesPublicTwoFactorQueryTimeWithoutAccountCredentials() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/health/SteamServerTimeService.kt"
        ).readText()

        assertTrue(source.contains("iface = \"ITwoFactorService\""))
        assertTrue(source.contains("method = \"QueryTime\""))
        assertTrue(source.contains("request = SteamProtoWriter()"))
        assertTrue(source.contains("SteamProtoReader(response).parse()"))
        assertFalse(source.contains("accessToken ="))
        assertFalse(source.contains("sharedSecret"))
        assertFalse(source.contains("identitySecret"))
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
