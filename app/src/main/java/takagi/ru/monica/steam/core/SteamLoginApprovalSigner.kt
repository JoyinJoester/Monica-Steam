package takagi.ru.monica.steam.core

import java.util.Base64

object SteamLoginApprovalSigner {
    fun signature(
        sharedSecretBase64: String,
        version: Int,
        clientId: Long,
        steamId: Long
    ): ByteArray {
        val key = Base64.getDecoder().decode(sharedSecretBase64.trim())
        val payload = littleEndian16(version) + littleEndian64(clientId) + littleEndian64(steamId)
        return SteamTotp.hmac("HmacSHA256", key, payload)
    }

    fun tokenSignature(
        sharedSecretBase64: String,
        tokenId: Long
    ): ByteArray {
        val key = Base64.getDecoder().decode(sharedSecretBase64.trim())
        return SteamTotp.hmac("HmacSHA256", key, littleEndian64(tokenId))
    }

    private fun littleEndian16(value: Int): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        ((value shr 8) and 0xff).toByte()
    )

    private fun littleEndian64(value: Long): ByteArray {
        var current = value
        return ByteArray(8) { index ->
            val byte = (current and 0xffL).toByte()
            current = current shr 8
            byte
        }
    }
}
