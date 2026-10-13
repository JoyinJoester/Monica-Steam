package takagi.ru.monica.steam.workshop

import takagi.ru.monica.steam.data.SteamAccount

internal enum class WorkshopSort(val wire: String) {
    POPULAR("trend"), MOST_SUBSCRIBED("totaluniquesubscribers"),
    NEWEST("mostrecent"), UPDATED("lastupdated")
}
internal data class WorkshopQuery(
    val search: String = "",
    val sort: WorkshopSort = WorkshopSort.POPULAR,
    val days: Int = 7,
    val tags: Set<String> = emptySet(),
    val subscribedOnly: Boolean = false
)
internal data class WorkshopTag(val value: String, val label: String, val group: String = "")
internal data class WorkshopItem(
    val id: String,
    val appId: Int,
    val title: String,
    val preview: String = "",
    val author: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val subscriptions: Long = 0,
    val updated: Long = 0,
    val created: Long = 0,
    val size: Long = 0,
    val fileType: Int = 0,
    val canSubscribe: Boolean = false,
    val subscribed: Boolean? = null,
    val children: List<String> = emptyList(),
    val dependencies: List<WorkshopItem> = emptyList(),
    val childCount: Int = 0,
    val previews: List<String> = emptyList(),
    val banned: Boolean = false,
    val incompatible: Boolean = false
) {
    val url: String get() = "https://steamcommunity.com/sharedfiles/filedetails/?id=$id"
}
internal data class WorkshopBatch(
    val items: List<WorkshopItem>,
    val page: Int,
    val hasMore: Boolean,
    val total: Int,
    val tags: List<WorkshopTag> = emptyList(),
    val subscriptionIds: List<String> = items.map { it.id }
)
@kotlinx.serialization.Serializable
internal enum class WorkshopFailure { LOGIN, RATE_LIMIT, UNAVAILABLE, NETWORK, INVALID_RESPONSE, UNCONFIRMED, BACKGROUND, SUBSCRIPTIONS_CHANGED }
internal class WorkshopException(val reason: WorkshopFailure) : Exception(reason.name)
internal interface SteamWorkshopGateway {
    suspend fun supportsWorkshop(appId: Int): Boolean
    suspend fun browse(account: SteamAccount?, appId: Int, query: WorkshopQuery, page: Int): WorkshopBatch
    suspend fun details(account: SteamAccount, appId: Int, id: String): WorkshopItem
    suspend fun publicDetails(appId: Int, id: String): WorkshopItem = throw WorkshopException(WorkshopFailure.LOGIN)
    suspend fun inspectItems(account: SteamAccount, appId: Int, ids: List<String>): List<WorkshopItem> =
        ids.mapNotNull { id ->
            try { details(account, appId, id) }
            catch (error: WorkshopException) {
                if (error.reason != WorkshopFailure.UNAVAILABLE) throw error
                null
            }
        }
    suspend fun setSubscription(account: SteamAccount, item: WorkshopItem, subscribe: Boolean, dependencies: Boolean)
}
internal fun WorkshopItem.matchesSearch(search: String): Boolean =
    search.isBlank() || title.contains(search.trim(), ignoreCase = true) ||
        description.contains(search.trim(), ignoreCase = true) || tags.any { it.contains(search.trim(), true) }

internal fun WorkshopQuery.scansSubscriptions(): Boolean =
    subscribedOnly && (search.isNotBlank() || sort != WorkshopSort.UPDATED)

internal fun sortSubscriptions(items: List<WorkshopItem>, sort: WorkshopSort): List<WorkshopItem> =
    items.sortedWith(workshopSubscriptionComparator(sort))

internal fun workshopSubscriptionComparator(sort: WorkshopSort): Comparator<WorkshopItem> =
    compareByDescending<WorkshopItem> {
        when (sort) {
            WorkshopSort.MOST_SUBSCRIBED -> it.subscriptions
            WorkshopSort.NEWEST -> it.created
            else -> it.updated
        }
    }.thenBy { it.id }
