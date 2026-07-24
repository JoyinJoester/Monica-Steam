package takagi.ru.monica.steam.library.family

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameOwnership
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

internal data class SteamFamilyLibraryFetch(
    val familyGroupId: Long? = null,
    val games: List<SteamGame> = emptyList(),
    val failure: SteamLibraryFailureReason? = null
)

internal class SteamFamilyLibraryService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun fetch(account: SteamAccount, language: String): SteamFamilyLibraryFetch {
        val accessToken = account.accessToken?.takeIf(String::isNotBlank)
            ?: return SteamFamilyLibraryFetch(
                failure = SteamLibraryFailureReason.SESSION_REQUIRED
            )
        val steamId = account.steamId.toLongOrNull()
            ?: return SteamFamilyLibraryFetch(
                failure = SteamLibraryFailureReason.SESSION_REQUIRED
            )
        val familyGroupId = runCatching {
            parseFamilyGroupId(
                api.callProtobuf(
                    iface = "IFamilyGroupsService",
                    method = "GetFamilyGroupForUser",
                    request = SteamProtoWriter().apply {
                        writeUint64(1, steamId)
                        writeBool(2, false)
                    },
                    accessToken = accessToken,
                    useGet = true
                )
            )
        }.getOrElse { error ->
            return SteamFamilyLibraryFetch(failure = failureReason(error))
        } ?: return SteamFamilyLibraryFetch()

        return runCatching {
            SteamFamilyLibraryFetch(
                familyGroupId = familyGroupId,
                games = parseSharedLibraryApps(
                    api.callProtobuf(
                        iface = "IFamilyGroupsService",
                        method = "GetSharedLibraryApps",
                        request = SteamProtoWriter().apply {
                            writeFixed64(1, familyGroupId)
                            writeBool(2, false)
                            writeBool(3, false)
                            writeString(5, language)
                            writeVarint(6, MAX_SHARED_APPS.toLong())
                            writeBool(7, false)
                            writeFixed64(8, steamId)
                        },
                        accessToken = accessToken,
                        useGet = true
                    )
                )
            )
        }.getOrElse { error ->
            SteamFamilyLibraryFetch(
                familyGroupId = familyGroupId,
                failure = failureReason(error)
            )
        }
    }

    private fun failureReason(error: Throwable): SteamLibraryFailureReason {
        return when (error) {
            is SteamApiException -> when {
                error.eResult == 5 || error.eResult == 15 ||
                    error.eResult == 401 || error.eResult == 403 ->
                    SteamLibraryFailureReason.SESSION_REQUIRED
                error.eResult == 429 || error.message?.contains("429") == true ->
                    SteamLibraryFailureReason.RATE_LIMITED
                else -> SteamLibraryFailureReason.NETWORK
            }
            is IllegalArgumentException,
            is IllegalStateException,
            is IndexOutOfBoundsException -> SteamLibraryFailureReason.INVALID_RESPONSE
            else -> SteamLibraryFailureReason.NETWORK
        }
    }

    companion object {
        private const val MAX_SHARED_APPS = 50_000

        internal fun parseFamilyGroupId(response: ByteArray): Long? {
            val fields = SteamProtoReader(response).parseAll()
            require(fields.isNotEmpty()) { "Steam family membership response is empty" }
            if (fields.firstOrNull { it.number == 2 }?.asBool == true) return null
            return requireNotNull(
                fields.firstOrNull { it.number == 1 }?.asLong?.takeUnless { it == 0L }
            ) { "Steam family membership response has no group id" }
        }

        internal fun parseSharedLibraryApps(response: ByteArray): List<SteamGame> {
            return SteamProtoReader(response).parseAll()
                .asSequence()
                .filter { it.number == 1 && it.bytes != null }
                .mapNotNull { appField ->
                    val fields = runCatching {
                        SteamProtoReader(appField.bytes ?: return@mapNotNull null).parseAll()
                    }.getOrNull() ?: return@mapNotNull null
                    val appId = fields.firstOrNull { it.number == 1 }
                        ?.asLong
                        ?.toInt()
                        ?.takeIf { it > 0 }
                        ?: return@mapNotNull null
                    val excludeReason = fields.firstOrNull { it.number == 10 }?.asInt ?: 0
                    if (excludeReason != 0) return@mapNotNull null
                    val ownerSteamIds = fields
                        .asSequence()
                        .filter { it.number == 2 && it.bytes != null }
                        .map { it.asFixed64UnsignedString }
                        .filter(String::isNotBlank)
                        .distinct()
                        .toList()
                    val playtimeSeconds = fields.firstOrNull { it.number == 13 }
                        ?.asLong
                        ?.coerceAtLeast(0L)
                        ?: 0L
                    SteamGame(
                        appId = appId,
                        name = fields.firstOrNull { it.number == 6 }
                            ?.asString
                            .orEmpty()
                            .ifBlank { "App $appId" },
                        playtimeForeverMinutes = (playtimeSeconds / 60L)
                            .coerceAtMost(Int.MAX_VALUE.toLong())
                            .toInt(),
                        playtimeRecentMinutes = 0,
                        iconHash = fields.firstOrNull { it.number == 9 }
                            ?.asString
                            .orEmpty(),
                        ownership = SteamGameOwnership.FAMILY_SHARED,
                        ownerSteamIds = ownerSteamIds
                    )
                }
                .distinctBy(SteamGame::appId)
                .toList()
        }
    }
}
