package takagi.ru.monica.keepass

import android.util.Base64
import app.keemobile.kotpass.constants.AutoTypeObfuscation
import app.keemobile.kotpass.constants.GroupOverride
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.modifiers.binaries
import app.keemobile.kotpass.database.modifiers.modifyBinaries
import app.keemobile.kotpass.database.modifiers.modifyParentGroup
import app.keemobile.kotpass.constants.PredefinedIcon
import app.keemobile.kotpass.models.AutoTypeData
import app.keemobile.kotpass.models.AutoTypeItem
import app.keemobile.kotpass.models.BinaryData
import app.keemobile.kotpass.models.BinaryReference
import app.keemobile.kotpass.models.CustomDataValue
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Group
import app.keemobile.kotpass.models.TimeData
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.time.Instant
import java.util.UUID

class KeePassChangeSetApplier {
    fun apply(
        database: KeePassDatabase,
        changeSet: KeePassChangeSet
    ): KeePassChangeSetApplyResult {
        val updatedDatabase = when (changeSet.operation) {
            KeePassChangeOperation.CREATE_GROUP_TREE -> createGroupTree(database, changeSet)
            KeePassChangeOperation.DELETE_GROUP_TREE -> deleteGroupTree(database, changeSet)
            KeePassChangeOperation.ADD_ATTACHMENT -> addAttachment(database, changeSet)
            KeePassChangeOperation.REMOVE_ATTACHMENT -> removeAttachment(database, changeSet)
            else -> null
        } ?: applyGroupChange(database, changeSet)
        return KeePassChangeSetApplyResult(
            updatedDatabase = updatedDatabase,
            appliedChangeId = changeSet.changeId,
            operation = changeSet.operation
        )
    }

    private fun applyGroupChange(
        database: KeePassDatabase,
        changeSet: KeePassChangeSet
    ): KeePassDatabase {
        val updatedRoot = when (changeSet.operation) {
            KeePassChangeOperation.CREATE_ENTRY -> createEntry(database.content.group, changeSet)
            KeePassChangeOperation.FIELD_PATCH -> applyFieldPatch(database.content.group, changeSet)
            KeePassChangeOperation.MOVE_ENTRY -> moveEntry(database.content.group, changeSet)
            KeePassChangeOperation.MOVE_TO_RECYCLE_BIN -> moveEntryToRecycleBin(database.content.group, changeSet)
            KeePassChangeOperation.RESTORE_FROM_RECYCLE_BIN -> restoreEntryFromRecycleBin(database.content.group, changeSet)
            KeePassChangeOperation.PERMANENT_DELETE -> permanentDelete(database.content.group, changeSet)
            KeePassChangeOperation.CREATE_GROUP -> createGroup(database.content.group, changeSet)
            KeePassChangeOperation.RENAME_GROUP -> renameGroup(database.content.group, changeSet)
            KeePassChangeOperation.DELETE_GROUP -> deleteGroup(database.content.group, changeSet)
            KeePassChangeOperation.MOVE_GROUP -> moveGroup(database.content.group, changeSet)
            KeePassChangeOperation.CREATE_GROUP_TREE,
            KeePassChangeOperation.DELETE_GROUP_TREE -> {
                throw IllegalArgumentException("Group tree operation must be handled before group replay")
            }
            KeePassChangeOperation.ADD_ATTACHMENT,
            KeePassChangeOperation.REMOVE_ATTACHMENT -> {
                throw IllegalArgumentException("Attachment operation must be handled before group replay")
            }
        }
        return database.modifyParentGroup { updatedRoot }
    }

    fun applyAll(
        database: KeePassDatabase,
        changes: List<KeePassChangeSet>
    ): KeePassChangeSetApplyBatchResult {
        var current = database
        val applied = mutableListOf<KeePassChangeSetApplyResult>()
        changes.forEach { change ->
            val result = apply(current, change)
            current = result.updatedDatabase
            applied += result
        }
        return KeePassChangeSetApplyBatchResult(
            updatedDatabase = current,
            applied = applied
        )
    }

    private fun createGroupTree(database: KeePassDatabase, changeSet: KeePassChangeSet): KeePassDatabase {
        val patch = changeSet.structurePatch
            ?: throw IllegalArgumentException("CREATE_GROUP_TREE requires structurePatch")
        val groupTreePatch = changeSet.groupTreePatch
            ?: throw IllegalArgumentException("CREATE_GROUP_TREE requires groupTreePatch")
        val targetParentGroupUuid = patch.targetGroupUuid?.toUuidOrNull()
            ?: groupTreePatch.targetParentGroupUuid?.toUuidOrNull()
            ?: throw IllegalArgumentException("CREATE_GROUP_TREE requires targetGroupUuid")
        val rootGroupUuid = groupTreePatch.root.uuid.toUuidOrNull()
            ?: throw IllegalArgumentException("CREATE_GROUP_TREE requires valid root uuid")
        if (containsGroupUuid(database.content.group, rootGroupUuid)) {
            throw IllegalArgumentException("KeePass group tree already exists for create: $rootGroupUuid")
        }
        ensureGroupTreeBinaryPoolComplete(groupTreePatch.root, groupTreePatch.binaryPool)
        val group = groupTreePatch.root.toGroup()
        val inserted = addGroupToParentUuid(
            group = database.content.group,
            parentGroupUuid = targetParentGroupUuid,
            newGroup = group
        )
        if (!inserted.inserted) {
            throw IllegalArgumentException("KeePass target group not found for group tree create: $targetParentGroupUuid")
        }
        return database
            .modifyParentGroup { inserted.group }
            .modifyBinaries { pool ->
                groupTreePatch.binaryPool.fold(pool) { current, item ->
                    val binaryData = item.toBinaryData()
                    current + (binaryData.hash to binaryData)
                }
            }
    }

