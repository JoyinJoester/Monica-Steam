package takagi.ru.monica.steam.store

import android.webkit.CookieManager
import java.util.concurrent.atomic.AtomicInteger

internal fun CookieManager.installSteamCookies(
    writes: List<SteamWebCookieWrite>,
    onCookiesReady: () -> Unit
) {
    if (writes.isEmpty()) {
        flush()
        onCookiesReady()
        return
    }
    val remaining = AtomicInteger(writes.size)
    writes.forEach { write ->
        setCookie(write.url, write.value) {
            if (remaining.decrementAndGet() == 0) {
                flush()
                onCookiesReady()
            }
        }
    }
}
