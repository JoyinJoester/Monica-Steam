package takagi.ru.monica.keepass

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.database.modifiers.binaries
import app.keemobile.kotpass.database.modifiers.modifyBinaries
import app.keemobile.kotpass.database.modifiers.modifyParentGroup
import app.keemobile.kotpass.models.BinaryData
import app.keemobile.kotpass.models.BinaryReference
import app.keemobile.kotpass.models.CustomDataValue
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Group
import app.keemobile.kotpass.models.Meta
import app.keemobile.kotpass.models.TimeData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.utils.KeePassRawStringField
import takagi.ru.monica.utils.KeePassCodecSupport
import takagi.ru.monica.utils.extractKeePassCustomFieldsForPasswordEntry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID

class KeePassKdbxFixtureIntegrationTest {

    @Test
    fun kdbxRoundTripPreservesOtpUnknownPluginAttachmentHistoryAndMetadataAfterPatch() {
        val credentials = Credentials.from(EncryptedValue.fromString("fixture-password"))
        val attachment = BinaryData.Uncompressed(
            memoryProtection = false,
            rawContent = "recovery attachment".toByteArray()
        )
        val entryUuid = UUID.randomUUID()
        val passkeyUuid = UUID.randomUUID()
        val originalEntry = fixtureEntry(entryUuid, attachment)
        val originalPasskeyEntry = fixturePasskeyEntry(passkeyUuid)
        val originalDatabase = KeePassDatabase.Ver4x.create(
            rootName = "Root",
            meta = Meta(generator = "Monica fixture", name = "G02 fixture"),
            credentials = credentials
        )
            .modifyBinaries { binaries -> binaries + (attachment.hash to attachment) }
            .modifyParentGroup { copy(entries = entries + originalEntry + originalPasskeyEntry) }

        val decodedOriginal = decode(encode(originalDatabase), credentials)
        val originalAfterFileRoundTrip = decodedOriginal.content.group.entries.single { it.uuid == entryUuid }
        val passkeyAfterFileRoundTrip = decodedOriginal.content.group.entries.single { it.uuid == passkeyUuid }
        assertFixtureEntryPreserved(originalAfterFileRoundTrip, decodedOriginal, attachment)
        assertFixturePasskeyEntryPreserved(passkeyAfterFileRoundTrip)

        val patchedEntry = KeePassEntryFieldPatch.fromEntryFields(
            replacementFields = EntryFields.of(
                "Title" to EntryValue.Plain("Renamed GitHub"),
                "Notes" to EntryValue.Plain("patched notes")
            ),
            removeManagedField = KeePassFieldRegistry::isPasswordEntryOverlayField,
            removeFieldNames = setOf("Title", "Notes")
        ).applyTo(originalAfterFileRoundTrip)
        val patchedDatabase = decodedOriginal.modifyParentGroup {
            copy(entries = entries.map { entry ->
                if (entry.uuid == entryUuid) patchedEntry else entry
            })
        }

        val decodedPatched = decode(encode(patchedDatabase), credentials)
        val patchedAfterFileRoundTrip = decodedPatched.content.group.entries.single { it.uuid == entryUuid }
        val passkeyAfterPasswordPatchRoundTrip = decodedPatched.content.group.entries.single { it.uuid == passkeyUuid }

        assertEquals("Renamed GitHub", patchedAfterFileRoundTrip.fields.getValue("Title").content)
        assertEquals("patched notes", patchedAfterFileRoundTrip.fields.getValue("Notes").content)
        assertFixtureEntryPreserved(patchedAfterFileRoundTrip, decodedPatched, attachment)
        assertFixtureCustomFieldsExtracted(patchedAfterFileRoundTrip)
        assertFixturePasskeyEntryPreserved(passkeyAfterPasswordPatchRoundTrip)
    }

