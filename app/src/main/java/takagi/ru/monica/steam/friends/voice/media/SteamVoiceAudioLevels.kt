package takagi.ru.monica.steam.friends.voice.media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.longOrNull
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import kotlin.math.sqrt

internal data class SteamVoiceAudioSample(
    val revision: Long,
    val local: Float,
    val remote: Map<Long, Float>
) {
    companion object {
        fun parse(raw: String): SteamVoiceAudioSample? = runCatching {
            require(raw.length <= 16_384)
            val json = Json.parseToJsonElement(raw).jsonObject
            val remote = json.getValue("remote").jsonArray
            require(remote.size <= 64)
            SteamVoiceAudioSample(
                json.getValue("revision").jsonPrimitive.longOrNull ?: error("revision"),
                json.getValue("local").jsonPrimitive.floatOrNull ?: 0f,
                remote.associate {
                    val row = it.jsonObject
                    val ssrc = row.getValue("ssrc").jsonPrimitive.longOrNull ?: error("ssrc")
                    require(ssrc in 0..0xffff_ffffL)
                    ssrc to (row.getValue("level").jsonPrimitive.floatOrNull ?: 0f)
                }
            )
        }.getOrNull()
    }
}

/** Main-thread, transient projection. Unknown streams are never attributed to a person. */
internal class SteamVoiceAudioLevels {
    var revision = 0L
        private set
    private var mapping: Map<Long, String> = emptyMap()
    private var observedAt = Long.MIN_VALUE
    var values: Map<String, Float> = emptyMap()
        private set

    fun replaceMapping(next: Map<Long, String>): Long {
        mapping = next.filter { (ssrc, id) -> ssrc in 0..0xffff_ffffL && id.isNotBlank() }.toMap()
        clear()
        return ++revision
    }

    fun removeParticipant(steamId: String) {
        mapping = mapping.filterValues { it != steamId }
        values = values - steamId
    }

    fun invalidate(): Long { clear(); return ++revision }

    fun clear() { values = emptyMap(); observedAt = Long.MIN_VALUE }

    fun expire(nowMillis: Long) {
        if (observedAt != Long.MIN_VALUE && nowMillis - observedAt >= 600) clear()
    }

    fun accept(sample: SteamVoiceAudioSample, state: SteamVoiceCallState, nowMillis: Long) {
        if (sample.revision != revision) return
        if (!state.isConnected) { clear(); return }
        val participants = state.participants.associateBy { it.steamId }
        fun audible(id: String) = participants[id]?.let { it.joined && !it.micMuted && !it.hasNoMic } ?: true
        values = buildMap {
            if (!state.microphoneMuted && state.accountSteamId.isNotBlank() && audible(state.accountSteamId)) {
                amplitude(sample.local).takeIf { it > 0f }?.let { put(state.accountSteamId, it) }
            }
            if (!state.outputMuted) sample.remote.forEach { (ssrc, level) ->
                val id = mapping[ssrc] ?: return@forEach
                if (id == state.accountSteamId || !audible(id)) return@forEach
                if (participants[id]?.joined != true && id != state.target?.partnerSteamId) return@forEach
                val amount = amplitude(level)
                if (amount > 0f) put(id, maxOf(get(id) ?: 0f, amount))
            }
        }
        observedAt = nowMillis
    }

    private fun amplitude(level: Float): Float =
        if (!level.isFinite() || level <= .008f) 0f else sqrt(level.coerceIn(0f, 1f)).coerceIn(0f, 1f)
}
