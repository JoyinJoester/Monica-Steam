package takagi.ru.monica.steam.store.data

import android.content.Context
import java.util.concurrent.TimeUnit
import takagi.ru.monica.steam.web.domain.normalizeSteamCookieValue
import takagi.ru.monica.steam.web.domain.SteamFamilyViewSessions
import java.util.concurrent.ConcurrentHashMap
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamHttpClientProvider
import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.data.SteamAccount
import java.io.IOException
import takagi.ru.monica.steam.store.catalog.data.SteamStoreCatalogService
import takagi.ru.monica.steam.store.filters.data.SteamStoreFilterMetadataService
import takagi.ru.monica.steam.store.filters.domain.SteamStoreFilterMetadata
import takagi.ru.monica.steam.store.filters.domain.SteamStoreFilterSelection
import takagi.ru.monica.steam.store.related.data.SteamStoreRelatedContentService
import takagi.ru.monica.steam.store.purchase.data.SteamStorePackageMetadataService
import takagi.ru.monica.steam.store.purchase.data.SteamStorePurchasePageService
import takagi.ru.monica.steam.store.interest.data.SteamStoreInterestService
import takagi.ru.monica.steam.store.interest.data.SteamStoreInterestMemoryDataSource
import takagi.ru.monica.steam.store.interest.data.SteamStoreInterestPreferences
import takagi.ru.monica.steam.store.interest.data.SteamStoreInterestPreferencesDataSource
import takagi.ru.monica.steam.store.interest.data.SteamStoreInterestRepository
import takagi.ru.monica.steam.store.interest.data.SteamStoreInterestSyncSettings
import takagi.ru.monica.steam.store.interest.domain.SteamStoreIgnoreSyncState
import takagi.ru.monica.steam.store.interest.domain.SteamStoreInterestAccount
import takagi.ru.monica.steam.store.interest.domain.SteamStoreInterestSyncResult
import takagi.ru.monica.steam.store.interest.domain.withoutIgnoredGames
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class SteamStoreRegionSearchResult(
    val countryCode: String?,
    val items: List<SteamStoreItem>
)

private data class SteamStoreSearchTarget(
    val countryCode: String?,
    val steamLoginSecure: String?
)

internal val STEAM_STORE_DISCOVERY_COUNTRY_CODES =
    listOf("US", "CN", "JP", "KR", "DE", "RU")

