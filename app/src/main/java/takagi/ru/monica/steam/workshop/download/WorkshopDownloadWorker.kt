package takagi.ru.monica.steam.workshop.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import java.io.File
import kotlinx.coroutines.*
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.workshop.WorkshopException
import takagi.ru.monica.steam.workshop.WorkshopFailure
import kotlin.coroutines.coroutineContext

class WorkshopDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val store = WorkshopDownloads.get(applicationContext)
    private var label = ""
    @Volatile private var stageProgress = WorkshopDownloadProgress(WorkshopDownloadStage.RESOLVING)
    private var lastProgressAt = 0L

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground()
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        var record = inputData.getString("download")?.let { store.read(it) } ?: return@withContext Result.failure()
        if (record.completed) return@withContext Result.success()
        label = record.title
        val staging = File(applicationContext.noBackupFilesDir, "workshop-staging/${record.id}")
        var exportRoot: DocumentFile? = null
        try {
            setForeground(foreground())
            val tree = DocumentFile.fromTreeUri(applicationContext, Uri.parse(record.tree))
                ?.takeIf { it.canWrite() && it.isDirectory } ?: throw SecurityException("Save folder unavailable")
            // A process death may bypass finally. Only remove this task's explicitly
            // named incomplete child, never the user's selected tree or a finished folder.
            record.savedRoot?.let { saved ->
                val uri = Uri.parse(saved)
                if (uri.authority == tree.uri.authority &&
                    DocumentsContract.getDocumentId(uri) != DocumentsContract.getTreeDocumentId(Uri.parse(record.tree))) {
                    DocumentFile.fromSingleUri(applicationContext, uri)
                        ?.takeIf { it.name == ".monica-${record.id}.partial" }?.delete()
                }
            }
            val account = record.handleKey?.let { key ->
                val repository = SteamAccountSourceRepository.get(applicationContext)
                val handle = repository.loadAllSessionHandles().firstOrNull {
                    it.stableKey == key && it.account.id.toString() == record.accountId && it.account.steamId == record.steamId
                } ?: throw WorkshopException(WorkshopFailure.LOGIN)
                repository.sessionManager.resolve(handle, false).account
            }
            val client = WorkshopDownloadClient()
            val source = client.details(record.appId, record.itemId)
            val files = client.download(source, staging, account,
                stageProgress = { publishProgress(it) }) { _, _ -> }
            coroutineContext.ensureActive()
            publishProgress(WorkshopDownloadProgress(WorkshopDownloadStage.SAVING))
            // Always create a new folder: never overwrite a user's existing files.
            val output = tree.createDirectory(".monica-${record.id}.partial") ?: throw SecurityException("Cannot create save folder")
            exportRoot = output
            record = record.copy(savedRoot = output.uri.toString())
            store.update(record)
            exportFiles(applicationContext, staging, files, output) { done, total ->
                publishProgress(WorkshopDownloadProgress(WorkshopDownloadStage.SAVING, done, total))
            }
            coroutineContext.ensureActive()
            check(output.renameTo("Wallpaper-${record.itemId}")) { "Cannot finish save folder" }
            record = record.copy(completed = true, savedRoot = output.uri.toString(), failure = null)
            store.update(record)
            exportRoot = null
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val reason = when {
                error is WorkshopDownloadAccessDenied -> "access"
                error is WorkshopDownloadUnavailable -> "unavailable"
                error is WorkshopException && error.reason == WorkshopFailure.LOGIN -> "login"
                error is SecurityException || error is WorkshopDownloadStorageUnavailable ||
                    error is java.io.FileNotFoundException -> "storage"
                error is SteamApiException && (error.httpStatusCode == 401 || error.eResult in listOf(5, 15, 65)) -> "access"
                error is IllegalArgumentException || error is IllegalStateException -> "invalid"
                stageProgress.stage == WorkshopDownloadStage.SAVING -> "storage"
                else -> "network"
            }
            store.update(record.copy(failure = reason, savedRoot = null))
            Result.failure(workDataOf("failure" to reason))
        } finally {
            // Only our new, incomplete directory and private staging are eligible for cleanup.
            withContext(NonCancellable + Dispatchers.IO) {
                exportRoot?.let { runCatching { it.delete() } }
                val parent = File(applicationContext.noBackupFilesDir, "workshop-staging").canonicalFile
                if (staging.canonicalFile.parentFile == parent) staging.deleteRecursively()
            }
        }
    }

    private suspend fun publishProgress(next: WorkshopDownloadProgress) {
        coroutineContext.ensureActive()
        val changedStage = stageProgress.stage != next.stage
        stageProgress = next
        val now = SystemClock.elapsedRealtime()
        if (changedStage || now - lastProgressAt >= 250 || (next.total > 0 && next.done == next.total)) {
            setProgress(next.toData())
            setForeground(foreground())
            lastProgressAt = now
        }
    }

    private fun foreground(): ForegroundInfo =
        workshopDownloadForeground(applicationContext, id, label, stageProgress)

    companion object {
        internal suspend fun exportFiles(context: Context, staging: File, files: List<File>, root: DocumentFile,
            progress: suspend (Long, Long) -> Unit = { _, _ -> }) {
            val total = files.sumOf { it.length() }
            var done = 0L
            progress(done, total)
            val directories = mutableMapOf("" to root)
            for (file in files) {
                coroutineContext.ensureActive()
                val relative = WorkshopContentCodec.safePath(file.relativeTo(staging).invariantSeparatorsPath)
                val parts = relative.split("/")
                var parent = root
                var prefix = ""
                for (part in parts.dropLast(1)) {
                    prefix = if (prefix.isEmpty()) part else "$prefix/$part"
                    parent = directories[prefix] ?: (parent.createDirectory(part)
                        ?: throw SecurityException("Cannot create folder")).also { directories[prefix] = it }
                }
                val output = parent.createFile("application/octet-stream", parts.last())
                    ?: throw SecurityException("Cannot create file")
                // Some document providers append .bin for octet-stream. Steam projects
                // reference exact filenames, so silently keeping the suffix breaks them.
                if (output.name != parts.last()) {
                    check(output.renameTo(parts.last()) && output.name == parts.last()) { "Cannot preserve original filename" }
                }
                val resolver = context.contentResolver
                requireNotNull(resolver.openOutputStream(output.uri, "w")).use { out ->
                    file.inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer); if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            progress(done, total)
                        }
                    }
                }
                check(output.length() == file.length()) { "Saved file is incomplete" }
            }
        }
    }
}

private const val WORKSHOP_DOWNLOAD_CHANNEL = "steam_workshop_downloads"

internal fun workshopDownloadForeground(context: Context, id: java.util.UUID, label: String,
    progress: WorkshopDownloadProgress): ForegroundInfo {
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(WORKSHOP_DOWNLOAD_CHANNEL, context.getString(R.string.wallpaper_downloads), NotificationManager.IMPORTANCE_LOW))
    val percent = ((progress.fraction ?: 0f) * 100).toInt()
    val notification = NotificationCompat.Builder(context, WORKSHOP_DOWNLOAD_CHANNEL)
        .setSmallIcon(R.drawable.ic_steam_chat_notification)
        .setContentTitle(label.ifBlank { context.getString(R.string.wallpaper_downloads) })
        .setContentText(context.getString(progress.stage.label))
        .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setProgress(100, percent, progress.fraction == null)
        .addAction(0, context.getString(R.string.workshop_cancel),
            WorkManager.getInstance(context).createCancelPendingIntent(id))
        .build()
    val notificationId = 0x6b000000 or (id.hashCode() and 0x00ffffff)
    return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId, notification)
}