    @Test
    fun groupTreeChangeSetCreatesAndDeletesSubtreeWithHistoryCustomDataAndTimes() {
        val credentials = Credentials.from(EncryptedValue.fromString("fixture-password"))
        val archiveUuid = UUID.randomUUID()
        val groupUuid = UUID.randomUUID()
        val childGroupUuid = UUID.randomUUID()
        val entryUuid = UUID.randomUUID()
        val historyUuid = UUID.randomUUID()
        val now = Instant.parse("2026-06-25T00:00:00Z")
        val baseDatabase = KeePassDatabase.Ver4x.create(
            rootName = "Root",
            meta = Meta(generator = "Monica fixture", name = "Group tree fixture"),
            credentials = credentials
        ).modifyParentGroup {
            copy(groups = groups + Group(uuid = archiveUuid, name = "Archive"))
        }
        val groupTreePatch = KeePassGroupTreeChangePatch(
            root = KeePassGroupTreeSnapshot(
                uuid = groupUuid.toString(),
                name = "Moved",
                tags = listOf("group-tag"),
                times = KeePassTimesPatch(
                    creationTimeEpochMillis = now.minusSeconds(300).toEpochMilli(),
                    lastModificationTimeEpochMillis = now.minusSeconds(200).toEpochMilli(),
                    lastAccessTimeEpochMillis = now.minusSeconds(100).toEpochMilli(),
                    locationChangedEpochMillis = now.minusSeconds(50).toEpochMilli(),
                    expires = false,
                    usageCount = 4
                ),
                customData = listOf(KeePassCustomDataPatch("group-plugin", "group state", now.toEpochMilli())),
                entries = listOf(
                    KeePassEntryTreeSnapshot(
                        uuid = entryUuid.toString(),
                        fields = listOf(
                            KeePassFieldChange("Title", "Moved Login"),
                            KeePassFieldChange("Password", "secret", protected = true),
                            KeePassFieldChange("External Plugin Field", "must stay")
                        ),
                        history = listOf(
                            KeePassEntryTreeSnapshot(
                                uuid = historyUuid.toString(),
                                fields = listOf(KeePassFieldChange("Title", "Old Login"))
                            )
                        ),
                        tags = listOf("entry-tag"),
                        times = KeePassTimesPatch(usageCount = 9),
                        customData = listOf(KeePassCustomDataPatch("entry-plugin", "entry state", now.toEpochMilli()))
                    )
                ),
                groups = listOf(
                    KeePassGroupTreeSnapshot(
                        uuid = childGroupUuid.toString(),
                        name = "Child",
                        entries = listOf(
                            KeePassEntryTreeSnapshot(
                                uuid = UUID.randomUUID().toString(),
                                fields = listOf(KeePassFieldChange("Title", "Nested Login"))
                            )
                        )
                    )
                )
            ),
            sourceRootGroupUuid = groupUuid.toString(),
            targetParentGroupUuid = archiveUuid.toString()
        )
        val createChangeSet = KeePassChangeSet(
            changeId = "create-group-tree",
            databaseId = 42,
            target = KeePassChangeTarget.GROUP,
            operation = KeePassChangeOperation.CREATE_GROUP_TREE,
            entryUuid = null,
            baseFingerprint = null,
            structurePatch = KeePassStructureChangePatch(
                targetGroupPath = "Archive",
                targetGroupUuid = archiveUuid.toString(),
                groupName = "Moved"
            ),
            groupTreePatch = groupTreePatch
        )
        val deleteChangeSet = createChangeSet.copy(
            changeId = "delete-group-tree",
            operation = KeePassChangeOperation.DELETE_GROUP_TREE,
            structurePatch = KeePassStructureChangePatch(
                sourceGroupPath = "Archive/Moved",
                sourceGroupUuid = groupUuid.toString(),
                groupName = "Moved"
            )
        )
        val applier = KeePassChangeSetApplier()

        val createdDatabase = applier.apply(baseDatabase, createChangeSet).updatedDatabase
        val createdGroup = findGroup(createdDatabase.content.group, groupUuid)!!
        val createdEntry = createdGroup.entries.single { it.uuid == entryUuid }

        assertEquals("Moved", createdGroup.name)
        assertEquals(listOf("group-tag"), createdGroup.tags)
        assertEquals(4, createdGroup.times!!.usageCount)
        assertEquals("group state", createdGroup.customData.getValue("group-plugin").value)
        assertEquals("Moved Login", createdEntry.fields.getValue("Title").content)
        assertEquals("secret", createdEntry.fields.getValue("Password").content)
        assertEquals("must stay", createdEntry.fields.getValue("External Plugin Field").content)
        assertEquals(listOf("entry-tag"), createdEntry.tags)
        assertEquals(9, createdEntry.times!!.usageCount)
        assertEquals("entry state", createdEntry.customData.getValue("entry-plugin").value)
        assertEquals("Old Login", createdEntry.history.single().fields.getValue("Title").content)
        assertEquals("Child", createdGroup.groups.single { it.uuid == childGroupUuid }.name)

        val deletedDatabase = applier.apply(createdDatabase, deleteChangeSet).updatedDatabase

        assertNull(findGroup(deletedDatabase.content.group, groupUuid))
    }

