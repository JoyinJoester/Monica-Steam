package takagi.ru.monica.steam.workshop.download

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.lifecycle.asFlow
import androidx.work.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.workshop.WorkshopItem
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle

@Serializable
internal data class WorkshopDownloadRecord(
    val id: String, val appId: Int, val itemId: String, val title: String, val preview: String,
    val tree: String, val handleKey: String? = null, val accountId: String? = null, val steamId: String? = null,
    val savedRoot: String? = null, val completed: Boolean = false, val failure: String? = null,
    val created: Long = System.currentTimeMillis()
)
internal data class WorkshopDownloadRow(val record: WorkshopDownloadRecord, val work: WorkInfo?) {
    val active: Boolean get() = work?.state?.isFinished == false
    val completed: Boolean get() = record.completed
}

/** Durable local-only metadata. Credentials and depot keys never enter WorkManager or disk. */
internal class WorkshopDownloads private constructor(private val context: Context) {
    private val directory = File(context.noBackupFilesDir, "workshop-downloads").apply { mkdirs() }
    private val lock = Mutex()
    private val enqueueLock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val records = MutableStateFlow<List<WorkshopDownloadRecord>>(emptyList())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manager = WorkManager.getInstance(context)
    val rows = combine(records, manager.getWorkInfosByTagLiveData(TAG).asFlow()) { saved, work ->
        saved.sortedByDescending { it.created }.map { record ->
            WorkshopDownloadRow(record, work.firstOrNull { it.id.toString() == record.id })
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    init { scope.launch { lock.withLock { reload() } } }

    private fun file(id: String): File = File(directory, UUID.fromString(id).toString() + ".json")
    private fun readFile(id: String): WorkshopDownloadRecord? = try {
        val raw = AtomicFile(file(id)).openRead().use { WorkshopContentCodec.readBounded(it, 32 * 1024) }
        json.decodeFromString<WorkshopDownloadRecord>(raw.toString(Charsets.UTF_8))
    } catch (_: java.io.FileNotFoundException) { null }

    private fun reload() {
        records.value = directory.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull {
            runCatching { readFile(it.nameWithoutExtension) }.getOrNull()
        }
    }

    suspend fun read(id: String): WorkshopDownloadRecord? = withContext(Dispatchers.IO) { lock.withLock { readFile(id) } }

    suspend fun update(record: WorkshopDownloadRecord) = withContext(Dispatchers.IO) {
        lock.withLock {
            val atomic = AtomicFile(file(record.id))
            val stream = atomic.startWrite()
            try { stream.write(json.encodeToString(record).toByteArray()); atomic.finishWrite(stream) }
            catch (error: Exception) { atomic.failWrite(stream); throw error }
            reload()
        }
    }

    suspend fun enqueue(item: WorkshopItem, tree: Uri, handle: SteamAccountSessionHandle?) = enqueueLock.withLock {
        val existing = withContext(Dispatchers.IO) { manager.getWorkInfosByTag(TAG).get() }
        val saved = withContext(Dispatchers.IO) { lock.withLock { reload(); records.value } }
        if (saved.any { r -> r.appId == item.appId && r.itemId == item.id &&
            existing.any { it.id.toString() == r.id && !it.state.isFinished } }) return@withLock
        val id = UUID.randomUUID()
        val record = WorkshopDownloadRecord(id.toString(), item.appId, item.id, item.title.take(512), item.preview.take(2048),
            tree.toString(), handle?.stableKey, handle?.account?.id?.toString(), handle?.account?.steamId)
        update(record)
        schedule(record)
    }

    private suspend fun schedule(record: WorkshopDownloadRecord) {
        val request = OneTimeWorkRequestBuilder<WorkshopDownloadWorker>()
            .setId(UUID.fromString(record.id)).addTag(TAG)
            .setInputData(workDataOf("download" to record.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresStorageNotLow(true).build())
            .build()
        withContext(Dispatchers.IO) {
            manager.enqueueUniqueWork("workshop-download-${record.appId}-${record.itemId}", ExistingWorkPolicy.KEEP, request).result.get()
        }
    }

    suspend fun retry(record: WorkshopDownloadRecord, tree: Uri, handle: SteamAccountSessionHandle?) = enqueueLock.withLock {
        val old = withContext(Dispatchers.IO) { manager.getWorkInfoById(UUID.fromString(record.id)).get() }
        if (old?.state?.isFinished == false || record.completed) return@withLock
        val existing = withContext(Dispatchers.IO) {
            manager.getWorkInfosForUniqueWork("workshop-download-${record.appId}-${record.itemId}").get()
        }
        if (existing.any { !it.state.isFinished }) return@withLock
        // A new work ID isolates late callbacks from the earlier attempt.
        val next = record.copy(id = UUID.randomUUID().toString(), tree = tree.toString(),
            handleKey = handle?.stableKey, accountId = handle?.account?.id?.toString(), steamId = handle?.account?.steamId,
            savedRoot = null, failure = null, created = System.currentTimeMillis())
        update(next)
        schedule(next)
    }

    fun cancel(record: WorkshopDownloadRecord) { manager.cancelWorkById(UUID.fromString(record.id)) }

    companion object {
        const val TAG = "workshop-downloads"
        @Volatile private var instance: WorkshopDownloads? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: WorkshopDownloads(context.applicationContext).also { instance = it }
        }
    }
}
