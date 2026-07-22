package takagi.ru.monica.keepass

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem

class KeePassTotpProjectionMatcherTest {
    @Test
    fun legacyProjectionWithoutEntryUuidMatchesByTotpIdentityBeforeTitlePathFallback() {
        val incoming = totp(
            id = 0,
            title = "Renamed account",
            groupPath = "Root/New",
            entryUuid = "entry-uuid",
            itemData = "same-secret-identity"
        )
        val legacyProjection = totp(
            id = 10,
            title = "Old account",
            groupPath = "Root/Old",
            entryUuid = null,
            itemData = "same-secret-identity"
        )
        val titlePathFallback = totp(
            id = 11,
            title = "Renamed account",
            groupPath = "Root/New",
            entryUuid = null,
            itemData = "different-secret-identity"
        )

        val matched = KeePassTotpProjectionMatcher.findExistingProjection(
            databaseId = DATABASE_ID,
            incoming = incoming,
            existingTotp = listOf(titlePathFallback, legacyProjection),
            existingByUuid = null,
            existingBySource = null,
            incomingIdentityKey = "same-secret-identity",
            identityKeyOf = SecureItem::itemData
        )

        assertEquals(legacyProjection.id, matched?.id)
    }

    @Test
    fun sourceEntryUuidWinsOverLegacyIdentityMatch() {
        val incoming = totp(
            id = 0,
            title = "Account",
            groupPath = "Root",
            entryUuid = "entry-uuid",
            itemData = "same-secret-identity"
        )
        val uuidMatch = totp(
            id = 20,
            title = "Account",
            groupPath = "Root",
            entryUuid = "entry-uuid",
            itemData = "changed-secret-identity"
        )
        val legacyIdentityMatch = totp(
            id = 21,
            title = "Old account",
            groupPath = "Root/Old",
            entryUuid = null,
            itemData = "same-secret-identity"
        )

        val matched = KeePassTotpProjectionMatcher.findExistingProjection(
            databaseId = DATABASE_ID,
            incoming = incoming,
            existingTotp = listOf(legacyIdentityMatch),
            existingByUuid = uuidMatch,
            existingBySource = null,
            incomingIdentityKey = "same-secret-identity",
            identityKeyOf = SecureItem::itemData
        )

        assertEquals(uuidMatch.id, matched?.id)
    }

    @Test
    fun titlePathFallbackIsOnlyUsedWhenIdentityCannotMatch() {
        val incoming = totp(
            id = 0,
            title = "Account",
            groupPath = "Root",
            entryUuid = "entry-uuid",
            itemData = "incoming-secret"
        )
        val titlePathFallback = totp(
            id = 30,
            title = "Account",
            groupPath = "Root",
            entryUuid = null,
            itemData = "different-secret"
        )

        val matched = KeePassTotpProjectionMatcher.findExistingProjection(
            databaseId = DATABASE_ID,
            incoming = incoming,
            existingTotp = listOf(titlePathFallback),
            existingByUuid = null,
            existingBySource = null,
            incomingIdentityKey = "incoming-secret",
            identityKeyOf = SecureItem::itemData
        )

        assertEquals(titlePathFallback.id, matched?.id)
    }

    private fun totp(
        id: Long,
        title: String,
        groupPath: String,
        entryUuid: String?,
        itemData: String
    ): SecureItem {
        return SecureItem(
            id = id,
            itemType = ItemType.TOTP,
            title = title,
            itemData = itemData,
            keepassDatabaseId = DATABASE_ID,
            keepassGroupPath = groupPath,
            keepassEntryUuid = entryUuid
        )
    }

    private companion object {
        const val DATABASE_ID = 7L
    }
}
