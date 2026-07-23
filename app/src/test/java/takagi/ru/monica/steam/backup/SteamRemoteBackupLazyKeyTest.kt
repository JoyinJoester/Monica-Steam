package takagi.ru.monica.steam.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamRemoteBackupLazyKeyTest {
    @Test
    fun duplicateBackupNamesReceiveUniqueKeys() {
        val backup = SteamMaFileRemoteBackup("backup.zip", 1L, 2L)
        val keys = listOf(backup, backup).mapIndexed(::steamRemoteBackupLazyKey)

        assertEquals(2, keys.distinct().size)
    }
}
