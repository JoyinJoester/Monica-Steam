package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.store.data.catalogCacheName
import takagi.ru.monica.steam.store.data.steamRegionalPriceCacheName
import takagi.ru.monica.steam.store.data.steamStoreDetailCacheName
import takagi.ru.monica.steam.store.data.steamStoreHomeCacheName
import takagi.ru.monica.steam.store.data.steamWishlistCacheName
import takagi.ru.monica.steam.store.domain.SteamStoreBrowseFilter
import takagi.ru.monica.steam.store.filters.domain.SteamStoreFilterSelection

class SteamStoreCacheVersionTest {
    @Test
    fun storeContentCachesDropLegacyEntriesThatDidNotApplyIgnoredGames() {
        assertEquals("v3_account_7_home_games_v2.json", steamStoreHomeCacheName(7L))
        assertEquals(
            "v3_account_7_catalog_all.json",
            catalogCacheName(
                accountId = 7L,
                filter = SteamStoreBrowseFilter.ALL,
                filters = SteamStoreFilterSelection()
            )
        )
        assertEquals("v3_account_7_detail_620.json", steamStoreDetailCacheName(7L, 620))
        assertEquals("v3_guest_home_games_v2.json", steamStoreHomeCacheName(null))
    }

    @Test
    fun unrelatedAccountDataCachesKeepTheirExistingNamespace() {
        assertEquals("v2_account_7_wishlist.json", steamWishlistCacheName(7L))
        assertEquals(
            "v2_account_7_regional_prices_620.json",
            steamRegionalPriceCacheName(7L, 620)
        )
    }
}
