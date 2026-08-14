package takagi.ru.monica.steam.store.share.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamStoreGameShareTest {
    private val share = SteamStoreGameShare(
        appId = 1091500,
        name = "Cyberpunk 2077",
        storeUrl = "https://store.steampowered.com/app/1091500/"
    )

    @Test
    fun cardCanBeSentWithoutCaption() {
        assertEquals(
            "Cyberpunk 2077\nhttps://store.steampowered.com/app/1091500/",
            share.messageBody("")
        )
    }

    @Test
    fun captionIsSeparatedFromTheNativeGameCardPayload() {
        assertEquals(
            "今晚一起玩吗？\n\nCyberpunk 2077\n" +
                "https://store.steampowered.com/app/1091500/",
            share.messageBody("  今晚一起玩吗？  ")
        )
    }
}
