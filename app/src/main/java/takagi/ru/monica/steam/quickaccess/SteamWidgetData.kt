package takagi.ru.monica.steam.quickaccess

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.data.SteamLibraryCacheRepository
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamLibrarySnapshot
import takagi.ru.monica.steam.profile.SteamMiniProfileDecor
import takagi.ru.monica.steam.profile.SteamMiniProfileDecorRepository
import takagi.ru.monica.steam.profile.SteamRemoteImageCache

data class SteamWidgetGame(
    val name: String,
    val playtimeMinutes: Int,
    val image: Bitmap?,
    val isCurrentlyPlaying: Boolean
)

data class SteamWidgetSnapshot(
    val displayName: String,
    val avatar: Bitmap?,
    val totalPlaytimeMinutes: Long,
    val inventoryCount: Int,
    val valueMinor: Long,
    val currency: String,
    val games: List<SteamWidgetGame>,
    val currentGame: SteamWidgetGame?
)

internal object SteamWidgetDataLoader {
    suspend fun load(context: Context, accountId: Long): SteamWidgetSnapshot? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val database = SteamDatabase.getDatabase(appContext)
        val securityManager = SecurityManager(appContext)
        val accountRepository = SteamAccountRepository(database.steamAccountDao(), securityManager)
        val cacheRepository = SteamLibraryCacheRepository(
            database.steamLibraryCacheDao(),
            securityManager
        )
        val account = accountRepository.getAccount(accountId) ?: return@withContext null
        // The recent-games widget can still show the live/current game when the
        // library has not been synced yet (or its cache was cleared).  Keep the
        // library optional instead of failing the whole snapshot.
        val library = cacheRepository.getLibrary(accountId)
        val decor = runCatching {
            SteamMiniProfileDecorRepository.get(appContext).load(account.steamId)
        }.getOrNull()
        val imageCache = SteamRemoteImageCache.get(appContext)
        val avatar = decor?.avatarUrl?.let { runCatching { imageCache.load(it) }.getOrNull() }
        val currentLibraryGame = decor?.currentGameAppId?.let { appId ->
            library?.games?.firstOrNull { it.appId == appId }
        } ?: decor?.currentGameName?.let { name ->
            library?.games?.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
        val current = decor?.currentGameName?.takeIf(String::isNotBlank)?.let { name ->
            SteamWidgetGame(
                name = currentLibraryGame?.name ?: name,
                playtimeMinutes = currentLibraryGame?.playtimeRecentMinutes ?: 0,
                image = decor.currentGameImageUrl?.let {
                    runCatching { imageCache.load(it) }.getOrNull()
                } ?: currentLibraryGame?.headerImageUrl?.takeIf(String::isNotBlank)?.let {
                    runCatching { imageCache.load(it) }.getOrNull()
                },
                isCurrentlyPlaying = true
            )
        }
        val games = library?.games.orEmpty()
            .sortedByDescending { it.playtimeRecentMinutes }
            .take(8)
            .map { game ->
                SteamWidgetGame(
                    name = game.name,
                    playtimeMinutes = game.playtimeRecentMinutes,
                    image = game.headerImageUrl.takeIf(String::isNotBlank)?.let {
                        runCatching { imageCache.load(it) }.getOrNull()
                    },
                    isCurrentlyPlaying = current != null &&
                        (decor.currentGameAppId == game.appId || current.name == game.name)
                )
            }
        val ordered = buildList {
            if (current != null) add(current)
            addAll(games.filterNot { it.isCurrentlyPlaying })
        }
        SteamWidgetSnapshot(
            displayName = decor?.personaName?.takeIf(String::isNotBlank)
                ?: account.displayName.ifBlank { account.accountName },
            avatar = avatar,
            totalPlaytimeMinutes = library?.totalPlaytimeMinutes ?: 0L,
            inventoryCount = library?.inventoryItemCount ?: 0,
            valueMinor = library?.estimatedReplacementValueMinor ?: 0L,
            currency = library?.currency?.ifBlank { "CNY" } ?: "CNY",
            games = ordered,
            currentGame = current
        )
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
        return "$symbol${"%.2f".format(java.util.Locale.US, amount)}"
    }
}
