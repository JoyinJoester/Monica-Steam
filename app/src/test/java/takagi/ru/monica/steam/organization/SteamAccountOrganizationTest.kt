package takagi.ru.monica.steam.organization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount

class SteamAccountOrganizationTest {
    @Test
    fun searchCoversOrganizationFields() {
        val account = account(
            groupName = "Market",
            tags = listOf("Main", "Trading"),
            note = "Keep for weekend sales"
        )
        val accounts = listOf(account)
        assertEquals(accounts, SteamAccountOrganizer.filter(accounts, "market"))
        assertEquals(accounts, SteamAccountOrganizer.filter(accounts, "trading"))
        assertEquals(accounts, SteamAccountOrganizer.filter(accounts, "weekend sales"))
        assertEquals(emptyList<SteamAccount>(), SteamAccountOrganizer.filter(accounts, "missing"))
    }

    @Test
    fun filtersCanBeCombined() {
        val accounts = listOf(
            account(id = 1L, groupName = "Main", tags = listOf("mobile"), pinned = true),
            account(id = 2L, groupName = "Main", tags = listOf("desktop"), pinned = true),
            account(id = 3L, groupName = "Archive", tags = listOf("mobile"), pinned = true)
        )
        val result = SteamAccountOrganizer.filter(
            accounts,
            query = "",
            filter = SteamAccountOrganizationFilter(
                groupName = "Main",
                tag = "mobile",
                pinnedOnly = true
            )
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun pinnedSortKeepsSortOrderAndIdStable() {
        val accounts = listOf(
            account(id = 10L, sortOrder = 2, pinned = false),
            account(id = 11L, sortOrder = 1, pinned = true),
            account(id = 12L, sortOrder = 1, pinned = true),
            account(id = 13L, sortOrder = 0, pinned = false)
        )
        assertEquals(listOf(11L, 12L, 13L, 10L), SteamAccountOrganizer.sortForDisplay(accounts).map { it.id })
    }

    @Test
    fun tagsNormalizeSeparatorsAndDuplicateCase() {
        assertEquals(
            listOf("Main", "trading", "库存"),
            SteamAccountOrganizationRules.parseTags("Main, trading\n库存，main")
        )
    }

    @Test
    fun validationReportsFieldLimits() {
        val validation = SteamAccountOrganizationRules.validate(
            groupName = "g".repeat(41),
            rawTags = (1..13).joinToString(",") { "tag$it" } + "," + "x".repeat(25),
            note = "n".repeat(301)
        )
        assertTrue(validation.groupTooLong)
        assertTrue(validation.tooManyTags)
        assertTrue(validation.tagTooLong)
        assertTrue(validation.noteTooLong)
        assertFalse(validation.isValid)
    }

    private fun account(
        id: Long = 1L,
        groupName: String? = null,
        tags: List<String> = emptyList(),
        note: String = "",
        pinned: Boolean = false,
        sortOrder: Int = 0
    ): SteamAccount {
        return SteamAccount(
            id = id,
            steamId = "765611980000000$id",
            accountName = "account$id",
            displayName = "Display $id",
            deviceId = "device",
            sharedSecret = "secret",
            identitySecret = null,
            revocationCode = null,
            tokenGid = null,
            accessToken = null,
            refreshToken = null,
            steamLoginSecure = null,
            rawSteamGuardJson = "{}",
            selected = id == 1L,
            sortOrder = sortOrder,
            createdAt = 0L,
            updatedAt = 0L,
            groupName = groupName,
            tags = tags,
            accentArgb = null,
            note = note,
            pinned = pinned
        )
    }
}
