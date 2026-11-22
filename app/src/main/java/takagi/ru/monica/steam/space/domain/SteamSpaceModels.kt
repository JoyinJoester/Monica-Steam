package takagi.ru.monica.steam.space.domain

import takagi.ru.monica.steam.data.SteamAccount
import kotlinx.serialization.Serializable

/** Read-only, account-scoped activity; callers run this blocking boundary off the UI thread. */
fun interface SteamSpaceGateway {
    fun fetchPage(account: SteamAccount, language: String, cursor: SteamSpaceCursor?): SteamSpacePage
}

@Serializable
data class SteamSpaceCursor(
    val accountId: Long,
    val accountSteamId: String,
    val beforeEpochSeconds: Long
)

data class SteamSpacePage(
    val events: List<SteamSpaceEvent>,
    val nextCursor: SteamSpaceCursor?,
    val partial: Boolean = false
)

@Serializable
enum class SteamSpaceEventKind {
    ACHIEVEMENT, SCREENSHOT, VIDEO, STATUS, REVIEW, GAME_ADDED, WISHLISTED,
    FIRST_PLAY, ANNOUNCEMENT, UNKNOWN
}

/** Text is Steam's original text, never a generated description or rendered HTML. */
@Serializable
data class SteamSpaceEvent(
    val id: String,
    val kind: SteamSpaceEventKind,
    val actorSteamId: String,
    val occurredAtEpochSeconds: Long,
    val appIds: List<Int> = emptyList(),
    val title: String? = null,
    val body: String? = null,
    val media: List<SteamSpaceMedia> = emptyList(),
    val achievements: List<SteamSpaceAchievement> = emptyList(),
    val sourceUrl: String,
    val partial: Boolean = false,
    val rawEventType: Int = 0,
    val appNames: Map<Int, String> = emptyMap(),
    val actorName: String = "",
    val actorAvatarUrl: String = ""
)

/** Sensitive previews require a cover; the UI must not load them before a user decision. */
@Serializable
data class SteamSpaceMedia(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    val sensitive: Boolean = false
)

@Serializable
data class SteamSpaceAchievement(
    val name: String,
    val displayName: String,
    val description: String? = null,
    val iconUrl: String? = null
)

class SteamSpaceFormatException : Exception("Steam returned an unsupported activity response")
