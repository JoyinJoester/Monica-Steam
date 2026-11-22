package takagi.ru.monica.steam.library.collections

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoField
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmGateway

internal class SteamCollectionConflict : IllegalStateException()
internal class SteamCollectionUnknown : IllegalStateException()

internal interface SteamCollectionsGateway {
    fun download(account: SteamAccount): SteamCollectionsSnapshot
    fun save(account: SteamAccount, change: SteamCollectionChange, isCurrent: () -> Boolean): SteamCollectionsSnapshot
}

/** Cloud configuration, not game save files. Send only the one explicitly reviewed entry. */
internal class SteamCollectionsService(
    private val cm: SteamCmGateway = SteamCmClient(),
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    private val newId: () -> String = { "uc-${UUID.randomUUID()}" }
) : SteamCollectionsGateway {
    override fun download(account: SteamAccount): SteamCollectionsSnapshot {
        val request = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply { writeVarint(1, 1); writeVarint(2, 0) })
        }
        return decodeCollectionsDownload(cm.callService(account, "CloudConfigStore.Download#1", request.toByteArray()))
    }

    override fun save(account: SteamAccount, change: SteamCollectionChange, isCurrent: () -> Boolean): SteamCollectionsSnapshot {
        val fresh = download(account)
        if (!isCurrent()) throw CancellationException("Collection account changed")
        val reviewed = when (change) {
            is SteamCollectionChange.Create -> null
            is SteamCollectionChange.Rename -> change.reviewed
            is SteamCollectionChange.Membership -> change.reviewed
            is SteamCollectionChange.Delete -> change.reviewed
        }
        val key = reviewed?.key ?: (STEAM_COLLECTION_PREFIX + newId())
        if (reviewed != null && fresh.entries.firstOrNull { it.key == key } != reviewed)
            throw SteamCollectionConflict()
        if (reviewed == null && fresh.entries.any { it.key == key }) throw SteamCollectionConflict()
        val original = reviewed?.let(::parseSteamCollection)
        if (reviewed != null) require(original?.editable == true)
        val value = when (change) {
            is SteamCollectionChange.Create -> buildJsonObject {
                put("id", key.removePrefix(STEAM_COLLECTION_PREFIX))
                put("name", validCollectionName(change.name))
                put("added", JsonArray(emptyList()))
            }.toString()
            is SteamCollectionChange.Delete -> reviewed!!.value
            else -> changedCollectionValue(original!!, change)
        }
        if (reviewed != null && change !is SteamCollectionChange.Delete &&
            Json.parseToJsonElement(value) == Json.parseToJsonElement(reviewed.value)) return fresh
        val timestamp = maxOf(clock(), (reviewed?.timestamp ?: 0) + 1)
        require(timestamp in 1..0xffffffffL)
        val desired = SteamCloudCollectionEntry(key, value, change is SteamCollectionChange.Delete, timestamp, reviewed?.version ?: 0)
        val request = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply {
                writeVarint(1, 1); writeUint64(2, fresh.version)
                writeMessage(3, SteamProtoWriter().apply {
                    writeString(1, desired.key); writeBool(2, desired.deleted)
                    writeString(3, desired.value); writeFixed32(4, desired.timestamp)
                    writeUint64(5, desired.version)
                })
            })
        }
        if (!isCurrent()) throw CancellationException("Collection account changed")
        // A timeout can mean the server applied the write. Never automatically replay it.
        val uploadError = try {
            cm.callService(account, "CloudConfigStore.Upload#1", request.toByteArray())
            null
        } catch (e: Exception) { e }
        val verified = try { download(account) } catch (e: Exception) {
            // Explicit service rejection means no accepted write; retain its actionable login/access error.
            if (uploadError is SteamApiException && uploadError.eResult != null) throw uploadError
            throw SteamCollectionUnknown()
        }
        val stored = verified.entries.firstOrNull { it.key == key }
        val matches = if (desired.deleted) stored == null || stored.deleted else
            stored != null && !stored.deleted && runCatching {
                Json.parseToJsonElement(stored.value) == Json.parseToJsonElement(desired.value)
            }.getOrDefault(false)
        if (!matches) {
            if (uploadError is SteamApiException && uploadError.eResult != null) throw uploadError
            throw SteamCollectionUnknown()
        }
        return verified
    }
}

internal fun decodeCollectionsDownload(bytes: ByteArray): SteamCollectionsSnapshot {
    require(bytes.size <= 32 * 1024 * 1024)
    val namespaces = SteamProtoReader(bytes).parseAll().filter { it.number == 1 }.map {
        SteamProtoReader(requireNotNull(it.bytes)).parseAll()
    }.filter { fields -> fields.firstOrNull { it.number == 1 }?.asInt == 1 }
    require(namespaces.size == 1) { "Missing or ambiguous collections namespace" }
    val fields = namespaces.single()
    fun List<SteamProtoField>.scalar(number: Int, wire: Int): SteamProtoField? {
        val found = filter { it.number == number }
        require(found.size <= 1 && found.all { it.wireType == wire })
        return found.singleOrNull()
    }
    require(fields.scalar(1, 0)?.asInt == 1)
    val version = fields.scalar(2, 0)?.varint ?: 0L
    val entries = fields.filter { it.number == 3 }.map {
        require(it.wireType == 2)
        val entry = SteamProtoReader(requireNotNull(it.bytes)).parseAll()
        SteamCloudCollectionEntry(
            key = requireNotNull(entry.scalar(1, 2)).asString,
            value = entry.scalar(3, 2)?.asString.orEmpty(),
            deleted = entry.scalar(2, 0)?.asBool ?: false,
            timestamp = entry.scalar(4, 5)?.asFixed32UnsignedLong ?: 0,
            version = entry.scalar(5, 0)?.asLong ?: 0
        ).also { value -> require(value.key.length <= 512 && value.value.length <= 4 * 1024 * 1024) }
    }
    require(entries.size <= 10000 && entries.map { it.key }.distinct().size == entries.size)
    return SteamCollectionsSnapshot(version, entries)
}
