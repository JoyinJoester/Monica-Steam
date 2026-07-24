package takagi.ru.monica.steam.store.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import takagi.ru.monica.steam.store.domain.SteamReviewSummary
import takagi.ru.monica.steam.store.domain.steamReviewScore

object SteamStoreReviewParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseOverall(payload: String): SteamReviewSummary? {
        val summary = json.parseToJsonElement(payload).jsonObject.obj("query_summary")
            ?: return null
        val positive = summary.int("total_positive") ?: 0
        val negative = summary.int("total_negative") ?: 0
        val total = summary.int("total_reviews") ?: (positive + negative)
        if (total <= 0) return null
        return SteamReviewSummary(
            score = summary.int("review_score") ?: steamReviewScore(positive, negative),
            positive = positive,
            negative = negative,
            total = total
        )
    }

    fun parseRecent(payload: String): SteamReviewSummary? {
        val recent = json.parseToJsonElement(payload).jsonObject
            .obj("results")
            ?.array("recent")
            ?: return null
        val positive = recent.sumOf { (it as? JsonObject)?.int("recommendations_up") ?: 0 }
        val negative = recent.sumOf { (it as? JsonObject)?.int("recommendations_down") ?: 0 }
        val total = positive + negative
        if (total <= 0) return null
        return SteamReviewSummary(
            score = steamReviewScore(positive, negative),
            positive = positive,
            negative = negative,
            total = total
        )
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
}
