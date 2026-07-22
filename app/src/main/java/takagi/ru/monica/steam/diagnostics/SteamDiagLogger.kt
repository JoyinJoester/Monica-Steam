package takagi.ru.monica.steam.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import takagi.ru.monica.BuildConfig
import takagi.ru.monica.utils.BoundedLogExecutorFactory

/**
 * Persisted Steam diagnostics for login/import failures.
 *
 * Keep entries protocol-level only: no account names, SteamIDs, passwords,
 * tokens, secrets, confirmation codes, or raw payloads.
 */
object SteamDiagLogger {
    private const val TAG = "MonicaSteamDiag"
    private const val LOG_DIR_NAME = "steam_logs"
    private const val LOG_FILE_NAME = "steam_diag_v1.log"
    private const val MAX_LOG_FILE_BYTES = 1024 * 1024L
    private const val ROTATE_KEEP_LINES = 4000

    private val fileLock = Any()
    private val writeExecutor = BoundedLogExecutorFactory.createSingleThreadExecutor("monica-steam-diag")

    @Volatile
    private var persistentLogFile: File? = null

    fun initialize(context: Context) {
        if (persistentLogFile != null) return
        synchronized(fileLock) {
            if (persistentLogFile != null) return
            runCatching {
                val logDir = File(context.applicationContext.filesDir, LOG_DIR_NAME)
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                val file = File(logDir, LOG_FILE_NAME)
                persistentLogFile = file
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val header = buildString {
                    appendLine("=== Monica Steam Diag Session ===")
                    appendLine("session_start=$time")
                    appendLine("app_version=${BuildConfig.FULL_VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("app_display_version=${BuildConfig.VERSION_NAME}")
                    appendLine("build_time=${BuildConfig.BUILD_TIME}")
                    appendLine("git_sha=${BuildConfig.GIT_SHA}")
                    appendLine("android_api=${Build.VERSION.SDK_INT}")
                    appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("===")
                }
                file.appendText(header)
            }.onFailure {
                Log.e(TAG, "Failed to initialize Steam diag logger", it)
            }
        }
    }

    fun append(rawLine: String) {
        val file = persistentLogFile
        if (file == null) {
            Log.w(TAG, "Persistent Steam diag log file is not initialized yet")
            return
        }
        val line = rawLine.trimEnd()
        writeExecutor.execute {
            val sanitizedLine = sanitize(line)
            Log.d(TAG, sanitizedLine)
            synchronized(fileLock) {
                runCatching {
                    if (file.exists() && file.length() > MAX_LOG_FILE_BYTES) {
                        rotate(file)
                    }
                    file.appendText(sanitizedLine + "\n")
                }.onFailure {
                    Log.e(TAG, "Failed to append Steam diag log", it)
                }
            }
        }
    }

    fun exportPersistedLogs(maxEntries: Int = 2000): String {
        val file = persistentLogFile ?: return ""
        if (!file.exists()) return ""
        return synchronized(fileLock) {
            runCatching {
                file.readLines()
                    .takeLast(maxEntries.coerceAtLeast(1))
                    .joinToString(separator = "\n")
            }.getOrDefault("")
        }
    }

    fun clear() {
        synchronized(fileLock) {
            runCatching {
                persistentLogFile?.let { file ->
                    if (file.exists()) {
                        file.writeText("")
                    }
                }
            }
        }
    }

    private fun rotate(file: File) {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val tail = runCatching {
            file.readLines().takeLast(ROTATE_KEEP_LINES)
        }.getOrElse { emptyList() }

        val output = buildString {
            appendLine("=== steam diag log rotated at $time ===")
            tail.forEach { appendLine(it) }
        }
        file.writeText(output)
    }

    private fun sanitize(text: String): String {
        return text
            .replace(Regex("\\bsteamid\\s*=\\s*[^\\s,]+", RegexOption.IGNORE_CASE), "steamid=<redacted>")
            .replace(Regex("\\b(account|user|username|accountName|account_name)\\s*=\\s*[^\\s,]+", RegexOption.IGNORE_CASE), "$1=<redacted>")
            .replace(Regex("\\b(password|pwd|passwd|token|access_token|refresh_token|shared_secret|identity_secret|secret_1|code)\\s*=\\s*[^\\s,]+", RegexOption.IGNORE_CASE), "$1=***")
            .replace(Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"), "***@***.com")
            .replace(Regex("\\b[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"), "***TOKEN***")
            .replace(Regex("\\b[A-Za-z0-9]{28,}\\b"), "***TOKEN***")
            .replace(Regex("[A-Za-z0-9+/]{40,}={0,2}"), "***TOKEN***")
    }
}
