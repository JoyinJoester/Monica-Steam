package takagi.ru.monica.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.security.SecurityManager
import java.io.File
import java.util.Date

data class OneDriveBackupConfig(
    val accountId: String,
    val displayName: String,
    val username: String,
    val folderPath: String
)

class OneDriveBackupHelper(context: Context) {
    private val appContext = context.applicationContext
    private val authManager = OneDriveAuthManager(appContext)
    private val securityManager = SecurityManager(appContext)

    fun getConfig(): OneDriveBackupConfig? {
        val prefs = preferences()
        migrateLegacyConfigIfNeeded(prefs)
        val accountId = securityManager.getProtectedString(SECURE_KEY_ACCOUNT_ID)?.takeIf { it.isNotBlank() } ?: return null
        val folderPath = securityManager.getProtectedString(SECURE_KEY_FOLDER_PATH)?.takeIf { it.isNotBlank() } ?: return null
        return OneDriveBackupConfig(
            accountId = accountId,
            displayName = securityManager.getProtectedString(SECURE_KEY_DISPLAY_NAME).orEmpty().ifBlank { "OneDrive" },
            username = securityManager.getProtectedString(SECURE_KEY_USERNAME).orEmpty(),
            folderPath = folderPath
        )
    }

    fun isConfigured(): Boolean = getConfig() != null