    private fun deleteGroupTree(database: KeePassDatabase, changeSet: KeePassChangeSet): KeePassDatabase {
        val patch = changeSet.structurePatch
            ?: throw IllegalArgumentException("DELETE_GROUP_TREE requires structurePatch")
        val groupTreePatch = changeSet.groupTreePatch
            ?: throw IllegalArgumentException("DELETE_GROUP_TREE requires groupTreePatch")
        val sourceGroupUuid = patch.sourceGroupUuid?.toUuidOrNull()
            ?: groupTreePatch.sourceRootGroupUuid?.toUuidOrNull()
            ?: throw IllegalArgumentException("DELETE_GROUP_TREE requires sourceGroupUuid")
        val payloadRootUuid = groupTreePatch.root.uuid.toUuidOrNull()
            ?: throw IllegalArgumentException("DELETE_GROUP_TREE requires valid root uuid")
        if (sourceGroupUuid != payloadRootUuid) {
            throw IllegalArgumentException("DELETE_GROUP_TREE source uuid does not match payload root uuid")
        }
        if (database.content.group.uuid == sourceGroupUuid) {
            throw IllegalArgumentException("KeePass root group cannot be deleted")
        }
        val result = removeGroupByUuid(database.content.group, sourceGroupUuid)
        if (!result.removed) {
            throw IllegalArgumentException("KeePass group not found for group tree delete: $sourceGroupUuid")
        }
        val payloadHashes = groupTreePatch.binaryPool
            .mapNotNull { it.hash.hexToByteStringOrNull() }
            .toSet()
        val withoutGroup = database.modifyParentGroup { result.group }
        return withoutGroup.modifyBinaries { pool ->
            payloadHashes.fold(pool) { current, hash ->
                if (anyEntryReferencesHash(withoutGroup.content.group, hash)) current else current - hash
            }
        }
    }

    private fun createEntry(root: Group, changeSet: KeePassChangeSet): Group {
        val entryUuid = changeSet.requiredEntryUuid()
        if (containsEntryUuid(root, entryUuid)) {
            throw IllegalArgumentException("KeePass entry already exists for create: $entryUuid")
        }
        val patch = changeSet.entryPatch
            ?: throw IllegalArgumentException("CREATE_ENTRY requires entryPatch")
        val targetGroupUuid = patch.targetGroupUuid?.toUuidOrNull()
            ?: throw IllegalArgumentException("CREATE_ENTRY requires targetGroupUuid")
        val fields = EntryFields.of(
            *patch.fields.map { field ->
                field.name to field.toEntryValue()
            }.toTypedArray()
        )
        val baseEntry = Entry(
            uuid = entryUuid,
            fields = fields
        )
        val entry = patch.iconName
            ?.let { iconName -> runCatching { PredefinedIcon.valueOf(iconName) }.getOrNull() }
            ?.let { icon -> baseEntry.copy(icon = icon) }
            ?: baseEntry
        val inserted = addEntryToGroupUuid(root, targetGroupUuid, entry)
        if (!inserted.inserted) {
            throw IllegalArgumentException("KeePass target group not found for create: $targetGroupUuid")
        }
        return inserted.group
    }

    private fun applyFieldPatch(root: Group, changeSet: KeePassChangeSet): Group {
        val entryUuid = changeSet.requiredEntryUuid()
        val fieldPatch = changeSet.fieldPatch
            ?: throw IllegalArgumentException("FIELD_PATCH requires fieldPatch")
        val replacementFields = EntryFields.of(
            *fieldPatch.replacementFields.map { field ->
                field.name to field.toEntryValue()
            }.toTypedArray()
        )
        val patch = KeePassEntryFieldPatch.fromEntryFields(
            replacementFields = replacementFields,
            removeManagedField = removeManagedFieldFor(fieldPatch.managedScope),
            removeFieldNames = fieldPatch.removeFieldNames + fieldPatch.replacementFields.map { it.name }
        )
        val result = updateEntryByUuid(root, entryUuid) { entry ->
            assertFieldPatchHasNoRemoteConflict(entry, changeSet, fieldPatch)
            patch.applyTo(entry)
        }
        if (!result.updated) {
            throw IllegalArgumentException("KeePass entry not found for field patch: $entryUuid")
        }
        return result.group
    }

