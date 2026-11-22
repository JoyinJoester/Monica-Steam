package takagi.ru.monica.steam.profile.viewer.domain

import java.util.Locale
import kotlinx.serialization.Serializable
import takagi.ru.monica.steam.friends.domain.SteamPersonaState
import takagi.ru.monica.steam.community.domain.SteamCommunityBadge
import takagi.ru.monica.steam.library.SteamAchievement
import takagi.ru.monica.steam.library.SteamAchievementSet
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameAchievements

@Serializable
data class SteamProfileViewerTarget(
    val steamId: String,
    val fallbackName: String = "",
    val fallbackAvatarUrl: String = "",
    val fallbackProfileUrl: String = ""
) {
    init {
        require(steamId.matches(Regex("7656119\\d{10}"))) { "valid SteamID64 required" }
    }
}

@Serializable
data class SteamProfileSummary(
    val steamId: String,
    val personaName: String,
    val realName: String = "",
    val avatarUrl: String = "",
    val profileUrl: String = "",
    val personaState: SteamPersonaState = SteamPersonaState.OFFLINE,
    val lastLogoff: Long = 0L,
    val timeCreated: Long = 0L,
    val currentGameId: String = "",
    val currentGameName: String = "",
    val countryCode: String = "",
    val steamLevel: Int? = null,
    val communityVisibilityState: Int = 0
) {
    val displayName: String get() = personaName.ifBlank { steamId }
    val isPlaying: Boolean get() = currentGameId.isNotBlank() || currentGameName.isNotBlank()
    val isPublic: Boolean get() = communityVisibilityState >= 3
}

@Serializable
enum class SteamProfileGameDataVisibility {
    AVAILABLE,
    PRIVATE,
    UNAVAILABLE
}

@Serializable
data class SteamProfileViewerSnapshot(
    val viewerAccountId: Long,
    val viewerSteamId: String,
    val target: SteamProfileSummary,
    val targetGames: List<SteamGame>,
    val viewerGames: List<SteamGame>,
    val gameDataVisibility: SteamProfileGameDataVisibility,
    val fetchedAt: Long,
    val friendCount: Int? = null,
    val groupCount: Int? = null,
    val badgeCount: Int? = null,
    val badges: List<SteamCommunityBadge> = emptyList()
) {
    val isSelf: Boolean get() = viewerSteamId == target.steamId
    val targetGameCount: Int get() = targetGames.distinctBy(SteamGame::appId).size
    val targetPlaytimeMinutes: Long
        get() = targetGames.sumOf { it.playtimeForeverMinutes.toLong() }
    val commonAppIds: Set<Int>
        get() {
            val viewerIds = viewerGames.mapTo(hashSetOf(), SteamGame::appId)
            return targetGames.asSequence()
                .map(SteamGame::appId)
                .filterTo(linkedSetOf()) { it in viewerIds }
        }
    val commonGameCount: Int get() = commonAppIds.size
    val perfectGames: List<SteamGame>
        get() = targetGames.filter(SteamGame::isPerfectAchievementGame)
    val perfectGameCount: Int get() = perfectGames.size
}

@Serializable
data class SteamProfileGroup(
    val groupId: String,
    val name: String,
    val avatarUrl: String = "",
    val profileUrl: String = "",
    val memberCount: Int? = null,
    val onlineCount: Int? = null,
    val inGameCount: Int? = null,
    val groupChatCount: Int? = null
)

internal fun SteamProfileViewerSnapshot.withKnownSelfGames(
    knownGames: List<SteamGame>
): SteamProfileViewerSnapshot {
    if (!isSelf || knownGames.isEmpty()) return this
    val knownByAppId = knownGames.associateBy(SteamGame::appId)
    val merged = targetGames.map { remoteGame ->
        val knownGame = knownByAppId[remoteGame.appId] ?: return@map remoteGame
        mergeSteamProfileGameProgress(remoteGame, knownGame)
    }.toMutableList()
    val presentAppIds = merged.mapTo(hashSetOf(), SteamGame::appId)
    merged += knownGames.filterNot { it.appId in presentAppIds }
    return copy(targetGames = merged.distinctBy(SteamGame::appId))
}

private fun mergeSteamProfileGameProgress(
    remoteGame: SteamGame,
    knownGame: SteamGame
): SteamGame {
    val remoteTotal = remoteGame.achievementTotalCount
    val knownTotal = knownGame.achievementTotalCount
    if (remoteTotal == null && knownTotal == null) return remoteGame
    val total = maxOf(remoteTotal ?: 0, knownTotal ?: 0).takeIf { it > 0 }
    val unlocked = maxOf(
        remoteGame.achievementUnlockedCount ?: 0,
        knownGame.achievementUnlockedCount ?: 0
    ).takeIf { total != null }
    val allUnlocked = total != null && (
        (remoteGame.allAchievementsUnlocked && remoteTotal == total) ||
            (knownGame.allAchievementsUnlocked && knownTotal == total) ||
            (unlocked ?: 0) >= total
        )
    return remoteGame.copy(
        achievementUnlockedCount = unlocked,
        achievementTotalCount = total,
        allAchievementsUnlocked = allUnlocked
    )
}

enum class SteamProfileGameScope {
    ALL,
    COMMON,
    TARGET_ONLY
}

