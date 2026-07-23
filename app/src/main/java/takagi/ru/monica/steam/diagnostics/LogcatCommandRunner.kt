package takagi.ru.monica.steam.diagnostics

import java.io.File
import java.util.concurrent.TimeUnit

internal data class LogcatCommandResult(
    val output: String,
    val exitCode: Int?,
    val timedOut: Boolean,
    val error: String?
) {
    val succeeded: Boolean
        get() = !timedOut && error == null && exitCode == 0
}

internal object LogcatCommandRunner {
    private const val DEFAULT_TIMEOUT_MILLIS = 3_000L
    private const val DESTROY_GRACE_MILLIS = 200L
    private const val MAX_OUTPUT_CHARS = 500_000

    fun read(
        cacheDir: File,
        command: Array<out String>,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        startProcess: (ProcessBuilder) -> Process = { builder -> builder.start() }
    ): LogcatCommandResult {
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            return LogcatCommandResult("", null, false, "cache directory unavailable")
        }
        val outputFile = runCatching {
            File.createTempFile("monica_logcat_", ".tmp", cacheDir)
        }.getOrElse { error ->
            return LogcatCommandResult("", null, false, error.message ?: "temp file failed")
        }
        try {
            val builder = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .redirectOutput(outputFile)
            val process = runCatching { startProcess(builder) }.getOrElse { error ->
                return LogcatCommandResult("", null, false, error.message ?: "process start failed")
            }
            val finished = runCatching {
                process.waitFor(timeoutMillis.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
            }.getOrDefault(false)
            if (!finished) {
                process.destroy()
                runCatching {
                    process.waitFor(DESTROY_GRACE_MILLIS, TimeUnit.MILLISECONDS)
                }
                if (runCatching { process.isAlive }.getOrDefault(true)) {
                    runCatching { process.destroyForcibly() }
                }
                return LogcatCommandResult(
                    output = readOutput(outputFile),
                    exitCode = null,
                    timedOut = true,
                    error = "timeout after ${timeoutMillis.coerceAtLeast(1L)}ms"
                )
            }
            val exitCode = runCatching { process.exitValue() }.getOrNull()
            return LogcatCommandResult(
                output = readOutput(outputFile),
                exitCode = exitCode,
                timedOut = false,
                error = null
            )
        } finally {
            runCatching { outputFile.delete() }
        }
    }

    private fun readOutput(file: File): String = runCatching {
        val text = file.readText()
        if (text.length <= MAX_OUTPUT_CHARS) text.trim() else text.takeLast(MAX_OUTPUT_CHARS).trim()
    }.getOrDefault("")
}
