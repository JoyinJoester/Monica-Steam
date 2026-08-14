package takagi.ru.monica.steam.library.analytics.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamLibrarySnapshot

@Serializable
data class SteamPlayActivityHistory(
    val accountId: Long,
    val baseline: List<SteamPlaytimeBaseline> = emptyList(),
    val days: List<SteamPlayActivityDay> = emptyList(),
    val updatedAt: Long = 0L,
    val dateAttributionVersion: Int = 1
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
    val minutes: Int,
    val iconHash: String = "",
    val headerImageUrl: String = ""
)

/**
 * Steam exposes cumulative playtime, a rolling two-week total and only the most recent play time
 * for each game. Playtime deltas are attributed to that Steam-provided date when it belongs to the
 * current observation window; otherwise the local snapshot date is used as a safe fallback.
 */
fun updateSteamPlayActivity(
    previous: SteamPlayActivityHistory?,
    snapshot: SteamLibrarySnapshot,
    localDate: String,
    recordedAt: Long,
    retentionDays: Int = 400,
    zoneId: ZoneId = ZoneId.systemDefault()
): SteamPlayActivityHistory {
    val previousBaseline = previous?.baseline.orEmpty().associateBy(SteamPlaytimeBaseline::appId)
    val shouldSeedRecent = (previous == null || previous.days.isEmpty()) &&
        snapshot.games.any { it.playtimeRecentMinutes > 0 }
    val deltas = if (shouldSeedRecent) {
        snapshot.games.mapNotNull { game ->
            game.playtimeRecentMinutes
                .takeIf { it > 0 }
                ?.let { minutes ->
                    SteamPlayActivityDelta(
                        date = resolveSteamPlayActivityDate(
                            game = game,
                            previousRecordedAt = null,
                            recordedAt = recordedAt,
                            fallbackDate = localDate,
                            zoneId = zoneId
                        ),
                        game = game.toPlayActivity(minutes)
                    )
                }
        }
    } else if (previousBaseline.isEmpty()) {
        emptyList()
    } else {
        snapshot.games.mapNotNull { game ->
            val old = previousBaseline[game.appId] ?: return@mapNotNull null
            val delta = game.playtimeForeverMinutes - old.cumulativeMinutes
            if (delta <= 0) null else SteamPlayActivityDelta(
                date = resolveSteamPlayActivityDate(
                    game = game,
                    previousRecordedAt = previous?.updatedAt?.takeIf { it > 0L },
                    recordedAt = recordedAt,
                    fallbackDate = localDate,
                    zoneId = zoneId
                ),
                game = game.toPlayActivity(delta)
            )
        }
    }

    val updatedDays = migrateLegacyPlayActivityDates(previous, snapshot, zoneId).toMutableList()
    deltas.groupBy(SteamPlayActivityDelta::date).forEach { (date, datedDeltas) ->
        val existingIndex = updatedDays.indexOfFirst { it.date == date }
        val existingGames = updatedDays
            .getOrNull(existingIndex)
            ?.games
            .orEmpty()
            .associateBy(SteamPlayActivityGame::appId)
            .toMutableMap()
        datedDeltas.forEach { delta ->
            val old = existingGames[delta.game.appId]
            existingGames[delta.game.appId] = delta.game.copy(
                minutes = delta.game.minutes + (old?.minutes ?: 0)
            )
        }
        val day = SteamPlayActivityDay(
            date = date,
            games = existingGames.values.sortedByDescending(SteamPlayActivityGame::minutes)
        )
        if (existingIndex >= 0) updatedDays[existingIndex] = day else updatedDays += day
    }

    val currentGames = snapshot.games.associateBy { it.appId }
    val enrichedDays = updatedDays.map { day ->
        day.copy(
            games = day.games.map { activity ->
                currentGames[activity.appId]?.let { game ->
                    activity.copy(
                        gameName = game.name,
                        iconHash = game.iconHash,
                        headerImageUrl = game.headerImageUrl
                    )
                } ?: activity
            }
        )
    }

    return SteamPlayActivityHistory(
        accountId = snapshot.accountId,
        baseline = snapshot.games.map { game ->
            SteamPlaytimeBaseline(game.appId, game.name, game.playtimeForeverMinutes)
        },
        days = enrichedDays.sortedByDescending(SteamPlayActivityDay::date).take(retentionDays),
        updatedAt = recordedAt,
        dateAttributionVersion = LAST_PLAYED_DATE_ATTRIBUTION_VERSION
    )
}

