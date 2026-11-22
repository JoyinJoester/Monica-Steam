package takagi.ru.monica.steam.friends.chat.richmedia.domain

import kotlin.math.min

internal data class SteamChatImageTile(val left: Float, val top: Float, val right: Float, val bottom: Float)
internal data class SteamChatImageLayout(val width: Float, val height: Float, val tiles: List<SteamChatImageTile>)

/** A single message's images only: never infer an album from unrelated adjacent messages. */
internal fun steamChatImageLayout(aspectRatios: List<Float>, availableWidth: Float): SteamChatImageLayout {
    val ratios = aspectRatios.take(10).map { if (it.isFinite() && it > 0f) it.coerceIn(.1f, 10f) else 4f / 3f }
    require(ratios.isNotEmpty())
    val limit = availableWidth.coerceIn(1f, 324f)
    if (ratios.size == 1) {
        val width = min(limit, (ratios[0] * 420f).coerceAtLeast(120f))
        val height = (width / ratios[0]).coerceIn(96f, 420f)
        return SteamChatImageLayout(width, height, listOf(SteamChatImageTile(0f, 0f, 1f, 1f)))
    }
    val tiles: List<SteamChatImageTile>
    val ratio: Float
    when (ratios.size) {
        2 -> if (ratios.all { it > 1.3f }) {
            ratio = 1f / (1f / ratios[0] + 1f / ratios[1])
            tiles = listOf(SteamChatImageTile(0f, 0f, 1f, .5f), SteamChatImageTile(0f, .5f, 1f, 1f))
        } else {
            ratio = ratios.sum()
            val split = (ratios[0] / ratio).coerceIn(.35f, .65f)
            tiles = listOf(SteamChatImageTile(0f, 0f, split, 1f), SteamChatImageTile(split, 0f, 1f, 1f))
        }
        3 -> if (ratios[0] < .85f) {
            val side = 1f / (1f / ratios[1] + 1f / ratios[2])
            ratio = ratios[0] + side
            val split = (ratios[0] / ratio).coerceIn(.55f, .72f)
            tiles = listOf(SteamChatImageTile(0f, 0f, split, 1f),
                SteamChatImageTile(split, 0f, 1f, .5f), SteamChatImageTile(split, .5f, 1f, 1f))
        } else {
            ratio = 1f / (1f / ratios[0] + 1f / (ratios[1] + ratios[2]))
            tiles = listOf(SteamChatImageTile(0f, 0f, 1f, .58f),
                SteamChatImageTile(0f, .58f, .5f, 1f), SteamChatImageTile(.5f, .58f, 1f, 1f))
        }
        else -> {
            val rows = (ratios.size + 1) / 2
            ratio = ratios.average().toFloat() * 2f / rows
            tiles = ratios.indices.map { index ->
                val row = index / 2
                val lastSingle = index == ratios.lastIndex && ratios.size % 2 == 1
                SteamChatImageTile(if (lastSingle) 0f else index % 2 * .5f,
                    row.toFloat() / rows, if (lastSingle) 1f else (index % 2 + 1) * .5f, (row + 1f) / rows)
            }
        }
    }
    return SteamChatImageLayout(limit, (limit / ratio).coerceIn(180f, 440f), tiles)
}
