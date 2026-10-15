package takagi.ru.monica.steam.library.family

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.library.SteamGameOwnership

class RustFamilySharedAppsParserTest {
    @Test
    fun bridgeLayoutProducesFamilyGames() {
        val payload = bridge(
            game(
                appId = 20,
                playtime = 7_200,
                name = "Shared game",
                icon = "icon-20",
                owners = listOf("76561198000000002", "76561198000000003"),
                lastPlayedAt = 4_000_000_000L
            )
        )

        val games = RustFamilySharedAppsParser.decodeForTest(payload)
        requireNotNull(games)
        assertEquals(1, games.size)
        assertEquals(20, games[0].appId)
        assertEquals("Shared game", games[0].name)
        assertEquals(7_200, games[0].playtimeForeverMinutes)
        assertEquals(0, games[0].playtimeRecentMinutes)
        assertEquals(4_000_000_000L, games[0].lastPlayedAt)
        assertEquals("icon-20", games[0].iconHash)
        assertEquals(
            listOf("76561198000000002", "76561198000000003"),
            games[0].ownerSteamIds
        )
        assertEquals(SteamGameOwnership.FAMILY_SHARED, games[0].ownership)
    }

    @Test
    fun corruptBridgeIsRejected() {
        assertNull(RustFamilySharedAppsParser.decodeForTest(byteArrayOf(1, 2, 3)))
        assertNull(
            RustFamilySharedAppsParser.decodeForTest(
                bridge(game(-1, 0, "bad", "", emptyList()))
            )
        )
        val trailing = bridge(game(20, 0, "A", "", emptyList())) + byteArrayOf(9)
        assertNull(RustFamilySharedAppsParser.decodeForTest(trailing))
    }

    @Test
    fun zeroOwnerStringRemainsValidLikeKotlinParser() {
        val games = RustFamilySharedAppsParser.decodeForTest(
            bridge(game(20, 1, "A", "", listOf("0")))
        )
        requireNotNull(games)
        assertEquals(listOf("0"), games.single().ownerSteamIds)
        assertTrue(games.single().isFamilyShared)
        assertEquals(0L, games.single().lastPlayedAt)
    }

    @Test
    fun oldBridgeVersionFallsBackInsteadOfSilentlyLosingActivity() {
        val old = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
            .put("MSF1".toByteArray()).putInt(1)
            .putInt(20).putInt(60).putInt(0).putInt(0).putInt(0).array()
        assertNull(RustFamilySharedAppsParser.decodeForTest(old))
        assertNull(RustFamilySharedAppsParser.decodeForTest(
            bridge(game(20, 60, "A", "", emptyList(), lastPlayedAt = -1L))
        ))
        val truncated = bridge(game(20, 60, "A", "", emptyList())).dropLast(1).toByteArray()
        assertNull(RustFamilySharedAppsParser.decodeForTest(truncated))
    }

    private fun bridge(vararg games: ByteArray): ByteArray = ByteBuffer.allocate(
        8 + games.sumOf(ByteArray::size)
    )
        .order(ByteOrder.LITTLE_ENDIAN)
        .put(byteArrayOf('M'.code.toByte(), 'S'.code.toByte(), 'F'.code.toByte(), '2'.code.toByte()))
        .putInt(games.size)
        .also { buffer -> games.forEach(buffer::put) }
        .array()

    private fun game(
        appId: Int,
        playtime: Int,
        name: String,
        icon: String,
        owners: List<String>,
        lastPlayedAt: Long = 0L
    ): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val iconBytes = icon.toByteArray(Charsets.UTF_8)
        val ownerBytes = owners.map { it.toByteArray(Charsets.UTF_8) }
        val size = 16 + 4 + nameBytes.size + 4 + iconBytes.size + 4 +
            ownerBytes.sumOf { 4 + it.size }
        return ByteBuffer.allocate(size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(appId)
            .putInt(playtime)
            .putLong(lastPlayedAt)
            .putInt(nameBytes.size)
            .put(nameBytes)
            .putInt(iconBytes.size)
            .put(iconBytes)
            .putInt(ownerBytes.size)
            .also { buffer ->
                ownerBytes.forEach { bytes ->
                    buffer.putInt(bytes.size)
                    buffer.put(bytes)
                }
            }
            .array()
    }
}
