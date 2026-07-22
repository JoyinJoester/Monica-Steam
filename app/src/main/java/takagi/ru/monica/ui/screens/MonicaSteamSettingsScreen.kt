package takagi.ru.monica.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.BuildConfig
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.Language
import takagi.ru.monica.data.ProgressBarStyle
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.data.UnifiedProgressBarMode
import takagi.ru.monica.plus.PlusActivationUiResult
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.alerts.SteamAlertPreferences
import takagi.ru.monica.steam.alerts.SteamAlertScheduler
import takagi.ru.monica.steam.alerts.SteamAlertSettings
import takagi.ru.monica.steam.navigation.SteamDockTab
import takagi.ru.monica.steam.navigation.reorderDockOrder
import takagi.ru.monica.steam.diagnostics.SteamSupportLogExporter
import takagi.ru.monica.steam.quickaccess.SteamQuickAccessInstaller
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.viewmodel.SettingsViewModel
import takagi.ru.monica.viewmodel.PasswordViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class SteamSettingsChild {
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
    EXTENSIONS
}

@Composable
fun MonicaSteamSettingsScreen(
    settings: AppSettings,
    settingsManager: SettingsManager,
    settingsViewModel: SettingsViewModel,
    passwordViewModel: PasswordViewModel,
    securityManager: SecurityManager,
    onNavigateBack: () -> Unit,
    onOpenBackup: () -> Unit = {},
    onOpenMdbx: () -> Unit = {},
    dockOrder: List<SteamDockTab> = SteamDockTab.DEFAULT_ORDER,
    onDockOrderChange: (List<SteamDockTab>) -> Unit = {},
    showNavigationBack: Boolean = true,
    modifier: Modifier = Modifier
) {
    var child by remember { mutableStateOf<SteamSettingsChild?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = child != null) {
        child = when (child) {
            SteamSettingsChild.CUSTOM_COLORS -> SteamSettingsChild.COLORS
            SteamSettingsChild.PAYMENT -> SteamSettingsChild.PLUS
            SteamSettingsChild.RESET_PASSWORD,
            SteamSettingsChild.SECURITY_QUESTIONS -> SteamSettingsChild.MASTER_PASSWORD_LOCKING
            else -> null
        }
    }
    AnimatedContent(
        targetState = child,
        modifier = modifier,
        transitionSpec = {
            easyNotesScreenEnter().togetherWith(easyNotesScreenExit())
        },
        label = "MonicaSteamSettingsNavigation"
    ) { animatedChild ->
        if (animatedChild == null) {
            MonicaSteamSharedSettingsHost(
                settings = settings,
                settingsManager = settingsManager,
                settingsViewModel = settingsViewModel,
                onNavigateBack = onNavigateBack,
                onOpenBackup = onOpenBackup,
                onOpenMdbx = onOpenMdbx,
                onOpenDock = { child = SteamSettingsChild.DOCK },
                onOpenColors = { child = SteamSettingsChild.COLORS },
                onOpenMasterPasswordLocking = {
                    child = if (securityManager.isMasterPasswordSet()) {
                        SteamSettingsChild.MASTER_PASSWORD_LOCKING
                    } else {
                        SteamSettingsChild.MASTER_PASSWORD_SETUP
                    }
                },
                onOpenPlus = { child = SteamSettingsChild.PLUS },
                onOpenDeveloper = { child = SteamSettingsChild.DEVELOPER },
                onOpenExtensions = { child = SteamSettingsChild.EXTENSIONS },
                showNavigationBack = showNavigationBack,
                modifier = Modifier.fillMaxSize(),
                context = context
            )
        } else {
            when (animatedChild) {
                SteamSettingsChild.DOCK -> SteamDockOrderScreen(
                    order = dockOrder,
                    onOrderChange = onDockOrderChange,
                    onNavigateBack = { child = null },
                    modifier = Modifier.fillMaxSize()
                )
                SteamSettingsChild.COLORS -> ColorSchemeSelectionScreen(
                    settingsViewModel = settingsViewModel,
                    onNavigateBack = { child = null },
                    onNavigateToCustomColors = { child = SteamSettingsChild.CUSTOM_COLORS },
                    modifier = Modifier.fillMaxSize()
                )
                SteamSettingsChild.CUSTOM_COLORS -> CustomColorSettingsScreen(
                    settingsViewModel = settingsViewModel,
                    onNavigateBack = { child = SteamSettingsChild.COLORS },
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
                    MasterPasswordLockingSettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateBack = { child = null },
                        onResetPassword = {
                            child = SteamSettingsChild.RESET_PASSWORD
                        },
                        onSecurityQuestions = {
                            child = SteamSettingsChild.SECURITY_QUESTIONS
                        }
                    )
                SteamSettingsChild.RESET_PASSWORD -> ResetPasswordScreen(
                    securityManager = securityManager,
                    onNavigateBack = {
                        child = SteamSettingsChild.MASTER_PASSWORD_LOCKING
                    },
                    onResetSuccess = {
                        child = SteamSettingsChild.MASTER_PASSWORD_LOCKING
                    }
                )
                SteamSettingsChild.SECURITY_QUESTIONS ->
                    SecurityQuestionsSetupScreen(
                        securityManager = securityManager,
                        onNavigateBack = {
                            child = SteamSettingsChild.MASTER_PASSWORD_LOCKING
                        },
                        onSetupComplete = {
                            child = SteamSettingsChild.MASTER_PASSWORD_LOCKING
                        }
                    )
                SteamSettingsChild.PLUS -> MonicaPlusScreen(
                    isPlusActivated = settings.isPlusActivated,
                    onNavigateBack = { child = null },
                    onNavigateToPayment = { child = SteamSettingsChild.PAYMENT },
                    onDeactivatePlus = { settingsViewModel.clearPlusLicenseData() },
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
                    modifier = Modifier.fillMaxSize()
                )
                SteamSettingsChild.DEVELOPER -> DeveloperSettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { child = null },
                    onNavigateToMdbx = onOpenMdbx,
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
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonicaSteamLegacySettingsScreen(
    settings: AppSettings,
    settingsManager: SettingsManager,
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onOpenBackup: () -> Unit = {},
    onOpenMdbx: () -> Unit = {},
    dockOrder: List<SteamDockTab> = SteamDockTab.DEFAULT_ORDER,
    onDockOrderChange: (List<SteamDockTab>) -> Unit = {},
    showNavigationBack: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showClipboardDialog by remember { mutableStateOf(false) }
    var showProgressStyleDialog by remember { mutableStateOf(false) }
    var showAlertIntervalDialog by remember { mutableStateOf(false) }
    var showDockOrderScreen by remember { mutableStateOf(false) }
    var showColorSchemeScreen by remember { mutableStateOf(false) }
    var showCustomColorScreen by remember { mutableStateOf(false) }
    var pendingSupportLog by remember { mutableStateOf<String?>(null) }
    val alertPreferences = remember(context) { SteamAlertPreferences(context) }
    val alertSettings by alertPreferences.settings.collectAsState(initial = SteamAlertSettings())
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    val supportLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val report = pendingSupportLog
        val saved = uri != null && report != null && runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(report)
            } ?: error("Unable to open output stream")
        }.isSuccess
        pendingSupportLog = null
        Toast.makeText(
            context,
            if (saved) R.string.steam_support_log_exported else R.string.steam_support_log_export_failed,
            Toast.LENGTH_LONG
        ).show()
    }

    if (showDockOrderScreen) {
        BackHandler { showDockOrderScreen = false }
        SteamDockOrderScreen(
            order = dockOrder,
            onOrderChange = onDockOrderChange,
            onNavigateBack = { showDockOrderScreen = false },
            modifier = modifier
        )
        return
    }

    if (showCustomColorScreen) {
        CustomColorSettingsScreen(
            settingsViewModel = settingsViewModel,
            onNavigateBack = { showCustomColorScreen = false }
        )
        return
    }

    if (showColorSchemeScreen) {
        ColorSchemeSelectionScreen(
            settingsViewModel = settingsViewModel,
            onNavigateBack = { showColorSchemeScreen = false },
            onNavigateToCustomColors = { showCustomColorScreen = true }
        )
        return
    }

    val themeLabel = when (settings.themeMode) {
        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
        ThemeMode.DARK -> stringResource(R.string.theme_dark)
    }
    val appearanceLabel = if (settings.oledPureBlackEnabled) {
        stringResource(R.string.appearance_with_oled_subtitle, themeLabel)
    } else {
        themeLabel
    }
    val languageLabel = when (settings.language) {
        Language.SYSTEM -> stringResource(R.string.language_system)
        Language.ENGLISH -> stringResource(R.string.language_english)
        Language.CHINESE -> stringResource(R.string.language_chinese)
        Language.VIETNAMESE -> stringResource(R.string.language_vietnamese)
        Language.JAPANESE -> stringResource(R.string.language_japanese)
        Language.RUSSIAN -> stringResource(R.string.language_russian)
    }
    val clipboardLabel = if (settings.clipboardAutoClearSeconds <= 0) {
        stringResource(R.string.clipboard_auto_clear_never)
    } else {
        stringResource(
            R.string.clipboard_auto_clear_seconds,
            settings.clipboardAutoClearSeconds
        )
    }
    val progressStyleLabel = when (settings.validatorProgressBarStyle) {
        ProgressBarStyle.LINEAR -> stringResource(R.string.progress_bar_style_linear)
        ProgressBarStyle.WAVE -> stringResource(R.string.progress_bar_style_wave)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    if (showNavigationBack) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                SteamSettingsSection(title = stringResource(R.string.steam_settings_appearance_section)) {
                    SteamSettingsItem(
                        icon = Icons.Default.Palette,
                        title = stringResource(R.string.theme),
                        subtitle = appearanceLabel,
                        onClick = { showAppearanceSheet = true }
                    )
                    SteamSettingsItem(
                        icon = Icons.Default.Wallpaper,
                        title = stringResource(R.string.color_scheme),
                        subtitle = stringResource(R.string.color_scheme_description),
                        onClick = { showColorSchemeScreen = true }
                    )
                    SteamSettingsItem(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.language),
                        subtitle = languageLabel,
                        onClick = { showLanguageDialog = true }
                    )
                    SteamSettingsItem(
                        icon = Icons.Default.ViewStream,
                        title = stringResource(R.string.steam_dock_order_title),
                        subtitle = stringResource(R.string.steam_dock_order_description),
                        onClick = { showDockOrderScreen = true }
                    )
                    SteamSettingsSwitchItem(
                        icon = Icons.Default.Speed,
                        title = stringResource(R.string.reduce_animations),
                        subtitle = stringResource(R.string.reduce_animations_description),
                        checked = settings.reduceAnimations,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                settingsManager.updateReduceAnimations(enabled)
                            }
                        }
                    )
                }
            }

            item {
                SteamSettingsSection(title = stringResource(R.string.steam_settings_data_section)) {
                    SteamSettingsItem(
                        icon = Icons.Default.CloudUpload,
                        title = stringResource(R.string.steam_backup_title),
                        subtitle = stringResource(R.string.steam_settings_mafile_backup_description),
                        onClick = onOpenBackup
                    )
                    SteamSettingsItem(
                        icon = Icons.Default.Storage,
                        title = stringResource(R.string.steam_mdbx_title),
                        subtitle = stringResource(R.string.steam_mdbx_description),
                        onClick = onOpenMdbx
                    )
                    SteamSettingsItem(
                        icon = Icons.Default.BugReport,
                        title = stringResource(R.string.steam_support_log_export),
                        subtitle = stringResource(R.string.steam_support_log_export_description),
                        onClick = {
                            coroutineScope.launch {
                                val report = SteamSupportLogExporter.collect(context)
                                pendingSupportLog = report
                                supportLogLauncher.launch("monica-steam-support-${System.currentTimeMillis()}.txt")
                            }
                        }
                    )
                }
            }

            item {
                SteamSettingsSection(title = stringResource(R.string.steam_quick_access_section)) {
                    SteamSettingsItem(
                        icon = Icons.Default.Widgets,
                        title = stringResource(R.string.steam_widget_account_title),
                        subtitle = stringResource(R.string.steam_widget_account_add_description),
                        onClick = {
                            val requested = SteamQuickAccessInstaller.requestPinAccountWidget(context)
                            Toast.makeText(
                                context,
                                if (requested) {
                                    R.string.steam_quick_access_widget_requested
                                } else {
                                    R.string.steam_quick_access_widget_unsupported
                                },
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                    SteamSettingsItem(
                        icon = Icons.Default.ViewStream,
                        title = stringResource(R.string.steam_widget_recent_title),
                        subtitle = stringResource(R.string.steam_widget_recent_add_description),
                        onClick = {
                            val requested = SteamQuickAccessInstaller.requestPinRecentGamesWidget(context)
                            Toast.makeText(
                                context,
                                if (requested) {
                                    R.string.steam_quick_access_widget_requested
                                } else {
                                    R.string.steam_quick_access_widget_unsupported
                                },
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }

            item {
                SteamSettingsSection(title = stringResource(R.string.settings_security)) {
                    SteamSettingsSwitchItem(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.steam_alerts_notifications),
                        subtitle = stringResource(R.string.steam_alerts_notifications_description),
                        checked = alertSettings.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                alertPreferences.setNotificationsEnabled(enabled)
                            }
                        }
                    )
                    SteamSettingsSwitchItem(
                        icon = Icons.Default.Security,
                        title = stringResource(R.string.screenshot_protection),
                        subtitle = if (settings.screenshotProtectionEnabled) {
                            stringResource(R.string.screenshot_protection_enabled)
                        } else {
                            stringResource(R.string.screenshot_protection_disabled)
                        },
                        checked = settings.screenshotProtectionEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                settingsManager.updateScreenshotProtectionEnabled(enabled)
                            }
                        }
                    )
                    SteamSettingsItem(
                        icon = Icons.Default.ContentPaste,
                        title = stringResource(R.string.clipboard_auto_clear_title),
                        subtitle = clipboardLabel,
                        onClick = { showClipboardDialog = true }
                    )
                }
            }

            item {
                SteamSettingsSection(title = stringResource(R.string.steam_alerts_section)) {
                    SteamSettingsSwitchItem(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.steam_alerts_enabled),
                        subtitle = stringResource(R.string.steam_alerts_enabled_description),
                        checked = alertSettings.enabled,
                        onCheckedChange = { enabled ->
                            if (
                                enabled &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            coroutineScope.launch {
                                alertPreferences.setEnabled(enabled)
                                SteamAlertScheduler.sync(context)
                            }
                        }
                    )
                    SteamSettingsSwitchItem(
                        icon = Icons.Default.Security,
                        title = stringResource(R.string.steam_alerts_session),
                        subtitle = stringResource(R.string.steam_alerts_session_description),
                        checked = alertSettings.sessionEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch { alertPreferences.setSessionEnabled(enabled) }
                        }
                    )
                    SteamSettingsSwitchItem(
                        icon = Icons.Default.Timeline,
                        title = stringResource(R.string.steam_alerts_confirmations),
                        subtitle = stringResource(R.string.steam_alerts_confirmations_description),
                        checked = alertSettings.confirmationsEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch { alertPreferences.setConfirmationsEnabled(enabled) }
                        }
                    )
                    SteamSettingsSwitchItem(
                        icon = Icons.Default.Devices,
                        title = stringResource(R.string.steam_alerts_devices),
                        subtitle = stringResource(R.string.steam_alerts_devices_description),
                        checked = alertSettings.devicesEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch { alertPreferences.setDevicesEnabled(enabled) }
                        }
                    )
                    SteamSettingsSwitchItem(
                        icon = Icons.Default.PriceChange,
                        title = stringResource(R.string.steam_alerts_prices),
                        subtitle = stringResource(R.string.steam_alerts_prices_description),
                        checked = alertSettings.pricesEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch { alertPreferences.setPricesEnabled(enabled) }
                        }
                    )
                    SteamSettingsItem(
                        icon = Icons.Default.Schedule,
                        title = stringResource(R.string.steam_alerts_interval),
                        subtitle = stringResource(
                            R.string.steam_alerts_interval_hours,
                            alertSettings.normalizedIntervalHours
                        ),
                        onClick = { showAlertIntervalDialog = true }
                    )
                    SteamSettingsItem(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.steam_alerts_system_settings),
                        subtitle = stringResource(R.string.steam_alerts_system_settings_description),
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            )
                        }
                    )
                }
            }

            item {
                SteamSettingsSection(title = stringResource(R.string.validator_settings_section)) {
                    SteamSettingsItem(
                        icon = Icons.Default.Timeline,
                        title = stringResource(R.string.validator_progress_bar_style),
                        subtitle = progressStyleLabel,
                        onClick = { showProgressStyleDialog = true }
                    )
                    SteamSettingsSwitchItem(
                        icon = Icons.Default.ViewStream,
                        title = stringResource(R.string.unified_progress_bar_title),
                        subtitle = stringResource(R.string.steam_unified_progress_bar_description),
                        checked = settings.validatorUnifiedProgressBar ==
                            UnifiedProgressBarMode.ENABLED,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                settingsManager.updateValidatorUnifiedProgressBar(
                                    if (enabled) {
                                        UnifiedProgressBarMode.ENABLED
                                    } else {
                                        UnifiedProgressBarMode.DISABLED
                                    }
                                )
                            }
                        }
                    )
                    SteamSettingsSwitchItem(
                        icon = Icons.Default.Speed,
                        title = stringResource(R.string.smooth_progress_bar_title),
                        subtitle = stringResource(R.string.steam_smooth_progress_bar_description),
                        checked = settings.validatorSmoothProgress,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                settingsManager.updateValidatorSmoothProgress(enabled)
                            }
                        }
                    )
                }
            }

            item {
                SteamSettingsSection(title = stringResource(R.string.extensions_steam_settings)) {
                    SteamSettingsSwitchItem(
                        icon = Icons.Default.Wallpaper,
                        title = stringResource(R.string.steam_mini_profile_background_title),
                        subtitle = stringResource(R.string.steam_mini_profile_background_description),
                        checked = settings.steamMiniProfileBackgroundEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                settingsManager.updateSteamMiniProfileBackgroundEnabled(enabled)
                            }
                        }
                    )
                }
            }

            item {
                SteamSettingsSection(title = stringResource(R.string.about)) {
                    SteamSettingsInfoItem(
                        title = stringResource(R.string.app_name),
                        subtitle = stringResource(
                            R.string.steam_settings_version,
                            BuildConfig.FULL_VERSION_NAME
                        )
                    )
                }
            }
        }
    }

    if (showAppearanceSheet) {
        SteamAppearanceSelectionSheet(
            currentTheme = settings.themeMode,
            oledPureBlackEnabled = settings.oledPureBlackEnabled,
            onThemeSelected = { theme ->
                coroutineScope.launch { settingsManager.updateThemeMode(theme) }
            },
            onOledPureBlackChanged = { enabled ->
                coroutineScope.launch { settingsManager.updateOledPureBlackEnabled(enabled) }
            },
            onDismiss = { showAppearanceSheet = false }
        )
    }

    if (showLanguageDialog) {
        SteamLanguageSelectionDialog(
            currentLanguage = settings.language,
            onLanguageSelected = { language ->
                coroutineScope.launch {
                    settingsManager.updateLanguage(language)
                    showLanguageDialog = false
                    (context as? Activity)?.recreate()
                }
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showClipboardDialog) {
        ChoiceDialog(
            title = stringResource(R.string.clipboard_auto_clear_title),
            options = listOf(0, 10, 20, 30, 60),
            selected = settings.clipboardAutoClearSeconds,
            optionLabel = { seconds ->
                if (seconds <= 0) {
                    context.getString(R.string.clipboard_auto_clear_never)
                } else {
                    context.getString(R.string.clipboard_auto_clear_seconds, seconds)
                }
            },
            onSelected = { seconds ->
                coroutineScope.launch {
                    settingsManager.updateClipboardAutoClearSeconds(seconds)
                }
                showClipboardDialog = false
            },
            onDismiss = { showClipboardDialog = false }
        )
    }

    if (showProgressStyleDialog) {
        ChoiceDialog(
            title = stringResource(R.string.validator_progress_bar_style),
            options = ProgressBarStyle.values().toList(),
            selected = settings.validatorProgressBarStyle,
            optionLabel = { style ->
                context.getString(
                    when (style) {
                        ProgressBarStyle.LINEAR -> R.string.progress_bar_style_linear
                        ProgressBarStyle.WAVE -> R.string.progress_bar_style_wave
                    }
                )
            },
            onSelected = { style ->
                coroutineScope.launch {
                    settingsManager.updateValidatorProgressBarStyle(style)
                }
                showProgressStyleDialog = false
            },
            onDismiss = { showProgressStyleDialog = false }
        )
    }

    if (showAlertIntervalDialog) {
        ChoiceDialog(
            title = stringResource(R.string.steam_alerts_interval),
            options = SteamAlertSettings.allowedIntervals.sorted(),
            selected = alertSettings.normalizedIntervalHours,
            optionLabel = { hours ->
                context.getString(R.string.steam_alerts_interval_hours, hours)
            },
            onSelected = { hours ->
                coroutineScope.launch {
                    alertPreferences.setIntervalHours(hours)
                    SteamAlertScheduler.sync(context)
                }
                showAlertIntervalDialog = false
            },
            onDismiss = { showAlertIntervalDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteamDockOrderScreen(
    order: List<SteamDockTab>,
    onOrderChange: (List<SteamDockTab>) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var localOrder by remember(order) {
        mutableStateOf(SteamDockTab.sanitizeOrder(order))
    }
    var reorderDirty by remember { mutableStateOf(false) }
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val reordered = reorderDockOrder(localOrder, from.index, to.index)
        if (reordered != localOrder) {
            localOrder = reordered
            reorderDirty = true
        }
    }
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && reorderDirty) {
            reorderDirty = false
            onOrderChange(localOrder)
        }
    }

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
                text = stringResource(R.string.steam_dock_order_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
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
                                SteamDockTab.SETTINGS -> Icons.Default.SettingsIcon
                            },
                            title = when (tab) {
                                SteamDockTab.TOKEN -> stringResource(R.string.steam_dock_token)
                                SteamDockTab.LIBRARY -> stringResource(R.string.steam_library_title)
                                SteamDockTab.STORE -> stringResource(R.string.steam_store_title)
                                SteamDockTab.SETTINGS -> stringResource(R.string.settings_title)
                            },
                            subtitle = stringResource(R.string.bottom_nav_reorder_hint),
                            checked = true,
                            switchEnabled = false,
                            onCheckedChange = {},
                            showSwitch = false,
                            dragHandleModifier = Modifier.longPressDraggableHandle(),
                            modifier = Modifier.shadow(elevation)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SteamSettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
        )
        content()
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteamSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SteamSettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteamAppearanceSelectionSheet(
    currentTheme: ThemeMode,
    oledPureBlackEnabled: Boolean,
    onThemeSelected: (ThemeMode) -> Unit,
    onOledPureBlackChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = stringResource(R.string.theme), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(R.string.appearance_sheet_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    ThemeMode.values().forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onThemeSelected(theme) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = theme == currentTheme,
                                onClick = { onThemeSelected(theme) }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(steamThemeDisplayName(theme, context))
                                if (theme == ThemeMode.DARK) {
                                    Text(
                                        text = stringResource(R.string.oled_pure_black_dark_mode_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOledPureBlackChanged(!oledPureBlackEnabled) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.oled_pure_black),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.oled_pure_black_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = oledPureBlackEnabled,
                        onCheckedChange = onOledPureBlackChanged
                    )
                }
            }

            FilledTonalButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun SteamLanguageSelectionDialog(
    currentLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                Language.values().forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(language) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = language == currentLanguage,
                            onClick = { onLanguageSelected(language) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(steamLanguageDisplayName(language, context))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

private fun steamThemeDisplayName(theme: ThemeMode, context: Context): String = when (theme) {
    ThemeMode.SYSTEM -> context.getString(R.string.theme_system)
    ThemeMode.LIGHT -> context.getString(R.string.theme_light)
    ThemeMode.DARK -> context.getString(R.string.theme_dark)
}

private fun steamLanguageDisplayName(language: Language, context: Context): String = when (language) {
    Language.SYSTEM -> context.getString(R.string.language_system)
    Language.ENGLISH -> context.getString(R.string.language_english)
    Language.CHINESE -> context.getString(R.string.language_chinese)
    Language.VIETNAMESE -> context.getString(R.string.language_vietnamese)
    Language.JAPANESE -> context.getString(R.string.language_japanese)
    Language.RUSSIAN -> context.getString(R.string.language_russian)
}

@Composable
private fun SteamSettingsInfoItem(
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelected(option) }
                        )
                        TextButton(
                            onClick = { onSelected(option) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = optionLabel(option),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