private data class SteamPlayActivityDelta(
    val date: String,
    val game: SteamPlayActivityGame
)

private fun SteamGame.toPlayActivity(minutes: Int): SteamPlayActivityGame = SteamPlayActivityGame(
    appId = appId,
    gameName = name,
    minutes = minutes,
    iconHash = iconHash,
    headerImageUrl = headerImageUrl
)

private fun resolveSteamPlayActivityDate(
    game: SteamGame,
    previousRecordedAt: Long?,
    recordedAt: Long,
    fallbackDate: String,
    zoneId: ZoneId
): String {
    val lastPlayedDate = game.lastPlayedDate(zoneId) ?: return fallbackDate
    val recordedDate = runCatching {
        LocalDate.ofInstant(Instant.ofEpochMilli(recordedAt), zoneId)
    }.getOrNull() ?: return fallbackDate
    if (lastPlayedDate > recordedDate) return fallbackDate

    val earliestTrustedDate = previousRecordedAt
        ?.let { timestamp ->
            runCatching {
                LocalDate.ofInstant(Instant.ofEpochMilli(timestamp), zoneId)
            }.getOrNull()
        }
        ?: recordedDate.minusDays(RECENT_PLAYTIME_WINDOW_DAYS)
    return lastPlayedDate
        .takeIf { it >= earliestTrustedDate }
        ?.toString()
        ?: fallbackDate
}

private fun migrateLegacyPlayActivityDates(
    previous: SteamPlayActivityHistory?,
    snapshot: SteamLibrarySnapshot,
    zoneId: ZoneId
): List<SteamPlayActivityDay> {
    val previousDays = previous?.days.orEmpty()
    if (previous == null || previous.dateAttributionVersion >= LAST_PLAYED_DATE_ATTRIBUTION_VERSION) {
        return previousDays
    }

    val currentGames = snapshot.games.associateBy(SteamGame::appId)
    val migrated = linkedMapOf<String, MutableMap<Int, SteamPlayActivityGame>>()
    previousDays.forEach { day ->
        val observedDate = runCatching { LocalDate.parse(day.date) }.getOrNull()
        day.games.forEach { activity ->
            val lastPlayedDate = currentGames[activity.appId]?.lastPlayedDate(zoneId)
            val targetDate = lastPlayedDate
                ?.takeIf { date ->
                    observedDate != null &&
                        date < observedDate &&
                        date >= observedDate.minusDays(RECENT_PLAYTIME_WINDOW_DAYS)
                }
                ?.toString()
                ?: day.date
            val games = migrated.getOrPut(targetDate) { linkedMapOf() }
            val existing = games[activity.appId]
            games[activity.appId] = activity.copy(
                minutes = activity.minutes + (existing?.minutes ?: 0)
            )
        }
    }
    return migrated.map { (date, games) ->
        SteamPlayActivityDay(
            date = date,
            games = games.values.sortedByDescending(SteamPlayActivityGame::minutes)
        )
    }
}

private fun SteamGame.lastPlayedDate(zoneId: ZoneId): LocalDate? = runCatching {
    lastPlayedAt
        .takeIf { it > 0L }
        ?.let { LocalDate.ofInstant(Instant.ofEpochSecond(it), zoneId) }
}.getOrNull()

private const val RECENT_PLAYTIME_WINDOW_DAYS = 14L
private const val LAST_PLAYED_DATE_ATTRIBUTION_VERSION = 2
