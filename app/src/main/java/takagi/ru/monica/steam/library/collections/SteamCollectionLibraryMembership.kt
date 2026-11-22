package takagi.ru.monica.steam.library.collections

import takagi.ru.monica.steam.library.SteamLibrarySnapshot

internal fun SteamLibrarySnapshot.allowsCollectionMembership(accountId: Long, appIds: Set<Int>): Boolean {
    if (this.accountId != accountId) return false
    // Collections belong to this account; available family games can be filed like owned games.
    val available = games.mapTo(hashSetOf()) { it.appId }
    return appIds.all { it > 0 && it in available }
}
