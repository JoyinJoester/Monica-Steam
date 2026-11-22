package takagi.ru.monica.ui.screens

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassTopAppBar as TopAppBar
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassScaffold as Scaffold
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
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
import takagi.ru.monica.steam.navigation.reorderFixedDockOrder
import takagi.ru.monica.steam.navigation.reorderLiquidGlassDockOrder
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.rememberSteamAdaptiveLayout
import takagi.ru.monica.steam.steamdb.ui.SteamDbSettingsScreen
import takagi.ru.monica.steam.notifications.settings.ui.SteamNotificationSettingsScreen
import takagi.ru.monica.steam.network.optimization.ui.SteamNetworkOptimizationAutoScreen
import takagi.ru.monica.steam.network.optimization.ui.SteamNetworkResolverSettingsScreen
import takagi.ru.monica.steam.network.optimization.ui.SteamNetworkOptimizationSettingsScreen
import takagi.ru.monica.steam.store.hints.ui.SteamStoreHintSettingsScreen
import takagi.ru.monica.steam.security.SteamAppLockGate
import takagi.ru.monica.steam.security.shouldProtectSteamSensitiveSurface
import takagi.ru.monica.steam.navigation.steamPageTransition
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
    DESIGN_STYLE,
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

    SteamSettingsChild.COLORS,
    SteamSettingsChild.DESIGN_STYLE -> SteamSettingsChild.APPEARANCE

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
    dockStyle: SteamDockStyle = SteamDockStyle.FIXED,
    onDockStyleChange: (SteamDockStyle) -> Unit = {},
    liquidGlassDockOrder: List<SteamDockTab> = SteamDockTab.LIQUID_GLASS_DEFAULT_ORDER,
    onLiquidGlassDockOrderChange: (List<SteamDockTab>) -> Unit = {},
    fixedDockOrder: List<SteamDockTab> = SteamDockTab.FIXED_DEFAULT_ORDER,
    onFixedDockOrderChange: (List<SteamDockTab>) -> Unit = {},
    showNavigationBack: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var child by remember { mutableStateOf<SteamSettingsChild?>(null) }
    val settingsScrollState = rememberScrollState()
    val dataManagementScrollState = rememberScrollState()
    val appearanceScrollState = rememberScrollState()
    val steamFeaturesScrollState = rememberScrollState()
    val navigationScrollState = rememberScrollState()
    val context = LocalContext.current
    val dockContentClearance = LocalSteamDockContentClearance.current
    val adaptiveLayout = rememberSteamAdaptiveLayout()

    val openMasterPasswordSettings: () -> Unit = {
        child = if (securityManager.isMasterPasswordSet()) {
            SteamSettingsChild.MASTER_PASSWORD_LOCKING
        } else {
            SteamSettingsChild.MASTER_PASSWORD_SETUP
        }
    }
    val settingsHomeSections = remember(
        context,
        settings.language,
        settings.screenshotProtectionEnabled,
        securityManager
    ) {
        buildMonicaSteamSettingsHomeSections(
            context = context,
            screenshotProtectionEnabled = settings.screenshotProtectionEnabled,
            onScreenshotProtectionChange = settingsViewModel::updateScreenshotProtectionEnabled,
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
            designStyle = dockStyle,
            onOpenDesignStyle = { child = SteamSettingsChild.DESIGN_STYLE },
            onOpenMasterPasswordLocking = openMasterPasswordSettings,
            onOpenPlus = { child = SteamSettingsChild.PLUS },
            onOpenDeveloper = { child = SteamSettingsChild.DEVELOPER },
            onOpenExtensions = { child = SteamSettingsChild.EXTENSIONS },
            onOpenNetworkOptimization = { child = SteamSettingsChild.NETWORK_OPTIMIZATION },
            onOpenStoreHints = { child = SteamSettingsChild.STORE_HINTS },
            onOpenPriceData = { child = SteamSettingsChild.PRICE_DATA },
            onOpenDataManagement = { child = SteamSettingsChild.DATA_MANAGEMENT },
            onOpenAppearance = { child = SteamSettingsChild.APPEARANCE },
            onOpenSteamFeatures = { child = SteamSettingsChild.STEAM_FEATURES },
            compactHomeSections = settingsHomeSections,
            compactHomeColumns = if (adaptiveLayout.useTwoPaneLayout) 2 else 1,
            additionalGroup = additionalGroup,
            showNavigationBack = showBack,
            modifier = Modifier.fillMaxSize(),
            context = context
        )
    }

    AnimatedContent(
        targetState = child,
        modifier = modifier.padding(
            horizontal = if (adaptiveLayout.useTwoPaneLayout) 16.dp else 0.dp
        ),
        transitionSpec = {
            steamPageTransition(settings.reduceAnimations, forward = targetState != null)
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
                SteamSettingsChild.DESIGN_STYLE -> SteamDesignStyleScreen(
                    style = dockStyle,
                    onStyleChange = onDockStyleChange,
                    onNavigateBack = { child = SteamSettingsChild.APPEARANCE },
                    modifier = Modifier.fillMaxSize()
                )
                SteamSettingsChild.DOCK -> SteamDockOrderScreen(
                    style = dockStyle,
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
                SteamSettingsChild.PRICE_DATA -> SteamDbSettingsScreen(
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
internal fun SteamDockOrderScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    style: SteamDockStyle = SteamDockStyle.FIXED,
    liquidGlassOrder: List<SteamDockTab> = SteamDockTab.LIQUID_GLASS_DEFAULT_ORDER,
    onLiquidGlassOrderChange: (List<SteamDockTab>) -> Unit = {},
    fixedOrder: List<SteamDockTab> = SteamDockTab.FIXED_DEFAULT_ORDER,
    onFixedOrderChange: (List<SteamDockTab>) -> Unit = {},
) {
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
            // The style itself lives in Appearance › Design style; this page only orders it.
            key(style) {
                SteamDockSortableList(
                    style = style,
                    order = when (style) {
                        SteamDockStyle.LIQUID_GLASS -> liquidGlassOrder
                        SteamDockStyle.FIXED -> fixedOrder
                    },
                    onOrderChange = when (style) {
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
                SteamDockStyle.LIQUID_GLASS -> SteamDockTab.completeLiquidGlassOrder(order)
                SteamDockStyle.FIXED -> SteamDockTab.completeFixedOrder(order)
            }
        )
    }
    var reorderDirty by remember { mutableStateOf(false) }
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val reordered = when (style) {
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
                    SteamDockStyle.LIQUID_GLASS ->
                        SteamDockTab.completeLiquidGlassOrder(localOrder)
                    SteamDockStyle.FIXED -> SteamDockTab.completeFixedOrder(localOrder)
                }
            )
        }
    }

    Text(
        text = stringResource(R.string.steam_dock_order_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
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
        // Monica's connected settings group: 24 dp outside, 4 dp inside, 2 dp gaps.
        verticalArrangement = Arrangement.spacedBy(2.dp),
        userScrollEnabled = !reorderableState.isAnyItemDragging
    ) {
        itemsIndexed(localOrder, key = { _, tab -> tab.name }) { index, tab ->
            ReorderableItem(reorderableState, key = tab.name) { isDragging ->
                val outer = 24.dp
                val inner = 4.dp
                val shape = if (isDragging) {
                    RoundedCornerShape(outer)
                } else {
                    RoundedCornerShape(
                        topStart = if (index == 0) outer else inner,
                        topEnd = if (index == 0) outer else inner,
                        bottomStart = if (index == localOrder.lastIndex) outer else inner,
                        bottomEnd = if (index == localOrder.lastIndex) outer else inner
                    )
                }
                SteamDockOrderRow(
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
                    dragHint = stringResource(R.string.steam_dock_drag_hint),
                    dragHandleModifier = Modifier.longPressDraggableHandle(),
                    modifier = Modifier
                        .shadow(if (isDragging) 8.dp else 0.dp, shape)
                        .clip(shape)
                        .background(
                            if (isDragging) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun SteamDockOrderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    dragHint: String,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .then(dragHandleModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = dragHint,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
