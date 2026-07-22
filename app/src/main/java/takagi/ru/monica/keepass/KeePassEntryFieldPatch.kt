package takagi.ru.monica.keepass

import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue

class KeePassEntryFieldPatch private constructor(
    private val replacementFields: Map<String, EntryValue>,
    private val removeManagedField: (String) -> Boolean,
    private val removeFieldNames: Set<String>
) {
    fun applyTo(entry: Entry): Entry {
        val removeFieldKeys = removeFieldNames.mapTo(mutableSetOf()) { KeePassFieldRegistry.normalize(it) }
        val updatedFields = linkedMapOf<String, EntryValue>()

        entry.fields.forEach { (name, value) ->
            val shouldRemove =
                KeePassFieldRegistry.normalize(name) in removeFieldKeys ||
                    shouldRemoveManagedField(name)
            if (!shouldRemove) {
                updatedFields[name] = value
            }
        }
        updatedFields.putAll(replacementFields)

        return entry.copy(
            fields = EntryFields.of(*updatedFields.toList().toTypedArray())
        )
    }

    private fun shouldRemoveManagedField(name: String): Boolean {
        if (!removeManagedField(name)) return false
        return when (KeePassFieldRegistry.roleOf(name)) {
            KeePassFieldRole.MONICA_PASSWORD,
            KeePassFieldRole.MONICA_SECURE_ITEM,
            KeePassFieldRole.MONICA_PASSKEY -> true
            else -> false
        }
    }

    fun toChangePatch(
        managedScope: KeePassManagedFieldScope,
        baseEntry: Entry? = null
    ): KeePassFieldChangePatch {
        return KeePassFieldChangePatch(
            managedScope = managedScope,
            replacementFields = replacementFields.map { (name, value) ->
                KeePassFieldChange(
                    name = name,
                    value = runCatching { value.content }.getOrDefault(""),
                    protected = value is EntryValue.Encrypted
                )
            },
            removeFieldNames = removeFieldNames.toList(),
            baseFields = baseEntry?.let { buildBaseFields(it) }.orEmpty()
        )
    }

    private fun buildBaseFields(entry: Entry): List<KeePassFieldBaseValue> {
        val touchedNames = linkedSetOf<String>()
        val explicitRemoveKeys = removeFieldNames.mapTo(mutableSetOf()) { KeePassFieldRegistry.normalize(it) }
        replacementFields.keys.forEach { touchedNames += it }
        removeFieldNames.forEach { touchedNames += it }
        entry.fields.forEach { (name, _) ->
            if (removeManagedField(name) || KeePassFieldRegistry.normalize(name) in explicitRemoveKeys) {
                touchedNames += name
            }
        }

        val existingByNormalized = linkedMapOf<String, Pair<String, EntryValue>>()
        entry.fields.forEach { (name, value) ->
            existingByNormalized[KeePassFieldRegistry.normalize(name)] = name to value
        }
        return touchedNames
            .mapNotNull { name -> name.trim().takeIf(String::isNotBlank) }
            .distinctBy { KeePassFieldRegistry.normalize(it) }
            .map { name ->
                val existing = existingByNormalized[KeePassFieldRegistry.normalize(name)]
                if (existing == null) {
                    KeePassFieldBaseValue(
                        name = name,
                        present = false
                    )
                } else {
                    val (existingName, value) = existing
                    KeePassFieldBaseValue(
                        name = existingName,
                        value = runCatching { value.content }.getOrDefault(""),
                        protected = value is EntryValue.Encrypted,
                        present = true
                    )
                }
            }
    }

    companion object {
        fun fromEntryFields(
            replacementFields: EntryFields,
            removeManagedField: (String) -> Boolean,
            removeFieldNames: Iterable<String> = emptyList()
        ): KeePassEntryFieldPatch {
            return KeePassEntryFieldPatch(
                replacementFields = replacementFields.toMap(),
                removeManagedField = removeManagedField,
                removeFieldNames = removeFieldNames
                    .mapNotNull { it.trim().takeIf(String::isNotBlank) }
                    .toSet()
            )
        }
    }
}
