package takagi.ru.monica.keepass

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class KeePassChangeSet(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val changeId: String = UUID.randomUUID().toString(),
    val databaseId: Long,
    val target: KeePassChangeTarget,
    val operation: KeePassChangeOperation,
    val entryUuid: String?,
    val baseFingerprint: String?,
    val baseGroupPath: String? = null,
    val baseGroupUuid: String? = null,
    val entryPatch: KeePassEntryCreatePatch? = null,
    val fieldPatch: KeePassFieldChangePatch? = null,
    val structurePatch: KeePassStructureChangePatch? = null,
    val attachmentPatch: KeePassAttachmentChangePatch? = null,
    val groupTreePatch: KeePassGroupTreeChangePatch? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported KeePass change set schema: $schemaVersion"
        }
        require(databaseId > 0) { "KeePass change set requires a database id" }
        require(operation != KeePassChangeOperation.FIELD_PATCH || fieldPatch != null) {
            "FIELD_PATCH requires fieldPatch"
        }
        require(operation != KeePassChangeOperation.CREATE_ENTRY || entryPatch != null) {
            "CREATE_ENTRY requires entryPatch"
        }
        require(!operation.isStructureOperation() || structurePatch != null) {
            "${operation.name} requires structurePatch"
        }
        require(!operation.isAttachmentOperation() || attachmentPatch != null) {
            "${operation.name} requires attachmentPatch"
        }
        require(!operation.isGroupTreeOperation() || groupTreePatch != null) {
            "${operation.name} requires groupTreePatch"
        }
        require(
            operation != KeePassChangeOperation.ADD_ATTACHMENT ||
                !attachmentPatch?.contentBase64.isNullOrBlank()
        ) {
            "ADD_ATTACHMENT requires contentBase64"
        }
    }

    fun isEntryScoped(): Boolean = entryUuid != null

    fun isTrashOperation(): Boolean {
        return operation == KeePassChangeOperation.MOVE_TO_RECYCLE_BIN ||
            operation == KeePassChangeOperation.RESTORE_FROM_RECYCLE_BIN
    }

    fun requiresBaseFingerprint(): Boolean {
        return operation != KeePassChangeOperation.CREATE_ENTRY &&
            operation != KeePassChangeOperation.CREATE_GROUP &&
            operation != KeePassChangeOperation.RENAME_GROUP &&
            operation != KeePassChangeOperation.DELETE_GROUP &&
            operation != KeePassChangeOperation.MOVE_GROUP &&
            operation != KeePassChangeOperation.CREATE_GROUP_TREE &&
            operation != KeePassChangeOperation.DELETE_GROUP_TREE
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class KeePassEntryCreatePatch(
    val targetGroupPath: String? = null,
    val targetGroupUuid: String? = null,
    val fields: List<KeePassFieldChange>,
    val iconName: String? = null
) {
    init {
        require(fields.isNotEmpty()) { "CREATE_ENTRY requires at least one field" }
    }
}

@Serializable
enum class KeePassChangeTarget {
    PASSWORD,
    SECURE_ITEM,
    PASSKEY,
    GROUP,
    UNKNOWN_ENTRY
}

@Serializable
enum class KeePassChangeOperation {
    CREATE_ENTRY,
    FIELD_PATCH,
    MOVE_ENTRY,
    MOVE_TO_RECYCLE_BIN,
    RESTORE_FROM_RECYCLE_BIN,
    PERMANENT_DELETE,
    ADD_ATTACHMENT,
    REMOVE_ATTACHMENT,
    CREATE_GROUP,
    RENAME_GROUP,
    DELETE_GROUP,
    MOVE_GROUP,
    CREATE_GROUP_TREE,
    DELETE_GROUP_TREE;

    fun isStructureOperation(): Boolean {
        return this in setOf(
            MOVE_ENTRY,
            MOVE_TO_RECYCLE_BIN,
            RESTORE_FROM_RECYCLE_BIN,
            PERMANENT_DELETE,
            CREATE_GROUP,
            RENAME_GROUP,
            DELETE_GROUP,
            MOVE_GROUP,
            CREATE_GROUP_TREE,
            DELETE_GROUP_TREE
        )
    }

    fun isAttachmentOperation(): Boolean {
        return this == ADD_ATTACHMENT || this == REMOVE_ATTACHMENT
    }

    fun isGroupTreeOperation(): Boolean {
        return this == CREATE_GROUP_TREE || this == DELETE_GROUP_TREE
    }
}

@Serializable
data class KeePassFieldChangePatch(
    val managedScope: KeePassManagedFieldScope,
    val replacementFields: List<KeePassFieldChange>,
    val removeFieldNames: List<String> = emptyList(),
    val baseFields: List<KeePassFieldBaseValue> = emptyList()
) {
    init {
        require(replacementFields.isNotEmpty() || removeFieldNames.isNotEmpty()) {
            "field patch must replace or remove at least one field"
        }
    }
}

@Serializable
data class KeePassFieldBaseValue(
    val name: String,
    val value: String? = null,
    val protected: Boolean = false,
    val present: Boolean = true
) {
    init {
        require(name.isNotBlank()) { "base field name cannot be blank" }
        require(present || value == null) { "missing base field cannot carry a value" }
    }
}

@Serializable
enum class KeePassManagedFieldScope {
    PASSWORD,
    SECURE_ITEM,
    PASSKEY,
    EXPLICIT_ONLY
}

@Serializable
data class KeePassFieldChange(
    val name: String,
    val value: String,
    val protected: Boolean = false
) {
    init {
        require(name.isNotBlank()) { "field change name cannot be blank" }
    }
}

@Serializable
data class KeePassStructureChangePatch(
    val sourceGroupPath: String? = null,
    val sourceGroupUuid: String? = null,
    val targetGroupPath: String? = null,
    val targetGroupUuid: String? = null,
    val recycleBinGroupUuid: String? = null,
    val previousParentGroupUuid: String? = null,
    val groupName: String? = null,
    val newGroupName: String? = null
) {
    fun isRecycleBinMove(): Boolean {
        return recycleBinGroupUuid != null &&
            previousParentGroupUuid != null &&
            targetGroupUuid == recycleBinGroupUuid
    }

    fun isRecycleBinRestore(): Boolean {
        return recycleBinGroupUuid != null &&
            previousParentGroupUuid != null &&
            sourceGroupUuid == recycleBinGroupUuid
    }
}

@Serializable
data class KeePassGroupTreeChangePatch(
    val root: KeePassGroupTreeSnapshot,
    val binaryPool: List<KeePassBinaryPoolItemPatch> = emptyList(),
    val sourceRootGroupUuid: String? = null,
    val targetParentGroupUuid: String? = null
) {
    init {
        require(root.uuid.isNotBlank()) { "group tree root uuid cannot be blank" }
        binaryPool.forEach { item ->
            require(item.hash.isNotBlank()) { "group tree binary pool hash cannot be blank" }
            require(item.contentBase64.isNotBlank()) { "group tree binary pool content cannot be blank" }
        }
    }
}

@Serializable
data class KeePassGroupTreeSnapshot(
    val uuid: String,
    val name: String,
    val notes: String? = null,
    val iconName: String? = null,
    val customIconUuid: String? = null,
    val expanded: Boolean = true,
    val defaultAutoTypeSequence: String = "",
    val enableAutoType: String? = null,
    val enableSearching: String? = null,
    val lastTopVisibleEntryUuid: String? = null,
    val previousParentGroupUuid: String? = null,
    val tags: List<String> = emptyList(),
    val times: KeePassTimesPatch? = null,
    val customData: List<KeePassCustomDataPatch> = emptyList(),
    val entries: List<KeePassEntryTreeSnapshot> = emptyList(),
    val groups: List<KeePassGroupTreeSnapshot> = emptyList()
) {
    init {
        require(uuid.isNotBlank()) { "group tree snapshot uuid cannot be blank" }
        require(name.isNotBlank()) { "group tree snapshot name cannot be blank" }
    }
}

@Serializable
data class KeePassEntryTreeSnapshot(
    val uuid: String,
    val fields: List<KeePassFieldChange>,
    val binaries: List<KeePassBinaryReferencePatch> = emptyList(),
    val history: List<KeePassEntryTreeSnapshot> = emptyList(),
    val iconName: String? = null,
    val customIconUuid: String? = null,
    val foregroundColor: String? = null,
    val backgroundColor: String? = null,
    val overrideUrl: String = "",
    val autoType: KeePassAutoTypePatch? = null,
    val tags: List<String> = emptyList(),
    val times: KeePassTimesPatch? = null,
    val customData: List<KeePassCustomDataPatch> = emptyList(),
    val previousParentGroupUuid: String? = null,
    val qualityCheck: Boolean = true
) {
    init {
        require(uuid.isNotBlank()) { "entry tree snapshot uuid cannot be blank" }
        require(fields.isNotEmpty()) { "entry tree snapshot requires fields" }
    }
}

@Serializable
data class KeePassAutoTypePatch(
    val enabled: Boolean = true,
    val obfuscation: String? = null,
    val defaultSequence: String = "",
    val items: List<KeePassAutoTypeItemPatch> = emptyList()
)

@Serializable
data class KeePassAutoTypeItemPatch(
    val window: String,
    val keystrokeSequence: String
) {
    init {
        require(window.isNotBlank()) { "auto-type window cannot be blank" }
    }
}

@Serializable
data class KeePassBinaryReferencePatch(
    val name: String,
    val hash: String
) {
    init {
        require(name.isNotBlank()) { "binary reference name cannot be blank" }
        require(hash.isNotBlank()) { "binary reference hash cannot be blank" }
    }
}

@Serializable
data class KeePassBinaryPoolItemPatch(
    val hash: String,
    val protected: Boolean = false,
    val compressed: Boolean = true,
    val contentBase64: String
)

@Serializable
data class KeePassTimesPatch(
    val creationTimeEpochMillis: Long? = null,
    val lastModificationTimeEpochMillis: Long? = null,
    val lastAccessTimeEpochMillis: Long? = null,
    val expiryTimeEpochMillis: Long? = null,
    val expires: Boolean = false,
    val usageCount: Int = 0,
    val locationChangedEpochMillis: Long? = null
)

@Serializable
data class KeePassCustomDataPatch(
    val key: String,
    val value: String,
    val lastModifiedEpochMillis: Long? = null
) {
    init {
        require(key.isNotBlank()) { "custom data key cannot be blank" }
    }
}

@Serializable
data class KeePassAttachmentChangePatch(
    val fileName: String,
    val binaryHash: String,
    val protected: Boolean = false,
    val compressed: Boolean = true,
    val contentBase64: String? = null
) {
    init {
        require(fileName.isNotBlank()) { "attachment fileName cannot be blank" }
        require(binaryHash.isNotBlank()) { "attachment binaryHash cannot be blank" }
    }
}

object KeePassChangeSetCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(changeSet: KeePassChangeSet): String {
        return json.encodeToString(KeePassChangeSet.serializer(), changeSet)
    }

    fun decode(raw: String): KeePassChangeSet {
        return json.decodeFromString(KeePassChangeSet.serializer(), raw)
    }
}
