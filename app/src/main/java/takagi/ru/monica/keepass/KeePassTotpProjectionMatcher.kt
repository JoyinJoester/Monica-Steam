package takagi.ru.monica.keepass

import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem

object KeePassTotpProjectionMatcher {
    fun findExistingProjection(
        databaseId: Long,
        incoming: SecureItem,
        existingTotp: List<SecureItem>,
        existingByUuid: SecureItem?,
        existingBySource: SecureItem?,
        incomingIdentityKey: String?,
        identityKeyOf: (SecureItem) -> String?
    ): SecureItem? {
        existingByUuid
            ?.takeIf { it.itemType == ItemType.TOTP }
            ?.let { return it }

        existingBySource
            ?.takeIf { it.itemType == ItemType.TOTP }
            ?.let { return it }

        incomingIdentityKey?.let { identityKey ->
            existingTotp.firstOrNull { candidate ->
                candidate.itemType == ItemType.TOTP &&
                    candidate.keepassDatabaseId == databaseId &&
                    candidate.keepassEntryUuid.isNullOrBlank() &&
                    identityKeyOf(candidate) == identityKey
            }?.let { return it }
        }

        return existingTotp.firstOrNull {
            it.itemType == ItemType.TOTP &&
                it.keepassDatabaseId == databaseId &&
                it.keepassGroupPath == incoming.keepassGroupPath &&
                it.title == incoming.title
        }
    }
}
