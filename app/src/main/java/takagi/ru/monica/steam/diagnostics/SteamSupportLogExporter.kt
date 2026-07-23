package takagi.ru.monica.steam.diagnostics

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.BuildConfig

object SteamSupportLogExporter {
    suspend fun collect(context: Context): String = withContext(Dispatchers.IO) {
        runCatching { SteamDiagLogger.initialize(context.applicationContext) }
        val persistedCrash = SteamCrashDiagnostics.readLastCrash(context.applicationContext)
        val appLogs = readLogcat(
            context,
            "logcat", "-d", "-v", "threadtime", "--pid",
            android.os.Process.myPid().toString(), "-t", "700", "*:V"
        )
        val mainBufferLogs = readLogcat(
            context,
            "logcat", "-d", "-b", "main", "-v", "threadtime", "-t", "700", "*:V"
        )
        val crashLogs = readLogcat(
            context,
            "logcat", "-d", "-b", "crash", "-v", "threadtime", "-t", "500", "*:V"
        )
        val systemLogs = readLogcat(
            context,
            "logcat", "-d", "-b", "system", "-v", "threadtime", "-t", "500",
            "AndroidRuntime:E", "System.err:W", "libc:E", "*:S"
        )
        val steamLogs = runCatching { SteamDiagLogger.exportPersistedLogs(2000) }
            .getOrElse { "Steam diagnostics unavailable: ${it.message}" }

        redactSteamSupportLog(
            buildString {
                appendLine("=== Monica Steam Support Log ===")
                appendLine("exportedAt=${DATE_FORMAT.format(Date())}")
                appendLine("package=${context.packageName}")
                appendLine("version=${BuildConfig.FULL_VERSION_NAME}")
                appendLine("android=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine()
                appendLine("=== App Process Logcat ===")
                appendLine(appLogs.ifBlank { "No app-process logs available." })
                appendLine()
                appendLine("=== Crash Logcat ===")
                appendLine(crashLogs.ifBlank { "No crash logs available." })
                appendLine()
                appendLine("=== Main Buffer Logcat ===")
                appendLine(mainBufferLogs.ifBlank { "No main-buffer logs available." })
                appendLine()
                appendLine("=== System Buffer Logcat ===")
                appendLine(systemLogs.ifBlank { "No system-buffer logs available." })
                appendLine()
                appendLine("=== Last Persisted Crash ===")
                appendLine(persistedCrash.ifBlank { "No persisted crash available." })
                appendLine()
                appendLine("=== Steam Structured Logs ===")
                appendLine(steamLogs.ifBlank { "No Steam structured logs available." })
            }
        )
    }

    private fun readLogcat(context: Context, vararg command: String): String {
        val result = LogcatCommandRunner.read(context.cacheDir, command)
        return when {
            result.timedOut -> "Logcat timed out: ${result.error.orEmpty()}"
            result.succeeded -> result.output
            result.output.isNotBlank() -> result.output
            else -> "Logcat unavailable: ${result.error ?: "exit=${result.exitCode}"}"
        }
    }

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
}

internal fun redactSteamSupportLog(raw: String): String {
    var result = raw
    SENSITIVE_ASSIGNMENTS.forEach { pattern ->
        result = pattern.replace(result) { match -> "${match.groupValues[1]}=[REDACTED]" }
    }
    result = STEAM_ID.replace(result, "[STEAM_ID_REDACTED]")
    result = JWT_LIKE.replace(result, "[TOKEN_REDACTED]")
    return result
}

private val SENSITIVE_ASSIGNMENTS = listOf(
    Regex("(?i)(steamLoginSecure|access[_-]?token|refresh[_-]?token|shared[_-]?secret|identity[_-]?secret|password)\\s*[=:]\\s*([^\\s,;]+)"),
)
private val STEAM_ID = Regex("(?<!\\d)7656119\\d{10}(?!\\d)")
private val JWT_LIKE = Regex("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}(?![A-Za-z0-9_-])")