internal fun SteamProfileViewerSnapshot.gamesForScope(
    scope: SteamProfileGameScope
): List<SteamGame> {
    val common = commonAppIds
    val scoped = when (scope) {
        SteamProfileGameScope.ALL -> targetGames
        SteamProfileGameScope.COMMON -> targetGames.filter { it.appId in common }
        SteamProfileGameScope.TARGET_ONLY -> targetGames.filterNot { it.appId in common }
    }
    return scoped.distinctBy(SteamGame::appId).sortedWith(
        compareByDescending<SteamGame> { it.playtimeRecentMinutes }
            .thenByDescending { it.playtimeForeverMinutes }
            .thenBy { it.name.lowercase(Locale.ROOT) }
    )
}

@Serializable
enum class SteamAchievementComparisonState {
    BOTH,
    VIEWER_ONLY,
    TARGET_ONLY,
    NEITHER
}

@Serializable
data class SteamAchievementComparisonEntry(
    val apiName: String,
    val displayName: String,
    val description: String,
    val iconUrl: String?,
    val lockedIconUrl: String?,
    val viewerAchieved: Boolean,
    val targetAchieved: Boolean,
    val viewerUnlockTimeSeconds: Long?,
    val targetUnlockTimeSeconds: Long?,
    val groupId: Long = 0L,
    val archived: Boolean = false,
    val hidden: Boolean = false
) {
    val state: SteamAchievementComparisonState
        get() = when {
            viewerAchieved && targetAchieved -> SteamAchievementComparisonState.BOTH
            viewerAchieved -> SteamAchievementComparisonState.VIEWER_ONLY
            targetAchieved -> SteamAchievementComparisonState.TARGET_ONLY
            else -> SteamAchievementComparisonState.NEITHER
        }
}

@Serializable
data class SteamAchievementComparison(
    val viewerSteamId: String,
    val targetSteamId: String,
    val appId: Int,
    val gameName: String,
    val achievements: List<SteamAchievementComparisonEntry>,
    val fetchedAt: Long,
    val targetSets: List<SteamAchievementSet> = emptyList()
) {
    val viewerCompleted: Int get() = achievements.count(SteamAchievementComparisonEntry::viewerAchieved)
    val targetCompleted: Int get() = achievements.count(SteamAchievementComparisonEntry::targetAchieved)
    val total: Int get() = achievements.size
}

enum class SteamAchievementComparisonFilter {
    ALL,
    BOTH,
    VIEWER_ONLY,
    TARGET_ONLY,
    NEITHER
}

internal fun SteamAchievementComparison.filtered(
    filter: SteamAchievementComparisonFilter
): List<SteamAchievementComparisonEntry> = when (filter) {
    SteamAchievementComparisonFilter.ALL -> achievements
    SteamAchievementComparisonFilter.BOTH -> achievements.filter {
        it.state == SteamAchievementComparisonState.BOTH
    }
    SteamAchievementComparisonFilter.VIEWER_ONLY -> achievements.filter {
        it.state == SteamAchievementComparisonState.VIEWER_ONLY
    }
    SteamAchievementComparisonFilter.TARGET_ONLY -> achievements.filter {
        it.state == SteamAchievementComparisonState.TARGET_ONLY
    }
    SteamAchievementComparisonFilter.NEITHER -> achievements.filter {
        it.state == SteamAchievementComparisonState.NEITHER
    }
}

internal fun buildSteamAchievementComparison(
    viewerSteamId: String,
    targetSteamId: String,
    viewer: SteamGameAchievements,
    target: SteamGameAchievements,
    fetchedAt: Long = System.currentTimeMillis()
): SteamAchievementComparison {
    val viewerByName = viewer.achievements.associateBy(SteamAchievement::apiName)
    val targetByName = target.achievements.associateBy(SteamAchievement::apiName)
    val orderedNames = linkedSetOf<String>().apply {
        addAll(target.achievements.map(SteamAchievement::apiName))
        addAll(viewer.achievements.map(SteamAchievement::apiName))
    }
    val entries = orderedNames.map { apiName ->
        val viewerAchievement = viewerByName[apiName]
        val targetAchievement = targetByName[apiName]
        val definition = targetAchievement ?: viewerAchievement
        SteamAchievementComparisonEntry(
            apiName = apiName,
            displayName = definition?.displayName.orEmpty().ifBlank { apiName },
            description = definition?.description.orEmpty(),
            iconUrl = definition?.iconUrl,
            lockedIconUrl = definition?.lockedIconUrl,
            viewerAchieved = viewerAchievement?.achieved == true,
            targetAchieved = targetAchievement?.achieved == true,
            viewerUnlockTimeSeconds = viewerAchievement?.unlockTimeSeconds,
            targetUnlockTimeSeconds = targetAchievement?.unlockTimeSeconds,
            groupId = definition?.groupId ?: 0L,
            archived = definition?.archived == true,
            hidden = definition?.hidden == true
        )
    }
    return SteamAchievementComparison(
        viewerSteamId = viewerSteamId,
        targetSteamId = targetSteamId,
        appId = target.appId,
        gameName = target.gameName.ifBlank { viewer.gameName },
        achievements = entries,
        targetSets = target.selectableSets,
        fetchedAt = fetchedAt
    )
}

enum class SteamProfileViewerFailureReason {
    ACCOUNT_REQUIRED,
    SESSION_REQUIRED,
    PRIVATE_PROFILE,
    GAME_DATA_PRIVATE,
    RATE_LIMITED,
    NETWORK,
    INVALID_RESPONSE
}

sealed interface SteamProfileViewerResult<out T> {
    data class Success<T>(val value: T) : SteamProfileViewerResult<T>
    data class Failure(val reason: SteamProfileViewerFailureReason) :
        SteamProfileViewerResult<Nothing>
}
