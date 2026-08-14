package takagi.ru.monica.steam.library

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

@Serializable
data class SteamGame(
    val appId: Int,
    val name: String,
    val playtimeForeverMinutes: Int,
    val playtimeRecentMinutes: Int,
    val iconHash: String = "",
    val headerImageUrl: String = "",
    val price: SteamGamePrice? = null,
    val regionalPrices: List<SteamRegionalPrice> = emptyList(),
    val achievementUnlockedCount: Int? = null,
    val achievementTotalCount: Int? = null,
    val allAchievementsUnlocked: Boolean = false,
    val ownership: SteamGameOwnership = SteamGameOwnership.OWNED,
    val ownerSteamIds: List<String> = emptyList(),
    val supportsSteamCloud: Boolean? = null,
    val achievementProgressPlaytimeMinutes: Int? = null,
    val lastPlayedAt: Long = 0L
) {
    val isPerfectAchievementGame: Boolean
        get() {
            val total = achievementTotalCount ?: return false
            return total > 0 && (
                allAchievementsUnlocked || (achievementUnlockedCount ?: 0) >= total
            )
        }

    val isFamilyShared: Boolean get() = ownership == SteamGameOwnership.FAMILY_SHARED
}

internal data class SteamGameAchievementSummary(
    val unlocked: Int,
    val total: Int,
    val percent: Int,
    val isPerfect: Boolean
)

internal fun SteamGame.achievementSummaryOrNull(): SteamGameAchievementSummary? {
    val total = achievementTotalCount?.takeIf { it > 0 } ?: return null
    val isPerfect = isPerfectAchievementGame
    val unlocked = if (isPerfect) {
        total
    } else {
        (achievementUnlockedCount ?: 0).coerceIn(0, total)
    }
    val percent = if (isPerfect) {
        100
    } else {
        ((unlocked.toLong() * 100L) / total.toLong()).toInt()
    }
    return SteamGameAchievementSummary(
        unlocked = unlocked,
        total = total,
        percent = percent,
        isPerfect = isPerfect
    )
}

@Serializable
enum class SteamGameOwnership {
    OWNED,
    FAMILY_SHARED
}

internal data class SteamGameAchievementProgress(
    val appId: Int,
    val unlocked: Int,
    val total: Int,
    val allUnlocked: Boolean
)

@Serializable
data class SteamGamePrice(
    val currency: String,
    val finalPriceMinor: Long,
    val originalPriceMinor: Long = finalPriceMinor,
    val isAvailable: Boolean,
    val fetchedAt: Long = System.currentTimeMillis(),
    val cnyFinalPriceMinor: Long? = null,
    val cnyOriginalPriceMinor: Long? = null,
    val exchangeRateFetchedAt: Long? = null
)

internal fun SteamGamePrice.withCnyConversion(
    unitsPerCny: Map<String, Double>,
    exchangeRateFetchedAt: Long
): SteamGamePrice {
    if (!isAvailable) return this
    val code = currency.trim().uppercase()
    val rate = if (code == "CNY") 1.0 else unitsPerCny[code]
    if (rate == null || !rate.isFinite() || rate <= 0.0) return this
    return copy(
        cnyFinalPriceMinor = (finalPriceMinor / rate).roundToLong(),
        cnyOriginalPriceMinor = (originalPriceMinor / rate).roundToLong(),
        exchangeRateFetchedAt = exchangeRateFetchedAt
    )
}

@Serializable
data class SteamRegionalPrice(
    val countryCode: String,
    val currency: String,
    val finalPriceMinor: Long,
    val originalPriceMinor: Long = finalPriceMinor,
    val isAvailable: Boolean,
    val fetchedAt: Long = System.currentTimeMillis(),
    val cnyFinalPriceMinor: Long? = null,
    val cnyOriginalPriceMinor: Long? = null,
    val exchangeRateFetchedAt: Long? = null
)

internal fun isSteamSouthAsiaPriceCountry(countryCode: String): Boolean =
    countryCode.trim().uppercase() in setOf("PK", "BD", "BT", "NP", "LK")

@Serializable
data class SteamLibrarySnapshot(
    val accountId: Long,
    val games: List<SteamGame>,
    val fetchedAt: Long,
    val region: String = "",
    val currency: String = "",
    val priceFailure: SteamLibraryFailureReason? = null,
    val familyGroupId: Long? = null,
    val familyShareFailure: SteamLibraryFailureReason? = null,
    val inventoryItemCount: Int? = null,
    val inventoryFetchedAt: Long? = null,
    val inventoryFailure: SteamLibraryFailureReason? = null,
    val achievementProgressFullSyncAt: Long? = null
) {
    val ownedGames: List<SteamGame> get() = games.filterNot(SteamGame::isFamilyShared)
    val sharedGames: List<SteamGame> get() = games.filter(SteamGame::isFamilyShared)
    val gameCount: Int get() = ownedGames.size
    val availableGameCount: Int get() = games.size
    val sharedGameCount: Int get() = sharedGames.size
    val totalPlaytimeMinutes: Long get() = games.sumOf { it.playtimeForeverMinutes.toLong() }
    val recentPlaytimeMinutes: Long get() = games.sumOf { it.playtimeRecentMinutes.toLong() }
    val pricedGameCount: Int get() = ownedGames.count { it.price?.isAvailable == true }
    val unpricedGameCount: Int get() = gameCount - pricedGameCount
    val estimatedReplacementValueMinor: Long
        get() = ownedGames.sumOf { game ->
            game.price?.takeIf { it.isAvailable }?.originalPriceMinor ?: 0L
        }
    val priceCoverage: Float
        get() = if (gameCount == 0) 0f else pricedGameCount.toFloat() / gameCount.toFloat()
}

