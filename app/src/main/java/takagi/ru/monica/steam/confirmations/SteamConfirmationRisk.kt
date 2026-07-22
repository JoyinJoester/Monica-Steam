package takagi.ru.monica.steam.confirmations

import takagi.ru.monica.steam.network.SteamConfirmation

enum class SteamConfirmationRiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

data class SteamConfirmationRisk(
    val level: SteamConfirmationRiskLevel,
    val reasons: List<SteamConfirmationRiskReason>
) {
    val requiresReview: Boolean
        get() = level != SteamConfirmationRiskLevel.LOW
}

enum class SteamConfirmationRiskReason {
    SENSITIVE_ACTION,
    FINANCIAL_CONTEXT,
    MISSING_CONTEXT,
    EXPIRED
}

object SteamConfirmationRiskEvaluator {
    private val sensitiveWords = setOf(
        "login", "sign in", "signin", "trade", "market", "sell", "purchase",
        "buy", "gift", "remove", "change", "authenticator", "password", "security"
    )
    private val amountPattern = Regex("(?:[$€£¥]|\\b(?:usd|eur|gbp|cny)\\b)\\s*\\d+(?:[.,]\\d+)?", RegexOption.IGNORE_CASE)

    fun evaluate(
        confirmation: SteamConfirmation,
        nowSeconds: Long = System.currentTimeMillis() / 1000L
    ): SteamConfirmationRisk {
        val context = "${confirmation.type} ${confirmation.headline} ${confirmation.summary}".lowercase()
        val reasons = buildList {
            if (sensitiveWords.any(context::contains)) add(SteamConfirmationRiskReason.SENSITIVE_ACTION)
            if (amountPattern.containsMatchIn(context)) add(SteamConfirmationRiskReason.FINANCIAL_CONTEXT)
            if (confirmation.type.isBlank() && confirmation.headline.isBlank() && confirmation.summary.isBlank()) {
                add(SteamConfirmationRiskReason.MISSING_CONTEXT)
            }
            if (confirmation.creationTime > 0L && nowSeconds - confirmation.creationTime > 10 * 60) {
                add(SteamConfirmationRiskReason.EXPIRED)
            }
        }
        val level = when {
            SteamConfirmationRiskReason.SENSITIVE_ACTION in reasons ||
                SteamConfirmationRiskReason.FINANCIAL_CONTEXT in reasons ||
                SteamConfirmationRiskReason.EXPIRED in reasons -> SteamConfirmationRiskLevel.HIGH
            SteamConfirmationRiskReason.MISSING_CONTEXT in reasons -> SteamConfirmationRiskLevel.MEDIUM
            else -> SteamConfirmationRiskLevel.LOW
        }
        return SteamConfirmationRisk(level = level, reasons = reasons)
    }
}