    private fun assertFieldPatchHasNoRemoteConflict(
        entry: Entry,
        changeSet: KeePassChangeSet,
        fieldPatch: KeePassFieldChangePatch
    ) {
        if (fieldPatch.baseFields.isNotEmpty()) {
            val changedBaseFields = fieldPatch.baseFields
                .filterNot { base -> entryMatchesBaseField(entry, base) }
                .map { it.name }
            if (changedBaseFields.isNotEmpty()) {
                throw KeePassChangeConflictException(
                    changeId = changeSet.changeId,
                    entryUuid = changeSet.entryUuid,
                    reason = "Remote changed field(s): ${changedBaseFields.joinToString(", ")}"
                )
            }
            return
        }

        val baseFingerprint = changeSet.baseFingerprint
        if (!baseFingerprint.isNullOrBlank() && KeePassEntryFingerprint.build(entry) != baseFingerprint) {
            throw KeePassChangeConflictException(
                changeId = changeSet.changeId,
                entryUuid = changeSet.entryUuid,
                reason = "Remote entry fingerprint changed before replay"
            )
        }
    }

    private fun entryMatchesBaseField(entry: Entry, base: KeePassFieldBaseValue): Boolean {
        var existing: EntryValue? = null
        val normalizedBaseName = KeePassFieldRegistry.normalize(base.name)
        entry.fields.forEach { (name, value) ->
            if (KeePassFieldRegistry.normalize(name) == normalizedBaseName) {
                existing = value
            }
        }
        if (!base.present) {
            return existing == null
        }
        val value = existing ?: return false
        return value.content == base.value.orEmpty() &&
            (value is EntryValue.Encrypted) == base.protected
    }

    private fun moveEntry(root: Group, changeSet: KeePassChangeSet): Group {
        val entryUuid = changeSet.requiredEntryUuid()
        val targetGroupUuid = changeSet.structurePatch?.targetGroupUuid?.toUuidOrNull()
            ?: throw IllegalArgumentException("MOVE_ENTRY requires targetGroupUuid")
        val removed = removeEntryByUuid(root, entryUuid)
        val entry = removed.removed?.entry
            ?: throw IllegalArgumentException("KeePass entry not found for move: $entryUuid")
        val inserted = addEntryToGroupUuid(removed.group, targetGroupUuid, entry)
        if (!inserted.inserted) {
            throw IllegalArgumentException("KeePass target group not found for move: $targetGroupUuid")
        }
        return inserted.group
    }

    private fun moveEntryToRecycleBin(root: Group, changeSet: KeePassChangeSet): Group {
        val entryUuid = changeSet.requiredEntryUuid()
        val patch = changeSet.structurePatch
            ?: throw IllegalArgumentException("MOVE_TO_RECYCLE_BIN requires structurePatch")
        val recycleBinUuid = patch.recycleBinGroupUuid?.toUuidOrNull()
            ?: throw IllegalArgumentException("MOVE_TO_RECYCLE_BIN requires recycleBinGroupUuid")
        val previousParentUuid = patch.previousParentGroupUuid?.toUuidOrNull()
            ?: throw IllegalArgumentException("MOVE_TO_RECYCLE_BIN requires previousParentGroupUuid")
        val removed = removeEntryByUuid(root, entryUuid, excludedGroupUuid = recycleBinUuid)
        val entry = removed.removed?.entry
            ?: throw IllegalArgumentException("KeePass entry not found for recycle bin move: $entryUuid")
        val actualPreviousParent = removed.removed.parentGroupUuid
        if (actualPreviousParent != previousParentUuid) {
            throw IllegalArgumentException(
                "KeePass recycle bin previous parent mismatch for $entryUuid"
            )
        }
        val inserted = addEntryToGroupUuid(
            group = removed.group,
            targetGroupUuid = recycleBinUuid,
            entry = entry.copy(previousParentGroup = previousParentUuid)
        )
        if (!inserted.inserted) {
            throw IllegalArgumentException("KeePass recycle bin group not found: $recycleBinUuid")
        }
        return inserted.group
    }

