package takagi.ru.monica.steam.data

enum class SteamSecurityEventType {
    HEALTH_CHECK,
    SESSION_CHANGED,
    DEVICE_CHANGED,
    CONFIRMATION_ACTION,
    BACKUP_EXPORTED,
    BACKUP_RESTORED,
    MARKET_ALERT
}

enum class SteamSecurityEventSeverity {
    INFO,
    WARNING,
    CRITICAL
}

data class SteamSecurityEvent(
    val id: Long,
    val accountId: Long?,
    val type: SteamSecurityEventType,
    val severity: SteamSecurityEventSeverity,
    val summary: String,
    val detail: String?,
    val occurredAt: Long
)

object SteamSecurityEventRetention {
    const val MAX_EVENTS = 500
}

object SteamSecurityEventSanitizer {
    fun sanitize(value: String): String {
        return value.trim()
            .replace(
                Regex("\\bsteamid\\s*=\\s*[^\\s,]+", RegexOption.IGNORE_CASE),
                "steamid=<redacted>"
            )
            .replace(
                Regex(
                    "\\b(account|user|username|accountName|account_name)\\s*=\\s*[^\\s,]+",
                    RegexOption.IGNORE_CASE
                ),
                "$1=<redacted>"
            )
            .replace(
                Regex(
                    "\\b(password|pwd|passwd|token|access_token|refresh_token|shared_secret|identity_secret|revocation_code|code)\\s*=\\s*[^\\s,]+",
                    RegexOption.IGNORE_CASE
                ),
                "$1=***"
            )
            .replace(Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"), "<redacted>")
            .replace(Regex("\\b7656119\\d{10}\\b"), "<redacted>")
            .replace(Regex("\\b[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"), "***")
            .replace(Regex("[A-Za-z0-9+/]{28,}={0,2}"), "***")
            .take(1_000)
    }
}