    private fun fixtureEntry(
        entryUuid: UUID,
        attachment: BinaryData
    ): Entry {
        val now = Instant.parse("2026-06-24T00:00:00Z")
        val historyEntry = Entry(
            uuid = UUID.randomUUID(),
            fields = EntryFields.of(
                "Title" to EntryValue.Plain("Historical title"),
                "TOTP Seed" to EntryValue.Encrypted(EncryptedValue.fromString("JBSWY3DPEHPK3PXP"))
            )
        )
        return Entry(
            uuid = entryUuid,
            fields = EntryFields.of(
                "Title" to EntryValue.Plain("GitHub"),
                "UserName" to EntryValue.Plain("octocat"),
                "Password" to EntryValue.Encrypted(EncryptedValue.fromString("old-password")),
                "URL" to EntryValue.Plain("https://github.com"),
                "Notes" to EntryValue.Plain("original notes"),
                "otp" to EntryValue.Plain("otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&issuer=GitHub"),
                "TOTP Seed" to EntryValue.Encrypted(EncryptedValue.fromString("JBSWY3DPEHPK3PXP")),
                "TOTP Settings" to EntryValue.Plain("period=30;digits=6;algorithm=SHA1"),
                "HOTP Counter" to EntryValue.Plain("9"),
                "_etm_plugin_state" to EntryValue.Plain("plugin must stay"),
                "External Unknown Field" to EntryValue.Plain("unknown must stay"),
                "Recovery PIN" to EntryValue.Encrypted(EncryptedValue.fromString("123456"))
            ),
            binaries = listOf(BinaryReference(hash = attachment.hash, name = "recovery.txt")),
            history = listOf(historyEntry),
            tags = listOf("work", "totp"),
            customData = mapOf("plugin-state" to CustomDataValue("custom must stay", now)),
            times = TimeData(
                creationTime = now.minusSeconds(300),
                lastAccessTime = now.minusSeconds(200),
                lastModificationTime = now.minusSeconds(100),
                locationChanged = now.minusSeconds(50),
                expiryTime = now.plusSeconds(86_400),
                expires = false,
                usageCount = 7
            )
        )
    }

    private fun fixturePasskeyEntry(entryUuid: UUID): Entry {
        val now = Instant.parse("2026-06-24T00:10:00Z")
        return Entry(
            uuid = entryUuid,
            fields = EntryFields.of(
                "Title" to EntryValue.Plain("GitHub [Passkey]"),
                "UserName" to EntryValue.Plain("octocat"),
                "Password" to EntryValue.Encrypted(EncryptedValue.fromString("")),
                "URL" to EntryValue.Plain("https://github.com"),
                "Notes" to EntryValue.Plain("passkey notes"),
                "MonicaPasskeyCredentialId" to EntryValue.Plain("legacy-monica-credential"),
                "MonicaPasskeyMode" to EntryValue.Plain("KEEPASS_COMPAT"),
                "MonicaPasskeyData" to EntryValue.Encrypted(EncryptedValue.fromString("""{"credentialId":"legacy-monica-credential"}""")),
                "Passkey" to EntryValue.Plain(""),
                "KPEX_PASSKEY_USERNAME" to EntryValue.Plain("octocat"),
                "KPEX_PASSKEY_PRIVATE_KEY_PEM" to EntryValue.Encrypted(EncryptedValue.fromString("-----BEGIN PRIVATE KEY-----\nfixture\n-----END PRIVATE KEY-----")),
                "KPEX_PASSKEY_CREDENTIAL_ID" to EntryValue.Encrypted(EncryptedValue.fromString("legacy-monica-credential")),
                "KPEX_PASSKEY_USER_HANDLE" to EntryValue.Encrypted(EncryptedValue.fromString("github-user-handle")),
                "KPEX_PASSKEY_RELYING_PARTY" to EntryValue.Plain("github.com"),
                "KPEX_PASSKEY_FLAG_BE" to EntryValue.Plain("true"),
                "KPEX_PASSKEY_FLAG_BS" to EntryValue.Plain("false"),
                "External Passkey Plugin Field" to EntryValue.Plain("passkey plugin must stay")
            ),
            tags = listOf("passkey"),
            customData = mapOf("passkey-plugin-state" to CustomDataValue("passkey custom must stay", now)),
            times = TimeData(
                creationTime = now.minusSeconds(300),
                lastAccessTime = now.minusSeconds(200),
                lastModificationTime = now.minusSeconds(100),
                locationChanged = now.minusSeconds(50),
                expiryTime = now.plusSeconds(86_400),
                expires = false,
                usageCount = 3
            )
        )
    }

