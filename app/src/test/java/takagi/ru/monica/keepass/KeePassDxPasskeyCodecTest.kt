package takagi.ru.monica.keepass

import app.keemobile.kotpass.models.EntryValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.PasskeyEntry

class KeePassDxPasskeyCodecTest {
    @Test
    fun buildCustomFieldPairsWritesKeePassDxCompatibleFields() {
        val fields = KeePassDxPasskeyCodec.buildCustomFieldPairs(
            passkey = passkey(
                credentialId = "credential-id",
                rpId = "GitHub.COM.",
                userId = "user-handle",
                userName = "octocat",
                privateKeyAlias = "stored-key",
                isBackedUp = true
            ),
            exportPrivateKeyPem = { "-----BEGIN PRIVATE KEY-----\nfixture\n-----END PRIVATE KEY-----" }
        ).toMap()

        assertEquals("", fields.getValue(KeePassDxPasskeyCodec.FIELD_PASSKEY).content)
        assertEquals("octocat", fields.getValue(KeePassDxPasskeyCodec.FIELD_USERNAME).content)
        assertEquals(
            "-----BEGIN PRIVATE KEY-----\nfixture\n-----END PRIVATE KEY-----",
            fields.getValue(KeePassDxPasskeyCodec.FIELD_PRIVATE_KEY).content
        )
        assertEquals("credential-id", fields.getValue(KeePassDxPasskeyCodec.FIELD_CREDENTIAL_ID).content)
        assertEquals("user-handle", fields.getValue(KeePassDxPasskeyCodec.FIELD_USER_HANDLE).content)
        assertEquals("github.com", fields.getValue(KeePassDxPasskeyCodec.FIELD_RELYING_PARTY).content)
        assertEquals("true", fields.getValue(KeePassDxPasskeyCodec.FIELD_FLAG_BE).content)
        assertEquals("true", fields.getValue(KeePassDxPasskeyCodec.FIELD_FLAG_BS).content)
        assertTrue(fields.getValue(KeePassDxPasskeyCodec.FIELD_PRIVATE_KEY) is EntryValue.Encrypted)
        assertTrue(fields.getValue(KeePassDxPasskeyCodec.FIELD_CREDENTIAL_ID) is EntryValue.Encrypted)
        assertTrue(fields.getValue(KeePassDxPasskeyCodec.FIELD_USER_HANDLE) is EntryValue.Encrypted)
    }

    @Test
    fun buildCustomFieldPairsFallsBackToExistingPrivateKeyWhenExportFails() {
        val fields = KeePassDxPasskeyCodec.buildCustomFieldPairs(
            passkey = passkey(
                credentialId = "credential-id",
                rpId = "example.com",
                userId = "",
                userName = "alice",
                privateKeyAlias = "non-exportable-key",
                isBackedUp = false
            ),
            existingFieldValue = { fieldName ->
                when (fieldName) {
                    KeePassDxPasskeyCodec.FIELD_PRIVATE_KEY -> "existing-private-key"
                    KeePassDxPasskeyCodec.FIELD_USER_HANDLE -> "existing-user-handle"
                    KeePassDxPasskeyCodec.FIELD_FLAG_BE -> "true"
                    KeePassDxPasskeyCodec.FIELD_FLAG_BS -> "true"
                    else -> ""
                }
            },
            exportPrivateKeyPem = { null }
        ).toMap()

        assertEquals("existing-private-key", fields.getValue(KeePassDxPasskeyCodec.FIELD_PRIVATE_KEY).content)
        assertEquals("existing-user-handle", fields.getValue(KeePassDxPasskeyCodec.FIELD_USER_HANDLE).content)
        assertEquals("true", fields.getValue(KeePassDxPasskeyCodec.FIELD_FLAG_BE).content)
        assertEquals("true", fields.getValue(KeePassDxPasskeyCodec.FIELD_FLAG_BS).content)
    }

    private fun passkey(
        credentialId: String,
        rpId: String,
        userId: String,
        userName: String,
        privateKeyAlias: String,
        isBackedUp: Boolean
    ): PasskeyEntry {
        return PasskeyEntry(
            credentialId = credentialId,
            rpId = rpId,
            rpName = rpId,
            userId = userId,
            userName = userName,
            userDisplayName = userName,
            publicKey = "",
            privateKeyAlias = privateKeyAlias,
            isBackedUp = isBackedUp,
            passkeyMode = PasskeyEntry.MODE_KEEPASS_COMPAT
        )
    }
}
