package takagi.ru.monica.steam.friends.chat.info.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamProfileBackgroundStoreTest {
    @Test
    fun readsEquippedBackgroundFromInlineStyle() {
        val html = """
            <div class="profile_header_bg_texture">
              <div class="profile_background_image_content"
                   style="background-image: url( 'https://steamcommunity-a.akamaihd.net/economy/image/abc/512x512' );"></div>
            </div>
        """.trimIndent()

        assertEquals(
            "https://steamcommunity-a.akamaihd.net/economy/image/abc/512x512",
            parseSteamProfileBackgroundUrl(html)
        )
    }

    @Test
    fun readsAnimatedBackgroundPoster() {
        val html = """
            <video class="profile_background_video" autoplay muted loop
                   poster="https://community.cloudflare.steamstatic.com/economy/image/movie/poster.webp"></video>
        """.trimIndent()

        assertEquals(
            "https://community.cloudflare.steamstatic.com/economy/image/movie/poster.webp",
            parseSteamProfileBackgroundUrl(html)
        )
    }

    @Test
    fun readsAnimatedBackgroundFromTheModernProfileMarkup() {
        val html = """
            <div class="no_header profile_page has_profile_background">
              <div class="profile_animated_background">
                <video playsinline autoplay muted loop
                       poster="https://shared.fastly.steamstatic.com/community_assets/images/items/1/poster.jpg">
                  <source src="https://shared.fastly.steamstatic.com/community_assets/images/items/1/bg.webm" type="video/webm">
                </video>
              </div>
            </div>
        """.trimIndent()

        assertEquals(
            "https://shared.fastly.steamstatic.com/community_assets/images/items/1/poster.jpg",
            parseSteamProfileBackgroundUrl(html)
        )
    }

    @Test
    fun prefersTheProfileBackgroundOverOtherPageImages() {
        val html = """
            <img class="avatar" src="https://avatars.steamstatic.com/self.jpg">
            <div class="profile_background_image_content"
                 style="background-image: url('https://steamcommunity-a.akamaihd.net/economy/image/bg/full')"></div>
        """.trimIndent()

        assertEquals(
            "https://steamcommunity-a.akamaihd.net/economy/image/bg/full",
            parseSteamProfileBackgroundUrl(html)
        )
    }

    @Test
    fun rejectsHostsOutsideTheSteamCdnAllowList() {
        val html = """
            <div class="profile_background_image_content"
                 style="background-image: url('https://cdn.example.com/tracker.gif')"></div>
        """.trimIndent()

        assertNull(parseSteamProfileBackgroundUrl(html))
    }

    @Test
    fun blankOrLoginPagesHaveNoBackground() {
        assertNull(parseSteamProfileBackgroundUrl(""))
        assertNull(parseSteamProfileBackgroundUrl("<html><body>Sign in to Steam</body></html>"))
    }
}
