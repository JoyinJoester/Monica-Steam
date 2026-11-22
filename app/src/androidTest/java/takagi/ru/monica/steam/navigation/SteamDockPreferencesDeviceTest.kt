package takagi.ru.monica.steam.navigation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SteamDockPreferencesDeviceTest {
    @Test fun removedTelegramStyleFallsBackAfterReopenWithoutChangingExistingOrders() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "dock-style-fixture-${UUID.randomUUID()}.preferences_pb")
        var job: Job? = null
        fun open(): DataStore<Preferences> {
            val owner = SupervisorJob()
            job = owner
            return PreferenceDataStoreFactory.create(scope = CoroutineScope(owner + Dispatchers.IO)) { file }
        }
        try {
            var dataStore = open()
            val fixed = listOf(SteamDockTab.TOKEN, SteamDockTab.SETTINGS, SteamDockTab.CHAT,
                SteamDockTab.LIBRARY, SteamDockTab.STORE)
            val glass = SteamDockTab.LIQUID_GLASS_DEFAULT_ORDER.reversed()
            dataStore.edit {
                it[stringPreferencesKey("dock_style")] = "LIQUID_GLASS"
                it[stringPreferencesKey("fixed_dock_order")] = fixed.joinToString(",") { tab -> tab.name }
                it[stringPreferencesKey("liquid_glass_dock_order")] = glass.joinToString(",") { tab -> tab.name }
            }
            var preferences = SteamDockPreferences(dataStore)
            val original = preferences.configuration.first()
            assertEquals(SteamDockStyle.LIQUID_GLASS, original.style)
            assertEquals(fixed, original.fixedOrder)
            assertEquals(glass, original.liquidGlassOrder)

            // An installed development build may have persisted the retired style.
            dataStore.edit {
                it[stringPreferencesKey("dock_style")] = "TELEGRAM"
                it[stringPreferencesKey("telegram_dock_order")] = "CHAT,STORE,LIBRARY,TOKEN,SETTINGS"
            }
            assertEquals(SteamDockStyle.FIXED, preferences.configuration.first().style)
            job!!.cancelAndJoin()
            dataStore = open()
            preferences = SteamDockPreferences(dataStore)
            val reopened = preferences.configuration.first()
            assertEquals(SteamDockStyle.FIXED, reopened.style)
            assertEquals(fixed, reopened.fixedOrder)
            assertEquals(glass, reopened.liquidGlassOrder)
            preferences.updateStyle(SteamDockStyle.LIQUID_GLASS)
            assertEquals(SteamDockStyle.LIQUID_GLASS, preferences.style.first())
            assertEquals(fixed, preferences.fixedOrder.first())
            assertEquals(glass, preferences.liquidGlassOrder.first())
            dataStore.edit { it[stringPreferencesKey("dock_style")] = "FUTURE_STYLE" }
            assertEquals(SteamDockStyle.FIXED, preferences.style.first())
            assertEquals(fixed, preferences.fixedOrder.first())
            assertEquals(glass, preferences.liquidGlassOrder.first())
            assertEquals("CHAT,STORE,LIBRARY,TOKEN,SETTINGS", dataStore.data.first()[stringPreferencesKey("telegram_dock_order")])
        } finally {
            job?.cancelAndJoin()
            assertTrue(!file.exists() || file.delete())
        }
    }
}
