package takagi.ru.monica.steam.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import takagi.ru.monica.BuildConfig

object SteamCrashDiagnostics {
    private const val LOG_DIR_NAME = "steam_logs"
    private const val CRASH_FILE_NAME = "last_crash.txt"
    internal const val MAX_CRASH_BYTES = 256 * 1024

    private val installed = AtomicBoolean(false)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun install(context: Context) {
        val appContext = context.applicationContext
        if (!installed.compareAndSet(false, true)) return

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                writeCrash(appContext, thread, throwable)
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun readLastCrash(context: Context): String {
        return runCatching {
            crashFile(context.applicationContext)
                .takeIf { it.exists() }
                ?.readText()
                ?.trim()
                .orEmpty()
        }.getOrDefault("")
    }

    fun clear(context: Context) {
        runCatching { crashFile(context.applicationContext).delete() }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val file = crashFile(context)
        file.parentFile?.mkdirs()

        val stackWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stackWriter))
        val report = buildString {
            appendLine("=== Monica Steam Uncaught Crash ===")
            appendLine("occurredAt=${formatDate(Date())}")
            appendLine("package=${context.packageName}")
            appendLine("appVersion=${BuildConfig.FULL_VERSION_NAME}")
            appendLine("displayVersion=${BuildConfig.VERSION_NAME}")
            appendLine("gitSha=${BuildConfig.GIT_SHA}")
            appendLine("android=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("thread=${thread.name} (${thread.id})")
            appendLine("exception=${throwable::class.java.name}")
            appendLine()
            appendLine(stackWriter.toString())
        }
        val redacted = redactSteamSupportLog(report).take(MAX_CRASH_BYTES)
        val parent = file.parentFile ?: return
        val temporary = File(parent, "${file.name}.tmp")
        temporary.writeText(redacted)
        if (!temporary.renameTo(file)) {
            file.writeText(redacted)
            temporary.delete()
        }
    }

    private fun crashFile(context: Context): File =
        File(File(context.filesDir, LOG_DIR_NAME), CRASH_FILE_NAME)

    private fun formatDate(date: Date): String = synchronized(dateFormat) {
        dateFormat.format(date)
    }
}
