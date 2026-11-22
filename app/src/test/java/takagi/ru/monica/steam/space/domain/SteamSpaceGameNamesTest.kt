package takagi.ru.monica.steam.space.domain

import org.junit.Assert.*
import org.junit.Test

class SteamSpaceGameNamesTest {
    private val event = SteamSpaceEvent("id", SteamSpaceEventKind.ACHIEVEMENT, "76561198000000001", 1,
        appIds = listOf(815370, 1091500), title = "Original", body = "Body", sourceUrl = "https://steamcommunity.com/my/home/")

    @Test fun libraryLabelsFillMissingAchievementNamesWithoutChangingContentOrIdentity() {
        val result = event.withKnownGameNames(mapOf(815370 to "绿色地狱", 1091500 to "赛博朋克 2077", 1 to "Unrelated"))
        assertEquals(mapOf(815370 to "绿色地狱", 1091500 to "赛博朋克 2077"), result.appNames)
        assertEquals(event.title, result.title)
        assertEquals(event.copy(appNames = result.appNames), result)
    }

    @Test fun officialExistingLabelsWinAndKnownTitlesAreUpdatedOnlyForGameEvents() {
        val existing = event.copy(kind = SteamSpaceEventKind.GAME_ADDED, appNames = mapOf(815370 to "Green Hell"))
        val result = existing.withKnownGameNames(mapOf(815370 to "Other language", 1091500 to "Cyberpunk 2077"))
        assertEquals("Green Hell\nCyberpunk 2077", result.title)
        assertEquals("Green Hell", result.appNames[815370])
        assertEquals(event.copy(kind = SteamSpaceEventKind.UNKNOWN), event.copy(kind = SteamSpaceEventKind.UNKNOWN)
            .withKnownGameNames(mapOf(815370 to "Do not reinterpret unknown events")))
    }
}
