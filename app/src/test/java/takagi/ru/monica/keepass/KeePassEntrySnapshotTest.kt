package takagi.ru.monica.keepass

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.models.BinaryReference
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class KeePassEntrySnapshotTest {

    @Test
    fun snapshotPreservesFieldRolesAndBinaryMetadata() {
        val uuid = UUID.randomUUID()
        val entry = Entry(
            uuid = uuid,
            fields = EntryFields.of(
                "Title" to EntryValue.Plain("GitHub"),
                "Password" to EntryValue.Encrypted(EncryptedValue.fromString("secret")),
                "otp" to EntryValue.Plain("otpauth://totp/GitHub:user?secret=ABC"),
                "KPEX_PASSKEY_CREDENTIAL_ID" to EntryValue.Encrypted(EncryptedValue.fromString("credential")),
                "_etm_template" to EntryValue.Plain("1"),
                "Security question" to EntryValue.Plain("First pet?")
            ),
            binaries = listOf(BinaryReference(hash = ByteArray(32) { 7 }.toByteString(), name = "recovery.pdf"))
        )

        val snapshot = KeePassEntrySnapshot.fromEntry(
            entry = entry,
            groupPath = "Accounts/GitHub",
            preservedMetadata = KeePassEntryPreservedMetadata(
                historyCount = 2,
                tags = setOf("work"),
                customDataKeys = setOf("plugin-state")
            )
        )
        val projection = snapshot.toProjection()

        assertEquals(uuid, snapshot.uuid)
        assertEquals("Accounts/GitHub", snapshot.groupPath)
        assertEquals(KeePassFieldRole.KEEPASS_TOTP, snapshot.fields.getValue("otp").role)
        assertEquals(KeePassFieldRole.KEEPASS_PASSKEY, snapshot.fields.getValue("KPEX_PASSKEY_CREDENTIAL_ID").role)
        assertEquals(KeePassFieldRole.KEEPASS_PLUGIN, snapshot.fields.getValue("_etm_template").role)
        assertEquals(KeePassFieldRole.UNKNOWN, snapshot.fields.getValue("Security question").role)
        assertTrue(snapshot.fields.getValue("Password").isProtected)
        assertEquals(1, snapshot.binaries.size)
        assertEquals(2, snapshot.preservedMetadata.historyCount)

        assertEquals(setOf("Title", "Password"), projection.standardFields.keys)
        assertEquals(setOf("otp"), projection.totpFields.keys)
        assertEquals(setOf("KPEX_PASSKEY_CREDENTIAL_ID"), projection.passkeyFields.keys)
        assertEquals(setOf("_etm_template"), projection.pluginFields.keys)
        assertEquals(setOf("Security question"), projection.unknownFields.keys)
        assertEquals(1, projection.binaryCount)
    }

    @Test
    fun fingerprintChangesWhenPreservedDataChanges() {
        val entry = Entry(
            uuid = UUID.randomUUID(),
            fields = EntryFields.of("Title" to EntryValue.Plain("GitHub"))
        )

        val first = KeePassEntrySnapshot.fromEntry(entry)
        val second = KeePassEntrySnapshot.fromEntry(
            entry = entry,
            preservedMetadata = KeePassEntryPreservedMetadata(tags = setOf("changed"))
        )

        assertNotEquals(first.rawFingerprint, second.rawFingerprint)
    }
}

