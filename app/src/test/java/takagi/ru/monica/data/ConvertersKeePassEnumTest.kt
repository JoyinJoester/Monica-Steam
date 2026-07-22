package takagi.ru.monica.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersKeePassEnumTest {
    private val converters = Converters()

    @Test
    fun keepassEnumConvertersFallbackForInvalidLegacyValues() {
        assertEquals(
            KeePassStorageLocation.INTERNAL,
            converters.toKeePassStorageLocation("BROKEN_STORAGE")
        )
        assertEquals(
            KeePassDatabaseSourceType.LOCAL_INTERNAL,
            converters.toKeePassDatabaseSourceType("WEBDAV")
        )
        assertEquals(
            KeePassOpenMode.DIRECT,
            converters.toKeePassOpenMode("UNKNOWN_MODE")
        )
        assertEquals(
            KeePassSyncStatus.LOCAL_ONLY,
            converters.toKeePassSyncStatus("STUCK")
        )
    }

    @Test
    fun keepassEnumConvertersReadCurrentValues() {
        assertEquals(
            KeePassStorageLocation.EXTERNAL,
            converters.toKeePassStorageLocation("EXTERNAL")
        )
        assertEquals(
            KeePassDatabaseSourceType.REMOTE_WEBDAV,
            converters.toKeePassDatabaseSourceType("REMOTE_WEBDAV")
        )
        assertEquals(
            KeePassOpenMode.WORKING_COPY,
            converters.toKeePassOpenMode("WORKING_COPY")
        )
        assertEquals(
            KeePassSyncStatus.PENDING_UPLOAD,
            converters.toKeePassSyncStatus("PENDING_UPLOAD")
        )
    }
}