class SteamStoreService(
    private val client: OkHttpClient = SteamHttpClientProvider.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
    private val api: SteamApiClient = SteamApiClient(client),
    private val reviewService: SteamStoreReviewService = SteamStoreReviewService(client),
    context: Context? = null
) {
    private val countryBySession = ConcurrentHashMap<String, String>()
    private val catalogService = SteamStoreCatalogService(client)
    private val filterMetadataService = SteamStoreFilterMetadataService(client)
    private val relatedContentService = SteamStoreRelatedContentService(api)
    private val packageMetadataService = SteamStorePackageMetadataService(client)
    private val purchasePageService = SteamStorePurchasePageService(client, relatedContentService)
    private val ignoredGamesService = SteamStoreInterestService(client, api)
    private val interestRepository = SteamStoreInterestRepository(
        local = context?.let(::SteamStoreInterestPreferencesDataSource)
            ?: SteamStoreInterestMemoryDataSource(),
        remote = ignoredGamesService,
        syncSettings = context?.let(::SteamStoreInterestPreferences)
            ?: object : SteamStoreInterestSyncSettings {
                override val syncWithSteam: Boolean = true
            }
    )

    fun accountCountryCode(account: SteamAccount): String? = accountCountryOrFail(
        steamLoginSecure = account.steamLoginSecure,
        accessToken = account.accessToken
    )

    fun budgetSuggestions(
        targetMinor: Int,
        countryCode: String,
        steamLoginSecure: String?,
        language: String = "schinese",
        wishlistAppIds: Set<Int> = emptySet(),
        limit: Int = 6
    ): List<SteamStoreItem> = catalogService.budgetSuggestions(
        targetMinor = targetMinor,
        countryCode = countryCode,
        steamLoginSecure = steamLoginSecure,
        language = language,
        wishlistAppIds = wishlistAppIds,
        limit = limit
    )

    fun featured(
        steamLoginSecure: String? = null,
        accessToken: String? = null,
        language: String = "schinese",
        steamId: String? = null
    ): SteamStoreHome {
        val countryCode = accountCountryOrFail(steamLoginSecure, accessToken)
        val body = get(
            path = "/api/featuredcategories",
            query = mapOf("l" to language),
            steamLoginSecure = steamLoginSecure,
            countryCode = countryCode
        )
        // Featured categories mixes hardware SKUs (even with type=0) into this
        // ranking. The game catalog applies Steam's category1=998 server-side.
        val topGames = runCatching {
            catalogService.page(
                filter = SteamStoreBrowseFilter.TOP_SELLERS,
                filters = SteamStoreFilterSelection(), start = 0, count = 12,
                language = language, countryCode = countryCode,
                steamLoginSecure = steamLoginSecure
            ).items
        }.onFailure { error ->
            SteamDiagLogger.append("store_home top_games_failed type=${error.javaClass.simpleName}")
        }.getOrDefault(emptyList())
        val featured = SteamStoreParser.parseFeatured(body, countryCode).copy(topSellers = topGames)
        val events = runCatching {
            SteamStoreParser.parseDiscoveryEvents(
                get(
                    path = "/",
                    query = mapOf("l" to language),
                    steamLoginSecure = steamLoginSecure,
                    countryCode = countryCode
                )
            )
        }.onFailure { error ->
            SteamDiagLogger.append(
                "store_discovery events_failed type=${error.javaClass.simpleName}"
            )
        }.getOrDefault(emptyList())
        val ignoredAppIds = ignoredAppIds(
            items = featured.specials + featured.topSellers +
                featured.newReleases + featured.comingSoon,
            steamLoginSecure = steamLoginSecure,
            accessToken = accessToken,
            countryCode = countryCode,
            steamId = steamId,
            forceRefresh = true
        )
        return featured.withoutIgnoredGames(ignoredAppIds).copy(events = events)
    }

    fun catalog(
        filter: SteamStoreBrowseFilter,
        filters: SteamStoreFilterSelection = SteamStoreFilterSelection(),
        start: Int = 0,
        count: Int = 24,
        steamLoginSecure: String? = null,
        accessToken: String? = null,
        language: String = "schinese",
        steamId: String? = null
    ): SteamStoreCatalogPage {
        val countryCode = accountCountryOrFail(steamLoginSecure, accessToken)
        val page = catalogService.page(
            filter = filter,
            filters = filters,
            start = start,
            count = count,
            language = language,
            countryCode = countryCode,
            steamLoginSecure = steamLoginSecure
        )
        return page.withoutIgnoredGames(
            ignoredAppIds(
                items = page.items,
                steamLoginSecure = steamLoginSecure,
                accessToken = accessToken,
                countryCode = countryCode,
                steamId = steamId
            )
        )
    }

    fun filterMetadata(
        steamLoginSecure: String? = null,
        accessToken: String? = null,
        language: String = "schinese"
    ): SteamStoreFilterMetadata = filterMetadataService.fetch(
        countryCode = accountCountryOrFail(steamLoginSecure, accessToken),
        steamLoginSecure = steamLoginSecure,
        language = language
    )

    suspend fun search(
        queryText: String,
        filters: SteamStoreFilterSelection = SteamStoreFilterSelection(),
        steamLoginSecure: String? = null,
        accessToken: String? = null,
        language: String = "schinese",
        steamId: String? = null
    ): List<SteamStoreItem> {
        if (queryText.isBlank()) return emptyList()
        val query = queryText.trim()
        val accountCountry = accountCountryOrFail(steamLoginSecure, accessToken)
        val targets = buildList {
            add(
                SteamStoreSearchTarget(
                    countryCode = accountCountry,
                    steamLoginSecure = steamLoginSecure
                )
            )
            STEAM_STORE_DISCOVERY_COUNTRY_CODES
                .filterNot { it.equals(accountCountry, ignoreCase = true) }
                .forEach { countryCode ->
                    add(SteamStoreSearchTarget(countryCode = countryCode, steamLoginSecure = null))
                }
        }.distinctBy { it.countryCode?.uppercase().orEmpty() }
        val attempts = coroutineScope {
            targets.map { target ->
                async {
                    try {
                        Result.success(
                            SteamStoreRegionSearchResult(
                                countryCode = target.countryCode,
                                items = if (filters.isActive) {
                                    catalogService.search(
                                        queryText = query,
                                        filters = filters,
                                        language = language,
                                        countryCode = target.countryCode,
                                        steamLoginSecure = target.steamLoginSecure
                                    )
                                } else {
                            SteamStoreParser.parseSearch(
                                        getAsync(
                                            path = "/api/storesearch/",
                                            query = mapOf("term" to query, "l" to language),
                                            steamLoginSecure = target.steamLoginSecure,
                                            countryCode = target.countryCode
                                        )
                                    )
                                }
                            )
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        SteamDiagLogger.append(
                            "store_search catalog_failed country=${target.countryCode ?: "account_default"} " +
                                "type=${error.javaClass.simpleName}"
                        )
                        Result.failure(error)
                    }
                }
            }.awaitAll()
        }
        val regionalResults = attempts.mapNotNull(Result<SteamStoreRegionSearchResult>::getOrNull)
        if (regionalResults.isEmpty()) {
            throw attempts.firstNotNullOfOrNull(Result<SteamStoreRegionSearchResult>::exceptionOrNull)
                ?: IllegalStateException("Steam 商店搜索没有返回数据")
        }
        val accountRegionResponded = accountCountry != null && regionalResults.any {
            it.countryCode.equals(accountCountry, ignoreCase = true)
        }
        val merged = mergeSteamStoreSearchResults(
            query = query,
            accountCountryCode = accountCountry,
            accountRegionResponded = accountRegionResponded,
            regionalResults = regionalResults
        )
        return merged.withoutIgnoredGames(
            ignoredAppIds(
                items = merged,
                steamLoginSecure = steamLoginSecure,
                accessToken = accessToken,
                countryCode = accountCountry,
                steamId = steamId
            )
        )
    }

    private fun ignoredAppIds(
        items: Collection<SteamStoreItem>,
        steamLoginSecure: String?,
        accessToken: String?,
        countryCode: String?,
        steamId: String?,
        forceRefresh: Boolean = false
    ): Set<Int> {
        val visibleAppIds = items.asSequence().map(SteamStoreItem::appId).toSet()
        if (visibleAppIds.isEmpty()) return emptySet()
        val account = steamStoreInterestAccount(
            steamId = steamId,
            steamLoginSecure = steamLoginSecure,
            accessToken = accessToken,
            countryCode = countryCode
        ) ?: return emptySet()
        return try {
            interestRepository.ignoredAppIds(account, forceRefresh).intersect(visibleAppIds)
        } catch (error: Throwable) {
            SteamDiagLogger.append(
                "store_ignored_state failed type=${error.javaClass.simpleName}"
            )
            emptySet()
        }
    }

    fun detail(
        appId: Int,
        steamLoginSecure: String? = null,
        accessToken: String? = null,
        language: String = "schinese",
        discoveryCountryCode: String? = null,
        steamId: String? = null
    ): SteamStoreDetail = resolveDetail(
        appId = appId,
        steamLoginSecure = steamLoginSecure,
        accessToken = accessToken,
        language = language,
        discoveryCountryCode = discoveryCountryCode
    ).let { detail ->
        val effectiveAccessToken = effectiveSteamStoreAccessToken(accessToken, steamLoginSecure)
        val ignored = steamStoreInterestAccount(
            steamId = steamId,
            steamLoginSecure = steamLoginSecure,
            accessToken = accessToken,
            countryCode = detail.accountCountryCode ?: detail.priceCountryCode
        )?.let { account ->
            interestRepository.ignoredAppIds(account).contains(detail.appId)
        } ?: false
        val purchasePage = purchasePageService.fetch(
            appId = detail.appId,
            countryCode = detail.priceCountryCode,
            language = language,
            steamLoginSecure = steamLoginSecure.takeIf {
                detail.availableInAccountRegion != false
            },
            accessToken = effectiveAccessToken
        )
        val visiblePackageOptions = if (purchasePage.visiblePackageIds.isEmpty()) {
            detail.packageOptions
        } else {
            detail.packageOptions.filter { it.packageId in purchasePage.visiblePackageIds }
        }
        detail.copy(
            packageId = visiblePackageOptions.firstOrNull()?.packageId,
            packageOptions = packageMetadataService.enrich(
                options = visiblePackageOptions,
                countryCode = detail.priceCountryCode,
                language = language
            ),
            relatedDlc = relatedContentService.fetch(
                appIds = detail.dlcAppIds,
                countryCode = detail.priceCountryCode.orEmpty(),
                language = language,
                accessToken = effectiveAccessToken
            ),
            tags = purchasePage.tags,
            bundles = purchasePage.bundles,
            ignored = ignored
        )
    }.let { detail -> attachReviews(detail, appId, language) }

    fun setIgnored(
        appId: Int,
        ignored: Boolean,
        steamId: String?,
        steamLoginSecure: String?,
        accessToken: String?
    ) {
        val resolvedSteamId = steamId?.trim()?.takeIf(String::isNotBlank)
            ?: throw SteamStoreIgnoreSessionException()
        interestRepository.applyLocal(resolvedSteamId, appId, ignored)
        syncIgnored(
            steamId = resolvedSteamId,
            steamLoginSecure = steamLoginSecure,
            accessToken = accessToken
        )
    }

    fun applyIgnoredLocally(
        appId: Int,
        ignored: Boolean,
        steamId: String?
    ): SteamStoreIgnoreSyncState {
        val resolvedSteamId = steamId?.trim()?.takeIf(String::isNotBlank)
            ?: throw SteamStoreIgnoreSessionException()
        return interestRepository.applyLocal(resolvedSteamId, appId, ignored)
    }

    fun syncIgnored(
        steamId: String?,
        steamLoginSecure: String?,
        accessToken: String?
    ): SteamStoreInterestSyncResult {
        val account = steamStoreInterestAccount(
            steamId = steamId,
            steamLoginSecure = steamLoginSecure,
            accessToken = accessToken,
            countryCode = null
        ) ?: throw SteamStoreIgnoreSessionException()
        return interestRepository.syncPending(account)
    }

    fun ignoredSyncState(
        steamId: String?,
        appId: Int
    ): SteamStoreIgnoreSyncState? = steamId
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { interestRepository.syncState(it, appId) }

    fun localIgnoredAppIds(steamId: String?): Set<Int> = steamId
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let(interestRepository::localIgnoredAppIds)
        .orEmpty()

    fun localIgnoredState(steamId: String?, appId: Int): Boolean? = steamId
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { interestRepository.localIgnoredState(it, appId) }

    fun compactDetail(
        appId: Int,
        steamLoginSecure: String? = null,
        accessToken: String? = null,
        language: String = "schinese",
        discoveryCountryCode: String? = null
    ): SteamStoreDetail = resolveDetail(
        appId = appId,
        steamLoginSecure = steamLoginSecure,
        accessToken = accessToken,
        language = language,
        discoveryCountryCode = discoveryCountryCode
    )

    private fun resolveDetail(
        appId: Int,
        steamLoginSecure: String?,
        accessToken: String?,
        language: String,
        discoveryCountryCode: String?
    ): SteamStoreDetail {
        val accountCountry = accountCountryOrFail(steamLoginSecure, accessToken)
        requestDetail(
            appId = appId,
            language = language,
            steamLoginSecure = steamLoginSecure,
            countryCode = accountCountry
        )?.let { detail ->
            return detail.copy(
                availableInAccountRegion = accountCountry?.let { true },
                accountCountryCode = accountCountry,
                priceCountryCode = accountCountry
            )
        }
        steamStoreDetailFallbackCountries(
            accountCountryCode = accountCountry,
            discoveryCountryCode = discoveryCountryCode
        ).forEach { countryCode ->
            val detail = runCatching {
                requestDetail(
                    appId = appId,
                    language = language,
                    steamLoginSecure = null,
                    countryCode = countryCode
                )
            }.onFailure { error ->
                SteamDiagLogger.append(
                    "store_detail fallback_failed app_id=$appId country=$countryCode " +
                        "type=${error.javaClass.simpleName}"
                )
            }.getOrNull()
            if (detail != null) {
                return detail.copy(
                    availableInAccountRegion = accountCountry?.let { false },
                    accountCountryCode = accountCountry,
                    priceCountryCode = countryCode
                )
            }
        }
        throw IllegalStateException("Steam 商店没有返回该商品详情")
    }

    private fun attachReviews(
        detail: SteamStoreDetail,
        appId: Int,
        language: String
    ): SteamStoreDetail = detail.copy(reviews = reviewService.fetch(appId, language))

    fun reviewPage(
        appId: Int,
        cursor: String,
        language: String = "schinese",
        filters: SteamReviewFilterSelection = SteamReviewFilterSelection()
    ): SteamReviewPage = reviewService.fetchPage(
        appId = appId,
        cursor = cursor,
        language = language,
        filters = filters
    )

    private fun requestDetail(
        appId: Int,
        language: String,
        steamLoginSecure: String?,
        countryCode: String?
    ): SteamStoreDetail? {
        val body = get(
            path = "/api/appdetails",
            query = mapOf(
                "appids" to appId.toString(),
                "l" to language
            ),
            steamLoginSecure = steamLoginSecure,
            countryCode = countryCode
        )
        return SteamStoreParser.parseDetail(appId, body)
    }

    fun wishlist(
        steamId: String,
        steamLoginSecure: String?,
        accessToken: String? = null,
        language: String = "schinese"
    ): List<SteamWishlistItem> {
        val accountToken = effectiveSteamStoreAccessToken(accessToken, steamLoginSecure)
            ?: throw SteamStoreWishlistSessionException()
        val country = accountCountryOrFail(steamLoginSecure, accountToken)
        val items = mutableListOf<SteamWishlistItem>()
        val seen = mutableSetOf<Int>()
        repeat(MAX_WISHLIST_PAGES) { page ->
            val pageItems = parseSteamWishlistProtoResponse(
                executeWishlistProtobuf(
                    method = "GetWishlistSortedFiltered",
                    request = buildSteamWishlistProtoRequest(
                        steamId = steamId,
                        startIndex = page * WISHLIST_PAGE_SIZE,
                        pageSize = WISHLIST_PAGE_SIZE,
                        countryCode = country,
                        language = language
                    ),
                    accessToken = accountToken,
                    useGet = true
                )
            )
            val newItems = pageItems.filter { seen.add(it.appId) }
            if (newItems.isEmpty()) return items
            items += newItems
            if (pageItems.size < WISHLIST_PAGE_SIZE) return items
        }
        return items
    }

    fun setWishlist(
        appId: Int,
        add: Boolean,
        steamLoginSecure: String?,
        accessToken: String? = null
    ) {
        val accountToken = effectiveSteamStoreAccessToken(accessToken, steamLoginSecure)
            ?: throw SteamStoreWishlistSessionException()
        executeWishlistProtobuf(
            method = if (add) "AddToWishlist" else "RemoveFromWishlist",
            request = buildSteamWishlistMutationProtoRequest(appId),
            accessToken = accountToken,
            useGet = false
        )
    }

    private fun get(
        path: String,
        query: Map<String, String>,
        steamLoginSecure: String?,
        countryCode: String? = null
    ): String {
        val request = buildSteamStoreRequest(path, query, steamLoginSecure, countryCode)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 403 && steamLoginSecure?.isNotBlank() == true) {
                    throw SteamStoreFamilyViewException()
                }
                if (response.isRedirect) {
                    throw SteamStoreSessionException("Steam 商店会话被重定向，请刷新后重试")
                }
                throw IllegalStateException("Steam 商店请求失败：${response.code}")
            }
            return response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Steam 商店返回空数据")
        }
    }

    private suspend fun getAsync(
        path: String,
        query: Map<String, String>,
        steamLoginSecure: String?,
        countryCode: String?
    ): String = suspendCancellableCoroutine { continuation ->
        val request = buildSteamStoreRequest(path, query, steamLoginSecure, countryCode)
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = runCatching {
                        if (!response.isSuccessful) {
                            if (response.code == 403 && steamLoginSecure?.isNotBlank() == true) {
                                throw SteamStoreFamilyViewException()
                            }
                            if (response.isRedirect) {
                                throw SteamStoreSessionException(
                                    "Steam 商店会话被重定向，请刷新后重试"
                                )
                            }
                            throw IllegalStateException("Steam 商店请求失败：${response.code}")
                        }
                        response.body?.string()?.takeIf(String::isNotBlank)
                            ?: throw IllegalStateException("Steam 商店返回空数据")
                    }
                    result.onSuccess { body ->
                        if (continuation.isActive) continuation.resume(body)
                    }.onFailure { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
        })
    }

    private fun executeWishlistProtobuf(
        method: String,
        request: SteamProtoWriter,
        accessToken: String,
        useGet: Boolean
    ): ByteArray {
        return try {
            api.callProtobuf(
                iface = "IWishlistService",
                method = method,
                request = request,
                accessToken = accessToken,
                useGet = useGet
            )
        } catch (error: SteamApiException) {
            if (error.eResult == 5 || error.eResult == 15 ||
                error.eResult == 401 || error.eResult == 403
            ) {
                throw SteamStoreWishlistSessionException()
            }
            throw IllegalStateException(
                "Steam 愿望单请求失败：${error.message ?: "unknown"}",
                error
            )
        }
    }

    private fun resolveCountryCode(
        steamLoginSecure: String?,
        accessToken: String?
    ): String? {
        val session = steamLoginSecure?.takeIf(String::isNotBlank)
        val accountToken = effectiveSteamStoreAccessToken(accessToken, session)
        val credential = accountToken ?: session ?: return null
        val key = credential.hashCode().toString()
        countryBySession[key]?.let { return it }
        val protobufCountry = accountToken?.let { token ->
            val attempt = runCatching {
                parseSteamStoreAccountCountry(
                    api.callProtobuf(
                        iface = "IStoreService",
                        method = "GetDiscoveryQueueSettings",
                        request = SteamProtoWriter(),
                        accessToken = token,
                        useGet = true
                    )
                )
            }
            attempt.onFailure { error ->
                SteamDiagLogger.append(
                    "store_region account_api_failed type=${error.javaClass.simpleName} " +
                        "result=${(error as? SteamApiException)?.eResult ?: "none"}"
                )
            }
            attempt.getOrNull()?.also { country ->
                SteamDiagLogger.append("store_region resolved source=account_api country=$country")
            }
        }
        val country = protobufCountry ?: session?.let {
            runCatching {
            val request = buildSteamStoreRequest(
                path = "/account/",
                query = emptyMap(),
                    steamLoginSecure = it,
                countryCode = null
            )
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                SteamStoreRegionParser.parseCountryCode(response.body?.string().orEmpty())
            }
            }.getOrNull()
        }
        country?.let { countryBySession[key] = it }
        if (country == null) {
            SteamDiagLogger.append(
                "store_region unresolved access_token_present=${accountToken != null} " +
                    "secure_cookie_present=${session != null}"
            )
        }
        return country
    }

    private fun accountCountryOrFail(
        steamLoginSecure: String?,
        accessToken: String?
    ): String? {
        val session = steamLoginSecure?.takeIf(String::isNotBlank)
        val accountToken = effectiveSteamStoreAccessToken(accessToken, session)
        if (session == null && accountToken == null) return null
        return resolveCountryCode(session, accountToken)
            ?: throw SteamStoreAccountRegionException()
    }

    private companion object {
        const val MAX_WISHLIST_PAGES = 20
        const val WISHLIST_PAGE_SIZE = 100
    }
}

