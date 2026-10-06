package takagi.ru.monica

import android.app.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import takagi.ru.monica.utils.AppLauncherIconManager
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.steam.diagnostics.SteamCrashDiagnostics
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.chat.background.SteamChatBackground
import takagi.ru.monica.steam.network.optimization.SteamNetworkOptimizationRuntime
import takagi.ru.monica.steam.network.optimization.SteamNetworkResolverSettingsRuntime

class MonicaSteamApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        SteamCrashDiagnostics.install(this)
        SteamDiagLogger.initialize(this)
        super.onCreate()
        SteamNetworkOptimizationRuntime.initialize(this)
        SteamNetworkResolverSettingsRuntime.initialize(this)
        applicationScope.launch {
            try {
                SteamChatBackground.syncService(this@MonicaSteamApplication)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                SteamDiagLogger.append(
                    "chat_background_application_sync failed type=${error::class.java.simpleName}"
                )
            }
        }
        applicationScope.launch(Dispatchers.IO) {
            try {
                val settings = SettingsManager(this@MonicaSteamApplication).settingsFlow.first()
                AppLauncherIconManager.applyIfStale(
                    this@MonicaSteamApplication,
                    settings.appLauncherIcon
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                SteamDiagLogger.append(
                    "launcher_entry_sync failed type=${error::class.java.simpleName}"
                )
            }
        }
    }
}
