package takagi.ru.monica.steam.scanner

import android.content.Context
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.importer.SteamMaFilePayload
import takagi.ru.monica.steam.navigation.SteamDockPreferences
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.SteamDockTab
import takagi.ru.monica.utils.SettingsManager

/** Separate instrumentation invocations before and after an in-place APK upgrade. No reset/recovery. */
class SteamToolchainPersistenceUpgradeTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val marker: File get() = File(context.filesDir, "toolchain-persistence-seed-v1.json")
    private val sourcePaths = listOf(
        "shared_prefs/monica_secure_prefs.xml",
        "files/datastore/settings.preferences_pb",
        "files/datastore/monica_steam_dock.preferences_pb"
    )
    private val fixedOrder = listOf(SteamDockTab.TOKEN, SteamDockTab.SETTINGS, SteamDockTab.CHAT, SteamDockTab.LIBRARY, SteamDockTab.STORE)
    private val glassOrder = SteamDockTab.LIQUID_GLASS_DEFAULT_ORDER.reversed()

    private fun guard(phase: String) {
        check(context.packageName == "takagi.ru.monica.steamapp.toolchainpilot")
        check(InstrumentationRegistry.getArguments().getString("persistencePhase") == phase)
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun fileAt(relative: String) = File(context.applicationInfo.dataDir, relative)
    private fun fileHashes(): JSONObject = JSONObject().apply {
        sourcePaths.forEach { relative ->
            val file = fileAt(relative)
            check(file.isFile && file.length() > 0) { "Required old data file missing: $relative" }
            put(relative, sha(file.readBytes()))
        }
    }

    private suspend fun stableHashes(): JSONObject {
        var previous = ""
        var same = 0
        repeat(100) {
            val hashes = fileHashes()
            val signature = sourcePaths.joinToString { hashes.getString(it) }
            same = if (signature == previous) same + 1 else 0
            previous = signature
            if (same >= 5) return hashes
            delay(50)
        }
        error("Seed writes did not settle; marker is deliberately not created")
    }

    @Test fun seedWithOldDataStoreAndSecurityCrypto() = runBlocking {
        guard("seed")
        check(!marker.exists()) { "Existing seed marker must never be overwritten" }
        val oldApkSha = requireNotNull(InstrumentationRegistry.getArguments().getString("oldApkSha256"))
        check(oldApkSha.matches(Regex("[0-9a-f]{64}")))
        check(!fileAt("shared_prefs/monica_secure_prefs.xml").exists()) { "Requires fresh disposable pilot data" }
        val dao = SteamDatabase.getDatabase(context).steamAccountDao()
        check(dao.count() == 0)
        val security = SecurityManager(context)
        check(!security.isMasterPasswordSet())
        security.setMasterPassword(PASSWORD)
        assertTrue(security.verifyMasterPassword(PASSWORD))
        val ciphertext = security.encryptData(PLAINTEXT)
        assertNotEquals(PLAINTEXT, ciphertext)
        assertEquals(PLAINTEXT, security.decryptData(ciphertext))
        val repository = SteamAccountRepository(dao, security)
        val id = repository.upsertFromMaFile(SteamMaFilePayload(
            steamId = "76561198000999110", accountName = "upgrade-persistence-synthetic",
            displayName = "Upgrade persistence synthetic", deviceId = "android:synthetic-upgrade",
            sharedSecret = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=", identitySecret = null,
            revocationCode = null, tokenGid = null, accessToken = "synthetic-persistence-token",
            refreshToken = "synthetic-persistence-refresh", steamLoginSecure = null, rawJson = "{}"
        ))
        val account = requireNotNull(repository.getAccount(id))
        val settings = SettingsManager(context)
        settings.prepareSteamMasterPasswordSetup()
        settings.updateSteamGuardCodeGroupingEnabled(false)
        val dock = SteamDockPreferences(context)
        dock.updateStyle(SteamDockStyle.LIQUID_GLASS)
        dock.updateFixedOrder(fixedOrder)
        dock.updateLiquidGlassOrder(glassOrder)
        assertEquals(SteamDockStyle.LIQUID_GLASS, dock.configuration.first().style)
        val data = JSONObject().put("format", 1).put("phase", "old_seed_complete")
            .put("oldApkSha256", oldApkSha).put("seedPid", Process.myPid())
            .put("accountId", id).put("accountPlainSha256", sha(account.toString().toByteArray()))
            .put("accountCipherSha256", sha(requireNotNull(dao.getById(id)).toString().toByteArray()))
            .put("ciphertext", ciphertext).put("fileHashes", stableHashes())
        check(marker.createNewFile())
        marker.writeText(data.toString())
        assertEquals("old_seed_complete", JSONObject(marker.readText()).getString("phase"))
        println("PERSISTENCE_UPGRADE seed=true old_apk=$oldApkSha marker_sha256=${sha(marker.readBytes())}")
        // Deliberately preserve the old files. Do not run cleanup/reset/export tests between phases.
    }

    @Test fun upgradedColdProcessReadsOldPreferencesPasswordCiphertextAndAccountUnchanged() = runBlocking {
        guard("verify")
        check(marker.isFile && marker.length() > 0) { "Missing completed old-version seed; no auto-seeding is allowed" }
        val markerBytes = marker.readBytes()
        val seed = JSONObject(String(markerBytes))
        assertEquals(1, seed.getInt("format"))
        assertEquals("old_seed_complete", seed.getString("phase"))
        assertEquals(InstrumentationRegistry.getArguments().getString("oldApkSha256"), seed.getString("oldApkSha256"))
        assertEquals(InstrumentationRegistry.getArguments().getString("seedMarkerSha256"), sha(markerBytes))
        assertNotEquals("Verification must run in a cold, different process", seed.getInt("seedPid"), Process.myPid())
        val before = fileHashes()
        sourcePaths.forEach { assertEquals("Pre-read bytes changed: $it", seed.getJSONObject("fileHashes").getString(it), before.getString(it)) }
        val security = SecurityManager(context)
        assertTrue("Upgrade must retain the original configured password", security.isMasterPasswordSet())
        assertFalse(security.verifyMasterPassword("incorrect-synthetic-password"))
        assertTrue(security.verifyMasterPassword(PASSWORD))
        assertEquals(PLAINTEXT, security.decryptData(seed.getString("ciphertext")))
        val dao = SteamDatabase.getDatabase(context).steamAccountDao()
        assertEquals(1, dao.count())
        val id = seed.getLong("accountId")
        assertEquals(seed.getString("accountCipherSha256"), sha(requireNotNull(dao.getById(id)).toString().toByteArray()))
        val account = requireNotNull(SteamAccountRepository(dao, security).getAccount(id))
        assertEquals(seed.getString("accountPlainSha256"), sha(account.toString().toByteArray()))
        val settings = SettingsManager(context).settingsFlow.first()
        assertTrue(settings.steamLockTokenPageOnly)
        assertFalse(settings.disablePasswordVerification)
        assertFalse(settings.steamGuardCodeGroupingEnabled)
        val dock = SteamDockPreferences(context).configuration.first()
        assertEquals(SteamDockStyle.LIQUID_GLASS, dock.style)
        assertEquals(fixedOrder, dock.fixedOrder)
        assertEquals(glassOrder, dock.liquidGlassOrder)
        val after = stableHashes()
        sourcePaths.forEach { assertEquals("Read unexpectedly rewrote old persistent bytes: $it", before.getString(it), after.getString(it)) }
        assertArrayEquals("Seed marker is evidence and must remain intact", markerBytes, marker.readBytes())
        println("PERSISTENCE_UPGRADE verify=true password=true ciphertext=true account=true datastore=true seed_marker=${sha(markerBytes)}")
        // Failure deliberately leaves every old file and marker intact for diagnosis.
    }

    companion object {
        private const val PASSWORD = "85421376"
        private const val PLAINTEXT = "Synthetic persistence value for toolchain upgrade"
    }
}
