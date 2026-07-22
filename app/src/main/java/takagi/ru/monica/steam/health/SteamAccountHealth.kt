package takagi.ru.monica.steam.health

import java.util.Base64
import takagi.ru.monica.steam.data.SteamAccount

enum class SteamHealthStatus {
    HEALTHY,
    ATTENTION,
    CRITICAL,
    UNKNOWN
}

enum class SteamHealthCheckType {
    STEAM_ID,
    DEVICE_ID,
    SHARED_SECRET,
    IDENTITY_SECRET,
    REVOCATION_CODE,
    SESSION,
    CLOCK
}

data class SteamHealthCheck(
    val type: SteamHealthCheckType,
    val status: SteamHealthStatus,
    val recoveryAction: String? = null
)

data class SteamAccountHealthReport(
    val accountId: Long,
    val status: SteamHealthStatus,
    val checks: List<SteamHealthCheck>,
    val checkedAt: Long,
    val clockOffsetSeconds: Long?,
    val clockStatus: SteamHealthStatus
)

data class SteamClockSnapshot(
    val currentStatus: SteamHealthStatus = SteamHealthStatus.UNKNOWN,
    val currentOffsetSeconds: Long? = null,
    val lastSuccessfulOffsetSeconds: Long? = null,
    val lastSuccessfulAt: Long? = null
) {
    companion object {
        fun merge(
            previous: SteamClockSnapshot,
            checkedAt: Long,
            serverTimeSeconds: Long?
        ): SteamClockSnapshot {
            val offset = serverTimeSeconds?.minus(checkedAt / 1_000L)
            if (offset == null) {
                return previous.copy(
                    currentStatus = SteamHealthStatus.UNKNOWN,
                    currentOffsetSeconds = null
                )
            }
            return SteamClockSnapshot(
                currentStatus = SteamClockHealth.statusForOffset(offset),
                currentOffsetSeconds = offset,
                lastSuccessfulOffsetSeconds = offset,
                lastSuccessfulAt = checkedAt
            )
        }
    }
}

object SteamClockHealth {
    fun statusForOffset(offsetSeconds: Long): SteamHealthStatus {
        return when {
            kotlin.math.abs(offsetSeconds) > 120L -> SteamHealthStatus.CRITICAL
            kotlin.math.abs(offsetSeconds) > 30L -> SteamHealthStatus.ATTENTION
            else -> SteamHealthStatus.HEALTHY
        }
    }
}

object SteamAccountHealthEvaluator {
    fun evaluate(
        account: SteamAccount,
        checkedAt: Long,
        serverTimeSeconds: Long?
    ): SteamAccountHealthReport {
        val clockOffsetSeconds = serverTimeSeconds?.minus(checkedAt / 1_000L)
        val clockStatus = clockOffsetSeconds
            ?.let(SteamClockHealth::statusForOffset)
            ?: SteamHealthStatus.UNKNOWN
        val checks = listOf(
            check(
                type = SteamHealthCheckType.STEAM_ID,
                valid = account.hasRealSteamId,
                invalidStatus = SteamHealthStatus.ATTENTION,
                recoveryAction = "Complete the Steam sign-in to restore the SteamID."
            ),
            check(
                type = SteamHealthCheckType.DEVICE_ID,
                valid = DEVICE_ID_PATTERN.matches(account.deviceId),
                invalidStatus = SteamHealthStatus.ATTENTION,
                recoveryAction = "Re-import the authenticator data to restore the device ID."
            ),
            check(
                type = SteamHealthCheckType.SHARED_SECRET,
                valid = isSteamSecret(account.sharedSecret),
                invalidStatus = SteamHealthStatus.CRITICAL,
                recoveryAction = "Restore a valid shared secret before using Steam Guard codes."
            ),
            check(
                type = SteamHealthCheckType.IDENTITY_SECRET,
                valid = isSteamSecret(account.identitySecret),
                invalidStatus = SteamHealthStatus.ATTENTION,
                recoveryAction = "Import the identity secret to enable confirmations."
            ),
            check(
                type = SteamHealthCheckType.REVOCATION_CODE,
                valid = !account.revocationCode.isNullOrBlank(),
                invalidStatus = SteamHealthStatus.ATTENTION,
                recoveryAction = "Store the recovery code in a protected backup."
            ),
            check(
                type = SteamHealthCheckType.SESSION,
                valid = !account.accessToken.isNullOrBlank() || !account.refreshToken.isNullOrBlank(),
                invalidStatus = SteamHealthStatus.ATTENTION,
                recoveryAction = "Sign in to Steam again to refresh the session."
            ),
            SteamHealthCheck(
                type = SteamHealthCheckType.CLOCK,
                status = clockStatus,
                recoveryAction = when (clockStatus) {
                    SteamHealthStatus.HEALTHY -> null
                    SteamHealthStatus.UNKNOWN -> "Connect to the internet and retry the server time check."
                    else -> "Enable automatic date and time in Android settings."
                }
            )
        )

        return SteamAccountHealthReport(
            accountId = account.id,
            status = aggregateStatus(checks.map(SteamHealthCheck::status)),
            checks = checks,
            checkedAt = checkedAt,
            clockOffsetSeconds = clockOffsetSeconds,
            clockStatus = clockStatus
        )
    }