    private fun restoreEntryFromRecycleBin(root: Group, changeSet: KeePassChangeSet): Group {
        val entryUuid = changeSet.requiredEntryUuid()
        val patch = changeSet.structurePatch
            ?: throw IllegalArgumentException("RESTORE_FROM_RECYCLE_BIN requires structurePatch")
        val recycleBinUuid = patch.recycleBinGroupUuid?.toUuidOrNull()
            ?: throw IllegalArgumentException("RESTORE_FROM_RECYCLE_BIN requires recycleBinGroupUuid")
        val previousParentUuid = patch.previousParentGroupUuid?.toUuidOrNull()
            ?: throw IllegalArgumentException("RESTORE_FROM_RECYCLE_BIN requires previousParentGroupUuid")
        val targetGroupUuid = patch.targetGroupUuid?.toUuidOrNull()
            ?: previousParentUuid
        if (targetGroupUuid == recycleBinUuid) {
            throw IllegalArgumentException("KeePass restore target cannot be recycle bin group: $targetGroupUuid")
        }
        val removed = removeEntryByUuid(
            group = root,
            entryUuid = entryUuid,
            requiredAncestorGroupUuid = recycleBinUuid
        )
        val entry = removed.removed?.entry
            ?: throw IllegalArgumentException("KeePass entry not found for recycle bin restore: $entryUuid")
        val actualPreviousParent = entry.previousParentGroup
        if (actualPreviousParent != null && actualPreviousParent != previousParentUuid) {
            throw IllegalArgumentException(
                "KeePass recycle bin restore previous parent mismatch for $entryUuid"
            )
        }
        val inserted = addEntryToGroupUuid(
            group = removed.group,
            targetGroupUuid = targetGroupUuid,
            entry = entry.copy(previousParentGroup = null)
        )
        if (!inserted.inserted) {
            throw IllegalArgumentException("KeePass restore target group not found: $targetGroupUuid")
        }
        return inserted.group
    }

    private fun permanentDelete(root: Group, changeSet: KeePassChangeSet): Group {
        val entryUuid = changeSet.requiredEntryUuid()
        val removed = removeEntryByUuid(root, entryUuid)
        if (removed.removed == null) {
            throw IllegalArgumentException("KeePass entry not found for permanent delete: $entryUuid")
        }
        return removed.group
    }

    private fun createGroup(root: Group, changeSet: KeePassChangeSet): Group {
        val patch = changeSet.structurePatch
            ?: throw IllegalArgumentException("CREATE_GROUP requires structurePatch")
        val groupUuid = patch.sourceGroupUuid?.toUuidOrNull()
            ?: throw IllegalArgumentException("CREATE_GROUP requires sourceGroupUuid")
        val parentGroupUuid = patch.targetGroupUuid?.toUuidOrNull()
            ?: throw IllegalArgumentException("CREATE_GROUP requires targetGroupUuid")
        val groupName = patch.groupName?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("CREATE_GROUP requires groupName")
        if (containsGroupUuid(root, groupUuid)) {
            throw IllegalArgumentException("KeePass group already exists for create: $groupUuid")
        }
        val result = addGroupToParentUuid(
            group = root,
            parentGroupUuid = parentGroupUuid,
            newGroup = Group(uuid = groupUuid, name = groupName)
        )
        if (!result.inserted) {
            throw IllegalArgumentException("KeePass parent group not found for create: $parentGroupUuid")
        }
        return result.group
    }

    private fun renameGroup(root: Group, changeSet: KeePassChangeSet): Group {
        val patch = changeSet.structurePatch
            ?: throw IllegalArgumentException("RENAME_GROUP requires structurePatch")
        val groupUuid = patch.sourceGroupUuid?.toUuidOrNull()
            ?: throw IllegalArgumentException("RENAME_GROUP requires sourceGroupUuid")
        val newName = patch.newGroupName?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("RENAME_GROUP requires newGroupName")
        val result = renameGroupByUuid(root, groupUuid, newName)
        if (!result.updated) {
            throw IllegalArgumentException("KeePass group not found for rename: $groupUuid")
        }
        return result.group
    }

    private fun deleteGroup(root: Group, changeSet: KeePassChangeSet): Group {
        val patch = changeSet.structurePatch
            ?: throw IllegalArgumentException("DELETE_GROUP requires structurePatch")
        val groupUuid = patch.sourceGroupUuid?.toUuidOrNull()
            ?: throw IllegalArgumentException("DELETE_GROUP requires sourceGroupUuid")
        if (root.uuid == groupUuid) {
            throw IllegalArgumentException("KeePass root group cannot be deleted")
        }
        val result = removeGroupByUuid(root, groupUuid)
        if (!result.removed) {
            throw IllegalArgumentException("KeePass group not found for delete: $groupUuid")
        }
        return result.group
    }

    private fun moveGroup(root: Group, changeSet: KeePassChangeSet): Group {
        val patch = changeSet.structurePatch
            ?: throw IllegalArgumentException("MOVE_GROUP requires structurePatch")
        val groupUuid = patch.sourceGroupUuid?.toUuidOrNull()
            ?: throw IllegalArgumentException("MOVE_GROUP requires sourceGroupUuid")
        val targetParentGroupUuid = patch.targetGroupUuid?.toUuidOrNull()
            ?: throw IllegalArgumentException("MOVE_GROUP requires targetGroupUuid")
        if (root.uuid == groupUuid) {
            throw IllegalArgumentException("KeePass root group cannot be moved")
        }
        if (groupUuid == targetParentGroupUuid) {
            throw IllegalArgumentException("KeePass group cannot be moved into itself")
        }

        val removed = removeGroupByUuidWithValue(root, groupUuid)
        val groupToMove = removed.removed
            ?: throw IllegalArgumentException("KeePass group not found for move: $groupUuid")
        if (containsGroupUuid(groupToMove, targetParentGroupUuid)) {
            throw IllegalArgumentException("KeePass group cannot be moved into its descendant")
        }

        val inserted = addGroupToParentUuid(
            group = removed.group,
            parentGroupUuid = targetParentGroupUuid,
            newGroup = groupToMove
        )
        if (!inserted.inserted) {
            throw IllegalArgumentException("KeePass target group not found for move: $targetParentGroupUuid")
        }
        return inserted.group
    }

