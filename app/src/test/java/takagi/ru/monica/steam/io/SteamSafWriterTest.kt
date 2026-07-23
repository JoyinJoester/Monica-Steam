package takagi.ru.monica.steam.io

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamSafWriterTest {
    @Test
    fun unavailableDestinationReturnsFailureInsteadOfThrowing() {
        assertFalse(SteamSafWriter.writeText(output = null, text = "diagnostic"))
        assertFalse(SteamSafWriter.writeBytes(output = null, bytes = byteArrayOf(1, 2, 3)))
    }

    @Test
    fun writesAndClosesTextAndBinaryExports() {
        val textOutput = ByteArrayOutputStream()
        val binaryOutput = ByteArrayOutputStream()

        assertTrue(SteamSafWriter.writeText(textOutput, "库存,价值"))
        assertTrue(SteamSafWriter.writeBytes(binaryOutput, byteArrayOf(1, 2, 3)))
        assertEquals("库存,价值", textOutput.toString(Charsets.UTF_8.name()))
        assertEquals(listOf<Byte>(1, 2, 3), binaryOutput.toByteArray().toList())
    }

    @Test
    fun providerWriteFailureIsContained() {
        val failingOutput = object : OutputStream() {
            override fun write(value: Int) {
                throw IOException("provider unavailable")
            }
        }

        assertFalse(SteamSafWriter.writeText(failingOutput, "diagnostic"))
    }
}
