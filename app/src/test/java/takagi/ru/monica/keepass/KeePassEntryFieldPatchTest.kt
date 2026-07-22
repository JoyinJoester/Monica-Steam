package takagi.ru.monica.keepass

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.models.BinaryReference
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeePassEntryFieldPatchTest {

    @Test
    fun passwordPatchPreservesTotpUnknownFieldsAndBinaries() {
        val entry = Entry(
            uuid = java.util.UUID.randomUUID(),
            fields = EntryFields.of(
                "Title" to EntryValue.Plain("Old title"),
                "Password" to EntryValue.Encrypted(EncryptedValue.fromString("old")),
                "otp" to EntryValue.Plain("otpauth://totp/GitHub:user?secret=ABC"),
                "Security question" to EntryValue.Plain("First pet?"),
                "_etm_template" to EntryValue.Plain("1")
            ),
            binaries = listOf(BinaryReference(hash = ByteArray(32) { 9 }.toByteString(), name = "recovery.pdf"))
        )
        val replacementFields = EntryFields.of(
            "Title" to EntryValue.Plain("New title"),
            "Password" to EntryValue.Encrypted(EncryptedValue.fromString("new"))
        )

        val patched = KeePassEntryFieldPatch.fromEntryFields(
            replacementFields = replacementFields,
            removeManagedField = KeePassFieldRegistry::isPasswordEntryOverlayField,
            removeFieldNames = replacementFields.keys
        ).applyTo(entry)

        assertEquals("New title", patched.fields.getValue("Title").content)
        assertEquals("new", patched.fields.getValue("Password").content)
        assertEquals("otpauth://totp/GitHub:user?secret=ABC", patched.fields.getValue("otp").content)
        assertEquals("First pet?", patched.fields.getValue("Security question").content)
        assertEquals("1", patched.fields.getValue("_etm_template").content)
        assertEquals(entry.binaries, patched.binaries)
    }

    @Test
    fun passwordRenameAndNotesPatchPreservesOtpCustomUnknownAndPluginFields() {
        val entry = Entry(
            uuid = java.util.UUID.randomUUID(),
            fields = EntryFields.of(
                "Title" to EntryValue.Plain("Old title"),
                "UserName" to EntryValue.Plain("old-user"),
                "Password" to EntryValue.Encrypted(EncryptedValue.fromString("old")),
                "URL" to EntryValue.Plain("https://example.com"),
                "Notes" to EntryValue.Plain("old notes"),
                "TOTP Seed" to EntryValue.Encrypted(EncryptedValue.fromString("JBSWY3DPEHPK3PXP")),
                "TOTP Settings" to EntryValue.Plain("period=30;digits=6;algorithm=SHA1"),
                "HOTP Counter" to EntryValue.Plain("7"),
                "Recovery Code" to EntryValue.Encrypted(EncryptedValue.fromString("must-stay-secret")),
                "External Unknown Field" to EntryValue.Plain("must stay"),
                "_etm_plugin_state" to EntryValue.Plain("must stay too")
            )
        )
        val replacementFields = EntryFields.of(
            "Title" to EntryValue.Plain("Renamed title"),
            "Notes" to EntryValue.Plain("new notes")
        )

        val patched = KeePassEntryFieldPatch.fromEntryFields(
            replacementFields = replacementFields,
            removeManagedField = KeePassFieldRegistry::isPasswordEntryOverlayField,
            removeFieldNames = replacementFields.keys
        ).applyTo(entry)

        assertEquals("Renamed title", patched.fields.getValue("Title").content)
        assertEquals("new notes", patched.fields.getValue("Notes").content)
        assertEquals("old-user", patched.fields.getValue("UserName").content)
        assertEquals("old", patched.fields.getValue("Password").content)
        assertEquals("https://example.com", patched.fields.getValue("URL").content)
        assertEquals("JBSWY3DPEHPK3PXP", patched.fields.getValue("TOTP Seed").content)
        assertEquals("period=30;digits=6;algorithm=SHA1", patched.fields.getValue("TOTP Settings").content)
        assertEquals("7", patched.fields.getValue("HOTP Counter").content)
        assertEquals("must-stay-secret", patched.fields.getValue("Recovery Code").content)
        assertEquals("must stay", patched.fields.getValue("External Unknown Field").content)
        assertEquals("must stay too", patched.fields.getValue("_etm_plugin_state").content)
    }

    @Test
    fun secureItemPatchDoesNotDeleteExternalUnknownFields() {
        val entry = Entry(
            uuid = java.util.UUID.randomUUID(),
            fields = EntryFields.of(
                "Title" to EntryValue.Plain("Old card"),
                "Card Number" to EntryValue.Encrypted(EncryptedValue.fromString("4111111111111111")),
                "External KeePass field" to EntryValue.Plain("must stay")
            )
        )
        val replacementFields = EntryFields.of(
            "Title" to EntryValue.Plain("New card"),
            "Card Number" to EntryValue.Encrypted(EncryptedValue.fromString("5555555555554444"))
        )

        val patched = KeePassEntryFieldPatch.fromEntryFields(
            replacementFields = replacementFields,
            removeManagedField = KeePassFieldRegistry::isSecureItemOverlayField,
            removeFieldNames = replacementFields.keys
        ).applyTo(entry)

        assertEquals("New card", patched.fields.getValue("Title").content)
        assertEquals("5555555555554444", patched.fields.getValue("Card Number").content)
        assertEquals("must stay", patched.fields.getValue("External KeePass field").content)
        assertTrue(patched.fields.keys.contains("External KeePass field"))
    }

    @Test
    fun passkeyPatchReplacesCompatFieldsAndPreservesExternalUnknownFields() {
        val entry = Entry(
            uuid = java.util.UUID.randomUUID(),
            fields = EntryFields.of(
                "Title" to EntryValue.Plain("Old passkey"),
                "MonicaPasskeyData" to EntryValue.Encrypted(EncryptedValue.fromString("old-json")),
                "KPEX_PASSKEY_CREDENTIAL_ID" to EntryValue.Encrypted(EncryptedValue.fromString("old-credential")),
                "External plugin state" to EntryValue.Plain("must stay")
            )
        )
        val replacementFields = EntryFields.of(
            "Title" to EntryValue.Plain("New passkey"),
            "MonicaPasskeyData" to EntryValue.Encrypted(EncryptedValue.fromString("new-json")),
            "KPEX_PASSKEY_CREDENTIAL_ID" to EntryValue.Encrypted(EncryptedValue.fromString("new-credential"))
        )

        val patched = KeePassEntryFieldPatch.fromEntryFields(
            replacementFields = replacementFields,
            removeManagedField = KeePassFieldRegistry::isPasskeyEntryOverlayField,
            removeFieldNames = replacementFields.keys
        ).applyTo(entry)

        assertEquals("New passkey", patched.fields.getValue("Title").content)
        assertEquals("new-json", patched.fields.getValue("MonicaPasskeyData").content)
        assertEquals("new-credential", patched.fields.getValue("KPEX_PASSKEY_CREDENTIAL_ID").content)
        assertEquals("must stay", patched.fields.getValue("External plugin state").content)
    }
}
