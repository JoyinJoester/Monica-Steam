package takagi.ru.monica.steam.store.purchase.domain

import kotlinx.serialization.Serializable

@Serializable
data class SteamStorePackageOption(
    val packageId: Int,
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val priceCents: Int? = null,
    val discountPercent: Int = 0,
    val isFreeLicense: Boolean = false,
    val canGetFreeLicense: Boolean = false,
    val discountEndEpochSeconds: Long? = null
)

@Serializable
data class SteamStoreDemo(
    val appId: Int,
    val description: String = ""
)

@Serializable
data class SteamStoreBaseGame(
    val appId: Int,
    val name: String = ""
)

@Serializable
enum class SteamStoreOwnershipStatus {
    UNKNOWN,
    NOT_OWNED,
    OWNED,
    FAMILY_SHARED
}

@Serializable
enum class SteamStorePurchaseContextFailure {
    SESSION_REQUIRED,
    RATE_LIMITED,
    NETWORK,
    INVALID_RESPONSE
}

@Serializable
data class SteamStorePurchaseContext(
    val accountSteamId: String,
    val appId: Int,
    val ownership: SteamStoreOwnershipStatus,
    val familyGroupId: Long? = null,
    val ownerSteamIds: List<String> = emptyList(),
    val failure: SteamStorePurchaseContextFailure? = null,
    val fetchedAt: Long = System.currentTimeMillis()
)

fun interface SteamStorePurchaseContextGateway {
    fun fetch(
        account: takagi.ru.monica.steam.data.SteamAccount,
        appId: Int,
        language: String
    ): SteamStorePurchaseContext
}
