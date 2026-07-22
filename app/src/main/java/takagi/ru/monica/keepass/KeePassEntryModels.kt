package takagi.ru.monica.keepass

import app.keemobile.kotpass.models.BinaryReference
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryValue
import java.security.MessageDigest
import java.util.UUID

data class KeePassEntryFieldSnapshot(
    val name: String,
    val value: String,
    val isProtected: Boolean,
    val role: KeePassFieldRole
)

data class KeePassEntryBinarySnapshot(
    val name: String,
    val hash: String
)

data class KeePassEntryPreservedMetadata(
    val historyCount: Int = 0,
    val tags: Set<String> = emptySet(),
    val customDataKeys: Set<String> = emptySet()
)

data class KeePassEntrySnapshot(
    val uuid: UUID,
    val groupPath: String?,
    val groupUuid: UUID?,
    val fields: Map<String, KeePassEntryFieldSnapshot>,
    val binaries: List<KeePassEntryBinarySnapshot>,
    val icon: String?,
    val timesFingerprint: String?,
    val preservedMetadata: KeePassEntryPreservedMetadata,
    val rawFingerprint: String
) {
    fun toProjection(): KeePassEntryProjection {
        return KeePassEntryProjection.fromSnapshot(this)
    }

    companion object {
        fun fromEntry(
            entry: Entry,
            groupPath: String? = null,
            groupUuid: UUID? = null,
            preservedMetadata: KeePassEntryPreservedMetadata = KeePassEntryPreservedMetadata()
        ): KeePassEntrySnapshot {
            val fieldSnapshots = linkedMapOf<String, KeePassEntryFieldSnapshot>()
            entry.fields.forEach { (name, value) ->
                fieldSnapshots[name] = KeePassEntryFieldSnapshot(
                    name = name,
                    value = value.safeContent(),
                    isProtected = value is EntryValue.Encrypted,
                    role = KeePassFieldRegistry.roleOf(name)
                )
            }
            val binarySnapshots = entry.binaries.map { it.toSnapshot() }
            val icon = runCatching { entry.icon.toString() }.getOrNull()
            val timesFingerprint = runCatching { entry.times.toString() }.getOrNull()
            return KeePassEntrySnapshot(
                uuid = entry.uuid,
                groupPath = groupPath,
                groupUuid = groupUuid,
                fields = fieldSnapshots,
                binaries = binarySnapshots,
                icon = icon,
                timesFingerprint = timesFingerprint,
                preservedMetadata = preservedMetadata,
                rawFingerprint = buildFingerprint(
                    uuid = entry.uuid,
                    groupPath = groupPath,
                    groupUuid = groupUuid,
                    fields = fieldSnapshots.values,
                    binaries = binarySnapshots,
                    icon = icon,
                    timesFingerprint = timesFingerprint,
                    preservedMetadata = preservedMetadata
                )
            )
        }

        private fun buildFingerprint(
            uuid: UUID,
            groupPath: String?,
            groupUuid: UUID?,
            fields: Collection<KeePassEntryFieldSnapshot>,
            binaries: List<KeePassEntryBinarySnapshot>,
            icon: String?,
            timesFingerprint: String?,
            preservedMetadata: KeePassEntryPreservedMetadata
        ): String {
            val digest = MessageDigest.getInstance("SHA-256")
            fun update(value: String?) {
                digest.update((value.orEmpty() + "\u0000").toByteArray(Charsets.UTF_8))
            }

            update(uuid.toString())
            update(groupPath)
            update(groupUuid?.toString())
            fields.sortedBy { KeePassFieldRegistry.normalize(it.name) }.forEach { field ->
                update(field.name)
                update(field.value)
                update(field.isProtected.toString())
                update(field.role.name)
            }
            binaries.sortedWith(compareBy({ it.name }, { it.hash })).forEach { binary ->
                update(binary.name)
                update(binary.hash)
            }
            update(icon)
            update(timesFingerprint)
            update(preservedMetadata.historyCount.toString())
            preservedMetadata.tags.sorted().forEach(::update)
            preservedMetadata.customDataKeys.sorted().forEach(::update)
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}

data class KeePassEntryProjection(
    val uuid: UUID,
    val groupPath: String?,
    val standardFields: Map<String, KeePassEntryFieldSnapshot>,
    val monicaOwnedFields: Map<String, KeePassEntryFieldSnapshot>,
    val totpFields: Map<String, KeePassEntryFieldSnapshot>,
    val passkeyFields: Map<String, KeePassEntryFieldSnapshot>,
    val pluginFields: Map<String, KeePassEntryFieldSnapshot>,
    val unknownFields: Map<String, KeePassEntryFieldSnapshot>,
    val binaryCount: Int
) {
    companion object {
        fun fromSnapshot(snapshot: KeePassEntrySnapshot): KeePassEntryProjection {
            return KeePassEntryProjection(
                uuid = snapshot.uuid,
                groupPath = snapshot.groupPath,
                standardFields = snapshot.fields.filterByRole(KeePassFieldRole.STANDARD),
                monicaOwnedFields = snapshot.fields.filterValues { KeePassFieldRegistry.isMonicaOwned(it.name) },
                totpFields = snapshot.fields.filterByRole(KeePassFieldRole.KEEPASS_TOTP),
                passkeyFields = snapshot.fields.filterValues {
                    it.role == KeePassFieldRole.KEEPASS_PASSKEY || it.role == KeePassFieldRole.MONICA_PASSKEY
                },
                pluginFields = snapshot.fields.filterByRole(KeePassFieldRole.KEEPASS_PLUGIN),
                unknownFields = snapshot.fields.filterByRole(KeePassFieldRole.UNKNOWN),
                binaryCount = snapshot.binaries.size
            )
        }

        private fun Map<String, KeePassEntryFieldSnapshot>.filterByRole(
            role: KeePassFieldRole
        ): Map<String, KeePassEntryFieldSnapshot> {
            return filterValues { it.role == role }
        }
    }
}

private fun EntryValue.safeContent(): String {
    return runCatching { content }.getOrDefault("")
}

private fun BinaryReference.toSnapshot(): KeePassEntryBinarySnapshot {
    return KeePassEntryBinarySnapshot(
        name = name,
        hash = hash.toString()
    )
}
