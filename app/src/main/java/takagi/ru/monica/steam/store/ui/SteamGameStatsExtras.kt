package takagi.ru.monica.steam.store.ui

import takagi.ru.monica.steam.store.points.domain.SteamPointsShopPage

internal data class SteamGameFriendTime(
    val steamId: String,
    val minutes: Int?,
    val recentMinutes: Int?,
    val name: String = steamId,
    val avatarUrl: String = ""
)

internal data class SteamGameFriendsActivity(
    val own: SteamGameFriendTime?,
    val friends: List<SteamGameFriendTime>
) {
    val averageMinutes: Double? get() = friends.mapNotNull { it.minutes }.takeIf { it.isNotEmpty() }?.average()
}

internal data class SteamGameBadgeCard(val name: String, val imageUrl: String, val owned: Boolean)
internal data class SteamGameBadgeCatalog(
    val name: String = "",
    val iconUrl: String = "",
    val description: String = "",
    val cards: List<SteamGameBadgeCard> = emptyList()
)

internal data class SteamGameStatsExtras(
    val avatarUrl: String = "",
    val friends: SteamGameFriendsActivity? = null,
    val badges: SteamGameBadgeCatalog? = null,
    val points: SteamPointsShopPage? = null,
    val loading: Set<String> = emptySet(),
    val failed: Set<String> = emptySet()
)

/** Missing/private minutes never become zero; the axis uses one shared linear scale. */
internal fun steamGamePlaytimeFraction(minutes: Double, maximum: Double): Float =
    if (!minutes.isFinite() || !maximum.isFinite() || maximum <= 0.0) 0f
    else (minutes / maximum).coerceIn(0.0, 1.0).toFloat()
