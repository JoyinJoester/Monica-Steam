package takagi.ru.monica.steam.backup

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.steam.backup.ui.SteamMaFileTransferScreen
import takagi.ru.monica.steam.backup.ui.rememberSteamMaFileTransferState
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.importer.SteamMaFileParser
import takagi.ru.monica.steam.session.SteamTemporaryAccounts
import takagi.ru.monica.steam.security.SteamAppLockGate
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel

class SteamMaFileExportAuthenticationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var security: SecurityManager
    private lateinit var destination: File
    private val viewModels = ViewModelStore()
    private var passwordDatabase: PasswordDatabase? = null
    private var requestCode: Int? = null
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) { this@SteamMaFileExportAuthenticationTest.requestCode = requestCode }
    }

    @Before fun prepare() = runBlocking {
        check(context.packageName.endsWith(".recoverytest"))
        security = SecurityManager(context)
        security.clearSecurityData()
        SessionManager.markLocked()
        SessionManager.updateAutoLockTimeout(5)
        SteamTemporaryAccounts.shared.clear()
        SteamDatabase.getDatabase(context).steamAccountDao().deleteAll()
        destination = File(context.cacheDir, "issue15-export.zip")
        destination.writeBytes(byteArrayOf())
    }

    @After fun cleanup() = runBlocking<Unit> {
        compose.runOnUiThread { viewModels.clear() }
        passwordDatabase?.close()
        SettingsManager(context).updateDisablePasswordVerification(false)
        SteamDatabase.getDatabase(context).steamAccountDao().deleteAll()
        SteamTemporaryAccounts.shared.clear()
        security.clearSecurityData()
        SessionManager.markLocked()
        destination.delete()
    }

    private fun openScreen(
        passwordProtected: Boolean = true,
        waitForAccounts: Boolean = true,
        authenticationGate: @Composable (@Composable () -> Unit) -> Unit = { content -> content() }
    ) {
        if (passwordProtected) security.setMasterPassword(PASSWORD)
        val repository = SteamAccountRepository(SteamDatabase.getDatabase(context).steamAccountDao(), security)
        val payload = SteamMaFileParser().parse("""
            {"steamid":"76561198000000001","account_name":"issue15-test",
             "device_id":"android:test","shared_secret":"MTIzNDU2Nzg5MDEyMzQ1Njc4OTA="}
        """.trimIndent())
        runBlocking { repository.upsertFromMaFile(payload) }
        repository.addTemporaryAccount(payload.copy(
            steamId = "76561198000000002", accountName = "temporary", sharedSecret = ""
        ))
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }) {
                MonicaTheme(darkTheme = false) {
                    val transferState = rememberSteamMaFileTransferState()
                    authenticationGate {
                        SteamMaFileTransferScreen(onNavigateBack = {}, transferState = transferState)
                    }
                }
            }
        }
        if (waitForAccounts) compose.waitUntil(10_000) {
            compose.onAllNodesWithText(context.getString(R.string.steam_backup_accounts, 1))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun selectDestination(uri: Uri? = Uri.fromFile(destination)) {
        compose.onNodeWithText(context.getString(R.string.steam_backup_export)).performClick()
        compose.runOnIdle { registry.dispatchResult(requireNotNull(requestCode), uri) }
    }

    private fun waitForAuthentication() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(context.getString(R.string.verify_identity)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0L, destination.length())
    }

    private fun enterPassword(value: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(value)
        compose.onNodeWithText(context.getString(R.string.start_export)).performClick()
    }

    @Test fun activeSessionDoesNotBypassExportAndWrongPasswordOrCancelWritesNothing() {
        openScreen()
        security.markVaultAuthenticated()
        selectDestination()
        waitForAuthentication()
        enterPassword("wrong-password")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(context.getString(R.string.current_password_incorrect))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0L, destination.length())
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.onNodeWithText(context.getString(R.string.verify_identity)).assertDoesNotExist()
        assertEquals(0L, destination.length())
    }

    @Test fun correctPasswordWritesValidArchiveAndNextExportRequiresFreshVerification() {
        openScreen()
        // Immediate re-lock must still allow a freshly authorized export.
        SessionManager.updateAutoLockTimeout(0)
        selectDestination()
        waitForAuthentication()
        enterPassword(PASSWORD)
        compose.waitUntil(10_000) { destination.length() > 0L }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(context.getString(R.string.steam_mafile_exported)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf("issue15-test"), SteamMaFileZipCodec().decode(destination.readBytes())
            .payloads.map { it.accountName })
        destination.writeBytes(byteArrayOf())
        selectDestination()
        waitForAuthentication()
        compose.onNodeWithText(context.getString(R.string.start_export)).assertIsNotEnabled()
        assertEquals(0L, destination.length())
    }

    @Test fun cancellingDocumentPickerDoesNotRequestAuthenticationOrWrite() {
        openScreen()
        selectDestination(null)
        compose.onNodeWithText(context.getString(R.string.verify_identity)).assertDoesNotExist()
        assertEquals(0L, destination.length())
    }

    @Test fun backgroundingVerificationDiscardsThePendingExport() {
        openScreen()
        selectDestination()
        waitForAuthentication()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText(context.getString(R.string.verify_identity)).assertDoesNotExist()
        assertEquals(0L, destination.length())
        selectDestination()
        waitForAuthentication()
    }

    @Test fun recreationDoesNotRestoreAnExportGrant() {
        openScreen()
        selectDestination()
        waitForAuthentication()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(0L, destination.length())
    }

    @Test fun optionalPasswordUnconfiguredStillAllowsExport() {
        openScreen(passwordProtected = false)
        selectDestination()
        compose.waitUntil(10_000) { destination.length() > 0L }
        compose.onNodeWithText(context.getString(R.string.verify_identity)).assertDoesNotExist()
        assertEquals(1, SteamMaFileZipCodec().decode(destination.readBytes()).payloads.size)
    }

    @Test fun sensitiveGateIgnoresStartupBypassAndRelocksOnReturn() {
        security.setMasterPassword(PASSWORD)
        val manager = SettingsManager(context)
        runBlocking { manager.updateDisablePasswordVerification(true) }
        val database = Room.inMemoryDatabaseBuilder(context, PasswordDatabase::class.java).build()
        passwordDatabase = database
        lateinit var passwords: PasswordViewModel
        lateinit var settings: SettingsViewModel
        compose.runOnUiThread {
            passwords = PasswordViewModel(PasswordRepository(database.passwordEntryDao()), security)
            settings = SettingsViewModel(manager)
            viewModels.put("passwords", passwords)
            viewModels.put("settings", settings)
            passwords.markAuthenticatedForBypass()
        }
        compose.setContent {
            MonicaTheme(darkTheme = false) {
                SteamAppLockGate(
                    allowStartupVerificationBypass = false,
                    settings = AppSettings(steamLockTokenPageOnly = true, disablePasswordVerification = true),
                    settingsViewModel = settings,
                    passwordViewModel = passwords,
                    securityManager = security
                ) { Text("Protected maFile backup") }
            }
        }
        compose.waitUntil(10_000) {
            settings.settings.value.disablePasswordVerification &&
                compose.onAllNodesWithText(context.getString(R.string.unlock)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Protected maFile backup").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("wrong-password")
        compose.onNodeWithText(context.getString(R.string.unlock)).performClick()
        compose.onNodeWithText("Protected maFile backup").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.error_invalid_password)).assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement(PASSWORD)
        compose.onNodeWithText(context.getString(R.string.unlock)).performClick()
        compose.onNodeWithText("Protected maFile backup").assertExists()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        SessionManager.markLocked()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Protected maFile backup").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.unlock)).assertExists()
    }

    @Test fun documentResultSurvivesImmediateRelockWithoutRetainingAuthorization() {
        val database = Room.inMemoryDatabaseBuilder(context, PasswordDatabase::class.java).build()
        passwordDatabase = database
        lateinit var passwords: PasswordViewModel
        lateinit var settings: SettingsViewModel
        compose.runOnUiThread {
            passwords = PasswordViewModel(PasswordRepository(database.passwordEntryDao()), security)
            settings = SettingsViewModel(SettingsManager(context))
            viewModels.put("passwords", passwords)
            viewModels.put("settings", settings)
        }
        openScreen(waitForAccounts = false, authenticationGate = { content ->
            SteamAppLockGate(
                allowStartupVerificationBypass = false,
                settings = AppSettings(steamLockTokenPageOnly = true, autoLockMinutes = 0),
                settingsViewModel = settings,
                passwordViewModel = passwords,
                securityManager = security,
                content = content
            )
        })
        fun unlockPage() {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(context.getString(R.string.unlock)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasSetTextAction()).performTextReplacement(PASSWORD)
            compose.onNodeWithText(context.getString(R.string.unlock)).performClick()
        }
        unlockPage()
        compose.onNodeWithText(context.getString(R.string.steam_backup_export)).performClick()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.runOnUiThread { registry.dispatchResult(requireNotNull(requestCode), Uri.fromFile(destination)) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertEquals(0L, destination.length())
        unlockPage()
        waitForAuthentication()
        enterPassword(PASSWORD)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(context.getString(R.string.steam_mafile_exported)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, SteamMaFileZipCodec().decode(destination.readBytes()).payloads.size)
    }

    private companion object { const val PASSWORD = "issue15-test-password" }
}
