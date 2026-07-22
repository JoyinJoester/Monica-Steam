package takagi.ru.monica.autofill_ng

import java.util.Locale
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.Accuracy
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.FieldHint

internal object AutofillDetectionPolicy {
    private val usernameLabelTranslations = listOf(
        "nickname",
        "username",
        "utilisateur",
        "login",
        "логин",
        "логін",
        "користувач",
        "пользовател",
        "用户名",
        "用戶名",
        "id",
        "customer",
    )

    fun genericNumberFallbackAccuracy(): Accuracy = Accuracy.LOW

    fun shouldKeepTarget(
        hint: FieldHint,
        accuracy: Accuracy,
        hasPasswordTarget: Boolean,
        manualRequest: Boolean,
    ): Boolean {
        if (manualRequest) return true
        if (!isAccountHint(hint)) return true
        return accuracy.score >= Accuracy.MEDIUM.score || hasPasswordTarget
    }

    fun shouldIncludeHiddenCredential(
        hint: FieldHint,
        accuracy: Accuracy,
    ): Boolean {
        val credentialHint = isAccountHint(hint) || isPasswordHint(hint)
        return credentialHint && accuracy.score >= Accuracy.MEDIUM.score
    }

    fun matchesUsernameLabel(value: String): Boolean {
        val normalized = value.lowercase(Locale.ENGLISH).trim()
        if (normalized.isBlank()) return false
        return usernameLabelTranslations.any { translation ->
            if (translation == "id") {
                normalized
                    .split(Regex("[^\\p{L}\\p{N}]+"))
                    .any { token -> token == translation }
            } else {
                translation in normalized
            }
        }
    }

    private fun isAccountHint(hint: FieldHint): Boolean =
        hint == FieldHint.USERNAME ||
            hint == FieldHint.EMAIL_ADDRESS ||
            hint == FieldHint.PHONE_NUMBER

    private fun isPasswordHint(hint: FieldHint): Boolean =
        hint == FieldHint.PASSWORD || hint == FieldHint.NEW_PASSWORD
}
