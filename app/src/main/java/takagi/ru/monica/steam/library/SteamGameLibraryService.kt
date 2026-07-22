package takagi.ru.monica.steam.library

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

internal data class SteamStoreMetadata(
    val headerImageUrl: String,
    val price: SteamGamePrice?
)

private data class SteamStoreMetadataResult(
    val items: Map<Int, SteamStoreMetadata>,
    val failure: SteamLibraryFailureReason?
)

class SteamGameLibraryService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun fetchLibrary(
        account: SteamAccount,
        countryCode: String,
        language: String
    ): SteamLibraryResult<SteamLibrarySnapshot> {
        val token = account.accessToken?.takeIf { it.isNotBlank() }
            ?: return SteamLibraryResult.Failure(SteamLibraryFailureReason.SESSION_REQUIRED)
        if (!account.hasRealSteamId) {
            return SteamLibraryResult.Failure(SteamLibraryFailureReason.SESSION_REQUIRED)
        }
        val games = runCatching {
            parseOwnedGames(
                api.callProtobuf(
                    iface = "IPlayerService",
                    method = "GetOwnedGames",
                    request = SteamProtoWriter().apply {
                        writeUint64(1, account.steamId.toLong())
                        writeBool(2, true)
                        writeBool(3, true)
                        writeString(7, language)
                    },
                    accessToken = token,
                    useGet = true
                )
            )
        }.getOrElse { return mapFailure(it) }
        val storeMetadata = fetchStoreMetadata(
            appIds = games.map(SteamGame::appId),
            countryCode = countryCode,
            language = language,
            accessToken = token
        )
        val enrichedGames = games.map { game ->
            val metadata = storeMetadata.items[game.appId]
            game.copy(
                headerImageUrl = metadata?.headerImageUrl.orEmpty(),
                price = metadata?.price
            )
        }
        return SteamLibraryResult.Success(
            SteamLibrarySnapshot(
                accountId = account.id,
                games = enrichedGames,
                fetchedAt = System.currentTimeMillis(),
                region = countryCode,
                currency = enrichedGames.firstNotNullOfOrNull { it.price?.currency }
                    ?: currencyForCountry(countryCode),
                priceFailure = storeMetadata.failure
            )
        )
    }

    fun fetchAchievements(
        account: SteamAccount,
        game: SteamGame,
        language: String
    ): SteamLibraryResult<SteamGameAchievements> {
        val token = account.accessToken?.takeIf { it.isNotBlank() }
            ?: return SteamLibraryResult.Failure(SteamLibraryFailureReason.SESSION_REQUIRED)
        if (!account.hasRealSteamId) {
            return SteamLibraryResult.Failure(SteamLibraryFailureReason.SESSION_REQUIRED)
        }
        return runCatching {
            val definitions = api.callProtobuf(
                iface = "IPlayerService",
                method = "GetGameAchievements",
                request = SteamProtoWriter().apply {
                    writeVarint(1, game.appId.toLong())
                    writeString(2, language)
                },
                accessToken = token,
                useGet = true
            )
            val userAchievements = api.callProtobuf(
                iface = "IPlayerService",
                method = "GetUserAchievements",
                request = SteamProtoWriter().apply {
                    writeUint64(1, account.steamId.toLong())
                    writeVarint(2, game.appId.toLong())
                },
                accessToken = token,
                useGet = true
            )
            parseAchievementResponses(
                accountId = account.id,
                appId = game.appId,
                gameName = game.name,
                definitionsResponse = definitions,
                userResponse = userAchievements
            )
        }.fold(
            onSuccess = { SteamLibraryResult.Success(it) },
            onFailure = ::mapFailure
        )
    }

    fun fetchRegionalPrices(
        account: SteamAccount,
        appId: Int,
        countryCodes: List<String>,
        language: String
    ): SteamLibraryResult<List<SteamRegionalPrice>> {
        val token = account.accessToken?.takeIf { it.isNotBlank() }
            ?: return SteamLibraryResult.Failure(SteamLibraryFailureReason.SESSION_REQUIRED)
        if (!account.hasRealSteamId) {
            return SteamLibraryResult.Failure(SteamLibraryFailureReason.SESSION_REQUIRED)
        }
        val prices = mutableListOf<SteamRegionalPrice>()
        var firstFailure: SteamLibraryFailureReason? = null
        countryCodes.map(String::uppercase).distinct().forEach { countryCode ->
            val result = fetchStoreMetadata(
                appIds = listOf(appId),
                countryCode = countryCode,
                language = language,
                accessToken = token
            )
            val metadata = result.items[appId]
            if (metadata != null) {
                val price = metadata.price
                prices += SteamRegionalPrice(
                    countryCode = countryCode,
                    currency = price?.currency ?: currencyForCountry(countryCode),
                    finalPriceMinor = price?.finalPriceMinor ?: 0L,
                    originalPriceMinor = price?.originalPriceMinor ?: 0L,
                    isAvailable = price?.isAvailable == true,
                    fetchedAt = price?.fetchedAt ?: System.currentTimeMillis()
                )
            }
            if (firstFailure == null) firstFailure = result.failure
        }
        return when {
            prices.isNotEmpty() -> SteamLibraryResult.Success(prices)
            firstFailure != null -> SteamLibraryResult.Failure(requireNotNull(firstFailure))
            else -> SteamLibraryResult.Failure(SteamLibraryFailureReason.INVALID_RESPONSE)
        }
    }

    private fun fetchStoreMetadata(
        appIds: List<Int>,
        countryCode: String,
        language: String,
        accessToken: String
    ): SteamStoreMetadataResult {
        val items = mutableMapOf<Int, SteamStoreMetadata>()
        var failure: SteamLibraryFailureReason? = null
        appIds.distinct().chunked(STORE_PRICE_BATCH_SIZE).forEach { batch ->
            runCatching {
                parseStoreItems(
                    response = api.callProtobuf(
                        iface = "IStoreBrowseService",
                        method = "GetItems",
                        request = buildStoreItemsRequest(batch, countryCode, language),
                        accessToken = accessToken,
                        useGet = true
                    ),
                    currency = currencyForCountry(countryCode)
                )
            }.onSuccess { parsed ->
                items += parsed
            }.onFailure { error ->
                if (failure == null) failure = failureReason(error)
            }
        }
        return SteamStoreMetadataResult(items = items, failure = failure)
    }

    private fun buildStoreItemsRequest(
        appIds: List<Int>,
        countryCode: String,
        language: String
    ): SteamProtoWriter {
        return SteamProtoWriter().apply {
            appIds.forEach { appId ->
                writeMessage(1, SteamProtoWriter().apply { writeVarint(1, appId.toLong()) })
            }
            writeMessage(2, SteamProtoWriter().apply {
                writeString(1, language)
                writeString(3, countryCode.uppercase())
            })
            writeMessage(3, SteamProtoWriter().apply {
                writeBool(1, true)
                writeBool(4, true)
            })
        }
    }

    private fun mapFailure(error: Throwable): SteamLibraryResult.Failure {
        return SteamLibraryResult.Failure(failureReason(error))
    }

    private fun failureReason(error: Throwable): SteamLibraryFailureReason {
        return when (error) {
            is SteamLibraryException -> error.reason
            is SteamApiException -> when {
                error.eResult == 5 || error.eResult == 15 ||
                    error.eResult == 401 || error.eResult == 403 ->
                    SteamLibraryFailureReason.SESSION_REQUIRED
                error.eResult == 429 || error.message?.contains("429") == true ->
                    SteamLibraryFailureReason.RATE_LIMITED
                else -> SteamLibraryFailureReason.NETWORK
            }
            else -> SteamLibraryFailureReason.NETWORK
        }
    }

    companion object {
        private const val STORE_PRICE_BATCH_SIZE = 40
        private const val STORE_ASSET_BASE =
            "https://shared.akamai.steamstatic.com/store_item_assets/"
        private val json = Json { ignoreUnknownKeys = true }

        internal fun currencyForCountry(countryCode: String): String {
            return when (countryCode.uppercase()) {
                "CN" -> "CNY"
                "US" -> "USD"
                "JP" -> "JPY"
                "KR" -> "KRW"
                "HK" -> "HKD"
                "TW" -> "TWD"
                "UA" -> "UAH"
                "IN" -> "INR"
                "ID" -> "IDR"
                else -> "USD"
            }
        }

        fun parseOwnedGames(response: ByteArray): List<SteamGame> {
            val fields = SteamProtoReader(response).parseAll()
            val declaredGameCount = fields
                .firstOrNull { it.number == 1 && it.wireType == 0 }
                ?.asInt
            val gameFields = fields.filter { it.number == 2 && it.bytes != null }
            if (
                fields.isEmpty() ||
                (declaredGameCount != null && declaredGameCount != gameFields.size)
            ) {
                throw SteamLibraryException(SteamLibraryFailureReason.INVALID_RESPONSE)
            }
            return gameFields
                .mapNotNull { field ->
                    val game = SteamProtoReader(field.bytes!!).parse()
                    val appId = game[1]?.asLong?.toInt() ?: return@mapNotNull null
                    SteamGame(
                        appId = appId,
                        name = game[2]?.asString.orEmpty().ifBlank { "App $appId" },
                        playtimeRecentMinutes = game[3]?.asLong?.coerceAtLeast(0L)?.toInt() ?: 0,
                        playtimeForeverMinutes = game[4]?.asLong?.coerceAtLeast(0L)?.toInt() ?: 0,
                        iconHash = game[5]?.asString.orEmpty()
                    )
                }
        }

        fun parseAchievements(
            accountId: Long = 0L,
            appId: Int,
            gameName: String,
            payload: JsonObject
        ): SteamLibraryResult<SteamGameAchievements> {
            val playerStats = payload["playerstats"] as? JsonObject
                ?: return SteamLibraryResult.Failure(SteamLibraryFailureReason.INVALID_RESPONSE)
            if ((playerStats["success"] as? JsonPrimitive)?.booleanOrNull == false) {
                return SteamLibraryResult.Failure(SteamLibraryFailureReason.PRIVATE_PROFILE)
            }
            val achievements = (playerStats["achievements"] as? JsonArray).orEmpty()
                .mapNotNull { raw ->
                    val item = raw as? JsonObject ?: return@mapNotNull null
                    SteamAchievement(
                        apiName = item.stringValue("apiname"),
                        displayName = item.stringValue("name").ifBlank { item.stringValue("apiname") },
                        description = item.stringValue("description"),
                        achieved = item.intValue("achieved") == 1,
                        unlockTimeSeconds = item.longValueOrNull("unlocktime")?.takeIf { it > 0L },
                        iconUrl = item.stringValue("icon").takeIf(String::isNotBlank),
                        lockedIconUrl = item.stringValue("icongray").takeIf(String::isNotBlank)
                    )
                }
            return SteamLibraryResult.Success(
                SteamGameAchievements(
                    accountId = accountId,
                    appId = appId,
                    gameName = gameName,
                    achievements = achievements,
                    fetchedAt = System.currentTimeMillis()
                )
            )
        }

        internal fun parseStoreItems(
            response: ByteArray,
            currency: String = "CNY"
        ): Map<Int, SteamStoreMetadata> {
            val fetchedAt = System.currentTimeMillis()
            return SteamProtoReader(response).parseAll()
                .asSequence()
                .filter { it.number == 1 && it.bytes != null }
                .mapNotNull { itemField ->
                    val item = SteamProtoReader(itemField.bytes!!).parse()
                    val appId = item[9]?.asLong?.toInt()?.takeIf { it > 0 }
                        ?: return@mapNotNull null
                    val assets = item[30]?.bytes?.let { SteamProtoReader(it).parse() }
                    val assetFormat = assets?.get(1)?.asString.orEmpty()
                    val assetFilename = sequenceOf(4, 2, 3)
                        .mapNotNull { assets?.get(it)?.asString?.takeIf(String::isNotBlank) }
                        .firstOrNull()
                    val headerImageUrl = buildStoreAssetUrl(assetFormat, assetFilename)
                    val isFree = item[13]?.asBool == true
                    val purchase = item[40]?.bytes?.let { SteamProtoReader(it).parse() }
                    val finalPrice = purchase?.get(5)?.asLong?.coerceAtLeast(0L)
                    val price = when {
                        isFree -> SteamGamePrice(
                            currency = currency,
                            finalPriceMinor = 0L,
                            originalPriceMinor = 0L,
                            isAvailable = true,
                            fetchedAt = fetchedAt
                        )
                        finalPrice != null -> SteamGamePrice(
                            currency = currency,
                            finalPriceMinor = finalPrice,
                            originalPriceMinor = purchase[6]?.asLong?.coerceAtLeast(0L)
                                ?: finalPrice,
                            isAvailable = true,
                            fetchedAt = fetchedAt
                        )
                        else -> null
                    }
                    appId to SteamStoreMetadata(
                        headerImageUrl = headerImageUrl,
                        price = price
                    )
                }
                .toMap()
        }

        fun parseAchievementResponses(
            accountId: Long,
            appId: Int,
            gameName: String,
            definitionsResponse: ByteArray,
            userResponse: ByteArray
        ): SteamGameAchievements {
            val statuses = SteamProtoReader(userResponse).parseAll()
                .asSequence()
                .filter { it.number == 1 && it.bytes != null }
                .mapNotNull { field ->
                    val status = SteamProtoReader(field.bytes!!).parse()
                    val key = status[1]?.asLong ?: return@mapNotNull null
                    val unlockTime = status[3]?.let { unlockField ->
                        when (unlockField.wireType) {
                            5 -> unlockField.asFixed32UnsignedLong
                            else -> unlockField.asLong
                        }.takeIf { it > 0L }
                    }
                    key to Pair(status[2]?.asBool == true, unlockTime)
                }
                .toMap()
            val achievements = SteamProtoReader(definitionsResponse).parseAll()
                .asSequence()
                .filter { it.number == 1 && it.bytes != null }
                .mapNotNull { field ->
                    val definition = SteamProtoReader(field.bytes!!).parse()
                    val key = definition[8]?.asLong ?: return@mapNotNull null
                    val apiName = definition[1]?.asString.orEmpty()
                    val status = statuses[key]
                    SteamAchievement(
                        apiName = apiName.ifBlank { "achievement_$key" },
                        displayName = definition[2]?.asString.orEmpty().ifBlank {
                            apiName.ifBlank { "Achievement" }
                        },
                        description = definition[3]?.asString.orEmpty(),
                        achieved = status?.first == true,
                        unlockTimeSeconds = status?.second,
                        iconUrl = definition[4]?.asString?.takeIf(String::isNotBlank),
                        lockedIconUrl = definition[5]?.asString?.takeIf(String::isNotBlank)
                    )
                }
                .toList()
            return SteamGameAchievements(
                accountId = accountId,
                appId = appId,
                gameName = gameName,
                achievements = achievements,
                fetchedAt = System.currentTimeMillis()
            )
        }

        private fun buildStoreAssetUrl(format: String, filename: String?): String {
            if (format.isBlank() || filename.isNullOrBlank()) return ""
            val resolved = format.replace("\${FILENAME}", filename)
            return if (resolved.startsWith("https://")) {
                resolved
            } else {
                STORE_ASSET_BASE + resolved.trimStart('/')
            }
        }

        fun parsePrice(appId: Int, payload: JsonObject): SteamGamePrice? {
            val app = payload[appId.toString()] as? JsonObject ?: return null
            val data = app["data"] as? JsonObject ?: return null
            if ((data["is_free"] as? JsonPrimitive)?.booleanOrNull == true) return null
            val overview = data["price_overview"] as? JsonObject ?: return null
            val finalPrice = overview.longValueOrNull("final") ?: return null
            val currency = overview.stringValue("currency")
            if (currency.isBlank()) return null
            return SteamGamePrice(
                currency = currency,
                finalPriceMinor = finalPrice.coerceAtLeast(0L),
                originalPriceMinor = overview.longValueOrNull("initial")
                    ?.coerceAtLeast(0L) ?: finalPrice.coerceAtLeast(0L),
                isAvailable = finalPrice > 0L
            )
        }
    }
}

private class SteamLibraryException(val reason: SteamLibraryFailureReason) : Exception()

private fun JsonObject.stringValue(key: String): String {
    return (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
}

private fun JsonObject.intValue(key: String): Int {
    return stringValue(key).toIntOrNull() ?: 0
}

private fun JsonObject.longValueOrNull(key: String): Long? {
    return stringValue(key).toLongOrNull()
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
