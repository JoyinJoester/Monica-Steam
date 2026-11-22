package takagi.ru.monica.steam.library

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.store.ui.isSteamReviewAuthorId

class SteamAchievementImagesTest {
    private fun achievement(icon: String?, gray: String?, done: Boolean = false) =
        SteamAchievement("a", "A", "", done, null, icon, gray)

    @Test fun relativeDefinitionsResolveAgainstTheCorrectGame() {
        assertEquals(listOf("https://shared.akamai.steamstatic.com/community_assets/images/apps/620/gray.jpg",
            "https://shared.akamai.steamstatic.com/community_assets/images/apps/620/icon.jpg"),
            steamAchievementImageUrls(620, achievement("icon.jpg", "gray.jpg")))
    }
    @Test fun missingGrayImageUsesTheActualNormalAchievementImage() {
        val url = "https://media.steampowered.com/steamcommunity/public/images/apps/620/icon.jpg"
        assertEquals(listOf("https://shared.akamai.steamstatic.com/community_assets/images/apps/620/icon.jpg"),
            steamAchievementImageUrls(620, achievement(url, null)))
    }
    @Test fun completedImagesPreferNormalAndHaveAGrayFallback() {
        assertTrue(steamAchievementImageUrls(620, achievement("a.jpg", "b.jpg", true)).first().endsWith("/a.jpg"))
        assertTrue(steamAchievementImageUrls(620, achievement(null, "b.jpg", true)).single().endsWith("/b.jpg"))
    }
    @Test fun unknownHostsTraversalAndMalformedDefinitionsDoNotLoad() {
        assertTrue(steamAchievementImageUrls(620, achievement("https://example.com/a.jpg", "../a.jpg")).isEmpty())
        assertTrue(steamAchievementImageUrls(0, achievement("a.jpg", null)).isEmpty())
    }
    @Test fun currentAimLabFilenamesAndLegacyCachedUrlsResolveToRealImageRoutes() {
        val filename = "936ca407dd5b223be31ae010b32eec08987ef3c6.jpg"
        val expected = "https://shared.akamai.steamstatic.com/community_assets/images/apps/714010/$filename"
        assertEquals(listOf(expected),steamAchievementImageUrls(714010,achievement(null,filename)))
        val old = "https://community.cloudflare.steamstatic.com/steamcommunity/public/images/apps/714010/$filename"
        assertEquals(listOf(expected),steamAchievementImageUrls(714010,achievement(null,old)))
        assertEquals(listOf(expected),steamAchievementImageUrls(714010,achievement(expected,expected)))
    }
    @Test fun authorNavigationRequiresARealIndividualSteamId() {
        assertTrue(isSteamReviewAuthorId("76561198000000001"))
        listOf("", "123", "76561197960265728", "76561198000000001/", "https://steamcommunity.com/profiles/1")
            .forEach { assertFalse(it, isSteamReviewAuthorId(it)) }
    }
}
