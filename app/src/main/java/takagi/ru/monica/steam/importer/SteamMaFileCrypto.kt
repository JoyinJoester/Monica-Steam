package takagi.ru.monica.steam.importer

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SteamMaFileCrypto {
    const val PBKDF2_ITERATIONS = 50_000
    private const val KEY_SIZE_BITS = 256

    fun deriveKey(
        password: String,
        saltBase64: String,
        iterations: Int = PBKDF2_ITERATIONS,
        keySizeBits: Int = KEY_SIZE_BITS
    ): ByteArray {
        require(password.isNotEmpty()) { "Password is empty" }
        require(saltBase64.isNotBlank()) { "Salt is empty" }
        val spec = PBEKeySpec(
            password.toCharArray(),
            Base64.getDecoder().decode(saltBase64),
            iterations,
            keySizeBits
        )
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(spec)
            .encoded
    }

    fun decrypt(
        password: String,
        saltBase64: String,
        ivBase64: String,
        encryptedBase64: String,
        iterations: Int = PBKDF2_ITERATIONS
    ): String? {
        return runCatching {
            val key = deriveKey(password, saltBase64, iterations)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                IvParameterSpec(Base64.getDecoder().decode(ivBase64))
            )
            String(cipher.doFinal(Base64.getDecoder().decode(encryptedBase64.trim())), Charsets.UTF_8)
        }.getOrNull()
    }

    fun encryptForTests(password: String, saltBase64: String, ivBase64: String, plaintext: String): String {
        val key = deriveKey(password, saltBase64)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            IvParameterSpec(Base64.getDecoder().decode(ivBase64))
        )
        return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
    }

    fun randomSaltBase64(): String = randomBase64(8)

    fun randomIvBase64(): String = randomBase64(16)

    private fun randomBase64(size: Int): String {
        val bytes = ByteArray(size)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }
}
