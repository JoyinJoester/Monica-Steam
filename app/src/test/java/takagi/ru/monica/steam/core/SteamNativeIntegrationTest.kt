package takagi.ru.monica.steam.core

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.steam.importer.SteamMaFileParser
import takagi.ru.monica.steam.library.RustOwnedGamesParser
import takagi.ru.monica.steam.library.family.RustFamilySharedAppsParser
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmProtocol

/** These tests must load a real host JNI library; a missing library cannot pass CI. */
class SteamNativeIntegrationTest {
    @Before
    fun requireNativeLibrary() {
        assumeTrue(java.lang.Boolean.getBoolean("steam.native.required"))
        assertTrue("Host JNI library failed to load", RustSteamCoreNative.isAvailable)
    }

    @Test
    fun ownedGamesCrossesJniAndDecodesUnicodeAndUnsignedFields() {
        val name = "游戏 🎮"
        val game = SteamProtoWriter().apply {
            writeVarint(1, 570)
            writeString(2, name)
            writeVarint(3, 5)
            writeVarint(4, 123)
            writeString(5, "icon")
        }.toByteArray()
        val response = SteamProtoWriter().apply {
            writeVarint(1, 1)
            writeBytes(2, game)
        }.toByteArray()
        assertNotNull(RustSteamCoreNative.parseOwnedGamesOrNull(response))
        val parsed = requireNotNull(RustOwnedGamesParser.parseOrNull(response)).single()
        assertEquals(570, parsed.appId)
        assertEquals(name, parsed.name)
        assertEquals(123, parsed.playtimeForeverMinutes)
        assertEquals(5, parsed.playtimeRecentMinutes)
    }

    @Test
    fun cmRoundTripUsesBothNativeEntryPoints() {
        val body = byteArrayOf(8, 1)
        val message = requireNotNull(RustSteamCoreNative.encodeCmMessageOrNull(
            151, 76561198000000000L, 42, body, -1, -1, "测试 🎮"
        ))
        assertNotNull(RustSteamCoreNative.decodeCmMessagesOrNull(message))
        val decoded = SteamCmProtocol.decodeMessages(message).single()
        assertEquals(151, decoded.eMsg)
        assertArrayEquals(body, decoded.body)
    }

    @Test
    fun sharedGameLastPlayedCrossesJniWithoutInventingRecentMinutes() {
        val response = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply {
                writeVarint(1, 20)
                writeFixed64(2, 76561198000000002L)
                writeString(6, "共享游戏 🎮")
                writeVarint(12, 4_000_000_000L)
                writeVarint(13, 120)
            })
        }.toByteArray()
        val parsed = requireNotNull(RustFamilySharedAppsParser.parseOrNull(response)).single()
        assertEquals(4_000_000_000L, parsed.lastPlayedAt)
        assertEquals(120, parsed.playtimeForeverMinutes)
        assertEquals(0, parsed.playtimeRecentMinutes)
        assertEquals("共享游戏 🎮", parsed.name)
    }

    @Test
    fun legacySecretImportRemainsCompatibleWhenNativeLibraryIsLoaded() {
        val secret = "C".repeat(26) + "B="
        val json = """{"steamid":"76561198000000000","shared_secret":"$secret"}"""
        val imported = SteamMaFileParser().parse(json)
        assertEquals("C".repeat(26) + "A=", imported.sharedSecret)
        assertEquals("TJCJN", SteamTotp.generateAuthCode(imported.sharedSecret, 1700000000))
    }

    @Test
    fun malformedNativeInputReturnsNullWithoutCrashingJvm() {
        val malformed = byteArrayOf(18, 127, 8)
        assertNull(RustSteamCoreNative.parseOwnedGamesOrNull(malformed))
        assertNull(RustSteamCoreNative.decodeCmMessagesOrNull(malformed))
    }
}
