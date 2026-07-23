package takagi.ru.monica

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import takagi.ru.monica.steam.friends.ui.SteamFriendsScreen
import takagi.ru.monica.steam.scanner.ui.SteamQrScannerScreen
import takagi.ru.monica.steam.backup.ui.SteamBackupScreen
import takagi.ru.monica.steam.health.ui.SteamHealthScreen
import takagi.ru.monica.steam.library.ui.SteamLibraryScreen
import takagi.ru.monica.steam.ui.SteamScreen
import takagi.ru.monica.steam.ui.setSteamUiScaledContent
import takagi.ru.monica.steam.store.ui.SteamStoreScreen
import takagi.ru.monica.steam.alerts.data.SteamAlertScheduler
import takagi.ru.monica.steam.diagnostics.SteamCrashDiagnostics
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
    FRIENDS,
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
        SteamCrashDiagnostics.install(this)
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            SteamAlertScheduler.sync(this@MonicaSteamActivity)
        }

        setSteamUiScaledContent {
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
                                        },
                                        onScan = { navigateTo(MonicaSteamPage.SCANNER) }
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

                        MonicaSteamPage.FRIENDS -> {
                            SteamFriendsScreen(
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
                                onOpenFriends = {
                                    navigateTo(MonicaSteamPage.FRIENDS)
                                },
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
    MonicaSteamPage.FRIENDS,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SteamStandaloneDock(
    order: List<SteamDockTab>,
    selected: SteamDockTab,
    showProgress: Boolean,
    onSelected: (SteamDockTab) -> Unit,
    onScan: () -> Unit
) {
    // Interaction pattern adapted from EssentialsFloatingToolbar (MIT).
    // See THIRD_PARTY_NOTICES.md for attribution and license text.
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val shouldHideLabel = fontScale > 1.25f || configuration.screenWidthDp < 400
    val tabs = SteamDockTab.sanitizeOrder(order)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (showProgress) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                .height(2.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = onScan,
                        modifier = Modifier.size(56.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.large,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.scan_qr_code)
                        )
                    }
                },
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                    toolbarContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    toolbarContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                SteamDockTab.sanitizeOrder(order).forEachIndexed { index, tab ->
                    val isSelected = tab == selected
                    val itemWidth by animateDpAsState(
                        targetValue = 48.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "steam_dock_item_width_${tab.name}"
                    )
                    val labelWidth by animateDpAsState(
                        targetValue = if (isSelected && !shouldHideLabel) 80.dp else 0.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "steam_dock_label_width_${tab.name}"
                    )
                    val spacerWidth by animateDpAsState(
                        targetValue = if (index < tabs.lastIndex) 8.dp else 0.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "steam_dock_spacing_${tab.name}"
                    )

                    IconButton(
                        onClick = { onSelected(tab) },
                        modifier = Modifier
                            .width(itemWidth + labelWidth)
                            .height(48.dp),
                        colors = if (isSelected) {
                            IconButtonDefaults.filledIconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        } else {
                            IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = tab.icon(),
                                contentDescription = tab.label(),
                                modifier = Modifier.size(24.dp)
                            )
                            if (isSelected && !shouldHideLabel) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = tab.label(),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                            }
                        }
                    }

                    if (index < tabs.lastIndex) {
                        Spacer(Modifier.width(spacerWidth))
                    }
                }
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