internal fun mergeSteamStoreSearchResults(
    query: String,
    accountCountryCode: String?,
    accountRegionResponded: Boolean,
    regionalResults: List<SteamStoreRegionSearchResult>
): List<SteamStoreItem> {
    val accountCountry = accountCountryCode?.trim()?.uppercase()
    val orderedResults = regionalResults.sortedBy { result ->
        if (accountCountry != null && result.countryCode.equals(accountCountry, true)) 0 else 1
    }
    val accountAppIds = orderedResults
        .firstOrNull { it.countryCode.equals(accountCountry, ignoreCase = true) }
        ?.items
        ?.mapTo(mutableSetOf(), SteamStoreItem::appId)
        .orEmpty()
    val merged = linkedMapOf<Int, SteamStoreItem>()
    orderedResults.forEach { regionalResult ->
        val priceCountry = regionalResult.countryCode?.trim()?.uppercase()
        val accountResult = accountCountry != null && priceCountry == accountCountry
        regionalResult.items.forEach { item ->
            val annotated = item.copy(
                availableInAccountRegion = when {
                    accountCountry == null || !accountRegionResponded -> null
                    item.appId in accountAppIds -> true
                    else -> false
                },
                accountCountryCode = accountCountry,
                priceCountryCode = priceCountry
            )
            if (accountResult) {
                merged[item.appId] = annotated
            } else {
                merged.putIfAbsent(item.appId, annotated)
            }
        }
    }
    return merged.values.sortedWith(
        compareBy<SteamStoreItem> { steamStoreSearchRelevance(query, it.name) }
            .thenBy { if (it.availableInAccountRegion == false) 1 else 0 }
            .thenBy { it.name.lowercase() }
    ).take(MAX_GLOBAL_SEARCH_RESULTS)
}

