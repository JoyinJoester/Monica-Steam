package takagi.ru.monica.steam.backup

import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

open class SteamBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

class SteamBackupAuthenticationException(cause: Throwable? = null) :
    SteamBackupException("备份密码错误或文件已被修改", cause)

class SteamBackupFormatException(message: String, cause: Throwable? = null) :
    SteamBackupException(message, cause)

/** Authenticated offline envelope. This format is separate from legacy maFile encryption. */
object SteamBackupCrypto {
    const val SCHEMA = 1
    const val KDF = "PBKDF2-HMAC-SHA256"
    const val CIPHER = "AES-256-GCM"
    const val ITERATIONS = 210_000

    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256
    private const val MAX_ITERATIONS = 2_000_000
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private val random = SecureRandom()

    fun encrypt(payload: String, password: String, secureRandom: SecureRandom = random): String {
        require(password.isNotEmpty()) { "备份密码不能为空" }
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
        val aad = aadBytes(SCHEMA, KDF, ITERATIONS)
        val cipherText = cipher(Cipher.ENCRYPT_MODE, password, salt, nonce, ITERATIONS, aad)
            .doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return json.encodeToString(
            buildJsonObject {
                put("schema", JsonPrimitive(SCHEMA))
                put("kdf", JsonPrimitive(KDF))
                put("cipher", JsonPrimitive(CIPHER))
                put("iterations", JsonPrimitive(ITERATIONS))
                put("salt", JsonPrimitive(Base64.getEncoder().encodeToString(salt)))
                put("nonce", JsonPrimitive(Base64.getEncoder().encodeToString(nonce)))
                put("ciphertext", JsonPrimitive(Base64.getEncoder().encodeToString(cipherText)))
            }
        )
    }

    @Throws(SteamBackupException::class)
    fun decrypt(envelope: String, password: String): String {
        if (password.isEmpty()) throw SteamBackupAuthenticationException()
        val root = try {
            json.parseToJsonElement(envelope).jsonObject
        } catch (error: Exception) {
            throw SteamBackupFormatException("备份文件不是有效 JSON", error)
        }
        val schema = root.requiredInt("schema")
        val kdf = root.requiredString("kdf")
        val cipherName = root.requiredString("cipher")
        val iterations = root.requiredInt("iterations")
        if (schema != SCHEMA || kdf != KDF || cipherName != CIPHER) {
            throw SteamBackupFormatException("不支持的备份格式")
        }
        if (iterations < ITERATIONS || iterations > MAX_ITERATIONS) {
            throw SteamBackupFormatException("备份迭代参数无效")
        }
        val salt = decodeBytes(root.requiredString("salt"), SALT_BYTES, "salt")
        val nonce = decodeBytes(root.requiredString("nonce"), NONCE_BYTES, "nonce")
        val cipherText = try {
            Base64.getDecoder().decode(root.requiredString("ciphertext"))
        } catch (error: IllegalArgumentException) {
            throw SteamBackupFormatException("备份密文无效", error)
        }
        if (cipherText.size < 16) throw SteamBackupFormatException("备份密文长度无效")
        return try {
            val plain = cipher(
                Cipher.DECRYPT_MODE,
                password,
                salt,
                nonce,
                iterations,
                aadBytes(schema, kdf, iterations)
            ).doFinal(cipherText)
            plain.toString(StandardCharsets.UTF_8)
        } catch (error: AEADBadTagException) {
            throw SteamBackupAuthenticationException(error)
        } catch (error: GeneralSecurityException) {
            throw SteamBackupAuthenticationException(error)
        }
    }

    private fun cipher(
        mode: Int,
        password: String,
        salt: ByteArray,
        nonce: ByteArray,
        iterations: Int,
        aad: ByteArray
    ): Cipher {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val keyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(aad)
            keyBytes.fill(0)
        }
    }

    private fun aadBytes(schema: Int, kdf: String, iterations: Int): ByteArray {
        return "schema=$schema;kdf=$kdf;cipher=$CIPHER;iterations=$iterations"
            .toByteArray(StandardCharsets.UTF_8)
    }

    private fun JsonObject.requiredString(name: String): String {
        return this[name]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: throw SteamBackupFormatException("备份字段缺失：$name")
    }

    private fun JsonObject.requiredInt(name: String): Int {
        return this[name]?.jsonPrimitive?.int
            ?: throw SteamBackupFormatException("备份字段无效：$name")
    }

    private fun decodeBytes(value: String, expectedSize: Int, field: String): ByteArray {
        val bytes = try {
            Base64.getDecoder().decode(value)
        } catch (error: IllegalArgumentException) {
            throw SteamBackupFormatException("备份字段无效：$field", error)
        }
        if (bytes.size != expectedSize) throw SteamBackupFormatException("备份字段长度无效：$field")
        return bytes
    }
}
