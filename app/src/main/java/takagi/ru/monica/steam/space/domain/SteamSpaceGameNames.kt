package takagi.ru.monica.steam.space.domain

/** A library name may fill a missing label, but never changes event identity or status text. */
internal fun SteamSpaceEvent.withKnownGameNames(names: Map<Int, String>): SteamSpaceEvent {
    if (kind == SteamSpaceEventKind.UNKNOWN || appIds.isEmpty()) return this
    val merged = appIds.mapNotNull { id ->
        (appNames[id]?.takeIf(String::isNotBlank) ?: names[id]?.trim()?.takeIf { it.isNotBlank() && it.length <= 512 })
            ?.let { id to it }
    }.toMap()
    if (merged == appNames) return this
    return copy(appNames = merged, title = if (kind in GAME_TITLE_KINDS)
        merged.values.takeIf { it.isNotEmpty() }?.joinToString("\n") else title)
}

private val GAME_TITLE_KINDS = setOf(SteamSpaceEventKind.GAME_ADDED, SteamSpaceEventKind.WISHLISTED,
    SteamSpaceEventKind.FIRST_PLAY, SteamSpaceEventKind.REVIEW)
