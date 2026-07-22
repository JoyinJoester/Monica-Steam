package takagi.ru.monica.steam.backup

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SteamBackupCryptoTest {
    @Test
    fun encryptDecryptRoundTrip() {
        val envelope = SteamBackupCrypto.encrypt("{\"accounts\":2}", "correct horse")
        assertEquals("{\"accounts\":2}", SteamBackupCrypto.decrypt(envelope, "correct horse"))
    }

    @Test
    fun wrongPasswordFailsAuthentication() {
        val envelope = SteamBackupCrypto.encrypt("payload", "correct horse")
        assertThrows(SteamBackupAuthenticationException::class.java) {
            SteamBackupCrypto.decrypt(envelope, "wrong horse")
        }
    }

    @Test
    fun ciphertextTamperingFailsAuthentication() {
        val envelope = SteamBackupCrypto.encrypt("payload", "correct horse")
        val root = Json.parseToJsonElement(envelope).jsonObject.toMutableMap()
        val bytes = Base64.getDecoder().decode(root.getValue("ciphertext").jsonPrimitive.content)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        root["ciphertext"] = JsonPrimitive(Base64.getEncoder().encodeToString(bytes))
        assertThrows(SteamBackupAuthenticationException::class.java) {
            SteamBackupCrypto.decrypt(JsonObject(root).toString(), "correct horse")
        }
    }

    @Test
    fun authenticatedMetadataTamperingIsRejected() {
        val envelope = SteamBackupCrypto.encrypt("payload", "correct horse")
        val root = Json.parseToJsonElement(envelope).jsonObject.toMutableMap()
        root["iterations"] = JsonPrimitive(SteamBackupCrypto.ITERATIONS + 1)
        assertThrows(SteamBackupAuthenticationException::class.java) {
            SteamBackupCrypto.decrypt(JsonObject(root).toString(), "correct horse")
        }
    }

    @Test
    fun malformedEnvelopeFailsFormat() {
        assertThrows(SteamBackupFormatException::class.java) {
            SteamBackupCrypto.decrypt("{}", "password")
        }
    }
}
