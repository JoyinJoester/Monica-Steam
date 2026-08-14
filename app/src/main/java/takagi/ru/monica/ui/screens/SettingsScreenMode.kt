package takagi.ru.monica.ui.screens

/**
 * Controls which part of the shared settings surface is visible.
 *
 * The default full mode keeps Monica Android's existing settings behavior.
 * Steam uses the compact home and child modes to keep its top-level page
 * focused while still reusing the same settings rows and dialogs.
 */
enum class SettingsScreenMode {
    FULL,
    COMPACT_HOME,
    DATA_MANAGEMENT,
    APPEARANCE,
    ADDITIONAL
}
