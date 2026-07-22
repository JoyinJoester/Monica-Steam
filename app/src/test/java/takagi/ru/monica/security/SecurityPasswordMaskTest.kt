package takagi.ru.monica.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityPasswordMaskTest {

    @Test
    fun normalPasswordKeepsFirstAndLastCharacterWithFixedDots() {
        assertEquals("p••••••d", securityPasswordMask("password"))
        assertEquals("a••••••z", securityPasswordMask("az"))
    }

    @Test
    fun shortAndBlankPasswordsRemainStable() {
        assertEquals("a••••••", securityPasswordMask("a"))
        assertEquals("••••••", securityPasswordMask(""))
    }

    @Test
    fun maskLengthDoesNotRevealOriginalPasswordLength() {
        assertEquals(
            securityPasswordMask("12345678").length,
            securityPasswordMask("this-is-a-much-longer-password").length
        )
    }
}
