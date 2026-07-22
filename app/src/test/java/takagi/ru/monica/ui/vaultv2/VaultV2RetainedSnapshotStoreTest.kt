package takagi.ru.monica.ui.vaultv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultV2RetainedSnapshotStoreTest {

    @Test
    fun `seed uses fallback before the first snapshot`() {
        val store = VaultV2RetainedSnapshotStore<String, List<String>>()

        val seed = store.seed(key = "main", fallback = listOf("fallback"))

        assertEquals(listOf("fallback"), seed.value)
        assertFalse(seed.hasSnapshot)
    }

    @Test
    fun `an empty computed snapshot is still restored as loaded`() {
        val store = VaultV2RetainedSnapshotStore<String, List<String>>()
        store.update(key = "main", value = emptyList())

        val seed = store.seed(key = "main", fallback = listOf("fallback"))

        assertEquals(emptyList<String>(), seed.value)
        assertTrue(seed.hasSnapshot)
    }

    @Test
    fun `main and archive snapshots remain independent`() {
        val store = VaultV2RetainedSnapshotStore<String, List<String>>()
        store.update(key = "main", value = listOf("password"))
        store.update(key = "archive", value = listOf("archived"))

        assertEquals(listOf("password"), store.seed("main", emptyList()).value)
        assertEquals(listOf("archived"), store.seed("archive", emptyList()).value)
    }

    @Test
    fun `clear removes every retained snapshot`() {
        val store = VaultV2RetainedSnapshotStore<String, List<String>>()
        store.update(key = "main", value = listOf("password"))
        store.update(key = "archive", value = listOf("archived"))

        store.clear()

        assertFalse(store.seed("main", emptyList()).hasSnapshot)
        assertFalse(store.seed("archive", emptyList()).hasSnapshot)
    }
}
