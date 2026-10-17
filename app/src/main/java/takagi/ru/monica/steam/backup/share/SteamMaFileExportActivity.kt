package takagi.ru.monica.steam.backup.share

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.backup.ui.SteamMaFileExportAuthentication
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.data.isLoginOnlyAccount
import takagi.ru.monica.steam.importer.SteamMaFileBackupCodec
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.utils.SettingsManager

/** Always requires a new, foreground user decision. No secrets/auth state enter saved state. */
class SteamMaFileExportActivity : FragmentActivity() {
    private var ui by mutableStateOf(MaFileConsentUi())
    private var settings by mutableStateOf(AppSettings())
    private lateinit var security: SecurityManager
    private var recipientUid = -1
    private var requestId = ""
    private var active = true
    private var authorized = false
    private var accounts = emptyList<SteamAccountSessionHandle>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.filterTouchesWhenObscured = true
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.setHideOverlayWindows(true)
        // Recreated activities never inherit confirmation, even if Android saved their Intent.
        if (savedInstanceState != null || !validateCaller()) {
            finish()
            return
        }
        setContent {
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            MonicaTheme(
                darkTheme = dark, oledPureBlackEnabled = settings.oledPureBlackEnabled,
                colorScheme = settings.colorScheme, customPrimaryColor = settings.customPrimaryColor,
                customSecondaryColor = settings.customSecondaryColor, customTertiaryColor = settings.customTertiaryColor,
                customNeutralColor = settings.customNeutralColor, customNeutralVariantColor = settings.customNeutralVariantColor
            ) {
                SteamMaFileConsentScreen(ui, ::unlock, ::select, ::deliver, ::finish)
                if (ui.authenticating) SteamMaFileExportAuthentication(
                    securityManager = security, biometricEnabled = settings.biometricEnabled,
                    onAuthorized = ::loadAccounts, onDismiss = ::finish,
                    onUnavailable = { fail(R.string.steam_handoff_auth_unavailable) }
                )
            }
        }
        lifecycleScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    security = SecurityManager(applicationContext)
                    SettingsManager(applicationContext).settingsFlow.first() to security.isMasterPasswordSet()
                }
                if (!active || isFinishing) return@launch
                settings = loaded.first
                ui = ui.copy(busy = false, canUnlock = loaded.second,
                    error = if (loaded.second) null else R.string.steam_handoff_password_required)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                fail(R.string.steam_handoff_auth_unavailable)
            }
        }
        lifecycleScope.launch { delay(5 * 60_000L); finish() }
    }

    @Suppress("DEPRECATION")
    private fun validateCaller(): Boolean = runCatching {
        if (callingPackage != SteamMaFileShareContract.STEAM_PACKAGE ||
            callingActivity?.packageName != SteamMaFileShareContract.STEAM_PACKAGE) return false
        val action = intent.action
        val version = intent.getIntExtra(SteamMaFileShareContract.EXTRA_VERSION, -1)
        val nonce = intent.getStringExtra(SteamMaFileShareContract.EXTRA_REQUEST_ID)
        if (!SteamMaFileShareContract.validRequest(action, version, nonce)) return false
        val info = packageManager.getApplicationInfo(SteamMaFileShareContract.STEAM_PACKAGE, 0)
        // A shared UID would make a different package an indistinguishable recipient.
        if (packageManager.getPackagesForUid(info.uid)?.toSet() != setOf(SteamMaFileShareContract.STEAM_PACKAGE)) return false
        recipientUid = info.uid
        requestId = nonce!!
        true
    }.getOrDefault(false)

    private fun foreground() = active && !isFinishing && !isDestroyed &&
        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    private fun unlocked() = security.isMasterPasswordSet() && security.isVaultRuntimeUnlocked()

    private fun unlock() {
        if (foreground() && ui.canUnlock && !ui.busy) ui = ui.copy(authenticating = true, error = null)
    }

    private fun loadAccounts() {
        if (!foreground()) return
        if (!runCatching { unlocked() }.getOrDefault(false)) {
            fail(R.string.steam_handoff_auth_unavailable)
            return
        }
        authorized = true
        ui = ui.copy(authenticating = false, busy = true)
        lifecycleScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    SteamAccountSourceRepository.get(applicationContext).loadAllSessionHandles()
                        .filter { eligible(it.account) }
                }
                if (!foreground() || !authorized || !unlocked()) { finish(); return@launch }
                accounts = loaded
                ui = ui.copy(busy = false, choosing = true, selectedKey = null,
                    accounts = loaded.map { handle ->
                        val account = handle.account
                        val source = when (val origin = handle.origin.source) {
                            SteamStorageSource.Local -> getString(R.string.steam_widget_local_account)
                            is SteamStorageSource.Mdbx -> "MDBX #${origin.databaseId}"
                        }
                        MaFileConsentAccount(handle.stableKey, account.displayName.ifBlank { account.accountName },
                            "${account.accountName} · $source\n${account.steamId}")
                    }, error = if (loaded.isEmpty()) R.string.steam_handoff_no_accounts else null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                fail(R.string.steam_handoff_load_failed)
            }
        }
    }

    private fun select(key: String) {
        if (foreground() && authorized && !ui.busy && accounts.any { it.stableKey == key }) {
            ui = ui.copy(selectedKey = key)
        }
    }

    private fun deliver() {
        if (!foreground() || !authorized || ui.busy) return
        val handle = accounts.singleOrNull { it.stableKey == ui.selectedKey } ?: return
        ui = ui.copy(busy = true, error = null)
        lifecycleScope.launch {
            var bytes: ByteArray? = null
            var uri: android.net.Uri? = null
            try {
                withContext(Dispatchers.IO) {
                    check(unlocked())
                    val account = SteamAccountSourceRepository.get(applicationContext).loadSessionAccount(handle)
                    check(eligible(account))
                    bytes = SteamMaFileBackupCodec.encode(account).toByteArray(Charsets.UTF_8)
                }
                if (!foreground() || !authorized || !unlocked()) { finish(); return@launch }
                // Recheck the installed receiving UID after asynchronous account loading.
                val uid = recipientUid
                check(validateCaller() && recipientUid == uid)
                val resultUri = SteamMaFileShareProvider.publish(applicationContext, uid, checkNotNull(bytes))
                bytes = null // Provider now owns and wipes this buffer.
                uri = resultUri
                val result = Intent().apply {
                    setDataAndType(resultUri, SteamMaFileShareContract.MIME_TYPE)
                    clipData = ClipData.newRawUri("maFile", resultUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(SteamMaFileShareContract.EXTRA_VERSION, SteamMaFileShareContract.VERSION)
                    putExtra(SteamMaFileShareContract.EXTRA_REQUEST_ID, requestId)
                }
                setResult(Activity.RESULT_OK, result)
                finish()
                uri = null // Delivery retains the ticket until first open or expiry.
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                setResult(Activity.RESULT_CANCELED)
                fail(R.string.steam_handoff_export_failed)
            } finally {
                bytes?.fill(0)
                uri?.let { SteamMaFileShareProvider.revoke(applicationContext, it) }
            }
        }
    }

    private fun fail(message: Int) {
        authorized = false
        accounts = emptyList()
        if (active && !isFinishing) ui = MaFileConsentUi(busy = false, canUnlock = this::security.isInitialized, error = message)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val obscured = MotionEvent.FLAG_WINDOW_IS_OBSCURED or MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
        if (event.flags and obscured != 0) return false
        return super.dispatchTouchEvent(event)
    }

    override fun finish() {
        active = false
        authorized = false
        accounts = emptyList()
        ui = MaFileConsentUi()
        super.finish()
    }

    override fun onStop() {
        // App switches, screen locking and rotation require a completely new request.
        if (active) finish()
        super.onStop()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Home/Recents can return before onStop during a transition. Discard consent immediately.
        if (active) finish()
    }

    private fun eligible(account: SteamAccount) =
        !account.isTemporary && !account.isLoginOnlyAccount && account.hasRealSteamId && account.sharedSecret.isNotBlank()
}
