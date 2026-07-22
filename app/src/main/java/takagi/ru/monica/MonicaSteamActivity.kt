package takagi.ru.monica

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import takagi.ru.monica.steam.navigation.SteamDockPreferences
import takagi.ru.monica.steam.navigation.SteamDockTab
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.security.SteamAppLockGate
import takagi.ru.monica.steam.ui.SteamQrScannerScreen
import takagi.ru.monica.steam.ui.SteamBackupScreen
import takagi.ru.monica.steam.ui.SteamHealthScreen
import takagi.ru.monica.steam.ui.SteamLibraryScreen
import takagi.ru.monica.steam.ui.SteamScreen
import takagi.ru.monica.steam.store.SteamStoreScreen
import takagi.ru.monica.steam.alerts.SteamAlertScheduler
import takagi.ru.monica.ui.base.BaseMonicaActivity
import takagi.ru.monica.ui.screens.MonicaSteamSettingsScreen
import takagi.ru.monica.ui.screens.MdbxLocalCreateScreen
import takagi.ru.monica.ui.screens.MdbxLocalOpenScreen
import takagi.ru.monica.ui.screens.MdbxManagerScreen
import takagi.ru.monica.ui.screens.MdbxWebDavCreateScreen
import takagi.ru.monica.ui.screens.MdbxWebDavOpenScreen
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.viewmodel.MdbxViewModel
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel

private enum class MonicaSteamPage {
    STEAM,
    SCANNER,
    HEALTH,
    LIBRARY,
    STORE,
    BACKUP,
    MDBX,
    MDBX_CREATE,
    MDBX_OPEN,
    MDBX_WEBDAV_CREATE,
    MDBX_WEBDAV_OPEN,
    SETTINGS
}

private const val MONICA_BACK_EXIT_TIMEOUT_MS = 2_000L

class MonicaSteamActivity : BaseMonicaActivity() {
    override fun shouldEnforceSharedSessionLock(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            SteamAlertScheduler.sync(this@MonicaSteamActivity)
        }

