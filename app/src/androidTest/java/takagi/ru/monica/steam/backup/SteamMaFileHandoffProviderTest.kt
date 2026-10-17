package takagi.ru.monica.steam.backup

import android.os.Process
import android.provider.OpenableColumns
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.backup.share.SteamMaFileShareProvider

/** Synthetic bytes only; never loads or mutates real accounts/security settings. */
class SteamMaFileHandoffProviderTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun readsOnceAndUsesGenericMetadata() {
        val bytes = "synthetic maFile transport test".toByteArray()
        val expected = bytes.copyOf()
        val uri = SteamMaFileShareProvider.publish(context, Process.myUid(), bytes)
        try {
            val resolver = context.contentResolver
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals("steam-account.maFile", it.getString(0))
            }
            resolver.openInputStream(uri)!!.use { assertArrayEquals(expected, it.readBytes()) }
            assertTrue(runCatching { resolver.openInputStream(uri)?.close() }.isFailure)
        } finally { SteamMaFileShareProvider.revoke(context, uri) }
    }

    @Test fun rejectsOtherUidWritesAndAlteredUris() {
        val uri = SteamMaFileShareProvider.publish(context, Process.myUid(), byteArrayOf(1))
        val otherUidUri = SteamMaFileShareProvider.publish(context, Process.myUid() + 1, byteArrayOf(2))
        try {
            val resolver = context.contentResolver
            assertTrue(runCatching { resolver.openFileDescriptor(uri, "rw")?.close() }.isFailure)
            assertTrue(runCatching { resolver.openInputStream(otherUidUri)?.close() }.isFailure)
            assertTrue(runCatching { resolver.openInputStream(uri.buildUpon().appendQueryParameter("x", "1").build())?.close() }.isFailure)
            assertTrue(runCatching { resolver.openInputStream(uri.buildUpon().fragment("x").build())?.close() }.isFailure)
        } finally {
            SteamMaFileShareProvider.revoke(context, uri)
            SteamMaFileShareProvider.revoke(context, otherUidUri)
        }
    }
}
