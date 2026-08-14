package takagi.ru.monica.ui.screens

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.ViewWeek
import takagi.ru.monica.R

internal enum class SteamSettingsAdditionalGroup {
    NONE,
    NAVIGATION,
    STEAM_EXPERIENCE
}

internal fun buildMonicaSteamSettingsHomeSections(
    context: Context,
    onOpenMasterPassword: () -> Unit,
    onOpenDataManagement: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenNavigation: () -> Unit,
    onOpenSteamExperience: () -> Unit,
    onOpenNotifications: () -> Unit
): List<SettingsNavigationSection> = listOf(
    SettingsNavigationSection(
        title = context.getString(R.string.steam_settings_group_security_data),
        entries = listOf(
            SettingsNavigationEntry(
                icon = Icons.Default.Security,
                title = context.getString(R.string.master_password_and_locking),
                subtitle = context.getString(R.string.master_password_and_locking_description),
                searchTexts = listOf(
                    context.getString(R.string.biometric_unlock),
                    context.getString(R.string.auto_lock),
                    context.getString(R.string.security_questions)
                ),
                onClick = onOpenMasterPassword
            ),
            SettingsNavigationEntry(
                icon = Icons.Default.Storage,
                title = context.getString(R.string.settings_data_management_entry_title),
                subtitle = context.getString(R.string.settings_data_management_entry_description),
                searchTexts = listOf(
                    context.getString(R.string.steam_backup_title),
                    context.getString(R.string.webdav_backup),
                    context.getString(R.string.mdbx_format_title)
                ),
                onClick = onOpenDataManagement
            )
        )
    ),
    SettingsNavigationSection(
        title = context.getString(R.string.steam_settings_group_interface),
        entries = listOf(
            SettingsNavigationEntry(
                icon = Icons.Default.Palette,
                title = context.getString(R.string.settings_appearance_entry_title),
                subtitle = context.getString(R.string.settings_appearance_entry_description),
                searchTexts = listOf(
                    context.getString(R.string.interface_scale_title),
                    context.getString(R.string.steam_avatar_shape_title),
                    context.getString(R.string.validator_progress_bar_style)
                ),
                onClick = onOpenAppearance
            ),
            SettingsNavigationEntry(
                icon = Icons.Default.ViewWeek,
                title = context.getString(R.string.steam_settings_navigation_title),
                subtitle = context.getString(R.string.steam_settings_navigation_description),
                searchTexts = listOf(
                    context.getString(R.string.bottom_nav_settings),
                    context.getString(R.string.reduce_animations)
                ),
                onClick = onOpenNavigation
            )
        )
    ),
    SettingsNavigationSection(
        title = context.getString(R.string.steam_settings_group_steam),
        entries = listOf(
            SettingsNavigationEntry(
                icon = Icons.Default.Storefront,
                title = context.getString(R.string.steam_settings_features_title),
                subtitle = context.getString(R.string.steam_settings_features_description),
                searchTexts = listOf(
                    context.getString(R.string.steam_link_handling_title),
                    context.getString(R.string.steam_store_hint_settings_title),
                    context.getString(R.string.itad_settings_title),
                    context.getString(R.string.itad_settings_description)
                ),
                onClick = onOpenSteamExperience
            ),
            SettingsNavigationEntry(
                icon = Icons.Default.Notifications,
                title = context.getString(R.string.steam_settings_connectivity_title),
                subtitle = context.getString(R.string.steam_settings_connectivity_description),
                searchTexts = listOf(
                    context.getString(R.string.steam_notification_settings_title),
                    context.getString(R.string.steam_notification_settings_description)
                ),
                onClick = onOpenNotifications
            )
        )
    )
)