        setContent {
            val settings by settingsManager.settingsFlow.collectAsState(initial = AppSettings())
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDarkTheme
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            MonicaTheme(
                darkTheme = darkTheme,
                oledPureBlackEnabled = settings.oledPureBlackEnabled,
                colorScheme = settings.colorScheme,
                customPrimaryColor = settings.customPrimaryColor,
                customSecondaryColor = settings.customSecondaryColor,
                customTertiaryColor = settings.customTertiaryColor,
                customNeutralColor = settings.customNeutralColor,
                customNeutralVariantColor = settings.customNeutralVariantColor
            ) {
                val steamSettingsViewModel: SettingsViewModel = viewModel {
                    SettingsViewModel(settingsManager)
                }
                val passwordDatabase = remember {
                    PasswordDatabase.getDatabase(this@MonicaSteamActivity.applicationContext)
                }
                val securityManager = remember {
                    SecurityManager(this@MonicaSteamActivity.applicationContext)
                }
                val passwordRepository = remember(passwordDatabase) {
                    PasswordRepository(passwordDatabase.passwordEntryDao())
                }
                val passwordViewModel: PasswordViewModel = viewModel {
                    PasswordViewModel(
                        repository = passwordRepository,
                        securityManager = securityManager
                    )
                }
                val mdbxViewModel: MdbxViewModel = viewModel {
                    MdbxViewModel(
                        application,
                        passwordDatabase.localMdbxDatabaseDao(),
                        passwordDatabase.mdbxRemoteSourceDao(),
                        passwordDatabase.passwordEntryDao(),
                        passwordDatabase.secureItemDao(),
                        passwordDatabase.passkeyDao(),
                        passwordDatabase.attachmentDao(),
                        passwordDatabase.customFieldDao(),
                        securityManager
                    )
                }
                var scannerAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
                var currentPage by rememberSaveable { mutableStateOf(MonicaSteamPage.STEAM) }
                var pageHistory by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
                var pendingQrResult by rememberSaveable { mutableStateOf<String?>(null) }
                var pendingQrAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
                var libraryRefreshing by rememberSaveable { mutableStateOf(false) }
                var backPressedOnce by remember { mutableStateOf(false) }
                val composeScope = rememberCoroutineScope()
                val dockPreferences = remember {
                    SteamDockPreferences(this@MonicaSteamActivity.applicationContext)
                }
                val dockOrder by dockPreferences.order.collectAsState(
                    initial = emptyList()
                )
                val homePage = dockOrder.firstOrNull()?.toPage() ?: MonicaSteamPage.STEAM
                var appliedInitialDockPage by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(dockOrder) {
                    if (!appliedInitialDockPage && dockOrder.isNotEmpty()) {
                        currentPage = homePage
                        pageHistory = emptyList()
                        appliedInitialDockPage = true
                    }
                }

                fun navigateTo(page: MonicaSteamPage) {
                    if (page == currentPage) return
                    pageHistory = if (page.isDockPage()) {
                        emptyList()
                    } else {
                        pageHistory + currentPage.name
                    }
                    currentPage = page
                }

                fun navigateBack() {
                    val parent = pageHistory.lastOrNull()
                        ?.let { name -> runCatching { MonicaSteamPage.valueOf(name) }.getOrNull() }
                    if (parent == null) {
                        pageHistory = emptyList()
                        currentPage = homePage
                    } else {
                        pageHistory = pageHistory.dropLast(1)
                        currentPage = parent
                    }
                    scannerAccountId = null
                }

                SteamAppLockGate(
                    settings = settings,
                    settingsViewModel = steamSettingsViewModel,
                    passwordViewModel = passwordViewModel,
                    securityManager = securityManager
                ) {
                    BackHandler(enabled = true) {
                        if (pageHistory.isNotEmpty()) {
                            navigateBack()
                            return@BackHandler
                        }

                        if (currentPage.isDockPage()) {
                            if (backPressedOnce) {
                                this@MonicaSteamActivity.finish()
                            } else {
                                backPressedOnce = true
                                Toast.makeText(
                                    this@MonicaSteamActivity,
                                    getString(R.string.press_back_again_to_exit),
                                    Toast.LENGTH_SHORT
                                ).show()
                                composeScope.launch {
                                    delay(MONICA_BACK_EXIT_TIMEOUT_MS)
                                    backPressedOnce = false
                                }
                            }
                            return@BackHandler
                        }

                        // Recovery path for a restored secondary page whose saved
                        // parent history is unavailable.
                        navigateBack()
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            bottomBar = {
                                if (currentPage.isDockPage()) {
                                    SteamStandaloneDock(
                                        order = dockOrder,
                                        selected = currentPage.toDockTab(),
                                        showProgress = currentPage == MonicaSteamPage.LIBRARY && libraryRefreshing,
                                        onSelected = { tab ->
                                            pageHistory = emptyList()
                                            currentPage = tab.toPage()
                                        }
                                    )
                                }
                            }
                        ) { dockPadding -> AnimatedContent(
                        targetState = currentPage,
                        label = "monica_steam_page_transition",
                        transitionSpec = {
                            if (initialState.isDockPage() && targetState.isDockPage()) {
                                // Monica Android's SimpleMainScreen swaps top-level tabs
                                // directly; only the NavigationBar selection animates.
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                // Every secondary route in Monica Android uses the
                                // EasyNotes scale/fade transition for both push and pop.
                                easyNotesScreenEnter().togetherWith(easyNotesScreenExit())
                            }
                        }
                    ) { page -> when (page) {
                        MonicaSteamPage.SCANNER -> {
                            SteamQrScannerScreen(
                                initialAccountId = scannerAccountId,
                                onQrCodeScanned = { qrData, accountId ->
                                    pendingQrResult = qrData
                                    pendingQrAccountId = accountId
                                    navigateBack()
                                    scannerAccountId = null
                                },
                                onNavigateBack = {
                                    navigateBack()
                                },
                                modifier = Modifier.fillMaxSize().padding(dockPadding)
                            )
                        }

                        MonicaSteamPage.SETTINGS -> {
                            MonicaSteamSettingsScreen(
                                settings = settings,
                                settingsManager = settingsManager,
                                settingsViewModel = steamSettingsViewModel,
                                passwordViewModel = passwordViewModel,
                                securityManager = securityManager,
                                onNavigateBack = { navigateBack() },
                                onOpenBackup = { navigateTo(MonicaSteamPage.BACKUP) },
                                onOpenMdbx = { navigateTo(MonicaSteamPage.MDBX) },
                                dockOrder = dockOrder,
                                onDockOrderChange = { order ->
                                    composeScope.launch { dockPreferences.updateOrder(order) }
                                },
                                showNavigationBack = false,
                                modifier = Modifier.fillMaxSize().padding(dockPadding)
                            )
                        }

                        MonicaSteamPage.HEALTH -> {
                            SteamHealthScreen(
                                onNavigateBack = { navigateBack() },
                                modifier = Modifier.fillMaxSize().padding(dockPadding)
                            )
                        }

                        MonicaSteamPage.LIBRARY -> {
                            SteamLibraryScreen(
                                onNavigateBack = { navigateBack() },
                                showNavigationBack = false,
                                onLoadingChange = { libraryRefreshing = it },
                                modifier = Modifier.fillMaxSize().padding(dockPadding)
                            )
                        }

                        MonicaSteamPage.STORE -> {
                            SteamStoreScreen(
                                showNavigationBack = false,
                                modifier = Modifier.fillMaxSize().padding(dockPadding)
                            )
                        }

                        MonicaSteamPage.BACKUP -> {
                            SteamBackupScreen(
                                onNavigateBack = { navigateBack() },
                                modifier = Modifier.fillMaxSize().padding(dockPadding)
                            )
                        }

                        MonicaSteamPage.MDBX -> {
                            MdbxManagerScreen(
                                viewModel = mdbxViewModel,
                                onNavigateBack = { navigateBack() },
                                onNavigateToLocalCreate = { navigateTo(MonicaSteamPage.MDBX_CREATE) },
                                onNavigateToLocalOpen = { navigateTo(MonicaSteamPage.MDBX_OPEN) },
                                onNavigateToWebDavCreate = {
                                    navigateTo(MonicaSteamPage.MDBX_WEBDAV_CREATE)
                                },
                                onNavigateToWebDavOpen = {
                                    navigateTo(MonicaSteamPage.MDBX_WEBDAV_OPEN)
                                },
                                onNavigateToOneDriveCreate = {},
                                onNavigateToOneDriveOpen = {},
                                localOnly = false,
                                oneDriveEnabled = false
                            )
                        }

                        MonicaSteamPage.MDBX_CREATE -> {
                            MdbxLocalCreateScreen(
                                viewModel = mdbxViewModel,
                                onNavigateBack = { navigateBack() }
                            )
                        }

                        MonicaSteamPage.MDBX_OPEN -> {
                            MdbxLocalOpenScreen(
                                viewModel = mdbxViewModel,
                                onNavigateBack = { navigateBack() }
                            )
                        }

                        MonicaSteamPage.MDBX_WEBDAV_CREATE -> {
                            MdbxWebDavCreateScreen(
                                viewModel = mdbxViewModel,
                                onNavigateBack = { navigateBack() }
                            )
                        }

                        MonicaSteamPage.MDBX_WEBDAV_OPEN -> {
                            MdbxWebDavOpenScreen(
                                viewModel = mdbxViewModel,
                                onNavigateBack = { navigateBack() }
                            )
                        }

                        MonicaSteamPage.STEAM -> {
                            SteamScreen(
                                onOpenHealth = {
                                    navigateTo(MonicaSteamPage.HEALTH)
                                },
                                onOpenBackup = {
                                    navigateTo(MonicaSteamPage.BACKUP)
                                },
                                pendingSteamQrResult = pendingQrResult,
                                pendingSteamQrAccountId = pendingQrAccountId,
                                onConsumePendingSteamQrResult = {
                                    pendingQrResult = null
                                    pendingQrAccountId = null
                                },
                                onScanSteamQrCode = { accountId ->
                                    scannerAccountId = accountId
                                    navigateTo(MonicaSteamPage.SCANNER)
                                },
                                modifier = Modifier.fillMaxSize().padding(dockPadding)
                            )
                        }
                        } }
                    }
                }
            }
        }
    }
}

}

