package takagi.ru.monica.steam.library.analytics.domain

import kotlinx.serialization.Serializable
import takagi.ru.monica.steam.library.SteamLibrarySnapshot

@Serializable
data class SteamPlayActivityHistory(
    val accountId: Long,
    val baseline: List<SteamPlaytimeBaseline> = emptyList(),
    val days: List<SteamPlayActivityDay> = emptyList(),
    val updatedAt: Long = 0L
)

@Serializable
data class SteamPlaytimeBaseline(
    val appId: Int,
    val gameName: String,
    val cumulativeMinutes: Int
)

@Serializable
data class SteamPlayActivityDay(
    val date: String,
    val games: List<SteamPlayActivityGame>
) {
    val totalMinutes: Int get() = games.sumOf(SteamPlayActivityGame::minutes)
}

@Serializable
data class SteamPlayActivityGame(
    val appId: Int,
    val gameName: String,
    val minutes: Int
)

/**
 * Steam exposes cumulative playtime, not a daily timeline. A first observation only establishes
 * the baseline; later positive deltas are attributed to the local day on which they are observed.
 */
fun updateSteamPlayActivity(
    previous: SteamPlayActivityHistory?,
    snapshot: SteamLibrarySnapshot,
    localDate: String,
    recordedAt: Long,
    retentionDays: Int = 400
): SteamPlayActivityHistory {
    val previousBaseline = previous?.baseline.orEmpty().associateBy(SteamPlaytimeBaseline::appId)
    val deltas = if (previousBaseline.isEmpty()) {
        emptyList()
    } else {
        snapshot.games.mapNotNull { game ->
            val old = previousBaseline[game.appId] ?: return@mapNotNull null
            val delta = game.playtimeForeverMinutes - old.cumulativeMinutes
            if (delta <= 0) null else SteamPlayActivityGame(game.appId, game.name, delta)
        }
    }

    val updatedDays = previous?.days.orEmpty().toMutableList()
    if (deltas.isNotEmpty()) {
        val existingIndex = updatedDays.indexOfFirst { it.date == localDate }
        val existingGames = if (existingIndex >= 0) {
            updatedDays[existingIndex].games.associateBy(SteamPlayActivityGame::appId).toMutableMap()
        } else {
            mutableMapOf()
        }
        deltas.forEach { delta ->
            val old = existingGames[delta.appId]
            existingGames[delta.appId] = delta.copy(minutes = delta.minutes + (old?.minutes ?: 0))
        }
        val day = SteamPlayActivityDay(
            date = localDate,
            games = existingGames.values.sortedByDescending(SteamPlayActivityGame::minutes)
        )
        if (existingIndex >= 0) updatedDays[existingIndex] = day else updatedDays += day
    }

    return SteamPlayActivityHistory(
        accountId = snapshot.accountId,
        baseline = snapshot.games.map { game ->
            SteamPlaytimeBaseline(game.appId, game.name, game.playtimeForeverMinutes)
        },
        days = updatedDays.sortedByDescending(SteamPlayActivityDay::date).take(retentionDays),
        updatedAt = recordedAt
    )
}
