package takagi.ru.monica.keepass

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.modifiers.modifyParentGroup
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Group
import app.keemobile.kotpass.models.Meta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class KeePassRecycleBinApplierTest {
    @Test
    fun moveToRecycleBinAndRestoreUseKdbxStructureNotRoomSoftDelete() {
        val accountsUuid = UUID.randomUUID()
        val recycleUuid = UUID.randomUUID()
        val entryUuid = UUID.randomUUID()
        val database = databaseWithAccountsAndRecycleBin(accountsUuid, recycleUuid, entryUuid)
        val applier = KeePassChangeSetApplier()

        val movedDatabase = applier.apply(
            database,
            recycleMoveChangeSet(
                entryUuid = entryUuid,
                sourceGroupUuid = accountsUuid,
                recycleGroupUuid = recycleUuid
            )
        ).updatedDatabase

        assertTrue(findGroup(movedDatabase.content.group, accountsUuid)!!.entries.none { it.uuid == entryUuid })
        val recycledEntry = findGroup(movedDatabase.content.group, recycleUuid)!!.entries.single { it.uuid == entryUuid }
        assertEquals(accountsUuid, recycledEntry.previousParentGroup)

        val restoredDatabase = applier.apply(
            movedDatabase,
            recycleRestoreChangeSet(
                entryUuid = entryUuid,
                targetGroupUuid = accountsUuid,
                recycleGroupUuid = recycleUuid
            )
        ).updatedDatabase

        assertTrue(findGroup(restoredDatabase.content.group, recycleUuid)!!.entries.none { it.uuid == entryUuid })
        val restoredEntry = findGroup(restoredDatabase.content.group, accountsUuid)!!.entries.single { it.uuid == entryUuid }
        assertNull(restoredEntry.previousParentGroup)
        assertEquals("octocat", restoredEntry.fields.getValue("UserName").content)
    }

    @Test(expected = IllegalArgumentException::class)
    fun restoreFromRecycleBinRejectsEntryOutsideRecycleBin() {
        val accountsUuid = UUID.randomUUID()
        val recycleUuid = UUID.randomUUID()
        val entryUuid = UUID.randomUUID()
        val database = databaseWithAccountsAndRecycleBin(accountsUuid, recycleUuid, entryUuid)

        KeePassChangeSetApplier().apply(
            database,
            recycleRestoreChangeSet(
                entryUuid = entryUuid,
                targetGroupUuid = accountsUuid,
                recycleGroupUuid = recycleUuid
            )
        )
    }

    private fun databaseWithAccountsAndRecycleBin(
        accountsUuid: UUID,
        recycleUuid: UUID,
        entryUuid: UUID
    ): KeePassDatabase {
        return KeePassDatabase.Ver4x.create(
            rootName = "Root",
            meta = Meta(generator = "Monica recycle test", name = "Recycle fixture"),
            credentials = Credentials.from(EncryptedValue.fromString("fixture-password"))
        ).modifyParentGroup {
            copy(
                groups = groups + Group(
                    uuid = accountsUuid,
                    name = "Accounts",
                    entries = listOf(
                        Entry(
                            uuid = entryUuid,
                            fields = EntryFields.of(
                                "Title" to EntryValue.Plain("GitHub"),
                                "UserName" to EntryValue.Plain("octocat")
                            )
                        )
                    )
                ) + Group(
                    uuid = recycleUuid,
                    name = "Recycle Bin"
                )
            )
        }
    }

    private fun recycleMoveChangeSet(
        entryUuid: UUID,
        sourceGroupUuid: UUID,
        recycleGroupUuid: UUID
    ): KeePassChangeSet {
        return KeePassChangeSet(
            changeId = "move-to-recycle",
            databaseId = DATABASE_ID,
            target = KeePassChangeTarget.PASSWORD,
            operation = KeePassChangeOperation.MOVE_TO_RECYCLE_BIN,
            entryUuid = entryUuid.toString(),
            baseFingerprint = "base-fingerprint",
            structurePatch = KeePassStructureChangePatch(
                sourceGroupPath = "Accounts",
                sourceGroupUuid = sourceGroupUuid.toString(),
                targetGroupPath = "Recycle Bin",
                targetGroupUuid = recycleGroupUuid.toString(),
                recycleBinGroupUuid = recycleGroupUuid.toString(),
                previousParentGroupUuid = sourceGroupUuid.toString()
            )
        )
    }

    private fun recycleRestoreChangeSet(
        entryUuid: UUID,
        targetGroupUuid: UUID,
        recycleGroupUuid: UUID
    ): KeePassChangeSet {
        return KeePassChangeSet(
            changeId = "restore-from-recycle",
            databaseId = DATABASE_ID,
            target = KeePassChangeTarget.PASSWORD,
            operation = KeePassChangeOperation.RESTORE_FROM_RECYCLE_BIN,
            entryUuid = entryUuid.toString(),
            baseFingerprint = "trash-fingerprint",
            structurePatch = KeePassStructureChangePatch(
                sourceGroupPath = "Recycle Bin",
                sourceGroupUuid = recycleGroupUuid.toString(),
                targetGroupPath = "Accounts",
                targetGroupUuid = targetGroupUuid.toString(),
                recycleBinGroupUuid = recycleGroupUuid.toString(),
                previousParentGroupUuid = targetGroupUuid.toString()
            )
        )
    }

    private fun findGroup(group: Group, uuid: UUID): Group? {
        if (group.uuid == uuid) return group
        group.groups.forEach { child ->
            val match = findGroup(child, uuid)
            if (match != null) return match
        }
        return null
    }

    private companion object {
        const val DATABASE_ID = 42L
    }
}
