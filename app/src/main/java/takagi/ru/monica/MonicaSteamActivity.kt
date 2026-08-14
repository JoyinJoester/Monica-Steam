package takagi.ru.monica

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import takagi.ru.monica.steam.navigation.SteamDockPreferences
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.SteamDockTab
import takagi.ru.monica.steam.navigation.icon
import takagi.ru.monica.steam.navigation.label
import takagi.ru.monica.steam.navigation.shouldEnableSteamLiquidGlassRuntimeEffects
import takagi.ru.monica.steam.navigation.shouldShowSteamDock
import takagi.ru.monica.steam.navigation.liquidglass.render.rememberSteamLiquidGlassBackdrop
import takagi.ru.monica.steam.navigation.liquidglass.render.steamLiquidGlassBackdropSource
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamLiquidGlassDock
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamLiquidGlassDockVisibility
import takagi.ru.monica.steam.navigation.ui.SteamEssentialsFloatingToolbar
import takagi.ru.monica.steam.navigation.ui.SteamFixedBottomBar
import takagi.ru.monica.steam.navigation.ui.SteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.SteamToolbarItem
import takagi.ru.monica.steam.navigation.ui.steamDockSwipe
import takagi.ru.monica.steam.navigation.ui.steamDockProgressiveBlur
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.MdbxRepository
import takagi.ru.monica.repository.MdbxRepositoryFactory
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.security.SteamAppLockGate
import takagi.ru.monica.steam.security.shouldProtectSteamSensitiveSurface
import takagi.ru.monica.steam.scanner.ui.SteamQrScannerScreen
import takagi.ru.monica.steam.community.ui.SteamCommunityScreen
import takagi.ru.monica.steam.backup.ui.SteamMaFileTransferScreen
import takagi.ru.monica.steam.health.ui.SteamHealthScreen
import takagi.ru.monica.steam.friends.chat.ui.SteamChatScreen
import takagi.ru.monica.steam.library.ui.SteamLibraryScreen
import takagi.ru.monica.steam.links.domain.SteamExternalLinkRouter
import takagi.ru.monica.steam.links.domain.SteamExternalLinkTarget
import takagi.ru.monica.steam.token.ui.SteamScreen
import takagi.ru.monica.steam.foundation.ui.ProvideSteamContentDensity
import takagi.ru.monica.steam.foundation.ui.setSteamUiScaledContent
import takagi.ru.monica.steam.store.share.domain.SteamStoreGameShare
import takagi.ru.monica.steam.store.ui.SteamStoreScreen
import takagi.ru.monica.steam.alerts.SteamAlerts
import takagi.ru.monica.steam.friends.chat.background.SteamChatBackground
import takagi.ru.monica.steam.friends.chat.background.SteamChatNotificationTarget
import takagi.ru.monica.ui.base.BaseMonicaActivity
import takagi.ru.monica.ui.screens.MonicaSteamSettingsScreen
import takagi.ru.monica.ui.screens.WebDavBackupScreen
import takagi.ru.monica.ui.screens.MdbxLocalCreateScreen
import takagi.ru.monica.ui.screens.MdbxLocalOpenScreen
import takagi.ru.monica.ui.screens.MdbxManagerScreen
import takagi.ru.monica.ui.screens.MdbxOneDriveCreateScreen
import takagi.ru.monica.ui.screens.MdbxOneDriveOpenScreen
import takagi.ru.monica.ui.screens.MdbxWebDavCreateScreen
import takagi.ru.monica.ui.screens.MdbxWebDavOpenScreen
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.viewmodel.MdbxViewModel
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel
import takagi.ru.monica.utils.AutoBackupManager

private enum class MonicaSteamPage {
    STEAM,
    SCANNER,
    HEALTH,
    COMMUNITY,
    LIBRARY,
    STORE,
    CHAT,
    MAFILE_TRANSFER,
    WEBDAV_BACKUP,
    MDBX,
    MDBX_CREATE,
    MDBX_OPEN,
    MDBX_WEBDAV_CREATE,
    MDBX_WEBDAV_OPEN,
    MDBX_ONEDRIVE_CREATE,
    MDBX_ONEDRIVE_OPEN,
    SETTINGS
}

