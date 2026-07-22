package takagi.ru.monica.autofill_ng.auth

import android.os.SystemClock
import java.net.URI
import java.util.Locale

data class AutofillGrantContext(
    val packageName: String,
    val webDomain: String?,
    val interactionIdentifier: String?,
    val fieldSignatureKey: String?,
) {
    fun normalized(): AutofillGrantContext = copy(
        packageName = packageName.trim().lowercase(Locale.ROOT),
        webDomain = webDomain
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.removePrefix("www.")
            ?.trim('.')
            ?.takeIf { it.isNotBlank() },
        interactionIdentifier = interactionIdentifier
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() },
        fieldSignatureKey = fieldSignatureKey
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() },
    )

    companion object {
        fun fromRequestUri(
            packageName: String,
            requestUri: String?,
            interactionIdentifier: String?,
            fieldSignatureKey: String?,
        ): AutofillGrantContext {
            val parsedUri = requestUri
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { URI(it) }.getOrNull() }
            val webDomain = parsedUri
                ?.takeUnless { it.scheme.equals("androidapp", ignoreCase = true) }
                ?.host
            return AutofillGrantContext(
                packageName = packageName,
                webDomain = webDomain,
                interactionIdentifier = interactionIdentifier,
                fieldSignatureKey = fieldSignatureKey,
            ).normalized()
        }
    }
}

class AutofillSessionGrantStore(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private data class Grant(
        val context: AutofillGrantContext,
        val expiresAtMillis: Long,
    )

    @Volatile
    private var activeGrant: Grant? = null

    fun grant(context: AutofillGrantContext) {
        val now = elapsedRealtime()
        activeGrant = Grant(
            context = context.grantScope(),
            expiresAtMillis = now + ttlMillis,
        )
    }

    fun isGranted(context: AutofillGrantContext): Boolean {
        val grant = activeGrant ?: return false
        if (elapsedRealtime() >= grant.expiresAtMillis) {
            clear()
            return false
        }
        return grant.context == context.grantScope()
    }

    fun clear() {
        activeGrant = null
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 30_000L
    }
}

private fun AutofillGrantContext.grantScope(): AutofillGrantContext =
    normalized().copy(fieldSignatureKey = null)

object AutofillSessionGrants {
    private val store = AutofillSessionGrantStore()

    fun grant(context: AutofillGrantContext) = store.grant(context)

    fun isGranted(context: AutofillGrantContext): Boolean = store.isGranted(context)

    fun clear() = store.clear()
}

object AutofillAuthenticationPolicy {
    fun requiresResponseUnlock(
        authenticationRequired: Boolean,
        vaultLocked: Boolean,
        grantActive: Boolean,
    ): Boolean = authenticationRequired && vaultLocked && !grantActive
}
