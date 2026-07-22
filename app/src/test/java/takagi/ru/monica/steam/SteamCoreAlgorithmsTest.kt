package takagi.ru.monica.steam

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.core.SteamLoginApprovalSigner
import takagi.ru.monica.steam.core.SteamTotp

class SteamCoreAlgorithmsTest {
    private val sharedSecret = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA="
    private val identitySecret = "YWJjZGVmZ2hpamtsbW5vcHFyc3Q="

    @Test
    fun steamGuardCodeMatchesReferenceVectors() {
        assertEquals("R87JJ", SteamTotp.generateAuthCode(sharedSecret, 1_700_000_000L))
        assertEquals("D6C35", SteamTotp.generateAuthCode(sharedSecret, 1_634_000_000L))
    }

    @Test
    fun confirmationHashMatchesReferenceVectors() {
        assertEquals(
            "lARGXtefNbogvcyP7DZJI0+XBYQ=",
            SteamTotp.generateConfirmationHash(identitySecret, 1_700_000_000L, "conf")
        )
        assertEquals(
            "v53H1MfBVFOCKLLTFJpiE7RCHWY=",
            SteamTotp.generateConfirmationHash(identitySecret, 1_700_000_000L, "allow")
        )
    }

    @Test
    fun loginApprovalSignatureUsesLittleEndianTuple() {
        val signature = SteamLoginApprovalSigner.signature(
            sharedSecretBase64 = sharedSecret,
            version = 2,
            clientId = 123_456_789_012_345_678L,
            steamId = 76_561_198_000_000_000L
        )

        assertEquals(
            "xz4nnjj/r+HksuFxcmG2w7eSz6ozdNagNzlVs3FKVIQ=",
            Base64.getEncoder().encodeToString(signature)
        )
    }
}