internal data class SteamAchievementProgressSyncPlan(
    val appIds: List<Int>,
    val isFullSync: Boolean
)

internal fun planSteamAchievementProgressSync(
    current: SteamLibrarySnapshot,
    forceFull: Boolean
): SteamAchievementProgressSyncPlan {
    val currentGames = current.games.distinctBy(SteamGame::appId)
    val isFullSync = forceFull || current.achievementProgressFullSyncAt == null
    if (isFullSync) {
        return SteamAchievementProgressSyncPlan(
            appIds = currentGames.map(SteamGame::appId),
            isFullSync = true
        )
    }

    val changedAppIds = currentGames.mapNotNull { game ->
        val playtimeIncreased = game.playtimeForeverMinutes >
            (game.achievementProgressPlaytimeMinutes ?: 0)
        game.appId.takeIf { playtimeIncreased }
    }
    return SteamAchievementProgressSyncPlan(
        appIds = changedAppIds,
        isFullSync = false
    )
}

internal fun mergeOwnedAndFamilySharedGames(
    ownedGames: List<SteamGame>,
    sharedGames: List<SteamGame>
): List<SteamGame> {
    val ownedAppIds = ownedGames.mapTo(hashSetOf(), SteamGame::appId)
    return ownedGames + sharedGames
        .asSequence()
        .filter(SteamGame::isFamilyShared)
        .filterNot { it.appId in ownedAppIds }
        .distinctBy(SteamGame::appId)
        .toList()
}

data class SteamInventorySummary(
    val itemCount: Int,
    val fetchedAt: Long
)

@Serializable
data class SteamAchievement(
    val apiName: String,
    val displayName: String,
    val description: String,
    val achieved: Boolean,
    val unlockTimeSeconds: Long?,
    val iconUrl: String?,
    val lockedIconUrl: String?
)

@Serializable
data class SteamGameAchievements(
    val accountId: Long,
    val appId: Int,
    val gameName: String,
    val achievements: List<SteamAchievement>,
    val fetchedAt: Long
) {
    val completed: List<SteamAchievement> get() = achievements.filter { it.achieved }
    val incomplete: List<SteamAchievement> get() = achievements.filterNot { it.achieved }
    val completionRate: Float
        get() = if (achievements.isEmpty()) 0f else completed.size.toFloat() / achievements.size.toFloat()
}

@Serializable
enum class SteamLibraryFailureReason {
    SESSION_REQUIRED,
    PRIVATE_PROFILE,
    RATE_LIMITED,
    NETWORK,
    INVALID_RESPONSE
}

sealed interface SteamLibraryResult<out T> {
    data class Success<T>(val value: T) : SteamLibraryResult<T>
    data class Failure(
        val reason: SteamLibraryFailureReason,
        val retryAfterSeconds: Long? = null
    ) : SteamLibraryResult<Nothing>
}

internal fun applyCnyConversions(
    prices: List<SteamRegionalPrice>,
    unitsPerCny: Map<String, Double>,
    exchangeRateFetchedAt: Long
): List<SteamRegionalPrice> {
    return prices.map { price ->
        val currency = price.currency.uppercase()
        val rate = if (currency == "CNY") 1.0 else unitsPerCny[currency]
        if (rate == null || !rate.isFinite() || rate <= 0.0 || !price.isAvailable) {
            price
        } else {
            price.copy(
                cnyFinalPriceMinor = (price.finalPriceMinor / rate).roundToLong(),
                cnyOriginalPriceMinor = (price.originalPriceMinor / rate).roundToLong(),
                exchangeRateFetchedAt = exchangeRateFetchedAt
            )
        }
    }
}

internal fun mergeCachedRegionalPriceConversions(
    fresh: List<SteamRegionalPrice>,
    cached: List<SteamRegionalPrice>
): List<SteamRegionalPrice> {
    val cachedByCountry = cached.associateBy { it.countryCode.uppercase() }
    return fresh.map { price ->
        val previous = cachedByCountry[price.countryCode.uppercase()]
        if (price.cnyFinalPriceMinor != null || previous == null) {
            price
        } else {
            price.copy(
                cnyFinalPriceMinor = previous.cnyFinalPriceMinor,
                cnyOriginalPriceMinor = previous.cnyOriginalPriceMinor,
                exchangeRateFetchedAt = previous.exchangeRateFetchedAt
            )
        }
    }
}

internal fun sortedRegionalPricesForDisplay(
    prices: List<SteamRegionalPrice>
): List<SteamRegionalPrice> {
    return prices.sortedWith(
        compareBy<SteamRegionalPrice> {
            if (it.countryCode.equals("CN", ignoreCase = true)) 0 else 1
        }.thenBy {
            if (it.countryCode.equals("CN", ignoreCase = true)) 0L
            else it.cnyFinalPriceMinor ?: Long.MAX_VALUE
        }.thenBy { it.countryCode.uppercase() }
    )
}