    private fun assertFixtureEntryPreserved(
        entry: Entry,
        database: KeePassDatabase,
        attachment: BinaryData
    ) {
        assertEquals("octocat", entry.fields.getValue("UserName").content)
        assertEquals("old-password", entry.fields.getValue("Password").content)
        assertEquals("https://github.com", entry.fields.getValue("URL").content)
        assertEquals(
            "otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&issuer=GitHub",
            entry.fields.getValue("otp").content
        )
        assertEquals("JBSWY3DPEHPK3PXP", entry.fields.getValue("TOTP Seed").content)
        assertEquals("period=30;digits=6;algorithm=SHA1", entry.fields.getValue("TOTP Settings").content)
        assertEquals("9", entry.fields.getValue("HOTP Counter").content)
        assertEquals("plugin must stay", entry.fields.getValue("_etm_plugin_state").content)
        assertEquals("unknown must stay", entry.fields.getValue("External Unknown Field").content)
        assertEquals("123456", entry.fields.getValue("Recovery PIN").content)
        assertEquals(listOf("work", "totp"), entry.tags)
        assertEquals(7, entry.times!!.usageCount)
        assertEquals("custom must stay", entry.customData.getValue("plugin-state").value)
        assertEquals(1, entry.history.size)
        assertEquals("Historical title", entry.history.single().fields.getValue("Title").content)
        assertEquals(1, entry.binaries.size)
        assertEquals("recovery.txt", entry.binaries.single().name)
        assertTrue(database.binaries.containsKey(attachment.hash))
        val expectedAttachment = attachment.inputStream().use { it.readBytes() }
        val actualAttachment = database.binaries.getValue(attachment.hash).inputStream().use { it.readBytes() }
        assertArrayEquals(expectedAttachment, actualAttachment)
    }

    private fun assertFixtureCustomFieldsExtracted(entry: Entry) {
        val customFields = extractKeePassCustomFieldsForPasswordEntry(
            entry.fields.map { (key, value) ->
                KeePassRawStringField(
                    key = key,
                    value = value.content,
                    isProtected = value is EntryValue.Encrypted
                )
            }
        )

        assertEquals(listOf("External Unknown Field", "Recovery PIN"), customFields.map { it.title })
        assertEquals("unknown must stay", customFields[0].value)
        assertEquals(false, customFields[0].isProtected)
        assertEquals("123456", customFields[1].value)
        assertEquals(true, customFields[1].isProtected)
    }

    private fun assertFixturePasskeyEntryPreserved(entry: Entry) {
        assertEquals("GitHub [Passkey]", entry.fields.getValue("Title").content)
        assertEquals("octocat", entry.fields.getValue("UserName").content)
        assertEquals("https://github.com", entry.fields.getValue("URL").content)
        assertEquals("passkey notes", entry.fields.getValue("Notes").content)
        assertEquals("legacy-monica-credential", entry.fields.getValue("MonicaPasskeyCredentialId").content)
        assertEquals("KEEPASS_COMPAT", entry.fields.getValue("MonicaPasskeyMode").content)
        assertEquals("""{"credentialId":"legacy-monica-credential"}""", entry.fields.getValue("MonicaPasskeyData").content)
        assertEquals("", entry.fields.getValue("Passkey").content)
        assertEquals("octocat", entry.fields.getValue("KPEX_PASSKEY_USERNAME").content)
        assertEquals("-----BEGIN PRIVATE KEY-----\nfixture\n-----END PRIVATE KEY-----", entry.fields.getValue("KPEX_PASSKEY_PRIVATE_KEY_PEM").content)
        assertEquals("legacy-monica-credential", entry.fields.getValue("KPEX_PASSKEY_CREDENTIAL_ID").content)
        assertEquals("github-user-handle", entry.fields.getValue("KPEX_PASSKEY_USER_HANDLE").content)
        assertEquals("github.com", entry.fields.getValue("KPEX_PASSKEY_RELYING_PARTY").content)
        assertEquals("true", entry.fields.getValue("KPEX_PASSKEY_FLAG_BE").content)
        assertEquals("false", entry.fields.getValue("KPEX_PASSKEY_FLAG_BS").content)
        assertEquals("passkey plugin must stay", entry.fields.getValue("External Passkey Plugin Field").content)
        assertEquals(listOf("passkey"), entry.tags)
        assertEquals(3, entry.times!!.usageCount)
        assertEquals("passkey custom must stay", entry.customData.getValue("passkey-plugin-state").value)
    }

    private fun findGroup(group: Group, uuid: UUID): Group? {
        if (group.uuid == uuid) return group
        group.groups.forEach { child ->
            val match = findGroup(child, uuid)
            if (match != null) return match
        }
        return null
    }

    private fun encode(database: KeePassDatabase): ByteArray {
        return ByteArrayOutputStream().use { output ->
            database.encode(output, cipherProviders = KeePassCodecSupport.cipherProviders)
            output.toByteArray()
        }
    }

    private fun decode(bytes: ByteArray, credentials: Credentials): KeePassDatabase {
        return KeePassDatabase.decode(
            ByteArrayInputStream(bytes),
            credentials,
            cipherProviders = KeePassCodecSupport.cipherProviders
        )
    }
}
