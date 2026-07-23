package takagi.ru.monica.steam.store

import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.store.ui.*

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamStoreLazyKeyTest {
    @Test
    fun duplicateAppIdsStillProduceUniqueStableKeys() {
        val duplicate = SteamStoreItem(appId = 3240220, name = "Duplicate")
        val keys = listOf(duplicate, duplicate, duplicate).mapIndexed(::steamStoreLazyKey)

        assertEquals(3, keys.distinct().size)
        assertEquals("3240220-0", keys[0])
        assertEquals("3240220-2", keys[2])
    }
}
