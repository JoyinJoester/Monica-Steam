package takagi.ru.monica.security

import kotlin.math.roundToInt

data class SecurityScoreInput(
    val totalPasswords: Int,
    val duplicatePasswordExtras: Int = 0,
    val duplicateUrlExtras: Int = 0,
    val weakPasswords: Int = 0,
    val mediumPasswords: Int = 0,
    val compromisedPasswords: Int = 0,
    val accountsMissing2FA: Int = 0,
    val inactivePasskeys: Int = 0,
)

object SecurityScoreCalculator {
    fun calculate(input: SecurityScoreInput): Int {
        val total = input.totalPasswords.coerceAtLeast(1)
        val duplicatePasswordPenalty = proportionalPenalty(
            count = input.duplicatePasswordExtras,
            total = total,
            maximum = 30,
            minimumWhenPresent = 2,
        )
        val compromisedPenalty = proportionalPenalty(
            count = input.compromisedPasswords,
            total = total,
            maximum = 35,
            minimumWhenPresent = 8,
        )
        val weakPenalty = proportionalPenalty(
            count = input.weakPasswords,
            total = total,
            maximum = 25,
            minimumWhenPresent = 2,
        )
        val mediumPenalty = proportionalPenalty(
            count = input.mediumPasswords,
            total = total,
            maximum = 8,
            minimumWhenPresent = 1,
        )
        val twoFactorPenalty = proportionalPenalty(
            count = input.accountsMissing2FA,
            total = total,
            maximum = 8,
            minimumWhenPresent = 1,
        )
        val inactivePasskeyPenalty = proportionalPenalty(
            count = input.inactivePasskeys,
            total = total,
            maximum = 3,
            minimumWhenPresent = 1,
        )
        val duplicateUrlPenalty = proportionalPenalty(
            count = input.duplicateUrlExtras,
            total = total,
            maximum = 3,
            minimumWhenPresent = 1,
        )

        val totalPenalty = duplicatePasswordPenalty +
            compromisedPenalty +
            weakPenalty +
            mediumPenalty +
            twoFactorPenalty +
            inactivePasskeyPenalty +
            duplicateUrlPenalty

        return (100 - totalPenalty).coerceIn(0, 100)
    }

    private fun proportionalPenalty(
        count: Int,
        total: Int,
        maximum: Int,
        minimumWhenPresent: Int,
    ): Int {
        if (count <= 0) return 0
        val boundedCount = count.coerceAtMost(total)
        val proportional = (boundedCount.toDouble() / total * maximum).roundToInt()
        return proportional.coerceIn(minimumWhenPresent, maximum)
    }
}
