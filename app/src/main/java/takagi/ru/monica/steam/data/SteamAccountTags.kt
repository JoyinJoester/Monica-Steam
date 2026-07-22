package takagi.ru.monica.steam.data

import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object SteamAccountTags {
    const val MAX_TAGS = 12
    const val MAX_TAG_LENGTH = 24

    fun normalize(values: Iterable<String>): List<String> {
        val seen = linkedSetOf<String>()
        val normalized = mutableListOf<String>()
        values.forEach { raw ->
            if (normalized.size >= MAX_TAGS) return@forEach
            val value = raw.trim().take(MAX_TAG_LENGTH)
            if (value.isEmpty()) return@forEach
            val key = value.lowercase(Locale.ROOT)
            if (seen.add(key)) normalized += value
        }
        return normalized
    }

    fun encode(values: Iterable<String>): String {
        return JsonArray(normalize(values).map(::JsonPrimitive)).toString()
    }

    fun decode(encoded: String?): List<String> {
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = Json.parseToJsonElement(encoded) as? JsonArray ?: return@runCatching emptyList()
            normalize(array.mapNotNull { (it as? JsonPrimitive)?.contentOrNull })
        }.getOrDefault(emptyList())
    }
}