    private fun addAttachment(database: KeePassDatabase, changeSet: KeePassChangeSet): KeePassDatabase {
        val entryUuid = changeSet.requiredEntryUuid()
        val patch = changeSet.attachmentPatch
            ?: throw IllegalArgumentException("ADD_ATTACHMENT requires attachmentPatch")
        val bytes = patch.contentBase64
            ?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: throw IllegalArgumentException("ADD_ATTACHMENT requires contentBase64")
        val binaryData: BinaryData = if (patch.compressed) {
            BinaryData.Uncompressed(patch.protected, bytes).toCompressed()
        } else {
            BinaryData.Uncompressed(patch.protected, bytes)
        }
        if (!binaryData.hash.hex().equals(patch.binaryHash, ignoreCase = true)) {
            throw IllegalArgumentException("ADD_ATTACHMENT binary hash mismatch")
        }
        val newRef = BinaryReference(hash = binaryData.hash, name = patch.fileName)
        val updated = updateEntryByUuid(database.content.group, entryUuid) { entry ->
            val alreadyLinked = entry.binaries.any {
                it.hash == newRef.hash && it.name == newRef.name
            }
            if (alreadyLinked) {
                entry
            } else {
                entry.copy(binaries = entry.binaries + newRef)
            }
        }
        if (!updated.updated) {
            throw IllegalArgumentException("KeePass entry not found for attachment add: $entryUuid")
        }
        return database
            .modifyParentGroup { updated.group }
            .modifyBinaries { pool ->
                if (pool.containsKey(binaryData.hash)) pool else pool + (binaryData.hash to binaryData)
            }
    }

    private fun removeAttachment(database: KeePassDatabase, changeSet: KeePassChangeSet): KeePassDatabase {
        val entryUuid = changeSet.requiredEntryUuid()
        val patch = changeSet.attachmentPatch
            ?: throw IllegalArgumentException("REMOVE_ATTACHMENT requires attachmentPatch")
        val requestedHash = patch.binaryHash.hexToByteString()
        var removedRef: BinaryReference? = null
        val updated = updateEntryByUuid(database.content.group, entryUuid) { entry ->
            val targetRef = entry.binaries.firstOrNull {
                it.hash == requestedHash && it.name == patch.fileName
            } ?: entry.binaries.firstOrNull {
                it.hash == requestedHash
            }
            if (targetRef == null) {
                entry
            } else {
                removedRef = targetRef
                entry.copy(binaries = entry.binaries - targetRef)
            }
        }
        if (!updated.updated) {
            throw IllegalArgumentException("KeePass entry not found for attachment remove: $entryUuid")
        }
        val removedHash = removedRef?.hash
            ?: throw IllegalArgumentException("KeePass attachment not found for remove: ${patch.binaryHash}")
        val dbWithUpdatedEntry = database.modifyParentGroup { updated.group }
        return if (anyEntryReferencesHash(dbWithUpdatedEntry.content.group, removedHash)) {
            dbWithUpdatedEntry
        } else {
            dbWithUpdatedEntry.modifyBinaries { pool -> pool - removedHash }
        }
    }

    private fun updateEntryByUuid(
        group: Group,
        entryUuid: UUID,
        updater: (Entry) -> Entry
    ): UpdateEntryResult {
        var updated = false
        val entries = group.entries.map { entry ->
            if (!updated && entry.uuid == entryUuid) {
                updated = true
                updater(entry)
            } else {
                entry
            }
        }
        val groups = group.groups.map { child ->
            val childResult = updateEntryByUuid(child, entryUuid, updater)
            if (childResult.updated) {
                updated = true
            }
            childResult.group
        }
        return UpdateEntryResult(group.copy(entries = entries, groups = groups), updated)
    }

