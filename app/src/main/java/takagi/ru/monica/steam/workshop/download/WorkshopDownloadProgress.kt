package takagi.ru.monica.steam.workshop.download

import androidx.work.Data
import androidx.work.workDataOf
import takagi.ru.monica.R

internal enum class WorkshopDownloadStage(val key: String, val step: Int, val label: Int) {
    RESOLVING("resolving", 0, R.string.wallpaper_resolving),
    DOWNLOADING("downloading", 1, R.string.wallpaper_downloading),
    VERIFYING("verifying", 2, R.string.wallpaper_verifying),
    CHECKING_SIZE("checking_size", 2, R.string.wallpaper_checking_size),
    SAVING("saving", 3, R.string.wallpaper_saving);

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: RESOLVING
    }
}

/** Counters belong to the current stage, never to a guessed overall percentage. */
internal data class WorkshopDownloadProgress(
    val stage: WorkshopDownloadStage,
    val done: Long = 0,
    val total: Long = 0
) {
    val fraction: Float? get() = total.takeIf { it > 0 }?.let {
        (done.coerceIn(0L, it).toDouble() / it).toFloat()
    }
    val countsFiles: Boolean get() = stage == WorkshopDownloadStage.VERIFYING

    fun toData(): Data = workDataOf("phase" to stage.key, "done" to done, "total" to total,
        "stage_progress_version" to 1)

    companion object {
        fun fromData(data: Data): WorkshopDownloadProgress {
            val stage = WorkshopDownloadStage.fromKey(data.getString("phase"))
            // Older workers published network counters during saving. They are
            // not evidence of how much the document provider has written.
            val hasCounters = data.getInt("stage_progress_version", 0) == 1 ||
                stage == WorkshopDownloadStage.DOWNLOADING
            return WorkshopDownloadProgress(stage,
                if (hasCounters) data.getLong("done", 0).coerceAtLeast(0) else 0,
                if (hasCounters) data.getLong("total", 0).coerceAtLeast(0) else 0)
        }
    }
}
