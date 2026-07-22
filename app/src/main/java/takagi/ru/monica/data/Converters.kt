package takagi.ru.monica.data

import androidx.room.TypeConverter
import java.util.Date

/**
 * Type converters for Room database
 */
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
    
    @TypeConverter
    fun fromItemType(value: ItemType?): String? {
        return value?.name
    }
    
    @TypeConverter
    fun toItemType(value: String?): ItemType? {
        return value?.let { ItemType.valueOf(it) }
    }

    @TypeConverter
    fun fromKeePassStorageLocation(value: KeePassStorageLocation?): String? {
        return value?.name
    }

    @TypeConverter
    fun toKeePassStorageLocation(value: String?): KeePassStorageLocation {
        return enumValueOrDefault(value, KeePassStorageLocation.INTERNAL)
    }

    @TypeConverter
    fun fromKeePassDatabaseSourceType(value: KeePassDatabaseSourceType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toKeePassDatabaseSourceType(value: String?): KeePassDatabaseSourceType {
        return enumValueOrDefault(value, KeePassDatabaseSourceType.LOCAL_INTERNAL)
    }

    @TypeConverter
    fun fromKeePassOpenMode(value: KeePassOpenMode?): String? {
        return value?.name
    }

    @TypeConverter
    fun toKeePassOpenMode(value: String?): KeePassOpenMode {
        return enumValueOrDefault(value, KeePassOpenMode.DIRECT)
    }

    @TypeConverter
    fun fromKeePassSyncStatus(value: KeePassSyncStatus?): String? {
        return value?.name
    }

    @TypeConverter
    fun toKeePassSyncStatus(value: String?): KeePassSyncStatus {
        return enumValueOrDefault(value, KeePassSyncStatus.LOCAL_ONLY)
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, defaultValue: T): T {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) return defaultValue
        return runCatching { enumValueOf<T>(normalized) }.getOrDefault(defaultValue)
    }
}