private const val MONICA_BACK_EXIT_TIMEOUT_MS = 2_000L
private const val MONICA_STEAM_DOCK_CONTENT_KEY = "monica_steam_dock_root"
private const val STEAM_AUTO_BACKUP_PREFS_NAME = "webdav_config"
private const val STEAM_AUTO_BACKUP_ENABLED_KEY = "auto_backup_enabled"
private const val STEAM_LAST_BACKUP_TIME_KEY = "last_backup_time"
private const val STEAM_AUTO_BACKUP_INIT_DELAY_MS = 1_500L
private const val STEAM_AUTO_BACKUP_INTERVAL_HOURS = 12L

@Composable
private fun SteamStartupSurface() {
    MonicaTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {}
    }
}

class MonicaSteamActivity : BaseMonicaActivity() {
    private val pendingChatNotificationRequest =
        MutableStateFlow<SteamChatNotificationTarget?>(null)
    private val pendingExternalSteamLink = MutableStateFlow<SteamExternalLinkTarget?>(null)

    override fun shouldEnforceSharedSessionLock(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeChatNotificationIntent(intent)
        consumeExternalSteamLinkIntent(intent)

        lifecycleScope.launch {
            SteamAlerts.sync(this@MonicaSteamActivity)
        }
        initializeWebDavAutoBackupDeferred()

        setSteamUiScaledContent steamContent@{
            val loadedSettings by settingsManager.settingsFlow.collectAsState(
                initial = null
            )
            val dockPreferences = remember {
                SteamDockPreferences(this@MonicaSteamActivity.applicationContext)
            }
            val loadedDockConfiguration by dockPreferences.configuration.collectAsState(
                initial = null
            )
            val settings = loadedSettings
            val dockConfiguration = loadedDockConfiguration
            if (settings == null || dockConfiguration == null) {
                SteamStartupSurface()
                return@steamContent
            }

            val dockStyle = dockConfiguration.style
            val dockOrder = dockConfiguration.m3eOrder
            val liquidGlassDockOrder = dockConfiguration.liquidGlassOrder
            val fixedDockOrder = dockConfiguration.fixedOrder
            val activeDockOrder = when (dockStyle) {
                SteamDockStyle.M3E -> dockOrder
                SteamDockStyle.LIQUID_GLASS -> liquidGlassDockOrder
                SteamDockStyle.FIXED -> fixedDockOrder
            }
            val homePage = activeDockOrder.firstOrNull()?.toPage() ?: MonicaSteamPage.STEAM
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
                val chatNotificationRequest by pendingChatNotificationRequest.collectAsState()
                val externalSteamLink by pendingExternalSteamLink.collectAsState()
                val mdbxRepository: MdbxRepository = remember(passwordDatabase, securityManager) {
                    MdbxRepositoryFactory.create(
                        context = this@MonicaSteamActivity.applicationContext,
                        database = passwordDatabase,
                        securityManager = securityManager
                    )
                }
                val passwordRepository = remember(passwordDatabase, mdbxRepository) {
                    PasswordRepository(
                        passwordDatabase.passwordEntryDao(),
                        passwordDatabase.categoryDao(),
                        passwordDatabase.bitwardenFolderDao(),
                        passwordDatabase.secureItemDao(),
                        passwordDatabase.passkeyDao(),
                        passwordDatabase.passwordArchiveSyncMetaDao(),
                        passwordDatabase.passwordHistoryDao(),
                        mdbxRepository = mdbxRepository
                    )
                }
                val secureItemRepository = remember(passwordDatabase, mdbxRepository, securityManager) {
                    SecureItemRepository(
                        passwordDatabase.secureItemDao(),
                        mdbxRepository,
                        securityManager::decryptDataIfMonicaCiphertext
                    )
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
                var currentPage by rememberSaveable { mutableStateOf(homePage) }
                var pageHistory by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
                var pendingQrResult by rememberSaveable { mutableStateOf<String?>(null) }
                var pendingQrAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
                var pendingStoreAppId by rememberSaveable { mutableStateOf<Int?>(null) }
                var pendingStoreWebUrl by rememberSaveable { mutableStateOf<String?>(null) }
                var pendingCommunitySteamId by rememberSaveable { mutableStateOf<String?>(null) }
                var pendingChatPartnerSteamId by rememberSaveable { mutableStateOf<String?>(null) }
                var pendingChatGameShare by rememberSaveable {
                    mutableStateOf<SteamStoreGameShare?>(null)
                }
                var pendingChatGameSharePartnerSteamId by rememberSaveable {
                    mutableStateOf<String?>(null)
                }
                var pendingSteamNotifications by rememberSaveable { mutableStateOf(false) }
                var pendingAddSteamAccount by rememberSaveable { mutableStateOf(false) }
                var isSteamChatThreadOpen by rememberSaveable { mutableStateOf(false) }
                var activePlatformViewCount by remember { mutableIntStateOf(0) }
                val isPlatformViewActive = activePlatformViewCount > 0
                val onPlatformViewVisibilityChanged: (Boolean) -> Unit = { active ->
                    activePlatformViewCount = (
                        activePlatformViewCount + if (active) 1 else -1
                    ).coerceAtLeast(0)
                }
                var backPressedOnce by remember { mutableStateOf(false) }
                val composeScope = rememberCoroutineScope()
                val liquidGlassBackdrop = rememberSteamLiquidGlassBackdrop()
                val density = LocalDensity.current
                val imeVisible = WindowInsets.ime.getBottom(density) > 0
                val dockBlurHeightPx = with(density) { 130.dp.toPx() }
                val dockVisible = shouldShowSteamDock(
                    hasConfiguration = true,
                    isDockPage = currentPage.isDockPage(dockStyle),
                    chatThreadOpen = isSteamChatThreadOpen,
                    platformViewActive = isPlatformViewActive,
                    imeVisible = imeVisible
                )
                val liquidGlassEffectsEnabled = shouldEnableSteamLiquidGlassRuntimeEffects(
                    dockStyle = dockStyle,
                    dockVisible = dockVisible,
                    platformViewActive = isPlatformViewActive
                )
                var appliedInitialDockPage by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(dockConfiguration, homePage) {
                    if (!appliedInitialDockPage) {
                        currentPage = homePage
                        pageHistory = emptyList()
                        appliedInitialDockPage = true
                    }
                }

                LaunchedEffect(chatNotificationRequest) {
                    val request = chatNotificationRequest ?: return@LaunchedEffect
                    val activated = SteamChatBackground.activateNotificationTarget(
                        this@MonicaSteamActivity,
                        request
                    )
                    pendingChatNotificationRequest.value = null
                    if (activated) {
                        appliedInitialDockPage = true
                        pageHistory = emptyList()
                        pendingChatGameShare = null
                        pendingChatGameSharePartnerSteamId = null
                        pendingChatPartnerSteamId = request.partnerSteamId
                        currentPage = MonicaSteamPage.CHAT
                    } else {
                        Toast.makeText(
                            this@MonicaSteamActivity,
                            R.string.steam_chat_background_target_unavailable,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                LaunchedEffect(externalSteamLink) {
                    when (val target = externalSteamLink ?: return@LaunchedEffect) {
                        is SteamExternalLinkTarget.StoreApp -> {
                            pendingStoreWebUrl = null
                            pendingStoreAppId = target.appId
                            currentPage = MonicaSteamPage.STORE
                        }
                        is SteamExternalLinkTarget.CommunityProfile -> {
                            pendingCommunitySteamId = target.steamId
                            currentPage = MonicaSteamPage.COMMUNITY
                        }
                        is SteamExternalLinkTarget.Web -> {
                            pendingStoreAppId = null
                            pendingStoreWebUrl = target.url
                            currentPage = MonicaSteamPage.STORE
                        }
                    }
                    appliedInitialDockPage = true
                    pageHistory = emptyList()
                    pendingExternalSteamLink.value = null
                }

                LaunchedEffect(currentPage, dockStyle) {
                    if (currentPage.isDockPage(dockStyle) && pageHistory.isNotEmpty()) {
                        pageHistory = emptyList()
                    }
                    if (
                        currentPage != MonicaSteamPage.STEAM &&
                        currentPage != MonicaSteamPage.CHAT
                    ) {
                        isSteamChatThreadOpen = false
                    }
                }

                fun navigateTo(page: MonicaSteamPage) {
                    if (page == currentPage) return
                    pageHistory = if (page.isDockPage(dockStyle)) {
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

                fun openSteamAccountAddition() {
                    pendingAddSteamAccount = true
                    navigateTo(MonicaSteamPage.STEAM)
                }

                CompositionLocalProvider(
                    LocalReduceAnimations provides settings.reduceAnimations
                ) {
                    SteamAppLockGate(
                        enabled = !settings.steamLockTokenPageOnly,
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

                        if (currentPage.isDockPage(dockStyle)) {
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
                        Box(modifier = Modifier.fillMaxSize()) {
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                contentWindowInsets = WindowInsets(0, 0, 0, 0)
                            ) {
                                ProvideSteamContentDensity {
                                    CompositionLocalProvider(
                                        LocalSteamDockContentClearance provides if (dockVisible) {
                                            SteamDockContentClearance
                                        } else {
                                            0.dp
                                        }
                                    ) {
                                        AnimatedContent(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .steamDockProgressiveBlur(
                                                    enabled = dockStyle == SteamDockStyle.M3E &&
                                                        dockVisible,
                                                    blurRadius = 40f,
                                                    height = dockBlurHeightPx
                                                )
                                                .steamLiquidGlassBackdropSource(
                                                    backdrop = liquidGlassBackdrop,
                                                    enabled = liquidGlassEffectsEnabled
                                                ),
                                            targetState = currentPage,
                                            label = "monica_steam_page_transition",
                                            contentKey = { page -> page.transitionContentKey(dockStyle) },
                                            transitionSpec = {
                                                if (
                                                    initialState.isDockPage(dockStyle) &&
                                                    targetState.isDockPage(dockStyle)
                                                ) {
                                                    // Monica Android's SimpleMainScreen swaps top-level tabs
                                                    // directly; only the NavigationBar selection animates.
                                                    EnterTransition.None togetherWith ExitTransition.None
                                                } else {
                                                    // Every secondary route in Monica Android uses the
                                                    // EasyNotes scale/fade transition for both push and pop.
                                                    easyNotesScreenEnter(settings.reduceAnimations)
                                                        .togetherWith(
                                                            easyNotesScreenExit(settings.reduceAnimations)
                                                        )
                                                }
                                            }
                                        ) { page ->
                                            when (page) {
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
                                modifier = Modifier.fillMaxSize()
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
                                onOpenMaFileTransfer = {
                                    navigateTo(MonicaSteamPage.MAFILE_TRANSFER)
                                },
                                onOpenWebDavBackup = {
                                    navigateTo(MonicaSteamPage.WEBDAV_BACKUP)
                                },
                                onOpenMdbx = { navigateTo(MonicaSteamPage.MDBX) },
                                dockStyle = dockStyle,
                                onDockStyleChange = { style ->
                                    composeScope.launch { dockPreferences.updateStyle(style) }
                                },
                                dockOrder = dockOrder,
                                onDockOrderChange = { order ->
                                    composeScope.launch { dockPreferences.updateOrder(order) }
                                },
                                liquidGlassDockOrder = liquidGlassDockOrder,
                                onLiquidGlassDockOrderChange = { order ->
                                    composeScope.launch {
                                        dockPreferences.updateLiquidGlassOrder(order)
                                    }
                                },
                                fixedDockOrder = fixedDockOrder,
                                onFixedDockOrderChange = { order ->
                                    composeScope.launch {
                                        dockPreferences.updateFixedOrder(order)
                                    }
                                },
                                showNavigationBack = false,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        MonicaSteamPage.HEALTH -> {
                            SteamHealthScreen(
                                onNavigateBack = { navigateBack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        MonicaSteamPage.COMMUNITY -> {
                            SteamCommunityScreen(
                                onNavigateBack = { navigateBack() },
                                initialSteamId = pendingCommunitySteamId,
                                onInitialSteamIdConsumed = { pendingCommunitySteamId = null },
                                onOpenStoreApp = { appId ->
                                    pendingStoreAppId = appId
                                    navigateTo(MonicaSteamPage.STORE)
                                },
                                onOpenStore = { navigateTo(MonicaSteamPage.STORE) },
                                onAddSteamAccount = ::openSteamAccountAddition,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        MonicaSteamPage.LIBRARY -> {
                            SteamLibraryScreen(
                                onNavigateBack = { navigateBack() },
                                showNavigationBack = false,
                                onOpenSettings = { navigateTo(MonicaSteamPage.SETTINGS) },
                                onOpenNotifications = {
                                    pendingSteamNotifications = true
                                    navigateTo(MonicaSteamPage.STEAM)
                                },
                                onAddSteamAccount = ::openSteamAccountAddition,
                                onPlatformViewVisibilityChanged = onPlatformViewVisibilityChanged,
                                onOpenStoreApp = { appId ->
                                    pendingStoreAppId = appId
                                    navigateTo(MonicaSteamPage.STORE)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        MonicaSteamPage.STORE -> {
                            SteamStoreScreen(
                                showNavigationBack = false,
                                onOpenSettings = { navigateTo(MonicaSteamPage.SETTINGS) },
                                onOpenNotifications = {
                                    pendingSteamNotifications = true
                                    navigateTo(MonicaSteamPage.STEAM)
                                },
                                onAddSteamAccount = ::openSteamAccountAddition,
                                onOpenChatShare = { partnerSteamId, share ->
                                    pendingChatPartnerSteamId = partnerSteamId
                                    pendingChatGameShare = share
                                    pendingChatGameSharePartnerSteamId = partnerSteamId
                                    navigateTo(MonicaSteamPage.CHAT)
                                },
                                initialAppId = pendingStoreAppId,
                                onInitialAppIdConsumed = { pendingStoreAppId = null },
                                initialWebUrl = pendingStoreWebUrl,
                                onInitialWebUrlConsumed = { pendingStoreWebUrl = null },
                                onPlatformViewVisibilityChanged = onPlatformViewVisibilityChanged,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        MonicaSteamPage.CHAT -> {
                            SteamChatScreen(
                                standalone = true,
                                requestedPartnerSteamId = pendingChatPartnerSteamId,
                                onConsumeRequestedPartner = {
                                    pendingChatPartnerSteamId = null
                                },
                                requestedGameShare = pendingChatGameShare,
                                requestedGameSharePartnerSteamId =
                                    pendingChatGameSharePartnerSteamId,
                                onConsumeRequestedGameShare = {
                                    pendingChatGameShare = null
                                    pendingChatGameSharePartnerSteamId = null
                                },
                                onThreadVisibilityChange = { open ->
                                    isSteamChatThreadOpen = open
                                },
                                onPlatformViewVisibilityChanged = onPlatformViewVisibilityChanged,
                                onOpenStoreApp = { appId ->
                                    pendingStoreAppId = appId
                                    navigateTo(MonicaSteamPage.STORE)
                                },
                                onAddSteamAccount = ::openSteamAccountAddition,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        MonicaSteamPage.MAFILE_TRANSFER -> {
                            SteamMaFileTransferScreen(
                                onNavigateBack = { navigateBack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        MonicaSteamPage.WEBDAV_BACKUP -> {
                            WebDavBackupScreen(
                                passwordRepository = passwordRepository,
                                secureItemRepository = secureItemRepository,
                                onNavigateBack = { navigateBack() },
                                steamMaFileOnly = true
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
                                onNavigateToOneDriveCreate = {
                                    navigateTo(MonicaSteamPage.MDBX_ONEDRIVE_CREATE)
                                },
                                onNavigateToOneDriveOpen = {
                                    navigateTo(MonicaSteamPage.MDBX_ONEDRIVE_OPEN)
                                },
                                localOnly = false,
                                oneDriveEnabled = true
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

                        MonicaSteamPage.MDBX_ONEDRIVE_CREATE -> {
                            MdbxOneDriveCreateScreen(
                                viewModel = mdbxViewModel,
                                onNavigateBack = { navigateBack() }
                            )
                        }

                        MonicaSteamPage.MDBX_ONEDRIVE_OPEN -> {
                            MdbxOneDriveOpenScreen(
                                viewModel = mdbxViewModel,
                                onNavigateBack = { navigateBack() }
                            )
                        }

                        MonicaSteamPage.STEAM -> {
                            SteamAppLockGate(
                                enabled = shouldProtectSteamSensitiveSurface(
                                    tokenPageOnly = settings.steamLockTokenPageOnly,
                                    startupVerificationBypass =
                                        settings.disablePasswordVerification
                                ),
                                allowStartupVerificationBypass = false,
                                settings = settings,
                                settingsViewModel = steamSettingsViewModel,
                                passwordViewModel = passwordViewModel,
                                securityManager = securityManager
                            ) {
                                SteamScreen(
                                    showStandaloneSettingsEntry = true,
                                    onOpenStandaloneSettings = {
                                        navigateTo(MonicaSteamPage.SETTINGS)
                                    },
                                    onOpenBackup = {
                                        navigateTo(MonicaSteamPage.MAFILE_TRANSFER)
                                    },
                                    onOpenCommunity = { steamId ->
                                        pendingCommunitySteamId = steamId
                                        navigateTo(MonicaSteamPage.COMMUNITY)
                                    },
                                    openNotificationsOnEntry = pendingSteamNotifications,
                                    onNotificationsEntryConsumed = {
                                        pendingSteamNotifications = false
                                    },
                                    openAddAccountOnEntry = pendingAddSteamAccount,
                                    onAddAccountEntryConsumed = {
                                        pendingAddSteamAccount = false
                                    },
                                    onOpenStoreApp = { appId ->
                                        pendingStoreAppId = appId
                                        navigateTo(MonicaSteamPage.STORE)
                                    },
                                    onOpenChat = { partnerSteamId ->
                                        pendingChatGameShare = null
                                        pendingChatGameSharePartnerSteamId = null
                                        pendingChatPartnerSteamId = partnerSteamId
                                        navigateTo(MonicaSteamPage.CHAT)
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
                                    onPlatformViewVisibilityChanged =
                                        onPlatformViewVisibilityChanged,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                                            }
                                        }
                                    }
                                }
                    }

                            if (dockStyle == SteamDockStyle.M3E && dockVisible) {
                                SteamStandaloneDock(
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                    order = dockOrder,
                                    selected = currentPage.toDockTab(),
                                    onSelected = { tab ->
                                        pageHistory = emptyList()
                                        currentPage = tab.toPage()
                                    }
                                )
                            }
                            if (!imeVisible) {
                                SteamLiquidGlassDockVisibility(
                                    visible = dockStyle == SteamDockStyle.LIQUID_GLASS && dockVisible,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .zIndex(1f)
                                ) {
                                    SteamLiquidGlassDock(
                                        order = liquidGlassDockOrder,
                                        selected = currentPage.toDockTab(),
                                        backdrop = liquidGlassBackdrop,
                                        runtimeEffectsEnabled = liquidGlassEffectsEnabled,
                                        onSelected = { tab ->
                                            pageHistory = emptyList()
                                            currentPage = tab.toPage()
                                        }
                                    )
                                }
                            }
                            if (dockStyle == SteamDockStyle.FIXED && dockVisible) {
                                SteamFixedBottomBar(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .zIndex(1f),
                                    order = fixedDockOrder,
                                    selected = currentPage.toDockTab(),
                                    onSelected = { tab ->
                                        pageHistory = emptyList()
                                        currentPage = tab.toPage()
                                    }
                                )
                            }
                }
            }
                    }
                }
    }

}

}

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeChatNotificationIntent(intent)
        consumeExternalSteamLinkIntent(intent)
    }

    private fun consumeChatNotificationIntent(intent: Intent?) {
        SteamChatBackground.consumeNotification(intent)?.let { request ->
            pendingChatNotificationRequest.value = request
        }
    }

    private fun consumeExternalSteamLinkIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        SteamExternalLinkRouter.route(intent.dataString)?.let { target ->
            pendingExternalSteamLink.value = target
        }
    }

    private fun initializeWebDavAutoBackupDeferred() {
        lifecycleScope.launch {
            delay(STEAM_AUTO_BACKUP_INIT_DELAY_MS)
            runCatching {
                val prefs = getSharedPreferences(STEAM_AUTO_BACKUP_PREFS_NAME, MODE_PRIVATE)
                if (!prefs.getBoolean(STEAM_AUTO_BACKUP_ENABLED_KEY, false)) return@runCatching
                val lastBackupTime = prefs.getLong(STEAM_LAST_BACKUP_TIME_KEY, 0L)
                if (shouldTriggerWebDavAutoBackup(lastBackupTime, System.currentTimeMillis())) {
                    AutoBackupManager(this@MonicaSteamActivity).triggerBackupNow(
                        steamMaFileOnly = true
                    )
                }
            }.onFailure { error ->
                takagi.ru.monica.steam.diagnostics.SteamDiagLogger.append(
                    "webdav_auto_backup_init failed type=${error::class.java.simpleName}"
                )
            }
        }
    }

    private fun shouldTriggerWebDavAutoBackup(lastBackupTime: Long, currentTime: Long): Boolean {
        if (lastBackupTime == 0L) return true
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = lastBackupTime
        val lastBackupDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val lastBackupYear = calendar.get(java.util.Calendar.YEAR)
        calendar.timeInMillis = currentTime
        val currentDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val currentYear = calendar.get(java.util.Calendar.YEAR)
        val isNewDay = currentYear > lastBackupYear ||
            (currentYear == lastBackupYear && currentDay > lastBackupDay)
        if (!isNewDay) return false
        val hoursSinceLastBackup = (currentTime - lastBackupTime) / (1_000L * 60L * 60L)
        return hoursSinceLastBackup >= STEAM_AUTO_BACKUP_INTERVAL_HOURS
    }

}

private fun MonicaSteamPage.isDockPage(style: SteamDockStyle): Boolean = when (this) {
    MonicaSteamPage.STEAM,
    MonicaSteamPage.LIBRARY,
    MonicaSteamPage.STORE,
    MonicaSteamPage.CHAT -> true
    MonicaSteamPage.SETTINGS -> style == SteamDockStyle.LIQUID_GLASS ||
        style == SteamDockStyle.FIXED
    MonicaSteamPage.SCANNER,
    MonicaSteamPage.HEALTH,
    MonicaSteamPage.COMMUNITY,
    MonicaSteamPage.MAFILE_TRANSFER,
    MonicaSteamPage.WEBDAV_BACKUP -> false
    MonicaSteamPage.MDBX,
    MonicaSteamPage.MDBX_CREATE,
    MonicaSteamPage.MDBX_OPEN,
    MonicaSteamPage.MDBX_WEBDAV_CREATE,
    MonicaSteamPage.MDBX_WEBDAV_OPEN,
    MonicaSteamPage.MDBX_ONEDRIVE_CREATE,
    MonicaSteamPage.MDBX_ONEDRIVE_OPEN -> false
}

private fun MonicaSteamPage.transitionContentKey(style: SteamDockStyle): Any =
    if (isDockPage(style)) MONICA_STEAM_DOCK_CONTENT_KEY else this

private fun MonicaSteamPage.toDockTab(): SteamDockTab = when (this) {
    MonicaSteamPage.LIBRARY -> SteamDockTab.LIBRARY
    MonicaSteamPage.STORE -> SteamDockTab.STORE
    MonicaSteamPage.CHAT -> SteamDockTab.CHAT
    MonicaSteamPage.SETTINGS -> SteamDockTab.SETTINGS
    else -> SteamDockTab.TOKEN
}

private fun SteamDockTab.toPage(): MonicaSteamPage = when (this) {
    SteamDockTab.TOKEN -> MonicaSteamPage.STEAM
    SteamDockTab.LIBRARY -> MonicaSteamPage.LIBRARY
    SteamDockTab.STORE -> MonicaSteamPage.STORE
    SteamDockTab.CHAT -> MonicaSteamPage.CHAT
    SteamDockTab.SETTINGS -> MonicaSteamPage.SETTINGS
}

internal fun initialSteamDockPage(order: List<SteamDockTab>): String =
    when (SteamDockTab.sanitizeOrder(order).firstOrNull() ?: SteamDockTab.TOKEN) {
        SteamDockTab.TOKEN -> "STEAM"
        SteamDockTab.LIBRARY -> "LIBRARY"
        SteamDockTab.STORE -> "STORE"
        SteamDockTab.CHAT -> "CHAT"
        SteamDockTab.SETTINGS -> "SETTINGS"
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SteamStandaloneDock(
    modifier: Modifier = Modifier,
    order: List<SteamDockTab>,
    selected: SteamDockTab,
    onSelected: (SteamDockTab) -> Unit
) {
    val tabs = SteamDockTab.sanitizeOrder(order)
        .filterNot { it == SteamDockTab.TOKEN }
    val tokenSelected = selected == SteamDockTab.TOKEN
    val tokenLabel = SteamDockTab.TOKEN.label()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .steamDockSwipe(
                order = tabs,
                selected = selected,
                thresholdPx = with(LocalDensity.current) { 56.dp.toPx() },
                onSelected = onSelected
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        SteamEssentialsFloatingToolbar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(1f),
            selectedIndex = tabs.indexOf(selected),
            items = tabs.map { tab ->
                SteamToolbarItem(
                    icon = tab.icon(),
                    label = tab.label(),
                    onClick = { onSelected(tab) }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { onSelected(SteamDockTab.TOKEN) },
                    modifier = Modifier.size(56.dp),
                    containerColor = if (tokenSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (tokenSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    shape = MaterialTheme.shapes.large,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = tokenLabel,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )
    }
}
