package takagi.ru.monica

import android.app.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
                takagi.ru.monica.security.SecureStorageStartup.awaitReadyForMaintenance(this@MonicaSteamApplication)
                SteamChatBackground.syncService(this@MonicaSteamApplication)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                SteamDiagLogger.append(
                    "chat_background_application_sync failed type=${error::class.java.simpleName}"
                )
            }
        }
    }
}