internal fun steamStoreDetailFallbackCountries(
    accountCountryCode: String?,
    discoveryCountryCode: String?
): List<String> {
    val accountCountry = accountCountryCode?.trim()?.uppercase()
    return buildList {
        discoveryCountryCode?.trim()?.uppercase()?.takeIf { it.length == 2 }?.let(::add)
        addAll(STEAM_STORE_DISCOVERY_COUNTRY_CODES)
    }.filterNot { it == accountCountry }.distinct()
}

private fun steamStoreSearchRelevance(query: String, name: String): Int {
    val normalizedQuery = query.trim().lowercase()
    val normalizedName = name.trim().lowercase()
    return when {
        normalizedName == normalizedQuery -> 0
        normalizedName.startsWith(normalizedQuery) -> 1
        normalizedName.contains(normalizedQuery) -> 2
        normalizedQuery.split(Regex("\\s+")).all(normalizedName::contains) -> 3
        else -> 4
    }
}

private const val MAX_GLOBAL_SEARCH_RESULTS = 48

private fun steamStoreInterestAccount(
    steamId: String?,
    steamLoginSecure: String?,
    accessToken: String?,
    countryCode: String?
): SteamStoreInterestAccount? {
    val resolvedSteamId = steamId?.trim()?.takeIf(String::isNotBlank) ?: return null
    return SteamStoreInterestAccount(
        steamId = resolvedSteamId,
        steamLoginSecure = steamLoginSecure,
        accessToken = accessToken,
        countryCode = countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 } ?: "US"
    )
}

