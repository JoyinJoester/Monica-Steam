package takagi.ru.monica.steam.store.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import takagi.ru.monica.steam.store.domain.SteamReviewPage
import takagi.ru.monica.steam.store.domain.SteamReviewSummary
import takagi.ru.monica.steam.store.domain.SteamUserReview
import takagi.ru.monica.steam.store.domain.steamReviewScore

object SteamStoreReviewParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseOverall(payload: String): SteamReviewSummary? {
        return parsePage(payload).summary
    }

    fun parsePage(payload: String): SteamReviewPage {
        val root = json.parseToJsonElement(payload).jsonObject
        val summary = root.obj("query_summary")?.toSummary()
        val items = root.array("reviews").orEmpty().mapNotNull { element ->
            val review = element as? JsonObject ?: return@mapNotNull null
            val body = review.string("review").orEmpty().trim()
            if (body.isBlank()) return@mapNotNull null
            val author = review.obj("author")
            val recommendationId = review.string("recommendationid")
                .orEmpty()
                .ifBlank {
                    "${author?.string("steamid").orEmpty()}-${review.long("timestamp_created") ?: 0L}"
                }
            SteamUserReview(
                recommendationId = recommendationId,
                authorSteamId = author?.string("steamid").orEmpty(),
                body = body,
                votedUp = review.bool("voted_up"),
                createdAt = review.long("timestamp_created") ?: 0L,
                updatedAt = review.long("timestamp_updated") ?: 0L,
                playtimeForeverMinutes = author?.int("playtime_forever") ?: 0,
                playtimeAtReviewMinutes = author?.int("playtime_at_review") ?: 0,
                playtimeLastTwoWeeksMinutes = author?.int("playtime_last_two_weeks") ?: 0,
                votesUp = review.int("votes_up") ?: 0,
                votesFunny = review.int("votes_funny") ?: 0,
                steamPurchase = review.bool("steam_purchase"),
                receivedForFree = review.bool("received_for_free"),
                writtenDuringEarlyAccess = review.bool("written_during_early_access"),
                language = review.string("language").orEmpty()
            )
        }
        return SteamReviewPage(
            summary = summary,
            items = items,
            nextCursor = root.string("cursor")?.takeIf(String::isNotBlank)
        )
    }

    private fun JsonObject.toSummary(): SteamReviewSummary? {
        val positive = int("total_positive") ?: 0
        val negative = int("total_negative") ?: 0
        val total = int("total_reviews") ?: (positive + negative)
        if (total <= 0) return null
        return SteamReviewSummary(
            score = int("review_score") ?: steamReviewScore(positive, negative),
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

    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: false
}