private fun MonicaSteamPage.isDockPage(): Boolean = when (this) {
    MonicaSteamPage.STEAM,
    MonicaSteamPage.LIBRARY,
    MonicaSteamPage.STORE,
    MonicaSteamPage.SETTINGS -> true
    MonicaSteamPage.SCANNER,
    MonicaSteamPage.HEALTH,
    MonicaSteamPage.BACKUP -> false
    MonicaSteamPage.MDBX,
    MonicaSteamPage.MDBX_CREATE,
    MonicaSteamPage.MDBX_OPEN,
    MonicaSteamPage.MDBX_WEBDAV_CREATE,
    MonicaSteamPage.MDBX_WEBDAV_OPEN -> false
}

private fun MonicaSteamPage.toDockTab(): SteamDockTab = when (this) {
    MonicaSteamPage.LIBRARY -> SteamDockTab.LIBRARY
    MonicaSteamPage.STORE -> SteamDockTab.STORE
    MonicaSteamPage.SETTINGS -> SteamDockTab.SETTINGS
    else -> SteamDockTab.TOKEN
}

private fun SteamDockTab.toPage(): MonicaSteamPage = when (this) {
    SteamDockTab.TOKEN -> MonicaSteamPage.STEAM
    SteamDockTab.LIBRARY -> MonicaSteamPage.LIBRARY
    SteamDockTab.STORE -> MonicaSteamPage.STORE
    SteamDockTab.SETTINGS -> MonicaSteamPage.SETTINGS
}

