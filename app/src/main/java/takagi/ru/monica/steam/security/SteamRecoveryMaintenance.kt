package takagi.ru.monica.steam.security

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.security.LocalVaultRecovery
import takagi.ru.monica.security.PortableLocalCipherMigration
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.itad.data.ItadCredentialStore

/** Safe to resume: compare the original ciphertext before each write, never change user timestamps. */
internal object SteamRecoveryMaintenance {
    suspend fun run(context: Context, security: SecurityManager) {
        if (!security.isVaultRuntimeUnlocked() || !LocalVaultRecovery(context).available()) return
        PortableLocalCipherMigration.run(SteamDatabase.getDatabase(context), security, steam = true)
        PortableLocalCipherMigration.run(PasswordDatabase.getDatabase(context), security)
        security.migrateProtectedDeviceCiphertexts()
        ItadCredentialStore(context).readApiKey()
        for (name in listOf("steam_friend_chat_cache", "steam_group_chat_cache", "steam_chat_conversation_preferences")) {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            for ((key, value) in prefs.all) {
                currentCoroutineContext().ensureActive()
                if (!security.isVaultRuntimeUnlocked()) return
                if (value !is String) continue
                val replacement = runCatching { PortableLocalCipherMigration.portableCipher(value, security) }.getOrNull() ?: continue
                // Normal writers use the same preference-instance monitor.
                synchronized(prefs) {
                    if (security.isVaultRuntimeUnlocked() && prefs.getString(key, null) == value) {
                        check(prefs.edit().putString(key, replacement).commit())
                    }
                }
            }
        }
        for (directory in listOf(File(context.filesDir, "steam_play_activity"), File(context.noBackupFilesDir, "steam/library_cache"))) {
            for (file in directory.listFiles().orEmpty().filter { it.isFile && (it.name.endsWith(".json.enc") || it.name.endsWith(".json")) }) {
                currentCoroutineContext().ensureActive()
                if (!security.isVaultRuntimeUnlocked()) return
                SteamProtectedFileAccess.withFile(file) {
                    val atomic = AtomicFile(file)
                    val old = runCatching { atomic.readFully().toString(Charsets.UTF_8) }.getOrNull() ?: return@withFile
                    val replacement = runCatching { PortableLocalCipherMigration.portableCipher(old, security) }.getOrNull() ?: return@withFile
                    if (!security.isVaultRuntimeUnlocked()) return@withFile
                    val stream = atomic.startWrite()
                    try {
                        stream.write(replacement.toByteArray(Charsets.UTF_8))
                        atomic.finishWrite(stream)
                    } catch (error: Throwable) {
                        atomic.failWrite(stream)
                        throw error
                    }
                }
            }
        }
    }
}
