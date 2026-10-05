package takagi.ru.monica.security

import android.content.Context
import androidx.security.crypto.MasterKey
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyStore
import takagi.ru.monica.data.Language
import takagi.ru.monica.steam.data.SteamAccountEntity
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.security.SteamRecoveryMaintenance
import takagi.ru.monica.utils.SettingsManager

/** Explicit stages allow real activity launches and process deaths between key loss and recovery. */
class SteamStartupRecoveryInstrumentedTest {
    @Test fun exerciseIsolatedStartupStage() = runBlocking {
        val stage = InstrumentationRegistry.getArguments().getString("recoveryStage") ?: return@runBlocking
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".recoverytest"))
        val password = "Steam-Recovery-Test-2026!"
        val original = File(context.applicationInfo.dataDir, "shared_prefs/monica_secure_prefs.xml")
        val snapshot = File(context.cacheDir, "startup-original-prefs.xml")
        when (stage) {
            "seed" -> {
                val security = SecurityManager(context)
                assertFalse(security.isMasterPasswordSet())
                val settings = SettingsManager(context)
                settings.updateLanguage(Language.CHINESE)
                settings.updateScreenshotProtectionEnabled(false)
                settings.updateDisablePasswordVerification(false)
                val dao = SteamDatabase.getDatabase(context).steamAccountDao()
                assertEquals(0, dao.count())
                fun encrypt(value: String) = security.encryptDataLegacyCompat(value)
                dao.insert(SteamAccountEntity(
                    steamId = encrypt("synthetic-offline-account"), accountName = encrypt("recovery_fixture"),
                    displayName = encrypt("恢复测试账号"), deviceId = encrypt("android:synthetic-device"),
                    sharedSecret = encrypt("c3ludGhldGljLXN0ZWFtLXNlY3JldA=="), identitySecret = null,
                    revocationCode = encrypt("synthetic-revocation"), tokenGid = null, accessToken = null,
                    refreshToken = null, steamLoginSecure = null, rawSteamGuardJson = encrypt("{}"),
                    note = encrypt("本地离线备注"), tagsJson = encrypt("[\"测试\"]"),
                    groupName = encrypt("恢复分组"), selected = true, createdAt = 1234, updatedAt = 5678
                ))
                assertTrue(security.setMasterPassword(password))
                security.markVaultAuthenticated()
                SteamRecoveryMaintenance.run(context, security)
                assertTrue(LocalVaultRecovery(context).available())
                assertTrue(dao.getAccounts().single().sharedSecret.startsWith("MDK|"))
            }
            "lose" -> {
                assertTrue(LocalVaultRecovery(context).available())
                original.copyTo(snapshot, overwrite = true)
                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                for (alias in listOf(MasterKey.DEFAULT_MASTER_KEY_ALIAS, "monica_data_key_v2_compat", "monica_data_key_v2")) {
                    if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
                }
                SecurityManager.clearRuntimeUnlockCache()
                assertTrue(SecureStorageStartup.prepare(context) is SecureStartupResult.Blocked)
                assertArrayEquals(snapshot.readBytes(), original.readBytes())
            }
            "verify" -> {
                assertTrue(LocalVaultRecovery(context).activeStore() != null)
                val security = SecurityManager(context)
                assertFalse(security.unlockVaultWithPassword("wrong-password"))
                assertTrue(security.unlockVaultWithPassword(password))
                val account = SteamAccountRepository(SteamDatabase.getDatabase(context).steamAccountDao(), security).getAccounts().single()
                assertEquals("恢复测试账号", account.displayName)
                assertEquals("c3ludGhldGljLXN0ZWFtLXNlY3JldA==", account.sharedSecret)
                assertEquals("本地离线备注", account.note)
                assertEquals(listOf("测试"), account.tags)
                assertEquals("恢复分组", account.groupName)
                assertEquals(5678L, account.updatedAt)
                assertArrayEquals(snapshot.readBytes(), original.readBytes())
            }
            else -> error("Unknown test stage")
        }
    }
}
