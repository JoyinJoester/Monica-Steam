package takagi.ru.monica.steam.space

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.steam.space.data.SteamSpaceContentSettings
import takagi.ru.monica.steam.space.domain.SteamSpaceEventKind

@RunWith(AndroidJUnit4::class)
class SteamSpaceContentSettingsDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun categoriesPersistByNameAcrossStoreRecreationAndIgnoreUnknownFutureKinds() {
        val name = "steam_space_content_fixture_${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            val store = SteamSpaceContentSettings.create(context, name)
            assertTrue(store.loadHiddenKinds().isEmpty())
            val hidden = setOf(SteamSpaceEventKind.ACHIEVEMENT, SteamSpaceEventKind.VIDEO)
            assertTrue(store.saveHiddenKinds(hidden))
            assertEquals(hidden, SteamSpaceContentSettings.create(context, name).loadHiddenKinds())
            assertEquals(setOf("ACHIEVEMENT", "VIDEO"), preferences.getStringSet(SteamSpaceContentSettings.HIDDEN_KINDS, null))
            assertTrue(preferences.edit().putStringSet(SteamSpaceContentSettings.HIDDEN_KINDS,
                setOf("ACHIEVEMENT", "FUTURE_EVENT_KIND")).commit())
            val reopened = SteamSpaceContentSettings.create(context, name)
            assertEquals(setOf(SteamSpaceEventKind.ACHIEVEMENT), reopened.loadHiddenKinds())
            assertTrue(reopened.saveHiddenKinds(emptySet()))
            assertTrue(SteamSpaceContentSettings.create(context, name).loadHiddenKinds().isEmpty())
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
