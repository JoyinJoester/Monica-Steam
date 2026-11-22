package takagi.ru.monica.steam.library

import takagi.ru.monica.steam.network.SteamProtoReader

/** IPlayerService schema: definition groups=3, user groups=4. IDs are unsigned uint32. */
internal object SteamAchievementSetsParser {
    data class Result(val sets: List<SteamAchievementSet>, val excludedGroupIds: Set<Long>)

    fun parse(definitions: ByteArray, user: ByteArray): Result {
        require(definitions.size <= MAX_BYTES && user.size <= MAX_BYTES)
        val groups = SteamProtoReader(definitions).parseAll().filter { it.number == 3 && it.bytes != null }
        val progress = SteamProtoReader(user).parseAll().filter { it.number == 4 && it.bytes != null }
        require(groups.size <= MAX_GROUPS && progress.size <= MAX_GROUPS)
        val statuses = progress.mapNotNull { field ->
            val values = runCatching { SteamProtoReader(field.bytes!!).parse() }.getOrNull()
                ?: return@mapNotNull null
            val id = values[1]?.takeIf { it.wireType == 0 }?.asLong
                ?.takeIf { it in 0..0xffffffffL } ?: return@mapNotNull null
            id to values
        }.toMap()
        val excluded = mutableSetOf<Long>()
        val sets = groups.mapNotNull { field ->
            val values = runCatching { SteamProtoReader(field.bytes!!).parse() }.getOrNull()
                ?: return@mapNotNull null
            val id = values[1]?.takeIf { it.wireType == 0 }?.asLong
                ?.takeIf { it in 0..0xffffffffL } ?: return@mapNotNull null
            if (values[5]?.asBool == true || values[7]?.asBool == false) {
                excluded += id
                return@mapNotNull null
            }
            val status = statuses[id]
            SteamAchievementSet(
                groupId = id,
                name = values[2]?.asString.orEmpty(),
                dlcAppId = values[3]?.asLong?.takeIf { it in 1..0xffffffffL },
                archived = values[4]?.asBool == true,
                order = values[6]?.asLong ?: 0L,
                totalAchievements = values[8]?.asLong?.takeIf { it in 0..100_000L }?.toInt(),
                completionAchievements = values[9]?.asLong?.takeIf { it in 0..100_000L }?.toInt(),
                completedAchievements = status?.get(3)?.asLong?.takeIf { it in 0..100_000L }?.toInt(),
                isAchievable = status?.get(2)?.asBool,
                isCompleted = status?.get(4)?.asBool,
                timeCompletedSeconds = status?.get(9)?.let {
                    if (it.wireType == 5) it.asFixed32UnsignedLong else it.asLong
                }?.takeIf { it > 0L }
            )
        }.distinctBy { it.groupId }.filterNot { it.groupId in excluded }.sortedBy { it.order }
        return Result(sets, excluded)
    }

    private const val MAX_BYTES = 32 * 1024 * 1024
    private const val MAX_GROUPS = 10_000
}
