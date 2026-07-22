package takagi.ru.monica.keepass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem

class KeePassCrossDatabaseTransferTest {
    @Test
    fun bindPasswordKeepsExistingKeePassUuidWhenTargetDatabaseMatches() {
        val source = password(
            keepassDatabaseId = TARGET_DATABASE_ID,
            keepassEntryUuid = "existing-entry",
            bitwardenVaultId = 8L,
            bitwardenCipherId = "cipher",
            bitwardenLocalModified = true
        )

        val bound = KeePassCrossDatabaseTransfer.bindPasswordToTarget(
            entry = source,
            databaseId = TARGET_DATABASE_ID,
            groupPath = "Archive"
        )

        assertEquals("existing-entry", bound.keepassEntryUuid)
        assertEquals(TARGET_DATABASE_ID, bound.keepassDatabaseId)
        assertEquals("Archive", bound.keepassGroupPath)
        assertNull(bound.bitwardenVaultId)
        assertNull(bound.bitwardenCipherId)
        assertEquals(false, bound.bitwardenLocalModified)
    }

    @Test
    fun forcedPasswordTargetUuidIsStableForRetriesAndGroupAware() {
        val source = password(id = 99L)

        val first = KeePassCrossDatabaseTransfer.bindPasswordToTarget(
            entry = source,
            databaseId = TARGET_DATABASE_ID,
            groupPath = "Archive",
            forceNewEntryUuid = true
        )
        val retry = KeePassCrossDatabaseTransfer.bindPasswordToTarget(
            entry = source,
            databaseId = TARGET_DATABASE_ID,
            groupPath = "Archive",
            forceNewEntryUuid = true
        )
        val differentGroup = KeePassCrossDatabaseTransfer.bindPasswordToTarget(
            entry = source,
            databaseId = TARGET_DATABASE_ID,
            groupPath = "Inbox",
            forceNewEntryUuid = true
        )

        assertEquals(first.keepassEntryUuid, retry.keepassEntryUuid)
        assertNotEquals(first.keepassEntryUuid, differentGroup.keepassEntryUuid)
    }

    @Test
    fun secureItemTargetUuidReusesExistingUuidForSameDatabase() {
        val item = secureItem(
            keepassDatabaseId = TARGET_DATABASE_ID,
            keepassEntryUuid = "existing-secure-item"
        )

        val targetUuid = KeePassCrossDatabaseTransfer.secureItemTargetEntryUuid(
            item = item,
            databaseId = TARGET_DATABASE_ID,
            groupPath = "Cards"
        )

        assertEquals("existing-secure-item", targetUuid)
    }

    @Test
    fun secureItemTargetUuidIsStableAndTypeAwareForRetries() {
        val card = secureItem(id = 51L, itemType = ItemType.BANK_CARD)
        val document = secureItem(id = 51L, itemType = ItemType.DOCUMENT)

        val first = KeePassCrossDatabaseTransfer.secureItemTargetEntryUuid(
            item = card,
            databaseId = TARGET_DATABASE_ID,
            groupPath = "Wallet"
        )
        val retry = KeePassCrossDatabaseTransfer.secureItemTargetEntryUuid(
            item = card,
            databaseId = TARGET_DATABASE_ID,
            groupPath = "Wallet"
        )
        val differentType = KeePassCrossDatabaseTransfer.secureItemTargetEntryUuid(
            item = document,
            databaseId = TARGET_DATABASE_ID,
            groupPath = "Wallet"
        )

        assertEquals(first, retry)
        assertNotEquals(first, differentType)
    }

    private fun password(
        id: Long = 1L,
        keepassDatabaseId: Long? = null,
        keepassEntryUuid: String? = null,
        bitwardenVaultId: Long? = null,
        bitwardenCipherId: String? = null,
        bitwardenLocalModified: Boolean = false
    ): PasswordEntry {
        return PasswordEntry(
            id = id,
            title = "Example",
            website = "https://example.com",
            username = "alice",
            password = "secret",
            keepassDatabaseId = keepassDatabaseId,
            keepassEntryUuid = keepassEntryUuid,
            bitwardenVaultId = bitwardenVaultId,
            bitwardenCipherId = bitwardenCipherId,
            bitwardenLocalModified = bitwardenLocalModified
        )
    }

    private fun secureItem(
        id: Long = 1L,
        itemType: ItemType = ItemType.TOTP,
        keepassDatabaseId: Long? = null,
        keepassEntryUuid: String? = null
    ): SecureItem {
        return SecureItem(
            id = id,
            itemType = itemType,
            title = "Secure item",
            itemData = "{}",
            keepassDatabaseId = keepassDatabaseId,
            keepassEntryUuid = keepassEntryUuid
        )
    }

    private companion object {
        const val TARGET_DATABASE_ID = 7L
    }
}