    private fun removeEntryByUuid(
        group: Group,
        entryUuid: UUID,
        excludedGroupUuid: UUID? = null,
        requiredAncestorGroupUuid: UUID? = null,
        parentGroupUuid: UUID? = null,
        inExcludedGroup: Boolean = false,
        inRequiredAncestorGroup: Boolean = false
    ): RemoveEntryResult {
        val excluded = inExcludedGroup || group.uuid == excludedGroupUuid
        val requiredAncestorMatched = inRequiredAncestorGroup ||
            requiredAncestorGroupUuid == null ||
            group.uuid == requiredAncestorGroupUuid
        var removed: RemovedEntry? = null
        val entries = mutableListOf<Entry>()
        group.entries.forEach { entry ->
            if (!excluded && requiredAncestorMatched && removed == null && entry.uuid == entryUuid) {
                val resolvedParentUuid = group.uuid
                    ?: parentGroupUuid
                    ?: throw IllegalStateException("KeePass entry parent group has no uuid: $entryUuid")
                removed = RemovedEntry(
                    entry = entry,
                    parentGroupUuid = resolvedParentUuid
                )
            } else {
                entries += entry
            }
        }

        val groups = group.groups.map { child ->
            if (removed != null) {
                child
            } else {
                val childResult = removeEntryByUuid(
                    group = child,
                    entryUuid = entryUuid,
                    excludedGroupUuid = excludedGroupUuid,
                    requiredAncestorGroupUuid = requiredAncestorGroupUuid,
                    parentGroupUuid = group.uuid ?: parentGroupUuid,
                    inExcludedGroup = excluded,
                    inRequiredAncestorGroup = requiredAncestorMatched
                )
                removed = childResult.removed
                childResult.group
            }
        }
        return RemoveEntryResult(group.copy(entries = entries, groups = groups), removed)
    }

    private fun addEntryToGroupUuid(
        group: Group,
        targetGroupUuid: UUID,
        entry: Entry
    ): AddEntryResult {
        if (group.uuid == targetGroupUuid) {
            return AddEntryResult(group.copy(entries = group.entries + entry), inserted = true)
        }

        var inserted = false
        val groups = group.groups.map { child ->
            if (inserted) {
                child
            } else {
                val childResult = addEntryToGroupUuid(child, targetGroupUuid, entry)
                inserted = childResult.inserted
                childResult.group
            }
        }
        return AddEntryResult(group.copy(groups = groups), inserted)
    }

    private fun addGroupToParentUuid(
        group: Group,
        parentGroupUuid: UUID,
        newGroup: Group
    ): AddGroupResult {
        if (group.uuid == parentGroupUuid) {
            val conflict = group.groups.any { sibling ->
                sibling.uuid != newGroup.uuid && sibling.name.equals(newGroup.name, ignoreCase = true)
            }
            if (conflict) {
                throw IllegalArgumentException("KeePass group sibling name conflict: ${newGroup.name}")
            }
            return AddGroupResult(group.copy(groups = group.groups + newGroup), inserted = true)
        }

        var inserted = false
        val groups = group.groups.map { child ->
            if (inserted) {
                child
            } else {
                val childResult = addGroupToParentUuid(child, parentGroupUuid, newGroup)
                inserted = childResult.inserted
                childResult.group
            }
        }
        return AddGroupResult(group.copy(groups = groups), inserted)
    }

    private fun renameGroupByUuid(
        group: Group,
        groupUuid: UUID,
        newName: String
    ): UpdateEntryResult {
        var updated = false
        val groups = group.groups.map { child ->
            if (!updated && child.uuid == groupUuid) {
                val conflict = group.groups.any { sibling ->
                    sibling.uuid != groupUuid && sibling.name.equals(newName, ignoreCase = true)
                }
                if (conflict) {
                    throw IllegalArgumentException("KeePass group sibling name conflict: $newName")
                }
                updated = true
                child.copy(name = newName)
            } else {
                val childResult = renameGroupByUuid(child, groupUuid, newName)
                if (childResult.updated) {
                    updated = true
                }
                childResult.group
            }
        }
        return UpdateEntryResult(group.copy(groups = groups), updated)
    }

    private fun removeGroupByUuid(
        group: Group,
        groupUuid: UUID
    ): RemoveGroupResult {
        var removed = false
        val groups = mutableListOf<Group>()
        group.groups.forEach { child ->
            if (!removed && child.uuid == groupUuid) {
                removed = true
            } else if (!removed) {
                val childResult = removeGroupByUuid(child, groupUuid)
                removed = childResult.removed
                groups += childResult.group
            } else {
                groups += child
            }
        }
        return RemoveGroupResult(group.copy(groups = groups), removed)
    }

    private fun removeGroupByUuidWithValue(
        group: Group,
        groupUuid: UUID
    ): RemoveGroupWithValueResult {
        var removed: Group? = null
        val groups = mutableListOf<Group>()
        group.groups.forEach { child ->
            if (removed == null && child.uuid == groupUuid) {
                removed = child
            } else if (removed == null) {
                val childResult = removeGroupByUuidWithValue(child, groupUuid)
                removed = childResult.removed
                groups += childResult.group
            } else {
                groups += child
            }
        }
        return RemoveGroupWithValueResult(group.copy(groups = groups), removed)
    }

    private fun containsEntryUuid(group: Group, entryUuid: UUID): Boolean {
        return group.entries.any { it.uuid == entryUuid } ||
            group.groups.any { containsEntryUuid(it, entryUuid) }
    }

    private fun containsGroupUuid(group: Group, groupUuid: UUID): Boolean {
        return group.uuid == groupUuid || group.groups.any { containsGroupUuid(it, groupUuid) }
    }

