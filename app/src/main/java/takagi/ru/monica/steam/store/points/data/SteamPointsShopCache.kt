package takagi.ru.monica.steam.store.points.data

import android.content.Context
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.store.points.domain.SteamPointsShopCategory
import takagi.ru.monica.steam.store.points.domain.SteamPointsShopPage

internal class SteamPointsShopCache(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "steam_points_shop_cache")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun read(category: SteamPointsShopCategory, appId: Int? = null): SteamPointsShopPage? = runCatching {
        val file = File(directory, "${category.name.lowercase()}${appId?.let { "-app-$it" }.orEmpty()}.json")
        if (!file.isFile) return null
        json.decodeFromString<SteamPointsShopPage>(file.readText())
    }.getOrNull()

    fun write(page: SteamPointsShopPage, appId: Int? = null) {
        runCatching {
            directory.mkdirs()
            val target = File(directory, "${page.category.name.lowercase()}${appId?.let { "-app-$it" }.orEmpty()}.json")
            val pending = File(directory, "${target.name}.tmp")
            pending.writeText(json.encodeToString(page))
            if (!pending.renameTo(target)) {
                target.writeText(pending.readText())
                pending.delete()
            }
        }
    }
}
