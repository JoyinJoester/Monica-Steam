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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class KeePassChangeSetApplierConflictTest {
    @Test
    fun fieldPatchThrowsConflictWhenRemoteTouchedSameBaseField() {
        val entryUuid = UUID.randomUUID()
        val remoteChangedDatabase = databaseWithEntry(
            entryUuid = entryUuid,
            title = "Remote title",
            password = "original-password"
        )
        val changeSet = renameTitleChangeSet(
            entryUuid = entryUuid,
            baseTitle = "Base title",
            newTitle = "Local title"
        )

        try {
            KeePassChangeSetApplier().apply(remoteChangedDatabase, changeSet)
            fail("Expected KeePassChangeConflictException")
        } catch (error: KeePassChangeConflictException) {
            assertTrue(error.message.orEmpty().contains("Remote changed field(s): Title"))
        }
    }

    @Test
    fun fieldPatchAppliesWhenRemoteStillMatchesBaseField() {
        val entryUuid = UUID.randomUUID()
        val baseDatabase = databaseWithEntry(
            entryUuid = entryUuid,
            title = "Base title",
            password = "original-password"
        )
        val changeSet = renameTitleChangeSet(
            entryUuid = entryUuid,
            baseTitle = "Base title",
            newTitle = "Local title"
        )

        val updatedDatabase = KeePassChangeSetApplier().apply(baseDatabase, changeSet).updatedDatabase
        val updatedEntry = updatedDatabase.content.group.entries.single { it.uuid == entryUuid }

        assertEquals("Local title", updatedEntry.fields.getValue("Title").content)
        assertEquals("original-password", updatedEntry.fields.getValue("Password").content)
    }

    private fun databaseWithEntry(
        entryUuid: UUID,
        title: String,
        password: String
    ): KeePassDatabase {
        return KeePassDatabase.Ver4x.create(
            rootName = "Root",
            meta = Meta(generator = "Monica conflict test", name = "Conflict fixture"),
            credentials = Credentials.from(EncryptedValue.fromString("fixture-password"))
        ).modifyParentGroup {
            copy(
                entries = entries + Entry(
                    uuid = entryUuid,
                    fields = EntryFields.of(
                        "Title" to EntryValue.Plain(title),
                        "UserName" to EntryValue.Plain("octocat"),
                        "Password" to EntryValue.Encrypted(EncryptedValue.fromString(password))
                    )
                )
            )
        }
    }

    private fun renameTitleChangeSet(
        entryUuid: UUID,
        baseTitle: String,
        newTitle: String
    ): KeePassChangeSet {
        return KeePassChangeSet(
            changeId = "rename-title",
            databaseId = 42,
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
}
