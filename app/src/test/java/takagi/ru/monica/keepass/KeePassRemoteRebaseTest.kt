package takagi.ru.monica.keepass

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.modifiers.modifyParentGroup
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Meta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class KeePassRemoteRebaseTest {
    @Test
    fun pendingChangePersistsRemoteBaseSnapshotForLaterConflictDecisions() {
        val changeSet = fieldPatchChangeSet(
            entryUuid = UUID.randomUUID(),
            baseTitle = "Base title",
            newTitle = "Local title"
        )
        val snapshot = KeePassPendingChangeBaseSnapshot(
            remoteVersionToken = "version-1",
            remoteEtag = "etag-1",
            remoteLastModified = 1234L,
            baseHash = "base-hash",
            workingHashAtChange = "working-hash"
        )

        val pending = KeePassPendingChange.fromChangeSet(changeSet, baseSnapshot = snapshot)

        assertEquals("version-1", pending.baseRemoteVersionToken)
        assertEquals("etag-1", pending.baseRemoteEtag)
        assertEquals(1234L, pending.baseRemoteLastModified)
        assertEquals("base-hash", pending.baseHash)
        assertEquals("working-hash", pending.workingHashAtChange)
    }

    @Test
    fun rebaseAppliesReadyPendingChangesOnLatestRemoteWithoutOverwritingUnrelatedRemoteFields() {
        val entryUuid = UUID.randomUUID()
        val latestRemote = databaseWithEntry(
            entryUuid = entryUuid,
            title = "Base title",
            url = "https://remote.example",
            password = "remote-password"
        )
        val pendingChangeSet = fieldPatchChangeSet(
            entryUuid = entryUuid,
            baseTitle = "Base title",
            newTitle = "Local title"
        )
        val plan = KeePassPendingFlushPlan(
            databaseId = DATABASE_ID,
            ready = listOf(KeePassPendingFlushItem(pendingId = 1, changeSet = pendingChangeSet)),
            blocked = emptyList(),
            createdAtEpochMillis = 1L
        )

        val result = KeePassRemoteRebase.applyReadyChanges(latestRemote, plan)!!
        val rebasedEntry = result.updatedDatabase.content.group.entries.single { it.uuid == entryUuid }

        assertEquals("Local title", rebasedEntry.fields.getValue("Title").content)
        assertEquals("https://remote.example", rebasedEntry.fields.getValue("URL").content)
        assertEquals("remote-password", rebasedEntry.fields.getValue("Password").content)
        assertEquals(listOf(pendingChangeSet.changeId), result.applied.map { it.appliedChangeId })
    }

    @Test(expected = IllegalStateException::class)
    fun rebaseRejectsBlockedPendingPlanBeforeApplyingReadyChanges() {
        val entryUuid = UUID.randomUUID()
        val latestRemote = databaseWithEntry(
            entryUuid = entryUuid,
            title = "Base title",
            url = "https://remote.example",
            password = "remote-password"
        )
        val readyChangeSet = fieldPatchChangeSet(
            entryUuid = entryUuid,
            baseTitle = "Base title",
            newTitle = "Local title"
        )
        val plan = KeePassPendingFlushPlan(
            databaseId = DATABASE_ID,
            ready = listOf(KeePassPendingFlushItem(pendingId = 1, changeSet = readyChangeSet)),
            blocked = listOf(
                KeePassPendingFlushBlockedItem(
                    pendingId = 2,
                    changeId = "blocked-change",
                    reason = KeePassPendingFlushBlockReason.INVALID_PAYLOAD,
                    message = "bad payload"
                )
            ),
            createdAtEpochMillis = 1L
        )

        KeePassRemoteRebase.applyReadyChanges(latestRemote, plan)
    }

    @Test
    fun rebaseReturnsNullWhenThereAreNoReadyChanges() {
        val plan = KeePassPendingFlushPlan.empty(DATABASE_ID, now = 1L)

        val result = KeePassRemoteRebase.applyReadyChanges(
            remoteDatabase = databaseWithEntry(
                entryUuid = UUID.randomUUID(),
                title = "Base title",
                url = "https://remote.example",
                password = "remote-password"
            ),
            pendingPlan = plan
        )

        assertNull(result)
    }

    private fun databaseWithEntry(
        entryUuid: UUID,
        title: String,
        url: String,
        password: String
    ): KeePassDatabase {
        return KeePassDatabase.Ver4x.create(
            rootName = "Root",
            meta = Meta(generator = "Monica rebase test", name = "Remote rebase fixture"),
            credentials = Credentials.from(EncryptedValue.fromString("fixture-password"))
        ).modifyParentGroup {
            copy(
                entries = entries + Entry(
                    uuid = entryUuid,
                    fields = EntryFields.of(
                        "Title" to EntryValue.Plain(title),
                        "URL" to EntryValue.Plain(url),
                        "Password" to EntryValue.Encrypted(EncryptedValue.fromString(password))
                    )
                )
            )
        }
    }

    private fun fieldPatchChangeSet(
        entryUuid: UUID,
        baseTitle: String,
        newTitle: String
    ): KeePassChangeSet {
        return KeePassChangeSet(
            changeId = "title-change",
            databaseId = DATABASE_ID,
            target = KeePassChangeTarget.PASSWORD,
            operation = KeePassChangeOperation.FIELD_PATCH,
            entryUuid = entryUuid.toString(),
            baseFingerprint = "base-fingerprint",
            fieldPatch = KeePassFieldChangePatch(
                managedScope = KeePassManagedFieldScope.PASSWORD,
                replacementFields = listOf(KeePassFieldChange("Title", newTitle)),
                baseFields = listOf(KeePassFieldBaseValue("Title", baseTitle))
            )
        )
    }

    private companion object {
        const val DATABASE_ID = 42L
    }
}
