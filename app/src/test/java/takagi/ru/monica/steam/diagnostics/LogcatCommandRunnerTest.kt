package takagi.ru.monica.steam.diagnostics

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatCommandRunnerTest {
    @Test
    fun timedOutProcessIsDestroyedAndReturnsDiagnosticState() {
        val cacheDir = Files.createTempDirectory("monica-logcat-timeout").toFile()
        val process = FakeProcess(completes = false)

        val result = LogcatCommandRunner.read(
            cacheDir = cacheDir,
            command = arrayOf("logcat", "-d"),
            timeoutMillis = 5L,
            startProcess = { process }
        )

        assertTrue(result.timedOut)
        assertTrue(process.destroyCalled)
        assertFalse(result.succeeded)
        cacheDir.deleteRecursively()
    }

    @Test
    fun completedProcessReadsRedirectedFileWithoutPipeBlocking() {
        val cacheDir = Files.createTempDirectory("monica-logcat-output").toFile()

        val result = LogcatCommandRunner.read(
            cacheDir = cacheDir,
            command = arrayOf("logcat", "-d"),
            timeoutMillis = 1_000L,
            startProcess = { builder ->
                requireNotNull(builder.redirectOutput().file()).writeText("test log line\n")
                FakeProcess(completes = true, exitCode = 0)
            }
        )

        assertTrue(result.succeeded)
        assertFalse(result.timedOut)
        assertEquals("test log line", result.output)
        cacheDir.deleteRecursively()
    }

    private class FakeProcess(
        private val completes: Boolean,
        private val exitCode: Int = 0
    ) : Process() {
        var destroyCalled: Boolean = false
            private set

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun waitFor(): Int = exitValue()
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = completes
        override fun exitValue(): Int {
            if (!completes && !destroyCalled) throw IllegalThreadStateException()
            return exitCode
        }

        override fun destroy() {
            destroyCalled = true
        }

        override fun destroyForcibly(): Process {
            destroyCalled = true
            return this
        }

        override fun isAlive(): Boolean = !completes && !destroyCalled
    }
}
