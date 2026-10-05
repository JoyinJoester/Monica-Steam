package takagi.ru.monica.steam.security

import java.io.File

/** Share atomic-file exclusion across repositories and recovery conversion. */
internal object SteamProtectedFileAccess {
    private val locks = Array(32) { Any() }
    fun <T> withFile(file: File, block: () -> T): T =
        synchronized(locks[(file.absolutePath.hashCode() and Int.MAX_VALUE) % locks.size], block)
}
