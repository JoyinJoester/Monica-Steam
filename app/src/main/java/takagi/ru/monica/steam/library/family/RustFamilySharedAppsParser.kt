package takagi.ru.monica.steam.library.family

import java.nio.ByteBuffer
import java.nio.ByteOrder
import takagi.ru.monica.steam.core.RustSteamCoreNative
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameOwnership

/** Decoder for the coarse-grained Rust GetSharedLibraryApps parser. */
internal object RustFamilySharedAppsParser {
    fun parseOrNull(response: ByteArray): List<SteamGame>? {
        val payload = RustSteamCoreNative.parseFamilySharedAppsOrNull(response) ?: return null
        return decodeOrNull(payload)
    }

    internal fun decodeForTest(payload: ByteArray): List<SteamGame>? = decodeOrNull(payload)

    private fun decodeOrNull(payload: ByteArray): List<SteamGame>? =
        runCatching { decode(payload) }.getOrNull()

    private fun decode(payload: ByteArray): List<SteamGame> {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        require(buffer.remaining() >= HEADER_BYTES) { "Rust family bridge is truncated" }
        val magic = ByteArray(4).also(buffer::get)
        require(magic.contentEquals(MAGIC)) { "Rust family bridge version is unsupported" }
        val count = buffer.int
        require(count in 0..MAX_ITEMS) { "Rust family game count is invalid" }
        require(buffer.remaining().toLong() >= count.toLong() * MIN_ITEM_BYTES) {
            "Rust family bridge is truncated"
        }

        val games = ArrayList<SteamGame>(count)
        repeat(count) {
            require(buffer.remaining() >= MIN_ITEM_BYTES) { "Rust family game is truncated" }
            val appId = buffer.int
            val playtime = buffer.int
            val lastPlayedAt = buffer.long
            require(appId > 0) { "Rust family app id is invalid" }
            require(playtime >= 0) { "Rust family playtime is invalid" }
            require(lastPlayedAt >= 0L) { "Rust family last-played time is invalid" }
            val name = readString(buffer)
            val iconHash = readString(buffer)
            require(buffer.remaining() >= Int.SIZE_BYTES) { "Rust family owner count is truncated" }
            val ownerCount = buffer.int
            require(ownerCount in 0..MAX_OWNERS_PER_APP) { "Rust family owner count is invalid" }
            val owners = ArrayList<String>(ownerCount)
            repeat(ownerCount) {
                owners += readString(buffer)
            }
            games += SteamGame(
                appId = appId,
                name = name,
                playtimeForeverMinutes = playtime,
                playtimeRecentMinutes = 0,
                iconHash = iconHash,
                ownership = SteamGameOwnership.FAMILY_SHARED,
                ownerSteamIds = owners,
                lastPlayedAt = lastPlayedAt
            )
        }
        require(!buffer.hasRemaining()) { "Rust family bridge has trailing bytes" }
        return games
    }

    private fun readString(buffer: ByteBuffer): String {
        require(buffer.remaining() >= Int.SIZE_BYTES) { "Rust family string is truncated" }
        val length = buffer.int
        require(length >= 0 && length <= buffer.remaining()) { "Rust family string length is invalid" }
        return ByteArray(length).also(buffer::get).toString(Charsets.UTF_8)
    }

    // MSF1 omitted last-played time. Reject it so older native builds use the Kotlin fallback.
    private val MAGIC = byteArrayOf(
        'M'.code.toByte(), 'S'.code.toByte(), 'F'.code.toByte(), '2'.code.toByte()
    )
    private const val HEADER_BYTES = 8
    private const val MIN_ITEM_BYTES = 28
    private const val MAX_ITEMS = 100_000
    private const val MAX_OWNERS_PER_APP = 1_024
}
