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
    val ownerSteamIds: List<String> = emptyList()
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
    val fetchedAt: Long = System.currentTimeMillis()
)

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
    val inventoryFailure: SteamLibraryFailureReason? = null
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
