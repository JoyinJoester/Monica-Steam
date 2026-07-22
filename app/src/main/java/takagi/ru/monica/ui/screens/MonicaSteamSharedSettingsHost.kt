package takagi.ru.monica.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.steam.navigation.SteamDockTab
import takagi.ru.monica.steam.quickaccess.SteamQuickAccessInstaller
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.viewmodel.SettingsViewModel

/**
 * Steam-only adapter around Monica's native settings surface.
 * The adapter keeps Steam backup/MDBX as the only product-specific data actions
 * while the shared screen remains unchanged for the main Monica application.
 */
@Composable
internal fun MonicaSteamSharedSettingsHost(
    settings: AppSettings,
    settingsManager: SettingsManager,
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenMdbx: () -> Unit,
    onOpenDock: () -> Unit,
    onOpenColors: () -> Unit,
    onOpenMasterPasswordLocking: () -> Unit,
    onOpenPlus: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenExtensions: () -> Unit,
    showNavigationBack: Boolean,
    modifier: Modifier,
    context: Context
) {
    SettingsScreen(
        viewModel = settingsViewModel,
        onNavigateBack = onNavigateBack,
        onResetPassword = {},
        onSecurityQuestions = {},
        onNavigateToMasterPasswordLocking = onOpenMasterPasswordLocking,
        onNavigateToSyncBackup = {},
        onNavigateToAutofill = {},
        onNavigateToPasskeySettings = {},
        onNavigateToBottomNavSettings = onOpenDock,
        onNavigateToColorScheme = onOpenColors,
        onSecurityAnalysis = {},
        onNavigateToDeveloperSettings = onOpenDeveloper,
        requireDeveloperAuthentication = false,
        onNavigateToPermissionManagement = {},
        onNavigateToMonicaPlus = onOpenPlus,
        onNavigateToExtensions = onOpenExtensions,
        onNavigateToPageCustomization = {},
        onNavigateToMdbx = onOpenMdbx,
        showTopBar = showNavigationBack,
        showReduceAnimations = false,
        showSyncBackupSurface = false,
        showAutofillSurface = false,
        showMdbxSurface = true,
        steamBackupTitle = context.getString(R.string.steam_backup_title),
        steamBackupDescription = context.getString(R.string.steam_backup_description),
        onNavigateToSteamBackup = onOpenBackup,
        surfacePolicy = SettingsSurfacePolicy(
            showSecurityAnalysis = false,
            showMasterPasswordLocking = true,
            showPermissionManagement = false,
            showTrash = false,
            showClearData = false,
            showExtensions = settings.isPlusActivated,
            showPageCustomization = false,
            showUpdateCheck = false,
            showPreviewFeatures = false,
            showDeveloperSettings = true
        ),
        contentBottomPadding = 24.dp,
        modifier = modifier
    )
}

@Composable
internal fun SteamWidgetExtensionContent(context: Context) {
    SettingsSection(title = context.getString(R.string.steam_widget_section)) {
        SettingsItem(
            icon = Icons.Default.Widgets,
            title = context.getString(R.string.steam_widget_account_title),
            subtitle = context.getString(R.string.steam_widget_account_add_description),
            onClick = {
                if (!SteamQuickAccessInstaller.requestPinAccountWidget(context)) {
                    Toast.makeText(
                        context,
                        R.string.steam_quick_access_widget_unsupported,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
        SettingsItem(
            icon = Icons.Default.ViewList,
            title = context.getString(R.string.steam_widget_recent_title),
            subtitle = context.getString(R.string.steam_widget_recent_add_description),
            onClick = {
                if (!SteamQuickAccessInstaller.requestPinRecentGamesWidget(context)) {
                    Toast.makeText(
                        context,
                        R.string.steam_quick_access_widget_unsupported,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}