    private fun anyEntryReferencesHash(group: Group, hash: ByteString): Boolean {
        return group.entries.any { entry -> entry.binaries.any { it.hash == hash } } ||
            group.groups.any { anyEntryReferencesHash(it, hash) }
    }

    private fun removeManagedFieldFor(scope: KeePassManagedFieldScope): (String) -> Boolean {
        return when (scope) {
            KeePassManagedFieldScope.PASSWORD -> KeePassFieldRegistry::isPasswordEntryOverlayField
            KeePassManagedFieldScope.SECURE_ITEM -> KeePassFieldRegistry::isSecureItemOverlayField
            KeePassManagedFieldScope.PASSKEY -> KeePassFieldRegistry::isPasskeyEntryOverlayField
            KeePassManagedFieldScope.EXPLICIT_ONLY -> { _ -> false }
        }
    }

    private fun ensureGroupTreeBinaryPoolComplete(
        root: KeePassGroupTreeSnapshot,
        binaryPool: List<KeePassBinaryPoolItemPatch>
    ) {
        val available = binaryPool.mapTo(mutableSetOf()) { it.hash.lowercase() }
        val referenced = collectBinaryHashes(root)
        val missing = referenced.filterNot { it.lowercase() in available }
        if (missing.isNotEmpty()) {
            throw IllegalArgumentException("CREATE_GROUP_TREE missing binary pool content for hash(es): ${missing.joinToString(", ")}")
        }
    }

    private fun collectBinaryHashes(group: KeePassGroupTreeSnapshot): Set<String> {
        val hashes = linkedSetOf<String>()
        group.entries.forEach { entry -> collectBinaryHashes(entry, hashes) }
        group.groups.forEach { child -> hashes += collectBinaryHashes(child) }
        return hashes
    }

    private fun collectBinaryHashes(entry: KeePassEntryTreeSnapshot, hashes: MutableSet<String>) {
        entry.binaries.forEach { hashes += it.hash }
        entry.history.forEach { collectBinaryHashes(it, hashes) }
    }

    private fun KeePassGroupTreeSnapshot.toGroup(): Group {
        return Group(
            uuid = uuid.toUuidOrNull() ?: throw IllegalArgumentException("Invalid group tree uuid: $uuid"),
            name = name,
            notes = notes.orEmpty(),
            icon = iconName.toPredefinedIconOrNull() ?: PredefinedIcon.Folder,
            customIconUuid = customIconUuid?.toUuidOrNull(),
            times = times.toTimeData(),
            expanded = expanded,
            defaultAutoTypeSequence = defaultAutoTypeSequence,
            enableAutoType = enableAutoType.toGroupOverride(),
            enableSearching = enableSearching.toGroupOverride(),
            lastTopVisibleEntry = lastTopVisibleEntryUuid?.toUuidOrNull(),
            previousParentGroup = previousParentGroupUuid?.toUuidOrNull(),
            tags = tags,
            groups = groups.map { it.toGroup() },
            entries = entries.map { it.toEntry() },
            customData = customData.toCustomDataMap()
        )
    }

    private fun KeePassEntryTreeSnapshot.toEntry(): Entry {
        return Entry(
            uuid = uuid.toUuidOrNull() ?: throw IllegalArgumentException("Invalid entry tree uuid: $uuid"),
            icon = iconName.toPredefinedIconOrNull() ?: PredefinedIcon.Key,
            customIconUuid = customIconUuid?.toUuidOrNull(),
            foregroundColor = foregroundColor.orEmpty(),
            backgroundColor = backgroundColor.orEmpty(),
            overrideUrl = overrideUrl,
            times = times.toTimeData(),
            autoType = autoType.toAutoTypeData(),
            fields = EntryFields.of(*fields.map { it.name to it.toEntryValue() }.toTypedArray()),
            tags = tags,
            binaries = binaries.map { it.toBinaryReference() },
            history = history.map { it.toEntry() },
            customData = customData.toCustomDataMap(),
            previousParentGroup = previousParentGroupUuid?.toUuidOrNull(),
            qualityCheck = qualityCheck
        )
    }

    private fun KeePassBinaryReferencePatch.toBinaryReference(): BinaryReference {
        return BinaryReference(
            hash = hash.hexToByteString(),
            name = name
        )
    }

    private fun KeePassBinaryPoolItemPatch.toBinaryData(): BinaryData {
        val bytes = Base64.decode(contentBase64, Base64.NO_WRAP)
        val binaryData: BinaryData = if (compressed) {
            BinaryData.Uncompressed(this.protected, bytes).toCompressed()
        } else {
            BinaryData.Uncompressed(this.protected, bytes)
        }
        if (!binaryData.hash.hex().equals(hash, ignoreCase = true)) {
            throw IllegalArgumentException("Group tree binary hash mismatch: $hash")
        }
        return binaryData
    }