internal fun initialSteamDockPage(order: List<SteamDockTab>): String =
    when (SteamDockTab.sanitizeOrder(order).firstOrNull() ?: SteamDockTab.TOKEN) {
        SteamDockTab.TOKEN -> "STEAM"
        SteamDockTab.LIBRARY -> "LIBRARY"
        SteamDockTab.STORE -> "STORE"
        SteamDockTab.SETTINGS -> "SETTINGS"
    }

@Composable
private fun SteamStandaloneDock(
    order: List<SteamDockTab>,
    selected: SteamDockTab,
    showProgress: Boolean,
    onSelected: (SteamDockTab) -> Unit
) {
    Column {
        if (showProgress) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        }
        NavigationBar {
            SteamDockTab.sanitizeOrder(order).forEach { tab ->
                NavigationBarItem(
                    selected = tab == selected,
                    onClick = { onSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = tab.icon(),
                            contentDescription = null
                        )
                    },
                    label = { Text(tab.label()) },
                    alwaysShowLabel = true
                )
            }
        }
    }
}

private fun SteamDockTab.icon(): ImageVector = when (this) {
    SteamDockTab.TOKEN -> Icons.Default.Security
    SteamDockTab.LIBRARY -> Icons.Default.SportsEsports
    SteamDockTab.STORE -> Icons.Default.Storefront
    SteamDockTab.SETTINGS -> Icons.Default.Settings
}

@Composable
private fun SteamDockTab.label(): String = when (this) {
    SteamDockTab.TOKEN -> stringResource(R.string.steam_dock_token)
    SteamDockTab.LIBRARY -> stringResource(R.string.steam_library_title)
    SteamDockTab.STORE -> stringResource(R.string.steam_store_title)
    SteamDockTab.SETTINGS -> stringResource(R.string.settings_title)
}
