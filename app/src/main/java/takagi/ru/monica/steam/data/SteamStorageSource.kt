package takagi.ru.monica.steam.data

sealed interface SteamStorageSource {
    data object Local : SteamStorageSource
    data class Mdbx(val databaseId: Long) : SteamStorageSource
}
