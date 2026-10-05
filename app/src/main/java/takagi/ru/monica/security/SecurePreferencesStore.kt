package takagi.ru.monica.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** Opening failure is never an instruction to delete keys, clear preferences or use plaintext. */
internal class SecureStorageUnavailableException(
    val store: String,
    val reason: String,
    cause: Exception? = null
) : Exception("Secure storage unavailable: $reason", cause)

internal object SecurePreferencesStore {
    const val MONICA = "monica_secure_prefs"
    const val BITWARDEN = "bitwarden_secure_prefs"
    private const val KEY_KEYSET = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
    private const val VALUE_KEYSET = "__androidx_security_crypto_encrypted_prefs_value_keyset__"

    data class Opened(val masterKey: MasterKey, val preferences: SharedPreferences)

    @Synchronized
    fun preflight(context: Context, name: String, alias: String = MasterKey.DEFAULT_MASTER_KEY_ALIAS) {
        try {
            val raw = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val contents = raw.all
            val file = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
            if (contents.isEmpty() && (file.exists() || File(file.path + ".bak").exists())) {
                // Android may expose an empty map after XML loading fails. That is not
                // evidence of a new installation, and must not overwrite the old file.
                throw SecureStorageUnavailableException(name, "EMPTY_OR_UNREADABLE_STORE")
            }
            if (contents.isNotEmpty()) {
                // Tink otherwise generates a missing keyset, making an already damaged store worse.
                if (raw.getString(KEY_KEYSET, null).isNullOrBlank() || raw.getString(VALUE_KEYSET, null).isNullOrBlank()) {
                    throw SecureStorageUnavailableException(name, "INCOMPLETE_KEYSET")
                }
                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                if (!keyStore.containsAlias(alias)) {
                    throw SecureStorageUnavailableException(name, "MISSING_MASTER_KEY")
                }
            }
        } catch (error: SecureStorageUnavailableException) {
            throw error
        } catch (error: Exception) {
            throw SecureStorageUnavailableException(name, "UNREADABLE_SECURE_STORAGE", error)
        }
    }

    fun open(context: Context, name: String, alias: String = MasterKey.DEFAULT_MASTER_KEY_ALIAS): Opened {
        if (name == MONICA && alias == MasterKey.DEFAULT_MASTER_KEY_ALIAS) {
            val recovery = LocalVaultRecovery(context)
            val active = recovery.activeStore()
            val opened = openRaw(context, active?.first ?: name, active?.second ?: alias)
            try { recovery.validate(opened.preferences) }
            catch (error: SecureStorageUnavailableException) { throw error }
            catch (error: Exception) {
                throw SecureStorageUnavailableException(MONICA, "RECOVERY_VALIDATION_FAILED", error)
            }
            return opened.copy(preferences = recovery.wrap(opened.preferences))
        }
        return openRaw(context, name, alias)
    }

    @Synchronized
    internal fun openRaw(context: Context, name: String, alias: String): Opened {
        try {
            preflight(context, name, alias)
            val masterKey = MasterKey.Builder(context, alias)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val prefs = EncryptedSharedPreferences.create(context, name, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
            // Keyset authentication alone does not detect an unreadable individual preference.
            // Validate before any caller can interpret missing credentials as a fresh installation.
            prefs.all
            return Opened(masterKey, prefs)
        } catch (error: SecureStorageUnavailableException) {
            throw error
        } catch (error: Exception) {
            val authenticationFailed = generateSequence<Throwable>(error) { it.cause }.take(8)
                .any { it is javax.crypto.AEADBadTagException }
            throw SecureStorageUnavailableException(name,
                if (authenticationFailed) "AUTHENTICATION_FAILED" else "UNREADABLE_SECURE_STORAGE", error)
        }
    }

    fun validateExisting(context: Context, name: String) {
        preflight(context, name)
        if (context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isNotEmpty()) open(context, name)
    }
}

internal sealed interface SecureStartupResult {
    data class Ready(val manager: SecurityManager) : SecureStartupResult
    data class Blocked(val failure: SecureStorageUnavailableException) : SecureStartupResult
}

internal object SecureStorageStartup {
    // Only post-launch maintenance uses this process gate. A failed open must not start it.
    private val readiness = MutableStateFlow(false)
    val readyForMaintenance: Boolean get() = readiness.value
    suspend fun awaitReadyForMaintenance(context: Context) {
        // Application may be started by a background worker without MainActivity.
        // Healthy storage must still permit its normal maintenance in that case.
        if (!readyForMaintenance) withContext(Dispatchers.IO) { prepare(context) }
        readiness.first { it }
    }

    @Synchronized
    fun prepare(context: Context): SecureStartupResult {
        readiness.value = false
        return try {
            // Do not create the shared legacy alias over an existing Bitwarden store.
            // An unreadable Bitwarden settings store must not block a healthy local vault.
            if (LocalVaultRecovery(context).activeStore() == null &&
                context.getSharedPreferences(SecurePreferencesStore.MONICA, Context.MODE_PRIVATE).all.isEmpty()) {
                SecurePreferencesStore.preflight(context, SecurePreferencesStore.BITWARDEN)
            }
            val manager = SecurityManager(context)
            readiness.value = true
            SecureStartupResult.Ready(manager)
        } catch (error: SecureStorageUnavailableException) {
            SecurityManager.clearRuntimeUnlockCache()
            SessionManager.markLocked()
            SecureStartupResult.Blocked(error)
        }
    }

    fun diagnostic(failure: SecureStorageUnavailableException): String =
        "Monica ${takagi.ru.monica.BuildConfig.VERSION_NAME}\nAndroid API ${android.os.Build.VERSION.SDK_INT}\n" +
            "Secure storage: ${failure.store}\nCode: ${failure.reason}\nNo automatic reset performed."
}
