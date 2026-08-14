package takagi.ru.monica.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.plus.PlusActivationUiResult
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.SteamDockTab
import takagi.ru.monica.steam.navigation.reorderDockOrder
import takagi.ru.monica.steam.navigation.reorderFixedDockOrder
import takagi.ru.monica.steam.navigation.reorderLiquidGlassDockOrder
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.itad.ui.ItadSettingsScreen
import takagi.ru.monica.steam.notifications.settings.ui.SteamNotificationSettingsScreen
import takagi.ru.monica.steam.network.optimization.ui.SteamNetworkOptimizationAutoScreen
import takagi.ru.monica.steam.network.optimization.ui.SteamNetworkResolverSettingsScreen
import takagi.ru.monica.steam.network.optimization.ui.SteamNetworkOptimizationSettingsScreen
import takagi.ru.monica.steam.store.hints.ui.SteamStoreHintSettingsScreen
import takagi.ru.monica.steam.security.SteamAppLockGate
import takagi.ru.monica.steam.security.shouldProtectSteamSensitiveSurface
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.viewmodel.SettingsViewModel
import takagi.ru.monica.viewmodel.PasswordViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class SteamSettingsChild {
    DATA_MANAGEMENT,
    APPEARANCE,
    STEAM_FEATURES,
    NAVIGATION,
    DOCK,
    COLORS,
    CUSTOM_COLORS,
    MASTER_PASSWORD_SETUP,
    MASTER_PASSWORD_LOCKING,
    RESET_PASSWORD,
    SECURITY_QUESTIONS,
    PLUS,
    PAYMENT,
    DEVELOPER,
    EXTENSIONS,
    NOTIFICATIONS,
    NETWORK_OPTIMIZATION,
    NETWORK_OPTIMIZATION_RESOLVERS,
    NETWORK_OPTIMIZATION_ADVANCED,
    STORE_HINTS,
    PRICE_DATA
}

private fun SteamSettingsChild.parent(): SteamSettingsChild? = when (this) {
    SteamSettingsChild.DATA_MANAGEMENT,
    SteamSettingsChild.APPEARANCE,
    SteamSettingsChild.STEAM_FEATURES,
    SteamSettingsChild.NAVIGATION,
    SteamSettingsChild.MASTER_PASSWORD_SETUP,
    SteamSettingsChild.MASTER_PASSWORD_LOCKING,
    SteamSettingsChild.NOTIFICATIONS,
    SteamSettingsChild.NETWORK_OPTIMIZATION -> null

    SteamSettingsChild.COLORS -> SteamSettingsChild.APPEARANCE

    SteamSettingsChild.DOCK -> SteamSettingsChild.NAVIGATION
    SteamSettingsChild.EXTENSIONS,
    SteamSettingsChild.PLUS,
    SteamSettingsChild.DEVELOPER -> null

    SteamSettingsChild.CUSTOM_COLORS -> SteamSettingsChild.COLORS
    SteamSettingsChild.STORE_HINTS,
    SteamSettingsChild.PRICE_DATA -> SteamSettingsChild.STEAM_FEATURES
    SteamSettingsChild.NETWORK_OPTIMIZATION_RESOLVERS,
    SteamSettingsChild.NETWORK_OPTIMIZATION_ADVANCED -> SteamSettingsChild.NETWORK_OPTIMIZATION

    SteamSettingsChild.RESET_PASSWORD,
    SteamSettingsChild.SECURITY_QUESTIONS -> SteamSettingsChild.MASTER_PASSWORD_LOCKING

    SteamSettingsChild.PAYMENT -> SteamSettingsChild.PLUS
}