    fun saveConfig(session: OneDriveAccountSession, folderPath: String) {
        val normalizedFolderPath = OneDriveKeePassFileSource.normalizeOptionalRemotePath(folderPath)
        preferences().edit()
            .remove(KEY_ACCOUNT_ID)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_USERNAME)
            .remove(KEY_FOLDER_PATH)
            .apply()
        securityManager.putProtectedString(SECURE_KEY_ACCOUNT_ID, session.accountId)
        securityManager.putProtectedString(SECURE_KEY_DISPLAY_NAME, session.displayName)
        securityManager.putProtectedString(SECURE_KEY_USERNAME, session.username)
        securityManager.putProtectedString(SECURE_KEY_FOLDER_PATH, normalizedFolderPath)
    }

    fun clearConfig() {
        preferences().edit().clear().apply()
        securityManager.removeProtectedString(SECURE_KEY_ACCOUNT_ID)
        securityManager.removeProtectedString(SECURE_KEY_DISPLAY_NAME)
        securityManager.removeProtectedString(SECURE_KEY_USERNAME)
        securityManager.removeProtectedString(SECURE_KEY_FOLDER_PATH)
    }

    suspend fun getConfiguredSession(): OneDriveAccountSession? {
        val config = getConfig() ?: return null
        return runCatching { authManager.acquireAccessToken(config.accountId) }
            .getOrElse { error ->
                if (error.isOneDriveAuthTemporarilyUnavailable()) throw error
                null
            }
    }

    suspend fun listDirectory(accountId: String, currentPath: String?): List<FileSourceEntry> {
        return OneDriveKeePassFileSource(
            context = appContext,
            accountIdentifier = accountId
        ).listDirectory(currentPath)
    }

    suspend fun createFolder(accountId: String, currentPath: String?, name: String): FileSourceEntry {
        return OneDriveKeePassFileSource(
            context = appContext,
            accountIdentifier = accountId
        ).createDirectory(currentPath, name)
    }

    suspend fun listBackups(): Result<List<BackupFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val config = getConfig() ?: throw IllegalStateException("尚未配置 OneDrive 备份目录")
            val backups = OneDriveKeePassFileSource(
                context = appContext,
                accountIdentifier = config.accountId
            ).listDirectory(config.folderPath)
                .filter { !it.isDirectory && it.name.endsWith(".zip", ignoreCase = true) }
                .map { entry ->
                    BackupFile(
                        name = entry.name,
                        path = entry.path,
                        size = entry.sizeBytes ?: 0L,
                        modified = Date(entry.lastModified ?: System.currentTimeMillis())
                    )
                }
                .sortedByDescending { it.modified.time }
            Log.d(TAG, "Listed OneDrive backups: count=${backups.size}")
            backups
        }
    }

    suspend fun uploadBackup(file: File, isPermanent: Boolean): Result<BackupFile> = withContext(Dispatchers.IO) {
        runCatching {
            val config = getConfig() ?: throw IllegalStateException("尚未配置 OneDrive 备份目录")
            val targetName = if (isPermanent) {
                file.name.replace(".zip", "_permanent.zip")
            } else {
                file.name
            }
            Log.i(
                TAG,
                "Uploading OneDrive backup: sizeBytes=${file.length()}, permanent=$isPermanent"
            )
            val entry = OneDriveKeePassFileSource(
                context = appContext,
                accountIdentifier = config.accountId
            ).createFileInDirectory(
                parentPath = config.folderPath,
                name = targetName,
                bytes = file.readBytes()
            )
            cleanupBackups()
                .onSuccess { deleted ->
                    Log.i(TAG, "OneDrive backup cleanup completed after upload: deleted=$deleted")
                }
                .onFailure { error ->
                    Log.w(TAG, "OneDrive backup cleanup failed after upload: ${error.message}", error)
                }
            BackupFile(
                name = entry.name,
                path = entry.path,
                size = entry.sizeBytes ?: file.length(),
                modified = Date(entry.lastModified ?: System.currentTimeMillis())
            )
        }
    }

    suspend fun downloadBackup(backupFile: BackupFile, destFile: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val config = getConfig() ?: throw IllegalStateException("尚未配置 OneDrive 备份目录")
            val bytes = OneDriveKeePassFileSource(
                context = appContext,
                accountIdentifier = config.accountId,
                remotePath = backupFile.path
            ).read()
            destFile.writeBytes(bytes)
            destFile
        }
    }

    suspend fun deleteBackup(backupFile: BackupFile): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val config = getConfig() ?: throw IllegalStateException("尚未配置 OneDrive 备份目录")
            OneDriveKeePassFileSource(
                context = appContext,
                accountIdentifier = config.accountId
            ).deleteEntry(backupFile.path)
            true
        }
    }

    suspend fun markBackupAsPermanent(backupFile: BackupFile): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (backupFile.isPermanent) return@runCatching true
            val config = getConfig() ?: throw IllegalStateException("尚未配置 OneDrive 备份目录")
            val newName = backupFile.name.replace(".zip", "_permanent.zip")
            OneDriveKeePassFileSource(
                context = appContext,
                accountIdentifier = config.accountId
            ).renameEntry(backupFile.path, newName)
            true
        }
    }

    suspend fun unmarkPermanent(backupFile: BackupFile): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (!backupFile.isPermanent) return@runCatching true
            val config = getConfig() ?: throw IllegalStateException("尚未配置 OneDrive 备份目录")
            val newName = backupFile.name.replace("_permanent", "")
            OneDriveKeePassFileSource(
                context = appContext,
                accountIdentifier = config.accountId
            ).renameEntry(backupFile.path, newName)
            true
        }
    }

    suspend fun cleanupBackups(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val backups = listBackups().getOrThrow()
            val expiredBackups = BackupRetentionPolicy.expiredTemporaryBackupsToDelete(backups)
            Log.i(
                TAG,
                "OneDrive cleanup scan: total=${backups.size}, " +
                    "temporary=${backups.count { !it.isPermanent }}, candidates=${expiredBackups.size}"
            )
            var deleted = 0
            expiredBackups.forEach { backup ->
                Log.d(TAG, "Deleting expired OneDrive backup")
                deleteBackup(backup).getOrThrow()
                deleted++
            }
            deleted
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun preferences() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun migrateLegacyConfigIfNeeded(prefs: android.content.SharedPreferences) {
        val legacyAccountId = prefs.getString(KEY_ACCOUNT_ID, null)
        val legacyDisplayName = prefs.getString(KEY_DISPLAY_NAME, null)
        val legacyUsername = prefs.getString(KEY_USERNAME, null)
        val legacyFolderPath = prefs.getString(KEY_FOLDER_PATH, null)
        val hasLegacyValues =
            !legacyAccountId.isNullOrBlank() ||
                !legacyDisplayName.isNullOrBlank() ||
                !legacyUsername.isNullOrBlank() ||
                !legacyFolderPath.isNullOrBlank()
        if (!hasLegacyValues) return

        if (securityManager.getProtectedString(SECURE_KEY_ACCOUNT_ID).isNullOrBlank()) {
            securityManager.putProtectedString(SECURE_KEY_ACCOUNT_ID, legacyAccountId)
        }
        if (securityManager.getProtectedString(SECURE_KEY_DISPLAY_NAME).isNullOrBlank()) {
            securityManager.putProtectedString(SECURE_KEY_DISPLAY_NAME, legacyDisplayName)
        }
        if (securityManager.getProtectedString(SECURE_KEY_USERNAME).isNullOrBlank()) {
            securityManager.putProtectedString(SECURE_KEY_USERNAME, legacyUsername)
        }
        if (securityManager.getProtectedString(SECURE_KEY_FOLDER_PATH).isNullOrBlank()) {
            securityManager.putProtectedString(SECURE_KEY_FOLDER_PATH, legacyFolderPath)
        }

        prefs.edit()
            .remove(KEY_ACCOUNT_ID)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_USERNAME)
            .remove(KEY_FOLDER_PATH)
            .apply()
    }

    companion object {
        private const val TAG = "OneDriveBackupHelper"
        private const val PREFS_NAME = "onedrive_backup_config"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_USERNAME = "username"
        private const val KEY_FOLDER_PATH = "folder_path"
        private const val SECURE_KEY_ACCOUNT_ID = "onedrive_backup_account_id"
        private const val SECURE_KEY_DISPLAY_NAME = "onedrive_backup_display_name"
        private const val SECURE_KEY_USERNAME = "onedrive_backup_username"
        private const val SECURE_KEY_FOLDER_PATH = "onedrive_backup_folder_path"
    }
}
