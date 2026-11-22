package takagi.ru.monica.steam.library.collections

import kotlinx.serialization.json.*

internal const val STEAM_COLLECTION_PREFIX = "user-collections."

data class SteamCloudCollectionEntry(
    val key: String,
    val value: String,
    val deleted: Boolean,
    val timestamp: Long,
    val version: Long
)

data class SteamCollection(
    val entry: SteamCloudCollectionEntry,
    val name: String,
    val members: Set<Int>,
    val dynamic: Boolean,
    val editable: Boolean
) {
    val id: String get() = entry.key.removePrefix(STEAM_COLLECTION_PREFIX)
}

data class SteamCollectionsSnapshot(
    val version: Long,
    val entries: List<SteamCloudCollectionEntry>
) {
    val collections: List<SteamCollection> = entries.mapNotNull(::parseSteamCollection)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    // An unreadable ordinary collection may contain any game: do not invent "unfiled" results.
    val canIdentifyUnfiled: Boolean get() = collections.all { it.dynamic || it.editable }
}

private fun JsonObject.appIds(key: String): Set<Int>? {
    val element = this[key] ?: return emptySet()
    val array = element as? JsonArray ?: return null
    val ids = array.map { (it as? JsonPrimitive)?.takeUnless { p -> p.isString }?.intOrNull }
    if (ids.any { it == null || it <= 0 }) return null
    return ids.filterNotNull().toSet()
}

internal fun parseSteamCollection(entry: SteamCloudCollectionEntry): SteamCollection? {
    if (entry.deleted || !entry.key.startsWith(STEAM_COLLECTION_PREFIX)) return null
    val id = entry.key.removePrefix(STEAM_COLLECTION_PREFIX)
    if (id.isBlank()) return null
    val json = runCatching { Json.parseToJsonElement(entry.value) as? JsonObject }.getOrNull()
    val name = (json?.get("name") as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?.takeIf { it.isNotBlank() } ?: id
    val added = json?.appIds("added")
    val removed = json?.appIds("removed")
    val dynamic = json?.get("filterSpec")?.let { it != JsonNull } == true
    val validId = (json?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content == id
    val editable = json != null && validId && json["added"] is JsonArray &&
        added != null && removed != null && !dynamic
    return SteamCollection(entry, name, added.orEmpty() - removed.orEmpty(), dynamic, editable)
}

internal sealed interface SteamCollectionChange {
    data class Create(val name: String) : SteamCollectionChange
    data class Rename(val reviewed: SteamCloudCollectionEntry, val name: String) : SteamCollectionChange
    data class Membership(val reviewed: SteamCloudCollectionEntry, val changes: Map<Int, Boolean>) : SteamCollectionChange
    data class Delete(val reviewed: SteamCloudCollectionEntry) : SteamCollectionChange
}

enum class SteamCollectionsFailure { LOGIN, UNAVAILABLE, NETWORK, CONFLICT, INVALID, UNKNOWN }

data class SteamCollectionsState(
    val snapshot: SteamCollectionsSnapshot? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val failure: SteamCollectionsFailure? = null,
    val saved: Boolean = false
)

internal fun collectionGames(
    appIds: Set<Int>, selected: String?, snapshot: SteamCollectionsSnapshot?
): Set<Int> = when {
    selected == null -> appIds
    selected == "" -> if (snapshot?.canIdentifyUnfiled == true) appIds -
        snapshot.collections.filterNot { it.dynamic }.flatMap { it.members }.toSet() else emptySet()
    else -> appIds.intersect(snapshot?.collections?.firstOrNull { it.id == selected && it.editable }?.members.orEmpty())
}

internal fun changedCollectionValue(original: SteamCollection, change: SteamCollectionChange): String {
    require(original.editable)
    val json = Json.parseToJsonElement(original.entry.value).jsonObject
    return JsonObject(json.toMutableMap().apply {
        when (change) {
            is SteamCollectionChange.Rename -> put("name", JsonPrimitive(validCollectionName(change.name)))
            is SteamCollectionChange.Membership -> {
                require(change.changes.keys.all { it > 0 })
                val added = json.appIds("added")!!.toMutableSet()
                val removed = json.appIds("removed")!!.toMutableSet()
                change.changes.forEach { (id, include) ->
                    if (include) { added.add(id); removed.remove(id) }
                    else { added.remove(id); removed.add(id) }
                }
                put("added", JsonArray(added.sorted().map(::JsonPrimitive)))
                if (removed.isNotEmpty() || json.containsKey("removed"))
                    put("removed", JsonArray(removed.sorted().map(::JsonPrimitive)))
            }
            else -> error("Not an edit")
        }
    }).toString()
}

internal fun validCollectionName(name: String): String = name.trim().also {
    require(it.isNotEmpty() && it.length <= 128 && it.none { c -> c.isISOControl() })
}