@Composable
fun MonicaSteamSettingsScreen(
    settings: AppSettings,
    settingsManager: SettingsManager,
    settingsViewModel: SettingsViewModel,
    passwordViewModel: PasswordViewModel,
    securityManager: SecurityManager,
    onNavigateBack: () -> Unit,
    onOpenMaFileTransfer: () -> Unit = {},
    onOpenWebDavBackup: () -> Unit = {},
    onOpenMdbx: () -> Unit = {},
    dockStyle: SteamDockStyle = SteamDockStyle.M3E,
    onDockStyleChange: (SteamDockStyle) -> Unit = {},
    dockOrder: List<SteamDockTab> = SteamDockTab.DEFAULT_ORDER,
    onDockOrderChange: (List<SteamDockTab>) -> Unit = {},
    liquidGlassDockOrder: List<SteamDockTab> = SteamDockTab.LIQUID_GLASS_DEFAULT_ORDER,
    onLiquidGlassDockOrderChange: (List<SteamDockTab>) -> Unit = {},
    fixedDockOrder: List<SteamDockTab> = SteamDockTab.FIXED_DEFAULT_ORDER,
    onFixedDockOrderChange: (List<SteamDockTab>) -> Unit = {},
    showNavigationBack: Boolean = true,
    modifier: Modifier = Modifier
) {
    var child by remember { mutableStateOf<SteamSettingsChild?>(null) }
    val settingsScrollState = rememberScrollState()
    val dataManagementScrollState = rememberScrollState()
    val appearanceScrollState = rememberScrollState()
    val steamFeaturesScrollState = rememberScrollState()
    val navigationScrollState = rememberScrollState()
    val context = LocalContext.current
    val dockContentClearance = LocalSteamDockContentClearance.current

    val openMasterPasswordSettings: () -> Unit = {
        child = if (securityManager.isMasterPasswordSet()) {
            SteamSettingsChild.MASTER_PASSWORD_LOCKING
        } else {
            SteamSettingsChild.MASTER_PASSWORD_SETUP
        }
    }
    val settingsHomeSections = remember(context, settings.language, securityManager) {
        buildMonicaSteamSettingsHomeSections(
            context = context,
            onOpenMasterPassword = openMasterPasswordSettings,
            onOpenDataManagement = { child = SteamSettingsChild.DATA_MANAGEMENT },
            onOpenAppearance = { child = SteamSettingsChild.APPEARANCE },
            onOpenNavigation = { child = SteamSettingsChild.NAVIGATION },
            onOpenSteamExperience = { child = SteamSettingsChild.STEAM_FEATURES },
            onOpenNotifications = { child = SteamSettingsChild.NOTIFICATIONS }
        )
    }

    BackHandler(enabled = child != null) {
        child = child?.parent()
    }

    @Composable
    fun SharedSettingsSurface(
        mode: SettingsScreenMode,
        title: String?,
        scrollState: ScrollState,
        showBack: Boolean,
        onBack: () -> Unit,
        additionalGroup: SteamSettingsAdditionalGroup = SteamSettingsAdditionalGroup.NONE
    ) {
        MonicaSteamSharedSettingsHost(
            settings = settings,
            settingsManager = settingsManager,
            settingsViewModel = settingsViewModel,
            scrollState = scrollState,
            screenMode = mode,
            screenTitle = title,
            onNavigateBack = onBack,
            onOpenMaFileTransfer = onOpenMaFileTransfer,
            onOpenWebDavBackup = onOpenWebDavBackup,
            onOpenMdbx = onOpenMdbx,
            onOpenDock = { child = SteamSettingsChild.DOCK },
            onOpenColors = { child = SteamSettingsChild.COLORS },
            onOpenMasterPasswordLocking = openMasterPasswordSettings,
            onOpenPlus = { child = SteamSettingsChild.PLUS },
            onOpenDeveloper = { child = SteamSettingsChild.DEVELOPER },
            onOpenExtensions = { child = SteamSettingsChild.EXTENSIONS },
            onOpenNetworkOptimization = { child = SteamSettingsChild.NETWORK_OPTIMIZATION },
            onOpenStoreHints = { child = SteamSettingsChild.STORE_HINTS },
            onOpenItad = { child = SteamSettingsChild.PRICE_DATA },
            onOpenDataManagement = { child = SteamSettingsChild.DATA_MANAGEMENT },
            onOpenAppearance = { child = SteamSettingsChild.APPEARANCE },
            onOpenSteamFeatures = { child = SteamSettingsChild.STEAM_FEATURES },
            compactHomeSections = settingsHomeSections,
            additionalGroup = additionalGroup,
            showNavigationBack = showBack,
            modifier = Modifier.fillMaxSize(),
            context = context
        )
    }

    AnimatedContent(
        targetState = child,
        modifier = modifier,
        transitionSpec = {
            easyNotesScreenEnter(settings.reduceAnimations)
                .togetherWith(easyNotesScreenExit(settings.reduceAnimations))
        },
        label = "MonicaSteamSettingsNavigation"
    ) { animatedChild ->
        if (animatedChild == null) {
            SharedSettingsSurface(
                mode = SettingsScreenMode.COMPACT_HOME,
                title = null,
                scrollState = settingsScrollState,
                showBack = showNavigationBack,
                onBack = onNavigateBack
            )
        } else {
            when (animatedChild) {
                SteamSettingsChild.DATA_MANAGEMENT -> SharedSettingsSurface(
                    mode = SettingsScreenMode.DATA_MANAGEMENT,
                    title = context.getString(R.string.settings_data_management_entry_title),
                    scrollState = dataManagementScrollState,
                    showBack = true,
                    onBack = { child = null }
                )
                SteamSettingsChild.APPEARANCE -> SharedSettingsSurface(
                    mode = SettingsScreenMode.APPEARANCE,
                    title = context.getString(R.string.settings_appearance_entry_title),
                    scrollState = appearanceScrollState,
                    showBack = true,
                    onBack = { child = null }
                )
                SteamSettingsChild.STEAM_FEATURES -> SharedSettingsSurface(
                    mode = SettingsScreenMode.ADDITIONAL,
                    title = context.getString(R.string.steam_settings_features_title),
                    scrollState = steamFeaturesScrollState,
                    showBack = true,
                    onBack = { child = null },
                    additionalGroup = SteamSettingsAdditionalGroup.STEAM_EXPERIENCE
                )
                SteamSettingsChild.NAVIGATION -> SharedSettingsSurface(
                    mode = SettingsScreenMode.ADDITIONAL,
                    title = context.getString(R.string.steam_settings_navigation_title),
                    scrollState = navigationScrollState,
                    showBack = true,
                    onBack = { child = null },
                    additionalGroup = SteamSettingsAdditionalGroup.NAVIGATION
                )
                SteamSettingsChild.DOCK -> SteamDockOrderScreen(
                    order = dockOrder,
                    onOrderChange = onDockOrderChange,
                    style = dockStyle,
                    onStyleChange = onDockStyleChange,
                    liquidGlassOrder = liquidGlassDockOrder,
                    onLiquidGlassOrderChange = onLiquidGlassDockOrderChange,
                    fixedOrder = fixedDockOrder,
                    onFixedOrderChange = onFixedDockOrderChange,
                    onNavigateBack = { child = SteamSettingsChild.NAVIGATION },
                    modifier = Modifier.fillMaxSize()
                )
                SteamSettingsChild.COLORS -> ColorSchemeSelectionScreen(
                    settingsViewModel = settingsViewModel,
                    onNavigateBack = { child = SteamSettingsChild.APPEARANCE },
                    onNavigateToCustomColors = { child = SteamSettingsChild.CUSTOM_COLORS },
                    contentBottomPadding = dockContentClearance + 16.dp,
                    modifier = Modifier.fillMaxSize()
                )
                SteamSettingsChild.CUSTOM_COLORS -> CustomColorSettingsScreen(
                    settingsViewModel = settingsViewModel,
                    onNavigateBack = { child = SteamSettingsChild.COLORS },
                    contentBottomPadding = dockContentClearance + 16.dp,
                    modifier = Modifier.fillMaxSize()
                )
                SteamSettingsChild.MASTER_PASSWORD_SETUP -> LoginScreen(
                    viewModel = passwordViewModel,
                    settingsViewModel = settingsViewModel,
                    onAuthenticationSuccess = {
                        child = SteamSettingsChild.MASTER_PASSWORD_LOCKING
                    }
                )
                SteamSettingsChild.MASTER_PASSWORD_LOCKING ->
                    SteamSensitiveSettingsGate(
                        settings = settings,
                        settingsViewModel = settingsViewModel,
                        passwordViewModel = passwordViewModel,
                        securityManager = securityManager
                    ) {
                        MasterPasswordLockingSettingsScreen(
                            viewModel = settingsViewModel,
                            onNavigateBack = { child = null },
                            onResetPassword = {
                                child = SteamSettingsChild.RESET_PASSWORD
                            },
                            onSecurityQuestions = {
                                child = SteamSettingsChild.SECURITY_QUESTIONS
                            },
                            showSteamTokenPageLockOption = true,
                            contentBottomPadding = dockContentClearance + 16.dp
                        )
                    }
                SteamSettingsChild.RESET_PASSWORD -> SteamSensitiveSettingsGate(
                    settings = settings,
                    settingsViewModel = settingsViewModel,
                    passwordViewModel = passwordViewModel,
                    securityManager = securityManager
                ) {
                    ResetPasswordScreen(
                        securityManager = securityManager,
                        onNavigateBack = {
                            child = SteamSettingsChild.MASTER_PASSWORD_LOCKING
                        },
                        onResetSuccess = {
                            child = SteamSettingsChild.MASTER_PASSWORD_LOCKING
                        },
                        contentBottomPadding = dockContentClearance + 16.dp
                    )
                }
                SteamSettingsChild.SECURITY_QUESTIONS ->
                    SteamSensitiveSettingsGate(
                        settings = settings,
                        settingsViewModel = settingsViewModel,
                        passwordViewModel = passwordViewModel,
                        securityManager = securityManager
                    ) {
                        SecurityQuestionsSetupScreen(
                            securityManager = securityManager,
                            onNavigateBack = {
                                child = SteamSettingsChild.MASTER_PASSWORD_LOCKING
                            },
                            onSetupComplete = {
                                child = SteamSettingsChild.MASTER_PASSWORD_LOCKING
                            },
                            contentBottomPadding = dockContentClearance + 16.dp
                        )
                    }
                SteamSettingsChild.PLUS -> MonicaPlusScreen(
                    isPlusActivated = settings.isPlusActivated,
                    onNavigateBack = { child = null },
                    onNavigateToPayment = { child = SteamSettingsChild.PAYMENT },
                    onDeactivatePlus = { settingsViewModel.clearPlusLicenseData() },
                    contentBottomPadding = dockContentClearance + 16.dp,
                    modifier = Modifier.fillMaxSize()
                )
                SteamSettingsChild.PAYMENT -> PaymentScreen(
                    onNavigateBack = { child = SteamSettingsChild.PLUS },
                    onActivatePlus = {
                        settingsViewModel.updatePlusActivated(true)
                        PlusActivationUiResult(
                            success = true,
                            message = context.getString(R.string.plus_status_activated)
                        )
                    },
                    contentBottomPadding = dockContentClearance + 16.dp,
                    modifier = Modifier.fillMaxSize()
                )
                SteamSettingsChild.DEVELOPER -> DeveloperSettingsScreen(
                    onNavigateBack = { child = null },
                    contentBottomPadding = dockContentClearance + 16.dp,
                    modifier = Modifier.fillMaxSize()
                )
                SteamSettingsChild.EXTENSIONS -> ExtensionsScreen(
                    onNavigateBack = { child = null },
                    onNavigateToMonicaPlus = { child = SteamSettingsChild.PLUS },
                    isPlusActivated = settings.isPlusActivated,
                    clipboardAutoClearSeconds = settings.clipboardAutoClearSeconds,
                    onClipboardAutoClearSecondsChange = settingsViewModel::updateClipboardAutoClearSeconds,
                    steamMiniProfileBackgroundEnabled = settings.steamMiniProfileBackgroundEnabled,
                    onSteamMiniProfileBackgroundEnabledChange =
                        settingsViewModel::updateSteamMiniProfileBackgroundEnabled,
                    surfacePolicy = ExtensionsSurfacePolicy(
                        showQuickSetup = false,
                        showPasswordDisplay = false,
                        showTotp = false
                    ),
                    additionalContent = { SteamWidgetExtensionContent(context) },
                    contentBottomPadding = dockContentClearance + 16.dp,
                    modifier = Modifier.fillMaxSize()
                )
                SteamSettingsChild.NOTIFICATIONS -> SteamNotificationSettingsScreen(
                    onNavigateBack = { child = null },
                    modifier = Modifier.fillMaxSize()
                )
                SteamSettingsChild.NETWORK_OPTIMIZATION -> SteamNetworkOptimizationAutoScreen(
                    onNavigateBack = { child = null },
                    onOpenResolvers = {
                        child = SteamSettingsChild.NETWORK_OPTIMIZATION_RESOLVERS
                    },
                    onOpenAdvanced = {
                        child = SteamSettingsChild.NETWORK_OPTIMIZATION_ADVANCED
                    },
                    modifier = Modifier.fillMaxSize()
                )
                SteamSettingsChild.NETWORK_OPTIMIZATION_RESOLVERS -> {
                    SteamNetworkResolverSettingsScreen(
                        onNavigateBack = { child = SteamSettingsChild.NETWORK_OPTIMIZATION },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                SteamSettingsChild.NETWORK_OPTIMIZATION_ADVANCED -> {
                    SteamNetworkOptimizationSettingsScreen(
                        onNavigateBack = { child = SteamSettingsChild.NETWORK_OPTIMIZATION },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                SteamSettingsChild.STORE_HINTS -> SteamStoreHintSettingsScreen(
                    onNavigateBack = { child = SteamSettingsChild.STEAM_FEATURES },
                    modifier = Modifier.fillMaxSize()
                )
                SteamSettingsChild.PRICE_DATA -> ItadSettingsScreen(
                    onNavigateBack = { child = SteamSettingsChild.STEAM_FEATURES },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun SteamSensitiveSettingsGate(
    settings: AppSettings,
    settingsViewModel: SettingsViewModel,
    passwordViewModel: PasswordViewModel,
    securityManager: SecurityManager,
    content: @Composable () -> Unit
) {
    SteamAppLockGate(
        enabled = shouldProtectSteamSensitiveSurface(
            tokenPageOnly = settings.steamLockTokenPageOnly,
            startupVerificationBypass = settings.disablePasswordVerification
        ),
        allowStartupVerificationBypass = false,
        settings = settings,
        settingsViewModel = settingsViewModel,
        passwordViewModel = passwordViewModel,
        securityManager = securityManager,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteamDockOrderScreen(
    order: List<SteamDockTab>,
    onOrderChange: (List<SteamDockTab>) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    style: SteamDockStyle = SteamDockStyle.M3E,
    onStyleChange: (SteamDockStyle) -> Unit = {},
    liquidGlassOrder: List<SteamDockTab> = SteamDockTab.LIQUID_GLASS_DEFAULT_ORDER,
    onLiquidGlassOrderChange: (List<SteamDockTab>) -> Unit = {},
    fixedOrder: List<SteamDockTab> = SteamDockTab.FIXED_DEFAULT_ORDER,
    onFixedOrderChange: (List<SteamDockTab>) -> Unit = {}
) {
    var selectedStyle by remember(style) { mutableStateOf(style) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.steam_dock_order_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = stringResource(R.string.steam_dock_style_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp)
            )
            Text(
                text = stringResource(R.string.steam_dock_style_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                SteamDockStyle.entries.forEachIndexed { index, candidate ->
                    SegmentedButton(
                        selected = selectedStyle == candidate,
                        onClick = {
                            selectedStyle = candidate
                            onStyleChange(candidate)
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = SteamDockStyle.entries.size
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = stringResource(
                                when (candidate) {
                                    SteamDockStyle.M3E -> R.string.steam_dock_style_m3e
                                    SteamDockStyle.LIQUID_GLASS ->
                                        R.string.steam_dock_style_liquid_glass
                                    SteamDockStyle.FIXED -> R.string.steam_dock_style_fixed
                                }
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            key(selectedStyle) {
                SteamDockSortableList(
                    style = selectedStyle,
                    order = when (selectedStyle) {
                        SteamDockStyle.M3E -> order
                        SteamDockStyle.LIQUID_GLASS -> liquidGlassOrder
                        SteamDockStyle.FIXED -> fixedOrder
                    },
                    onOrderChange = when (selectedStyle) {
                        SteamDockStyle.M3E -> onOrderChange
                        SteamDockStyle.LIQUID_GLASS -> onLiquidGlassOrderChange
                        SteamDockStyle.FIXED -> onFixedOrderChange
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SteamDockSortableList(
    style: SteamDockStyle,
    order: List<SteamDockTab>,
    onOrderChange: (List<SteamDockTab>) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val dockContentClearance = LocalSteamDockContentClearance.current
    var localOrder by remember(style, order) {
        mutableStateOf(
            when (style) {
                SteamDockStyle.M3E -> SteamDockTab.completeOrder(order)
                SteamDockStyle.LIQUID_GLASS -> SteamDockTab.completeLiquidGlassOrder(order)
                SteamDockStyle.FIXED -> SteamDockTab.completeFixedOrder(order)
            }
        )
    }
    var enabledTabs by remember(style, order) {
        mutableStateOf(
            if (style == SteamDockStyle.M3E) {
                SteamDockTab.sanitizeOrder(order).toSet()
            } else {
                when (style) {
                    SteamDockStyle.LIQUID_GLASS -> SteamDockTab.LIQUID_GLASS_DEFAULT_ORDER.toSet()
                    SteamDockStyle.FIXED -> SteamDockTab.FIXED_DEFAULT_ORDER.toSet()
                    SteamDockStyle.M3E -> emptySet()
                }
            }
        )
    }
    var reorderDirty by remember { mutableStateOf(false) }
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val reordered = when (style) {
            SteamDockStyle.M3E -> reorderDockOrder(localOrder, from.index, to.index)
            SteamDockStyle.LIQUID_GLASS ->
                reorderLiquidGlassDockOrder(localOrder, from.index, to.index)
            SteamDockStyle.FIXED -> reorderFixedDockOrder(localOrder, from.index, to.index)
        }
        if (reordered != localOrder) {
            localOrder = reordered
            reorderDirty = true
        }
    }
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && reorderDirty) {
            reorderDirty = false
            onOrderChange(
                when (style) {
                    SteamDockStyle.M3E -> localOrder.filter { it in enabledTabs }
                    SteamDockStyle.LIQUID_GLASS ->
                        SteamDockTab.completeLiquidGlassOrder(localOrder)
                    SteamDockStyle.FIXED -> SteamDockTab.completeFixedOrder(localOrder)
                }
            )
        }
    }

    Text(
        text = stringResource(
            when (style) {
                SteamDockStyle.M3E -> R.string.steam_dock_m3e_order_hint
                SteamDockStyle.LIQUID_GLASS -> R.string.steam_dock_liquid_glass_order_hint
                SteamDockStyle.FIXED -> R.string.steam_dock_fixed_order_hint
            }
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 4.dp,
            end = 16.dp,
            bottom = dockContentClearance + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = !reorderableState.isAnyItemDragging
    ) {
        items(localOrder, key = SteamDockTab::name) { tab ->
            ReorderableItem(reorderableState, key = tab.name) { isDragging ->
                val elevation = if (isDragging) 8.dp else 0.dp
                BottomNavConfigRow(
                    icon = when (tab) {
                        SteamDockTab.TOKEN -> Icons.Default.Security
                        SteamDockTab.LIBRARY -> Icons.Default.SportsEsports
                        SteamDockTab.STORE -> Icons.Default.Storefront
                        SteamDockTab.CHAT -> Icons.Default.ChatBubble
                        SteamDockTab.SETTINGS -> Icons.Default.SettingsIcon
                    },
                    title = when (tab) {
                        SteamDockTab.TOKEN -> stringResource(R.string.steam_dock_token)
                        SteamDockTab.LIBRARY -> stringResource(R.string.steam_library_title)
                        SteamDockTab.STORE -> stringResource(R.string.steam_store_title)
                        SteamDockTab.CHAT -> stringResource(R.string.steam_chat_title)
                        SteamDockTab.SETTINGS -> stringResource(R.string.settings_title)
                    },
                    subtitle = stringResource(R.string.steam_dock_drag_hint),
                    checked = style != SteamDockStyle.M3E || tab in enabledTabs,
                    switchEnabled = style == SteamDockStyle.M3E,
                    onCheckedChange = { checked ->
                        if (style == SteamDockStyle.M3E) {
                            enabledTabs = if (checked) enabledTabs + tab else enabledTabs - tab
                            onOrderChange(localOrder.filter { it in enabledTabs })
                        }
                    },
                    showSwitch = style == SteamDockStyle.M3E,
                    dragHandleModifier = Modifier.longPressDraggableHandle(),
                    modifier = Modifier.shadow(elevation)
                )
            }
        }
    }
}
