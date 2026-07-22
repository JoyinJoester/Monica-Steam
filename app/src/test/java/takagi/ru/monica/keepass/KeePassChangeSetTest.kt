package takagi.ru.monica.keepass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeePassChangeSetTest {

    @Test
    fun createEntryChangeSetRoundTripsWithEntryPatch() {
        val changeSet = KeePassChangeSet(
            changeId = "create-1",
            databaseId = 42,
            target = KeePassChangeTarget.PASSWORD,
            operation = KeePassChangeOperation.CREATE_ENTRY,
            entryUuid = "entry-uuid",
            baseFingerprint = null,
            entryPatch = KeePassEntryCreatePatch(
                targetGroupPath = "Accounts",
                targetGroupUuid = "group-uuid",
                fields = listOf(
                    KeePassFieldChange(name = "Title", value = "New Login"),
                    KeePassFieldChange(name = "Password", value = "secret", protected = true)
                ),
                iconName = "Key"
            )
        )

        val decoded = KeePassChangeSetCodec.decode(KeePassChangeSetCodec.encode(changeSet))

        assertEquals(changeSet, decoded)
        assertTrue(decoded.isEntryScoped())
        assertFalse(decoded.requiresBaseFingerprint())
        assertEquals("group-uuid", decoded.entryPatch!!.targetGroupUuid)
    }

    @Test
    fun fieldPatchChangeSetRoundTripsWithBaseFingerprintAndEntryUuid() {
        val changeSet = KeePassChangeSet(
            changeId = "change-1",
            databaseId = 42,
            target = KeePassChangeTarget.PASSWORD,
            operation = KeePassChangeOperation.FIELD_PATCH,
            entryUuid = "entry-uuid",
            baseFingerprint = "base-fingerprint",
            fieldPatch = KeePassFieldChangePatch(
                managedScope = KeePassManagedFieldScope.PASSWORD,
                replacementFields = listOf(
                    KeePassFieldChange(name = "Title", value = "Renamed"),
                    KeePassFieldChange(name = "Password", value = "secret", protected = true)
                ),
                removeFieldNames = listOf("Old Monica Field")
            )
        )

        val decoded = KeePassChangeSetCodec.decode(KeePassChangeSetCodec.encode(changeSet))

        assertEquals(changeSet, decoded)
        assertTrue(decoded.isEntryScoped())
        assertTrue(decoded.requiresBaseFingerprint())
        assertFalse(decoded.isTrashOperation())
    }

    @Test
    fun trashMoveIsASeparateStructureChange() {
        val changeSet = KeePassChangeSet(
            changeId = "trash-1",
            databaseId = 42,
            target = KeePassChangeTarget.PASSWORD,
            operation = KeePassChangeOperation.MOVE_TO_RECYCLE_BIN,
            entryUuid = "entry-uuid",
            baseFingerprint = "base-fingerprint",
            baseGroupPath = "Accounts",
            baseGroupUuid = "group-uuid",
            structurePatch = KeePassStructureChangePatch(
                sourceGroupPath = "Accounts",
                sourceGroupUuid = "group-uuid",
                targetGroupPath = "Recycle Bin",
                targetGroupUuid = "recycle-group-uuid",
                recycleBinGroupUuid = "recycle-group-uuid",
                previousParentGroupUuid = "group-uuid"
            )
        )

        val decoded = KeePassChangeSetCodec.decode(KeePassChangeSetCodec.encode(changeSet))

        assertEquals(KeePassChangeOperation.MOVE_TO_RECYCLE_BIN, decoded.operation)
        assertTrue(decoded.isTrashOperation())
        assertTrue(decoded.structurePatch!!.isRecycleBinMove())
        assertFalse(decoded.structurePatch.isRecycleBinRestore())
    }

    @Test
    fun trashRestoreIsNotModeledAsAFieldPatch() {
        val changeSet = KeePassChangeSet(
            changeId = "restore-1",
            databaseId = 42,
            target = KeePassChangeTarget.SECURE_ITEM,
            operation = KeePassChangeOperation.RESTORE_FROM_RECYCLE_BIN,
            entryUuid = "entry-uuid",
            baseFingerprint = "trash-fingerprint",
            structurePatch = KeePassStructureChangePatch(
                sourceGroupPath = "Recycle Bin",
                sourceGroupUuid = "recycle-group-uuid",
                targetGroupPath = "Cards",
                targetGroupUuid = "target-group-uuid",
                recycleBinGroupUuid = "recycle-group-uuid",
                previousParentGroupUuid = "target-group-uuid"
            )
        )

        assertEquals(null, changeSet.fieldPatch)
        assertTrue(changeSet.isTrashOperation())
        assertTrue(changeSet.structurePatch!!.isRecycleBinRestore())
    }

    @Test
    fun groupCreateChangeSetRoundTripsWithoutBaseFingerprint() {
        val changeSet = KeePassChangeSet(
            changeId = "group-create-1",
            databaseId = 42,
            target = KeePassChangeTarget.GROUP,
            operation = KeePassChangeOperation.CREATE_GROUP,
            entryUuid = null,
            baseFingerprint = null,
            baseGroupUuid = "parent-group-uuid",
            structurePatch = KeePassStructureChangePatch(
                sourceGroupPath = "Accounts/Work",
                sourceGroupUuid = "new-group-uuid",
                targetGroupPath = "Accounts",
                targetGroupUuid = "parent-group-uuid",
                groupName = "Work"
            )
        )

        val decoded = KeePassChangeSetCodec.decode(KeePassChangeSetCodec.encode(changeSet))

        assertEquals(changeSet, decoded)
        assertFalse(decoded.isEntryScoped())
        assertFalse(decoded.requiresBaseFingerprint())
        assertEquals("Work", decoded.structurePatch!!.groupName)
    }

    @Test
    fun groupRenameAndDeleteAreStructureChanges() {
        val rename = KeePassChangeSet(
            changeId = "group-rename-1",
            databaseId = 42,
            target = KeePassChangeTarget.GROUP,
            operation = KeePassChangeOperation.RENAME_GROUP,
            entryUuid = null,
            baseFingerprint = null,
            baseGroupPath = "Accounts/Old",
            baseGroupUuid = "group-uuid",
            structurePatch = KeePassStructureChangePatch(
                sourceGroupPath = "Accounts/Old",
                sourceGroupUuid = "group-uuid",
                targetGroupPath = "Accounts/New",
                targetGroupUuid = "group-uuid",
                newGroupName = "New"
            )
        )
        val delete = KeePassChangeSet(
            changeId = "group-delete-1",
            databaseId = 42,
            target = KeePassChangeTarget.GROUP,
            operation = KeePassChangeOperation.DELETE_GROUP,
            entryUuid = null,
            baseFingerprint = null,
            baseGroupPath = "Accounts/New",
            baseGroupUuid = "group-uuid",
            structurePatch = KeePassStructureChangePatch(
                sourceGroupPath = "Accounts/New",
                sourceGroupUuid = "group-uuid"
            )
        )

        assertEquals(KeePassChangeOperation.RENAME_GROUP, rename.operation)
        assertEquals(KeePassChangeOperation.DELETE_GROUP, delete.operation)
        assertFalse(rename.requiresBaseFingerprint())
        assertFalse(delete.requiresBaseFingerprint())
    }

    @Test
    fun groupMoveIsAStructureChangeWithoutBaseFingerprint() {
        val changeSet = KeePassChangeSet(
            changeId = "group-move-1",
            databaseId = 42,
            target = KeePassChangeTarget.GROUP,
            operation = KeePassChangeOperation.MOVE_GROUP,
            entryUuid = null,
            baseFingerprint = null,
            baseGroupPath = "Accounts",
            baseGroupUuid = "old-parent-group-uuid",
            structurePatch = KeePassStructureChangePatch(
                sourceGroupPath = "Accounts/Work",
                sourceGroupUuid = "group-uuid",
                targetGroupPath = "Archive",
                targetGroupUuid = "new-parent-group-uuid",
                groupName = "Work"
            )
        )

        val decoded = KeePassChangeSetCodec.decode(KeePassChangeSetCodec.encode(changeSet))

        assertEquals(changeSet, decoded)
        assertEquals(KeePassChangeOperation.MOVE_GROUP, decoded.operation)
        assertFalse(decoded.isEntryScoped())
        assertFalse(decoded.requiresBaseFingerprint())
        assertEquals("new-parent-group-uuid", decoded.structurePatch!!.targetGroupUuid)
    }

    @Test
    fun groupTreeChangeSetRoundTripsWithLosslessPayload() {
        val changeSet = KeePassChangeSet(
            changeId = "group-tree-create-1",
            databaseId = 42,
            target = KeePassChangeTarget.GROUP,
            operation = KeePassChangeOperation.CREATE_GROUP_TREE,
            entryUuid = null,
            baseFingerprint = null,
            structurePatch = KeePassStructureChangePatch(
                targetGroupPath = "Archive",
                targetGroupUuid = "target-parent-group-uuid"
            ),
            groupTreePatch = KeePassGroupTreeChangePatch(
                sourceRootGroupUuid = "source-group-uuid",
                targetParentGroupUuid = "target-parent-group-uuid",
                root = KeePassGroupTreeSnapshot(
                    uuid = "source-group-uuid",
                    name = "Work",
                    tags = listOf("tag-a"),
                    times = KeePassTimesPatch(creationTimeEpochMillis = 123L),
                    customData = listOf(KeePassCustomDataPatch("plugin", "value")),
                    entries = listOf(
                        KeePassEntryTreeSnapshot(
                            uuid = "entry-uuid",
                            fields = listOf(
                                KeePassFieldChange("Title", "Login"),
                                KeePassFieldChange("Password", "secret", protected = true)
                            ),
                            binaries = listOf(KeePassBinaryReferencePatch("recovery.pdf", "aabbcc")),
                            history = listOf(
                                KeePassEntryTreeSnapshot(
                                    uuid = "history-entry-uuid",
                                    fields = listOf(KeePassFieldChange("Title", "Old Login"))
                                )
                            )
                        )
                    ),
                    groups = listOf(
                        KeePassGroupTreeSnapshot(
                            uuid = "child-group-uuid",
                            name = "Child"
                        )
                    )
                ),
                binaryPool = listOf(
                    KeePassBinaryPoolItemPatch(
                        hash = "aabbcc",
                        protected = false,
                        compressed = true,
                        contentBase64 = "AQID"
                    )
                )
            )
        )

        val decoded = KeePassChangeSetCodec.decode(KeePassChangeSetCodec.encode(changeSet))

        assertEquals(changeSet, decoded)
        assertEquals(KeePassChangeOperation.CREATE_GROUP_TREE, decoded.operation)
        assertFalse(decoded.requiresBaseFingerprint())
        assertEquals("Work", decoded.groupTreePatch!!.root.name)
        assertEquals("AQID", decoded.groupTreePatch.binaryPool.single().contentBase64)
    }

    @Test
    fun attachmentChangeSetsRoundTripWithContentForAddOnly() {
        val add = KeePassChangeSet(
            changeId = "attachment-add-1",
            databaseId = 42,
            target = KeePassChangeTarget.UNKNOWN_ENTRY,
            operation = KeePassChangeOperation.ADD_ATTACHMENT,
            entryUuid = "entry-uuid",
            baseFingerprint = "base-fingerprint",
            attachmentPatch = KeePassAttachmentChangePatch(
                fileName = "recovery.pdf",
                binaryHash = "abc123",
                protected = false,
                compressed = true,
                contentBase64 = "AQID"
            )
        )
        val remove = KeePassChangeSet(
            changeId = "attachment-remove-1",
            databaseId = 42,
            target = KeePassChangeTarget.UNKNOWN_ENTRY,
            operation = KeePassChangeOperation.REMOVE_ATTACHMENT,
            entryUuid = "entry-uuid",
            baseFingerprint = "base-fingerprint",
            attachmentPatch = KeePassAttachmentChangePatch(
                fileName = "recovery.pdf",
                binaryHash = "abc123"
            )
        )

        assertEquals(add, KeePassChangeSetCodec.decode(KeePassChangeSetCodec.encode(add)))
        assertEquals(remove, KeePassChangeSetCodec.decode(KeePassChangeSetCodec.encode(remove)))
        assertEquals("AQID", add.attachmentPatch!!.contentBase64)
        assertEquals(null, remove.attachmentPatch!!.contentBase64)
    }

    @Test(expected = IllegalArgumentException::class)
    fun structureOperationRequiresStructurePatch() {
        KeePassChangeSet(
            databaseId = 42,
            target = KeePassChangeTarget.PASSWORD,
            operation = KeePassChangeOperation.MOVE_TO_RECYCLE_BIN,
            entryUuid = "entry-uuid",
            baseFingerprint = "base-fingerprint"
        )
    }
}
