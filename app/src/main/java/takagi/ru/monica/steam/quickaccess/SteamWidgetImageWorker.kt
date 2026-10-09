package takagi.ru.monica.steam.quickaccess

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.data.SteamLibraryCacheRepository
import takagi.ru.monica.steam.foundation.ui.refreshSteamAvatarBitmap
import takagi.ru.monica.steam.profile.SteamMiniProfileDecorRepository
import takagi.ru.monica.steam.profile.SteamRemoteImageCache

/** Network enrichment runs outside the widget broadcast's short lifetime. */
class SteamWidgetImageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val widgetId = inputData.getInt(KEY_WIDGET, 0)
        val accountId = inputData.getLong(KEY_ACCOUNT, 0L)
        val databaseId = inputData.getLong(KEY_DATABASE, 0L).takeIf { it > 0L }
        fun stillBound() = accountId != 0L && SteamWidgetPreferences.accountId(applicationContext, widgetId) == accountId &&
            SteamWidgetPreferences.databaseId(applicationContext, widgetId) == databaseId
        if (!stillBound()) return@withContext Result.success()
        try {
            val account = SteamAccountSourceRepository.get(applicationContext).loadWidgetAccount(accountId, databaseId)
                ?: return@withContext Result.success()
            val database = SteamDatabase.getDatabase(applicationContext)
            val library = SteamLibraryCacheRepository(applicationContext, database.steamLibraryCacheDao(),
                SecurityManager(applicationContext)).getLibrary(accountId)
            val images = SteamRemoteImageCache.get(applicationContext)
            val jobs = buildList<suspend () -> Boolean> {
                add { refreshSteamAvatarBitmap(applicationContext, account.steamId) != null }
                recentWidgetGames(library).forEach { game ->
                    add { images.load(game.widgetHeaderUrl()) != null }
                }
                add {
                    val decor = SteamMiniProfileDecorRepository.get(applicationContext).load(account.steamId)
                    // Publish profile/current-game text before another image download.
                    if (stillBound()) SteamWidgetUpdater.refresh(applicationContext, widgetId, refreshImages = false)
                    val urls = listOfNotNull(decor?.avatarUrl, decor?.currentGameImageUrl).distinct()
                    urls.map { images.load(it) != null }.all { it } && decor != null
                }
            }
            val complete = enrichWidgetImages(jobs) {
                if (stillBound()) SteamWidgetUpdater.refresh(applicationContext, widgetId, refreshImages = false)
            }
            if (!complete && stillBound() && runAttemptCount < 2) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (stillBound() && runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val KEY_WIDGET = "widget"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_DATABASE = "database"
        private fun tag(widgetId: Int) = "steam_widget_images_$widgetId"

        internal fun enqueue(context: Context, widgetId: Int, accountId: Long, databaseId: Long?) {
            val request = OneTimeWorkRequestBuilder<SteamWidgetImageWorker>()
                .setInputData(workDataOf(KEY_WIDGET to widgetId, KEY_ACCOUNT to accountId, KEY_DATABASE to (databaseId ?: 0L)))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(tag(widgetId)).build()
            // Independent binding keys allow a newly selected account to fetch immediately.
            WorkManager.getInstance(context).enqueueUniqueWork("${tag(widgetId)}_${accountId}_${databaseId ?: 0L}",
                ExistingWorkPolicy.KEEP, request)
        }

        internal fun cancel(context: Context, widgetId: Int) {
            WorkManager.getInstance(context).cancelAllWorkByTag(tag(widgetId))
        }
    }
}

/** Each available image is published immediately, even if another server is slow or unavailable. */
internal suspend fun enrichWidgetImages(loads: List<suspend () -> Boolean>, publish: () -> Unit): Boolean = coroutineScope {
    loads.map { load -> async {
        try {
            load().also { if (it) publish() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    } }.awaitAll().all { it }
}
