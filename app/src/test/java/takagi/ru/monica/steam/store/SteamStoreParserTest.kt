package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreParserTest {
    @Test
    fun parsesFeaturedSearchAndDetailPayloads() {
        val featured = SteamStoreParser.parseFeatured(
            """{"specials":{"name":"优惠","items":[{"id":620,"name":"Portal 2","discount_percent":50,"original_price":4200,"final_price":2100,"currency":"CNY","large_capsule_image":"hero.jpg","header_image":"header.jpg"}]},"top_sellers":{"name":"热销商品","items":[]},"new_releases":{"name":"新品","items":[]}}"""
        )
        assertEquals(1, featured.specials.size)
        assertEquals(2100, featured.specials.single().finalPriceCents)
        assertEquals("¥21.00", featured.specials.single().formattedFinalPrice)

        val search = SteamStoreParser.parseSearch(
            """{"total":1,"items":[{"type":"app","name":"Portal 2","id":620,"price":{"currency":"CNY","initial":4200,"final":4200},"tiny_image":"tiny.jpg","platforms":{"windows":true,"mac":false,"linux":true}}]}"""
        )
        assertEquals(620, search.single().appId)
        assertTrue(search.single().linux)

        val detail = SteamStoreParser.parseDetail(
            appId = 620,
            payload = """{"620":{"success":true,"data":{"type":"game","name":"Portal 2","steam_appid":620,"short_description":"Puzzle","header_image":"header.jpg","price_overview":{"currency":"CNY","initial":4200,"final":2100,"discount_percent":50},"package_groups":[{"subs":[{"packageid":1234}]}],"developers":["Valve"],"publishers":["Valve"],"genres":[{"description":"冒险"}],"screenshots":[{"path_full":"shot.jpg"}],"release_date":{"date":"2011 年 4 月 19 日"}}}}"""
        )
        assertEquals("Portal 2", detail?.name)
        assertEquals(listOf("Valve"), detail?.developers)
        assertEquals("shot.jpg", detail?.screenshots?.single())
        assertEquals(1234, detail?.packageId)
    }

    @Test
    fun formatsCommonAccountCurrencies() {
        assertEquals("NT$75.60", formatSteamPrice(7560, "TWD"))
        assertEquals("£12.34", formatSteamPrice(1234, "GBP"))
        assertEquals("₩1234", formatSteamPrice(123400, "KRW"))
        assertEquals("HK$45.67", formatSteamPrice(4567, "HKD"))
    }
}