    private fun check(
        type: SteamHealthCheckType,
        valid: Boolean,
        invalidStatus: SteamHealthStatus,
        recoveryAction: String
    ): SteamHealthCheck {
        return SteamHealthCheck(
            type = type,
            status = if (valid) SteamHealthStatus.HEALTHY else invalidStatus,
            recoveryAction = recoveryAction.takeUnless { valid }
        )
    }

    private fun aggregateStatus(statuses: List<SteamHealthStatus>): SteamHealthStatus {
        return when {
            SteamHealthStatus.CRITICAL in statuses -> SteamHealthStatus.CRITICAL
            SteamHealthStatus.ATTENTION in statuses -> SteamHealthStatus.ATTENTION
            SteamHealthStatus.UNKNOWN in statuses -> SteamHealthStatus.UNKNOWN
            else -> SteamHealthStatus.HEALTHY
        }
    }

    private fun isSteamSecret(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return runCatching { Base64.getDecoder().decode(value).size == STEAM_SECRET_BYTES }
            .getOrDefault(false)
    }

    private const val STEAM_SECRET_BYTES = 20
    private val DEVICE_ID_PATTERN = Regex(
        """android:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"""
    )
}

object SteamHealthDiagnosticFormatter {
    fun format(
        reports: List<SteamAccountHealthReport>,
        generatedAt: Long,
        appVersion: String,
        androidApi: Int
    ): String {
        val statusCounts = SteamHealthStatus.entries.associateWith { status ->
            reports.count { it.status == status }
        }
        val checkCounts = SteamHealthCheckType.entries.associateWith { type ->
            reports.flatMap(SteamAccountHealthReport::checks)
                .groupingBy(SteamHealthCheck::type)
                .eachCount()[type] ?: 0
        }
        return buildString {
            appendLine("monica_steam_health_diagnostic=1")
            appendLine("generated_at_ms=$generatedAt")
            appendLine("app_version=${sanitizeMetadata(appVersion)}")
            appendLine("android_api=$androidApi")
            appendLine("accounts_total=${reports.size}")
            appendLine("healthy=${statusCounts.getValue(SteamHealthStatus.HEALTHY)}")
            appendLine("attention=${statusCounts.getValue(SteamHealthStatus.ATTENTION)}")
            appendLine("critical=${statusCounts.getValue(SteamHealthStatus.CRITICAL)}")
            appendLine("unknown=${statusCounts.getValue(SteamHealthStatus.UNKNOWN)}")
            SteamHealthCheckType.entries.forEach { type ->
                appendLine("checks_${type.name.lowercase()}=${checkCounts.getValue(type)}")
            }
        }
    }

    private fun sanitizeMetadata(value: String): String {
        return value.filter { it.isLetterOrDigit() || it in ".-_+" }.take(64)
    }
}
