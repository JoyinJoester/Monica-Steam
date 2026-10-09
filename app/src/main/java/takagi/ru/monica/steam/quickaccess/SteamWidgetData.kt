package takagi.ru.monica.steam.quickaccess

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.data.SteamLibraryCacheRepository
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamLibrarySnapshot
import takagi.ru.monica.steam.library.resolvedSteamLibraryCurrency
import takagi.ru.monica.steam.profile.SteamMiniProfileDecor
import takagi.ru.monica.steam.profile.SteamMiniProfileDecorRepository
import takagi.ru.monica.steam.profile.SteamRemoteImageCache
import takagi.ru.monica.steam.foundation.ui.readCachedSteamAvatarBitmap

data class SteamWidgetGame(
    val name: String,
    val playtimeMinutes: Int,
    val image: Bitmap?,
    val isCurrentlyPlaying: Boolean
)

data class SteamWidgetSnapshot(
    val displayName: String,
    val avatar: Bitmap?,
    val totalPlaytimeMinutes: Long?,
    val inventoryCount: Int?,
    val valueMinor: Long?,
    val currency: String,
    val games: List<SteamWidgetGame>,
    val currentGame: SteamWidgetGame?,
    val fetchedAt: Long? = null
)

internal object SteamWidgetDataLoader {
    suspend fun load(context: Context, accountId: Long, databaseId: Long? = null): SteamWidgetSnapshot? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val database = SteamDatabase.getDatabase(appContext)
        val cacheRepository = SteamLibraryCacheRepository(
            appContext, database.steamLibraryCacheDao(), SecurityManager(appContext)
        )
        loadCached(
            accountId = accountId,
            readAccount = { SteamAccountSourceRepository.get(appContext).loadWidgetAccount(it, databaseId) },
            readLibrary = cacheRepository::getLibrary,
            readDecor = SteamMiniProfileDecorRepository.get(appContext)::loadCached,
            readImage = SteamRemoteImageCache.get(appContext)::loadCached,
            readAvatar = { readCachedSteamAvatarBitmap(appContext, it) }
        )
    }

    // This is the production read path, also exercised with real encrypted cache repositories in tests.
    internal suspend fun loadCached(
        accountId: Long,
        readAccount: suspend (Long) -> SteamAccount?,
        readLibrary: suspend (Long) -> SteamLibrarySnapshot?,
        readDecor: (String) -> SteamMiniProfileDecor?,
        readImage: suspend (String) -> Bitmap?,
        readAvatar: (String) -> Bitmap? = { null }
    ): SteamWidgetSnapshot? {
        val account = readAccount(accountId) ?: return null
        val library = readLibrary(accountId)
        val decor = runCatching { readDecor(account.steamId) }.getOrNull()
        suspend fun image(url: String?): Bitmap? = url?.takeIf(String::isNotBlank)?.let {
            runCatching { readImage(it) }.getOrNull()?.let(::widgetBitmap)
        }
        val avatar = runCatching { readAvatar(account.steamId) }.getOrNull()?.let(::widgetBitmap)
            ?: image(decor?.avatarUrl)
        val currentLibraryGame = decor?.currentGameAppId?.let { appId ->
            library?.games?.firstOrNull { it.appId == appId }
        } ?: decor?.currentGameName?.let { name ->
            library?.games?.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
        val current = decor?.currentGameName?.takeIf(String::isNotBlank)?.let { name ->
            SteamWidgetGame(
                name = currentLibraryGame?.name ?: name,
                playtimeMinutes = currentLibraryGame?.playtimeRecentMinutes ?: 0,
                image = image(currentLibraryGame?.widgetHeaderUrl()) ?: image(decor.currentGameImageUrl),
                isCurrentlyPlaying = true
            )
        }
        val games = recentWidgetGames(library)
            .map { game ->
                SteamWidgetGame(
                    name = game.name,
                    playtimeMinutes = game.playtimeRecentMinutes,
                    image = image(game.widgetHeaderUrl()),
                    isCurrentlyPlaying = current != null &&
                        (decor.currentGameAppId == game.appId || current.name == game.name)
                )
            }
        val ordered = buildList {
            if (current != null) add(current)
            addAll(games.filterNot { it.isCurrentlyPlaying })
        }
        return SteamWidgetSnapshot(
            displayName = decor?.personaName?.takeIf(String::isNotBlank)
                ?: account.displayName.ifBlank { account.accountName },
            avatar = avatar,
            totalPlaytimeMinutes = library?.totalPlaytimeMinutes,
            inventoryCount = library?.inventoryItemCount,
            valueMinor = library?.takeIf { it.gameCount == 0 || it.pricedGameCount > 0 }?.estimatedReplacementValueMinor,
            currency = resolvedSteamLibraryCurrency(library),
            games = ordered,
            currentGame = current,
            fetchedAt = library?.fetchedAt
        )
    }

    private fun widgetBitmap(bitmap: Bitmap): Bitmap {
        val edge = maxOf(bitmap.width, bitmap.height)
        if (edge <= 256) return bitmap
        val scale = 256f / edge
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    }

    fun formatPlaytime(totalMinutes: Long): String {
        val hours = totalMinutes / 60L
        return if (hours >= 1000) "${hours / 1000}.${(hours % 1000) / 100}k h" else "${hours} h"
    }

    fun formatGamePlaytime(minutes: Int): String {
        val hours = minutes / 60
        return if (hours > 0) "${hours} h" else "${minutes} min"
    }

    fun formatValue(minor: Long, currency: String): String {
        val amount = minor / 100.0
        val symbol = when (currency.uppercase()) {
            "CNY" -> "¥"
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            "KRW" -> "₩"
            "TWD" -> "NT$"
            "HKD" -> "HK$"
            else -> currency.uppercase() + " "
        }
        val format = if (minor % 100L == 0L) "%,.0f" else "%,.2f"
        return "$symbol${format.format(java.util.Locale.US, amount)}"
    }
}

internal fun recentWidgetGames(library: SteamLibrarySnapshot?): List<SteamGame> = library?.games.orEmpty()
    .filter { it.lastPlayedAt > 0 || it.playtimeRecentMinutes > 0 }
    .sortedWith(compareByDescending<SteamGame> { it.lastPlayedAt }.thenByDescending { it.playtimeRecentMinutes })
    .take(2)

internal fun SteamGame.widgetHeaderUrl(): String = headerImageUrl.ifBlank {
    "https://cdn.akamai.steamstatic.com/steam/apps/$appId/header.jpg"
}
