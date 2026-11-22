package takagi.ru.monica.steam.library

import java.nio.ByteBuffer
import java.nio.ByteOrder
import takagi.ru.monica.steam.core.RustSteamCoreNative

/** Decoder for the Rust parser that joins achievement definitions with user status. */
internal object RustAchievementDetailsParser {
    fun parseGameAchievementsOrNull(
        accountId: Long,
        appId: Int,
        gameName: String,
        definitionsResponse: ByteArray,
        userResponse: ByteArray
    ): SteamGameAchievements? {
        val payload = RustSteamCoreNative.parseAchievementDetailsOrNull(
            definitionsResponse = definitionsResponse,
            userResponse = userResponse
        ) ?: return null
        // Older native binaries do not preserve group membership or archive flags.
        if (payload.getOrNull(3) != '2'.code.toByte()) return null
        val achievements = decodeOrNull(payload) ?: return null
        val sets = SteamAchievementSetsParser.parse(definitionsResponse, userResponse)
        return SteamGameAchievements(
            accountId = accountId,
            appId = appId,
            gameName = gameName,
            achievements = achievements.filterNot { it.groupId in sets.excludedGroupIds },
            sets = sets.sets,
            fetchedAt = System.currentTimeMillis()
        )
    }

    internal fun decodeForTest(payload: ByteArray): List<SteamAchievement>? =
        decodeOrNull(payload)

    private fun decodeOrNull(payload: ByteArray): List<SteamAchievement>? =
        runCatching { decode(payload) }.getOrNull()

    private fun decode(payload: ByteArray): List<SteamAchievement> {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        require(buffer.remaining() >= HEADER_BYTES) {
            "Rust achievement-details bridge is truncated"
        }
        val magic = ByteArray(4).also(buffer::get)
        val version2 = magic.contentEquals(MAGIC_V2)
        require(version2 || magic.contentEquals(MAGIC)) {
            "Rust achievement-details bridge version is unsupported"
        }
        val count = buffer.int
        require(count in 0..MAX_ITEMS) { "Rust achievement-details count is invalid" }
        val minimumItemBytes = MIN_ITEM_BYTES + if (version2) 8 else 0
        require(buffer.remaining().toLong() >= count.toLong() * minimumItemBytes) {
            "Rust achievement-details bridge is truncated"
        }

        val achievements = ArrayList<SteamAchievement>(count)
        repeat(count) {
            require(buffer.remaining() >= minimumItemBytes) {
                "Rust achievement-details item is truncated"
            }
            val achievedRaw = buffer.int
            val hasUnlockTime = buffer.int
            val unlockTime = buffer.long
            val groupId = if (version2) buffer.int.toLong() and 0xffffffffL else 0L
            val flags = if (version2) buffer.int else 0
            require(flags in 0..3) { "Rust achievement-details flags are invalid" }
            require(achievedRaw == 0 || achievedRaw == 1) {
                "Rust achievement-details achieved flag is invalid"
            }
            require(hasUnlockTime == 0 || hasUnlockTime == 1) {
                "Rust achievement-details unlock flag is invalid"
            }
            if (hasUnlockTime == 1) {
                require(unlockTime > 0L) { "Rust achievement-details unlock time is invalid" }
            }
            achievements += SteamAchievement(
                apiName = readRequiredString(buffer),
                displayName = readRequiredString(buffer),
                description = readRequiredString(buffer),
                achieved = achievedRaw == 1,
                unlockTimeSeconds = unlockTime.takeIf { hasUnlockTime == 1 },
                iconUrl = readOptionalString(buffer),
                lockedIconUrl = readOptionalString(buffer),
                groupId = groupId,
                archived = flags and 1 != 0,
                hidden = flags and 2 != 0
            )
        }
        require(!buffer.hasRemaining()) {
            "Rust achievement-details bridge has trailing bytes"
        }
        return achievements
    }

    private fun readRequiredString(buffer: ByteBuffer): String {
        require(buffer.remaining() >= Int.SIZE_BYTES) {
            "Rust achievement-details string is truncated"
        }
        val length = buffer.int
        require(length >= 0 && length <= buffer.remaining()) {
            "Rust achievement-details string length is invalid"
        }
        return ByteArray(length).also(buffer::get).toString(Charsets.UTF_8)
    }

    private fun readOptionalString(buffer: ByteBuffer): String? {
        require(buffer.remaining() >= Int.SIZE_BYTES) {
            "Rust achievement-details optional string is truncated"
        }
        val length = buffer.int
        if (length == -1) return null
        require(length >= 0 && length <= buffer.remaining()) {
            "Rust achievement-details optional string length is invalid"
        }
        return ByteArray(length).also(buffer::get).toString(Charsets.UTF_8)
    }

    private val MAGIC = byteArrayOf(
        'M'.code.toByte(), 'S'.code.toByte(), 'A'.code.toByte(), '1'.code.toByte()
    )
    private const val HEADER_BYTES = 8
    private val MAGIC_V2 = byteArrayOf('M'.code.toByte(), 'S'.code.toByte(), 'A'.code.toByte(), '2'.code.toByte())
    private const val MIN_ITEM_BYTES = 36
    private const val MAX_ITEMS = 100_000
}