internal fun effectiveSteamStoreAccessToken(
    accessToken: String?,
    steamLoginSecure: String?
): String? = accessToken?.trim()?.takeIf(String::isNotBlank)
    ?: steamLoginSecure
        ?.let(::normalizeSteamCookieValue)
        ?.substringAfter("||", missingDelimiterValue = "")
        ?.trim()
        ?.takeIf(String::isNotBlank)

internal fun parseSteamStoreAccountCountry(response: ByteArray): String? =
    SteamProtoReader(response).parse()[1]
        ?.asString
        ?.trim()
        ?.uppercase()
        ?.takeIf { it.matches(Regex("[A-Z]{2}")) }

internal fun buildSteamStoreRequest(
    path: String,
    query: Map<String, String>,
    steamLoginSecure: String?,
    countryCode: String? = null
): Request {
    require(path.startsWith("/"))
    val url = "https://store.steampowered.com$path".toHttpUrl().newBuilder()
        .apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
            countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 }?.let {
                addQueryParameter("cc", it)
            }
        }
        .build()
    return Request.Builder()
        .url(url)
        .header("User-Agent", "Monica-Steam/1.0")
        .header("Accept", "application/json")
        .apply {
            val cookies = buildList {
                if (path.startsWith("/app/")) {
                    add("birthtime=0")
                    add("lastagecheckage=1-January-1980")
                }
                steamLoginSecure?.takeIf(String::isNotBlank)?.let { value ->
                    add("steamLoginSecure=${encodeSteamCookieValue(value)}")
                    steamIdFromLoginSecure(value)
                        ?.let(SteamFamilyViewSessions::cookieFor)
                        ?.let(::add)
                }
            }
            if (cookies.isNotEmpty()) {
                header("Cookie", cookies.joinToString("; "))
            }
        }
        .get()
        .build()
}

private fun steamIdFromLoginSecure(value: String): String? = normalizeSteamCookieValue(value)
    .substringBefore("||", missingDelimiterValue = "")
    .trim()
    .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

internal fun encodeSteamCookieValue(value: String): String = URLEncoder.encode(
    normalizeSteamCookieValue(value),
    StandardCharsets.UTF_8.name()
).replace("+", "%20")

internal object SteamStoreRegionParser {
    private val countryPatterns = listOf(
        Regex("\\\"wallet_country\\\"\\s*:\\s*\\\"([A-Za-z]{2})\\\""),
        Regex("wallet_country\\s*[=:]\\s*['\\\"]([A-Za-z]{2})['\\\"]"),
        Regex("\\\"country_code\\\"\\s*:\\s*\\\"([A-Za-z]{2})\\\"")
    )

    fun parseCountryCode(html: String): String? = countryPatterns.asSequence()
        .mapNotNull { it.find(html)?.groupValues?.getOrNull(1) }
        .map { it.uppercase() }
        .firstOrNull { it.length == 2 }
}
