package takagi.ru.monica.steam.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SteamTotp {
    private const val CODE_CHARS = "23456789BCDFGHJKMNPQRTVWXY"

    fun generateAuthCode(sharedSecretBase64: String, unixTimeSeconds: Long): String {
        val key = Base64.getDecoder().decode(sharedSecretBase64.trim())
        val counter = unixTimeSeconds / 30L
        val timeBytes = ByteBuffer.allocate(8)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(counter)
            .array()
        val digest = hmac("HmacSHA1", key, timeBytes)
        val start = digest[19].toInt() and 0x0f
        var fullCode = ((digest[start].toInt() and 0x7f) shl 24) or
            ((digest[start + 1].toInt() and 0xff) shl 16) or
            ((digest[start + 2].toInt() and 0xff) shl 8) or
            (digest[start + 3].toInt() and 0xff)

        return buildString {
            repeat(5) {
                append(CODE_CHARS[fullCode % CODE_CHARS.length])
                fullCode /= CODE_CHARS.length
            }
        }
    }

    fun generateConfirmationHash(
        identitySecretBase64: String,
        unixTimeSeconds: Long,
        tag: String
    ): String {
        val key = Base64.getDecoder().decode(identitySecretBase64.trim())
        val tagBytes = tag.take(32).toByteArray(Charsets.UTF_8)
        val timeBytes = ByteBuffer.allocate(8)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(unixTimeSeconds)
            .array()
        val payload = timeBytes + tagBytes
        return Base64.getEncoder().encodeToString(hmac("HmacSHA1", key, payload))
    }

    fun secondsRemaining(unixTimeSeconds: Long): Int = (30L - (unixTimeSeconds % 30L)).toInt()

    internal fun hmac(algorithm: String, key: ByteArray, payload: ByteArray): ByteArray {
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(key, algorithm))
        return mac.doFinal(payload)
    }
}
