package takagi.ru.monica.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import takagi.ru.monica.R
import takagi.ru.monica.data.AppLauncherIcon

object AppLauncherIconManager {
    private const val TAG = "AppLauncherIconManager"
    private const val MAIN_ACTIVITY = "takagi.ru.monica.MonicaSteamActivity"
    private const val MODERN_ALIAS = "takagi.ru.monica.ModernVisibleLauncherAlias"
    private const val CLASSIC_ALIAS = "takagi.ru.monica.ClassicVisibleLauncherAlias"

    internal fun aliasFor(icon: AppLauncherIcon): String = when (icon) {
        AppLauncherIcon.MODERN -> MODERN_ALIAS
        AppLauncherIcon.CLASSIC -> CLASSIC_ALIAS
    }

    // The enabled flags the manifest declares, used to read back a component
    // that never received a runtime override.
    internal val manifestDeclaredStates: Map<String, Int> = mapOf(
        MODERN_ALIAS to PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        CLASSIC_ALIAS to PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    )

    /**
     * Alias class name to enabled state, with the incoming entry ordered first:
     * launchers below API 33 apply these one by one and must never observe a
     * moment without a home-screen icon.
     */
    internal fun launcherStatesFor(icon: AppLauncherIcon): Map<String, Int> = linkedMapOf(
        aliasFor(icon) to PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        aliasFor(oppositeOf(icon)) to PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    )

    fun apply(context: Context, icon: AppLauncherIcon) {
        repairLaunchTarget(context)
        applyVisibleLauncherSelection(context, icon)
    }

    fun applyIfStale(context: Context, icon: AppLauncherIcon) {
        if (isSelectionStale(context, icon)) apply(context, icon)
    }

    fun repairLaunchEntryPointsAfterUpgrade(
        context: Context,
        icon: AppLauncherIcon
    ) = apply(context, icon)

    internal fun isSelectionStale(context: Context, icon: AppLauncherIcon): Boolean {
        val packageManager = context.packageManager
        return launcherStatesFor(icon).any { (alias, expected) ->
            val launchComponent = component(context, alias)
            if (!packageManager.hasDeclaredActivity(launchComponent)) return@any false
            val override = packageManager.getComponentEnabledSetting(launchComponent)
            val effective = if (override == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                manifestDeclaredStates[alias] ?: override
            } else {
                override
            }
            effective != expected
        }
    }

    fun resolveBrandingIconRes(context: Context): Int {
        return R.drawable.monica_launcher
    }

    fun applyBiometricPromptBranding(context: Context, promptInfoBuilder: Any) {
        val builderClass = promptInfoBuilder.javaClass
        val iconRes = resolveBrandingIconRes(context)

        runCatching {
            builderClass.methods.firstOrNull { method ->
                method.name == "setLogoRes" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType
            }?.invoke(promptInfoBuilder, iconRes)
        }

        runCatching {
            builderClass.methods.firstOrNull { method ->
                method.name == "setLogoDescription" &&
                    method.parameterTypes.size == 1 &&
                    CharSequence::class.java.isAssignableFrom(method.parameterTypes[0])
            }?.invoke(promptInfoBuilder, context.getString(R.string.app_name))
        }
    }

    private fun repairLaunchTarget(context: Context) {
        val packageManager = context.packageManager
        component(context, MAIN_ACTIVITY)
            .takeIf { packageManager.hasDeclaredActivity(it) }
            ?.let { launchComponent ->
                packageManager.setComponentEnabledSettingSafely(
                    launchComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
    }

    private fun applyVisibleLauncherSelection(
        context: Context,
        icon: AppLauncherIcon
    ) {
        val packageManager = context.packageManager
        val states = launcherStatesFor(icon).mapNotNull { (alias, state) ->
            val launchComponent = component(context, alias)
            launchComponent.takeIf { packageManager.hasDeclaredActivity(it) }?.let { it to state }
        }

        if (states.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.setComponentEnabledSettingsSafely(
                states.map { (launchComponent, state) ->
                    PackageManager.ComponentEnabledSetting(
                        launchComponent,
                        state,
                        PackageManager.DONT_KILL_APP
                    )
                }
            )
            return
        }

        states.forEach { (component, state) ->
            packageManager.setComponentEnabledSettingSafely(
                component,
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun oppositeOf(icon: AppLauncherIcon): AppLauncherIcon = when (icon) {
        AppLauncherIcon.MODERN -> AppLauncherIcon.CLASSIC
        AppLauncherIcon.CLASSIC -> AppLauncherIcon.MODERN
    }

    private fun component(context: Context, className: String): ComponentName =
        ComponentName(context.packageName, className)

    // An undeclared component must never reach the PackageManager binder
    // (Android 16 throws instead of ignoring it), hence the guarded lookup.
    private fun PackageManager.hasDeclaredActivity(component: ComponentName): Boolean =
        runCatching {
            getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
        }.isSuccess

    private fun PackageManager.setComponentEnabledSettingSafely(
        component: ComponentName,
        newState: Int,
        flags: Int
    ) {
        runCatching { setComponentEnabledSetting(component, newState, flags) }
            .onFailure { error ->
                Log.w(TAG, "Unable to update launcher component $component", error)
            }
    }

    private fun PackageManager.setComponentEnabledSettingsSafely(
        settings: List<PackageManager.ComponentEnabledSetting>
    ) {
        runCatching { setComponentEnabledSettings(settings) }
            .onFailure { error ->
                Log.w(TAG, "Unable to update launcher component batch", error)
                // Some OEM PackageManager implementations reject a batch even
                // when every component was declared. Fall back one-by-one.
                settings.forEach { setting ->
                    val component = setting.componentName ?: return@forEach
                    setComponentEnabledSettingSafely(
                        component,
                        setting.enabledState,
                        setting.enabledFlags
                    )
                }
            }
    }
}