    private fun KeePassTimesPatch?.toTimeData(): TimeData {
        val base = defaultTimeData()
        if (this == null) return base
        return base.copy(
            creationTime = creationTimeEpochMillis?.let(Instant::ofEpochMilli) ?: base.creationTime,
            lastAccessTime = lastAccessTimeEpochMillis?.let(Instant::ofEpochMilli) ?: base.lastAccessTime,
            lastModificationTime = lastModificationTimeEpochMillis?.let(Instant::ofEpochMilli)
                ?: base.lastModificationTime,
            locationChanged = locationChangedEpochMillis?.let(Instant::ofEpochMilli) ?: base.locationChanged,
            expiryTime = expiryTimeEpochMillis?.let(Instant::ofEpochMilli) ?: base.expiryTime,
            expires = expires,
            usageCount = usageCount
        )
    }

    private fun defaultTimeData(): TimeData {
        return TimeData(
            creationTime = Instant.EPOCH,
            lastAccessTime = Instant.EPOCH,
            lastModificationTime = Instant.EPOCH,
            locationChanged = Instant.EPOCH,
            expiryTime = Instant.EPOCH,
            expires = false,
            usageCount = 0
        )
    }

    private fun List<KeePassCustomDataPatch>.toCustomDataMap(): Map<String, CustomDataValue> {
        return associate { item ->
            item.key to CustomDataValue(
                value = item.value,
                lastModified = item.lastModifiedEpochMillis?.let(Instant::ofEpochMilli) ?: Instant.EPOCH
            )
        }
    }

    private fun KeePassAutoTypePatch?.toAutoTypeData(): AutoTypeData {
        val base = defaultAutoTypeData()
        if (this == null) return base
        return base.copy(
            enabled = enabled,
            obfuscation = obfuscation.toAutoTypeObfuscation(),
            defaultSequence = defaultSequence,
            items = items.map { AutoTypeItem(it.window, it.keystrokeSequence) }
        )
    }

    private fun defaultAutoTypeData(): AutoTypeData {
        return AutoTypeData(
            enabled = true,
            obfuscation = AutoTypeObfuscation.None,
            defaultSequence = "",
            items = emptyList()
        )
    }

    private fun String?.toPredefinedIconOrNull(): PredefinedIcon? {
        return this?.takeIf { it.isNotBlank() }?.let { runCatching { PredefinedIcon.valueOf(it) }.getOrNull() }
    }

    private fun String?.toGroupOverride(): GroupOverride {
        return this?.takeIf { it.isNotBlank() }?.let {
            runCatching { GroupOverride.valueOf(it) }.getOrNull()
        } ?: GroupOverride.Inherit
    }

    private fun String?.toAutoTypeObfuscation(): AutoTypeObfuscation {
        return this?.takeIf { it.isNotBlank() }?.let {
            runCatching { AutoTypeObfuscation.valueOf(it) }.getOrNull()
        } ?: AutoTypeObfuscation.None
    }

    private fun KeePassFieldChange.toEntryValue(): EntryValue {
        return if (protected) {
            EntryValue.Encrypted(EncryptedValue.fromString(value))
        } else {
            EntryValue.Plain(value)
        }
    }

    private fun KeePassChangeSet.requiredEntryUuid(): UUID {
        return entryUuid?.toUuidOrNull()
            ?: throw IllegalArgumentException("${operation.name} requires entryUuid")
    }

    private fun String.toUuidOrNull(): UUID? {
        return runCatching { UUID.fromString(this) }.getOrNull()
    }

    private fun String.hexToByteString(): ByteString {
        val normalized = trim()
        require(normalized.length % 2 == 0) { "Invalid hex length" }
        val bytes = ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        return bytes.toByteString()
    }

    private fun String.hexToByteStringOrNull(): ByteString? {
        return runCatching { hexToByteString() }.getOrNull()
    }

    private data class UpdateEntryResult(
        val group: Group,
        val updated: Boolean
    )

    private data class RemoveEntryResult(
        val group: Group,
        val removed: RemovedEntry?
    )

    private data class RemovedEntry(
        val entry: Entry,
        val parentGroupUuid: UUID
    )

    private data class AddEntryResult(
        val group: Group,
        val inserted: Boolean
    )

    private data class AddGroupResult(
        val group: Group,
        val inserted: Boolean
    )

    private data class RemoveGroupResult(
        val group: Group,
        val removed: Boolean
    )

    private data class RemoveGroupWithValueResult(
        val group: Group,
        val removed: Group?
    )
}

data class KeePassChangeSetApplyResult(
    val updatedDatabase: KeePassDatabase,
    val appliedChangeId: String,
    val operation: KeePassChangeOperation
)

data class KeePassChangeSetApplyBatchResult(
    val updatedDatabase: KeePassDatabase,
    val applied: List<KeePassChangeSetApplyResult>
)

class KeePassChangeConflictException(
    changeId: String,
    entryUuid: String?,
    reason: String
) : IllegalStateException(
    "KeePass pending change conflict changeId=$changeId entryUuid=${entryUuid.orEmpty()}: $reason"
)
