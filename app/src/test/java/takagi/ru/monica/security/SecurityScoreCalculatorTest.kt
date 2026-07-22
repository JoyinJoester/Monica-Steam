package takagi.ru.monica.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityScoreCalculatorTest {

    @Test
    fun perfectVaultKeepsFullScore() {
        assertEquals(
            100,
            SecurityScoreCalculator.calculate(
                SecurityScoreInput(totalPasswords = 100)
            )
        )
    }

    @Test
    fun duplicatePasswordsAloneCannotReduceScoreToZero() {
        val score = SecurityScoreCalculator.calculate(
            SecurityScoreInput(
                totalPasswords = 445,
                duplicatePasswordExtras = 444,
            )
        )

        assertEquals(70, score)
    }

    @Test
    fun duplicatePenaltyUsesRatioInsteadOfRawVaultSize() {
        val smallVaultScore = SecurityScoreCalculator.calculate(
            SecurityScoreInput(
                totalPasswords = 100,
                duplicatePasswordExtras = 50,
            )
        )
        val largeVaultScore = SecurityScoreCalculator.calculate(
            SecurityScoreInput(
                totalPasswords = 1_000,
                duplicatePasswordExtras = 500,
            )
        )

        assertEquals(smallVaultScore, largeVaultScore)
    }

    @Test
    fun compromisedPasswordsRemainHighPriority() {
        val duplicateOnlyScore = SecurityScoreCalculator.calculate(
            SecurityScoreInput(
                totalPasswords = 100,
                duplicatePasswordExtras = 10,
            )
        )
        val compromisedScore = SecurityScoreCalculator.calculate(
            SecurityScoreInput(
                totalPasswords = 100,
                compromisedPasswords = 10,
            )
        )

        assertTrue(compromisedScore < duplicateOnlyScore)
    }

    @Test
    fun aSingleCompromisedPasswordAlwaysHasVisibleImpact() {
        val score = SecurityScoreCalculator.calculate(
            SecurityScoreInput(
                totalPasswords = 1_000,
                compromisedPasswords = 1,
            )
        )

        assertEquals(92, score)
    }
}
