package takagi.ru.monica.steam.itad.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SecurePreferencesStore
import takagi.ru.monica.steam.itad.domain.ItadApiKeyPolicy
import takagi.ru.monica.steam.itad.domain.ItadApiKeyValidationError

sealed interface ItadCredentialSaveResult {
    data object Saved : ItadCredentialSaveResult
    data object WriteFailed : ItadCredentialSaveResult
    data class Invalid(val error: ItadApiKeyValidationError) : ItadCredentialSaveResult
}

fun interface ItadApiKeyProvider {
    fun readApiKey(): String?
}

class ItadCredentialStore(context: Context) : ItadApiKeyProvider {
    private val applicationContext = context.applicationContext
    private val security by lazy { SecurityManager(applicationContext) }

    private val preferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SecurePreferencesStore.preflight(applicationContext, PREFERENCES_NAME)
        val masterKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            applicationContext,
            PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun readApiKey(): String? {
        return runCatching {
            if (security.getProtectedString(MIGRATED_KEY) != null) {
                security.getProtectedString(RECOVERABLE_KEY)
            } else {
                val old = preferences.getString(API_KEY, null)
                    ?.let { ItadApiKeyPolicy.validate(it).normalizedKey }
                security.putProtectedStrings(mapOf(RECOVERABLE_KEY to old, MIGRATED_KEY to "1"))
                old
            }
        }.getOrNull()?.let { ItadApiKeyPolicy.validate(it).normalizedKey }
    }

    fun saveApiKey(rawKey: String): ItadCredentialSaveResult {
        val validation = ItadApiKeyPolicy.validate(rawKey)
        val normalized = validation.normalizedKey
            ?: return ItadCredentialSaveResult.Invalid(
                validation.error ?: ItadApiKeyValidationError.EMPTY
            )
        return if (runCatching {
            security.putProtectedStrings(mapOf(RECOVERABLE_KEY to normalized, MIGRATED_KEY to "1"))
        }.isSuccess) {
            ItadCredentialSaveResult.Saved
        } else {
            ItadCredentialSaveResult.WriteFailed
        }
    }

    fun clearApiKey(): Boolean = runCatching {
        security.putProtectedStrings(mapOf(RECOVERABLE_KEY to null, MIGRATED_KEY to "1"))
    }.isSuccess

    private companion object {
        const val PREFERENCES_NAME = "monica_itad_credentials"
        const val API_KEY = "api_key"
        const val RECOVERABLE_KEY = "steam_itad_api_key_v1"
        const val MIGRATED_KEY = "steam_itad_api_key_migrated_v1"
    }
}
