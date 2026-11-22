package takagi.ru.monica.steam.scanner

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Process
import android.util.Base64
import android.util.Xml
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.navigation.SteamDockPreferences
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.SteamDockTab
import takagi.ru.monica.utils.SettingsManager

/**
 * Separate, opt-in diagnosis of the preserved FIRST failed upgrade verification.
 * Does not rerun password verification, edit preferences, replace files, or create keys.
 * The original exact-file-hash failure is retained and must not be replaced by this test.
 */
class SteamToolchainPersistenceDiagnosticTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val marker get() = File(context.filesDir, "toolchain-persistence-seed-v1.json")
    private val protectedPaths = listOf(SECURE_PREFS, SETTINGS_DATASTORE, DOCK_DATASTORE)

    @Test fun firstReadOnlyRefreshedReadyFlagAndCompatWrapperOfTheSameMdk() = runBlocking {
        check(context.packageName == "takagi.ru.monica.steamapp.toolchainpilot")
        check(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        check(arguments.getString("persistencePhase") == "diagnose-first-read")
        val seedMarkerSha = requiredSha("seedMarkerSha256")
        val firstReadPrefsSha = requiredSha("firstReadSecurePrefsSha256")
        val oldApkSha = requiredSha("oldApkSha256")
        val markerBytes = readRequired(marker)
        assertEquals("Original seed marker hash", seedMarkerSha, sha(markerBytes))
        val seed = JSONObject(markerBytes.toString(Charsets.UTF_8))
        assertEquals(1, seed.getInt("format"))
        assertEquals("old_seed_complete", seed.getString("phase"))
        assertEquals(oldApkSha, seed.getString("oldApkSha256"))
        assertNotEquals("Diagnosis must use a different process than the seed", seed.getInt("seedPid"), Process.myPid())

        // This is an instrumentation APK asset, never copied over the live preferences.
        val oldXml = instrumentation.context.assets.open(OLD_XML_ASSET).use { it.readBytes() }
        check(oldXml.isNotEmpty() && oldXml.size <= 1024 * 1024)
        assertEquals("Old fixture must be bound to the original seed", seed.getJSONObject("fileHashes").getString(SECURE_PREFS), sha(oldXml))
        val before = protectedPaths.associateWith { readRequired(fileAt(it)) }
        assertEquals("Inspect the exact preserved first-read result", firstReadPrefsSha, sha(before.getValue(SECURE_PREFS)))
        assertNotEquals("The first-read hash failure must actually be present", sha(oldXml), firstReadPrefsSha)
        listOf(SETTINGS_DATASTORE, DOCK_DATASTORE).forEach { path ->
            assertEquals("Every original DataStore byte must remain: $path", seed.getJSONObject("fileHashes").getString(path), sha(before.getValue(path)))
        }
        val oldEntries = parseStringPreferences(oldXml)
        val newEntries = parseStringPreferences(before.getValue(SECURE_PREFS))
        assertTrue("XML key membership must be exactly unchanged", oldEntries.keys == newEntries.keys)
        KEYSET_NAMES.forEach { key ->
            assertTrue("Both original encrypted keysets must exist", oldEntries.containsKey(key))
            assertTrue("An encrypted keyset changed", oldEntries.getValue(key) == newEntries.getValue(key))
        }

        // Existing constructor only; no getOrGenerate* helper is invoked by this test.
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        check(keyStore.containsAlias("_androidx_security_master_key_")) { "Original preferences key must already exist" }
        check(keyStore.containsAlias(COMPAT_ALIAS)) { "Original compatibility key must already exist" }
        val compatEntry = keyStore.getEntry(COMPAT_ALIAS, null) as? KeyStore.SecretKeyEntry
        checkNotNull(compatEntry) { "Original compatibility key must be readable" }
        val security = SecurityManager(context)
        val prefs = SecurityManager::class.java.getDeclaredField("sharedPreferences").apply { isAccessible = true }
            .get(security) as SharedPreferences
        check(prefs.javaClass.name == "androidx.security.crypto.EncryptedSharedPreferences")
        val encryptKey = prefs.javaClass.getDeclaredMethod("encryptKey", String::class.java).apply { isAccessible = true }
        fun encryptedKey(plain: String) = encryptKey.invoke(prefs, plain) as String
        val readyKey = encryptedKey("mdk_ready")
        val wrapperKey = encryptedKey("mdk_keystore_blob")
        val allowed = setOf(readyKey, wrapperKey)
        assertEquals("The two allowed encrypted keys must be distinct", 2, allowed.size)
        assertTrue("Both known keys must exist in the old fixture", oldEntries.keys.containsAll(allowed))
        val changed = oldEntries.keys.filter { oldEntries.getValue(it) != newEntries.getValue(it) }.toSet()
        assertTrue("Exactly mdk_ready and mdk_keystore_blob ciphertexts must have changed", changed == allowed)
        oldEntries.keys.filterNot { it in allowed }.forEach { key ->
            assertTrue("An unapproved encrypted XML value changed", oldEntries.getValue(key) == newEntries.getValue(key))
        }

        val valueAead = prefs.javaClass.getDeclaredField("mValueAead").apply { isAccessible = true }.get(prefs)
        val decryptValue = Class.forName("com.google.crypto.tink.Aead", true, context.classLoader)
            .getMethod("decrypt", ByteArray::class.java, ByteArray::class.java)
        fun serialized(entries: Map<String, String>, key: String): ByteArray = decryptValue.invoke(
            valueAead, Base64.decode(entries.getValue(key), Base64.DEFAULT), key.toByteArray(Charsets.UTF_8)
        ) as ByteArray
        fun stringPreference(entries: Map<String, String>, plainKey: String): String {
            val bytes = serialized(entries, encryptedKey(plainKey))
            try {
                val buffer = ByteBuffer.wrap(bytes)
                check(buffer.remaining() >= 8 && buffer.int == 0) { "Expected a serialized string preference" }
                val size = buffer.int
                check(size >= 0 && size == buffer.remaining()) { "Unexpected serialized string length" }
                val text = ByteArray(size)
                try {
                    buffer.get(text)
                    return text.toString(Charsets.UTF_8)
                } finally {
                    text.fill(0)
                }
            } finally {
                bytes.fill(0)
            }
        }
        // Authenticate every old/new encrypted value with the current keyset, not merely its XML hash.
        oldEntries.keys.filterNot { it in KEYSET_NAMES }.forEach { key ->
            val oldValue = serialized(oldEntries, key)
            val newValue = serialized(newEntries, key)
            try {
                if (key != wrapperKey) {
                    assertTrue("A stored preference's serialized plaintext changed", MessageDigest.isEqual(oldValue, newValue))
                }
                if (key == readyKey) {
                    assertTrue("Old and new mdk_ready must both be boolean true", oldValue.contentEquals(byteArrayOf(0, 0, 0, 5, 1)))
                }
            } finally {
                oldValue.fill(0)
                newValue.fill(0)
            }
        }
        assertTrue("Current preferences retain mdk_ready", prefs.getBoolean("mdk_ready", false))
        val oldWrapper = stringPreference(oldEntries, "mdk_keystore_blob")
        val newWrapper = stringPreference(newEntries, "mdk_keystore_blob")
        assertTrue("Live preferences must match the preserved current XML", newWrapper == prefs.getString("mdk_keystore_blob", null))
        assertTrue("Both envelopes must be compatibility wrappers", oldWrapper.startsWith("CP|") && newWrapper.startsWith("CP|"))
        assertTrue("The inner compatibility wrapper was actually refreshed", oldWrapper != newWrapper)

        val oldMdk = decryptGcm(compatEntry.secretKey, oldWrapper.removePrefix("CP|"))
        val newMdk = decryptGcm(compatEntry.secretKey, newWrapper.removePrefix("CP|"))
        val passwordSalt = decodeHex(stringPreference(oldEntries, "mdk_password_salt"))
        val passwordSpec = PBEKeySpec(PASSWORD.toCharArray(), passwordSalt, 100000, 256)
        val passwordKeyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(passwordSpec).encoded
        val passwordMdk = try {
            decryptGcm(SecretKeySpec(passwordKeyBytes, "AES"), stringPreference(oldEntries, "mdk_password_blob"))
        } finally {
            passwordSpec.clearPassword()
            passwordSalt.fill(0)
            passwordKeyBytes.fill(0)
        }
        try {
            assertTrue("All three envelopes must contain a complete 256-bit MDK", oldMdk.size == 32 && newMdk.size == 32 && passwordMdk.size == 32)
            // Fixed failure labels intentionally avoid dumping secret byte arrays or their hashes.
            assertTrue("Original and refreshed compatibility wrappers must contain the same MDK", MessageDigest.isEqual(oldMdk, newMdk))
            assertTrue("The unchanged password wrapper must contain that same actual MDK", MessageDigest.isEqual(oldMdk, passwordMdk))
        } finally {
            oldMdk.fill(0)
            newMdk.fill(0)
            passwordMdk.fill(0)
        }

        // Password proof without verifyMasterPassword, which would refresh the very evidence under diagnosis.
        assertTrue("The original password is still configured", security.isMasterPasswordSet())
        val masterSalt = decodeHex(stringPreference(oldEntries, "master_password_salt"))
        try {
            val originalHash = stringPreference(oldEntries, "master_password_hash")
            assertTrue("The original password proof must match", security.hashMasterPassword(PASSWORD, masterSalt).first == originalHash)
            assertTrue("An incorrect password must not match", security.hashMasterPassword("incorrect-synthetic-password", masterSalt).first != originalHash)
        } finally {
            masterSalt.fill(0)
        }
        assertTrue("Original ciphertext must still decrypt", security.decryptData(seed.getString("ciphertext")) == PLAINTEXT)
        val dao = SteamDatabase.getDatabase(context).steamAccountDao()
        assertEquals("Only the original synthetic account exists", 1, dao.count())
        val accountId = seed.getLong("accountId")
        val cipherAccount = requireNotNull(dao.getById(accountId))
        assertEquals("Every original encrypted account field remains", seed.getString("accountCipherSha256"), sha(cipherAccount.toString().toByteArray()))
        val account = requireNotNull(SteamAccountRepository(dao, security).getAccount(accountId))
        assertEquals("Every original plaintext account field remains", seed.getString("accountPlainSha256"), sha(account.toString().toByteArray()))
        val settings = SettingsManager(context).settingsFlow.first()
        assertTrue(settings.steamLockTokenPageOnly)
        assertFalse(settings.disablePasswordVerification)
        assertFalse(settings.steamGuardCodeGroupingEnabled)
        val dock = SteamDockPreferences(context).configuration.first()
        assertEquals(SteamDockStyle.LIQUID_GLASS, dock.style)
        assertEquals(listOf(SteamDockTab.TOKEN, SteamDockTab.SETTINGS, SteamDockTab.CHAT, SteamDockTab.LIBRARY, SteamDockTab.STORE), dock.fixedOrder)
        assertEquals(SteamDockTab.LIQUID_GLASS_DEFAULT_ORDER.reversed(), dock.liquidGlassOrder)

        // Do not merely wait until a changed file settles: every sample must equal the entry snapshot.
        repeat(6) {
            protectedPaths.forEach { path ->
                assertTrue("Diagnosis rewrote protected bytes: $path", before.getValue(path).contentEquals(readRequired(fileAt(path))))
            }
            assertTrue("The seed marker must remain byte-identical", markerBytes.contentEquals(readRequired(marker)))
            delay(50)
        }
        println("PERSISTENCE_DIAGNOSE exact_two_fields=true ready=true same_actual_mdk=true keysets=true password_proof=true ciphertext=true account=true datastore=true read_only=true seed_marker=$seedMarkerSha")
    }

    private fun parseStringPreferences(bytes: ByteArray): Map<String, String> {
        val parser = Xml.newPullParser().apply { setInput(bytes.inputStream(), "UTF-8") }
        check(parser.nextTag() == XmlPullParser.START_TAG && parser.name == "map" && parser.attributeCount == 0)
        val result = linkedMapOf<String, String>()
        while (parser.nextTag() == XmlPullParser.START_TAG) {
            check(parser.name == "string" && parser.attributeCount == 1 && parser.getAttributeName(0) == "name") { "Unexpected XML preference shape" }
            val name = requireNotNull(parser.getAttributeValue(null, "name"))
            check(!result.containsKey(name)) { "Duplicate XML preference key" }
            result[name] = parser.nextText()
        }
        check(parser.eventType == XmlPullParser.END_TAG && parser.name == "map")
        var trailing = parser.next()
        while (trailing == XmlPullParser.TEXT && parser.isWhitespace) trailing = parser.next()
        check(trailing == XmlPullParser.END_DOCUMENT)
        return result
    }

    private fun decryptGcm(key: SecretKey, payload: String): ByteArray {
        val combined = Base64.decode(payload, Base64.NO_WRAP)
        try {
            check(combined.size == 12 + 32 + 16) { "Expected a 32-byte MDK AES-GCM envelope" }
            return Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, combined, 0, 12))
                doFinal(combined, 12, combined.size - 12)
            }
        } finally {
            combined.fill(0)
        }
    }

    private fun decodeHex(hex: String): ByteArray {
        check(hex.isNotEmpty() && hex.length % 2 == 0 && hex.all { it in '0'..'9' || it in 'a'..'f' }) { "Malformed original salt" }
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun fileAt(relative: String) = File(context.applicationInfo.dataDir, relative)
    private fun readRequired(file: File): ByteArray {
        check(file.isFile && file.length() in 1..(1024 * 1024)) { "Required bounded fixture file is missing" }
        return file.readBytes()
    }
    private fun requiredSha(name: String): String = requireNotNull(arguments.getString(name)).also {
        check(it.matches(Regex("[0-9a-f]{64}"))) { "A complete SHA-256 argument is required" }
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val SECURE_PREFS = "shared_prefs/monica_secure_prefs.xml"
        private const val SETTINGS_DATASTORE = "files/datastore/settings.preferences_pb"
        private const val DOCK_DATASTORE = "files/datastore/monica_steam_dock.preferences_pb"
        private const val OLD_XML_ASSET = "toolchain-persistence/old-monica-secure-prefs.xml"
        private const val COMPAT_ALIAS = "monica_data_key_v2_compat"
        private const val PASSWORD = "85421376"
        private const val PLAINTEXT = "Synthetic persistence value for toolchain upgrade"
        private val KEYSET_NAMES = setOf(
            "__androidx_security_crypto_encrypted_prefs_key_keyset__",
            "__androidx_security_crypto_encrypted_prefs_value_keyset__"
        )
    }
}
