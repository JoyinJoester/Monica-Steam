package takagi.ru.monica.steam.library.collections

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.*
import takagi.ru.monica.steam.network.cm.SteamCmGateway

class SteamCollectionsServiceTest {
    private val account = SteamAccount(1, "76561198000000001", "player", "Test", "android:test", "test",
        null, null, null, "fixture-token", null, null, "{}", true, 0, 0, 0)
    private fun entry(id: String = "uc-a", value: String = """{"id":"$id","name":"Co-op","added":[620,570,999],"removed":[570],"custom":{"keep":true}}""") =
        SteamCloudCollectionEntry("user-collections.$id", value, false, 100, 7)
    private class Gateway(var entries: List<SteamCloudCollectionEntry>) : SteamCmGateway {
        var version = 8L
        var uploads = 0
        var upload: ByteArray? = null
        var apply = true
        var failure: Exception? = null
        var failReadback = false
        var afterDownload: (() -> Unit)? = null
        override fun callService(account: SteamAccount, method: String, request: ByteArray): ByteArray {
            if (method == "CloudConfigStore.Download#1") {
                val ns = SteamProtoReader(SteamProtoReader(request).parseAll().single().bytes!!).parse()
                assertEquals(1, ns[1]?.asInt); assertEquals(0L, ns[2]?.asLong)
                if (uploads > 0 && failReadback) throw IOException()
                return SteamProtoWriter().apply { writeMessage(1, SteamProtoWriter().apply {
                    writeVarint(1, 1); writeUint64(2, version)
                    entries.forEach { e -> writeMessage(3, encoded(e)) }
                }) }.toByteArray().also { afterDownload?.invoke() }
            }
            assertEquals("CloudConfigStore.Upload#1", method)
            uploads++; upload = request
            val ns = SteamProtoReader(SteamProtoReader(request).parseAll().single().bytes!!).parseAll()
            assertEquals(1, ns.first { it.number == 1 }.asInt)
            assertEquals(version, ns.first { it.number == 2 }.asLong)
            val fields = SteamProtoReader(ns.single { it.number == 3 }.bytes!!).parse()
            val changed = SteamCloudCollectionEntry(fields[1]!!.asString, fields[3]!!.asString,
                fields[2]!!.asBool, fields[4]!!.asFixed32UnsignedLong, fields[5]!!.asLong)
            if (apply) { entries = entries.filterNot { it.key == changed.key } + changed.copy(version = version + 1); version++ }
            failure?.let { throw it }
            return byteArrayOf()
        }
        override fun exchangeClientMessage(account: SteamAccount, requestEMsg: Int, responseEMsg: Int, request: ByteArray): ByteArray = error("unused")
        companion object { fun encoded(e: SteamCloudCollectionEntry) = SteamProtoWriter().apply {
            writeString(1, e.key); writeBool(2, e.deleted); writeString(3, e.value)
            writeFixed32(4, e.timestamp); writeUint64(5, e.version)
        } }
    }
    private fun service(gateway: Gateway) = SteamCollectionsService(gateway, clock = { 90 }, newId = { "uc-new" })

    @Test fun parsesMembershipAndPreservesRawEntry() {
        val raw = entry()
        val parsed = parseSteamCollection(raw)!!
        assertTrue(parsed.editable); assertEquals(setOf(620, 999), parsed.members); assertEquals(raw, parsed.entry)
    }
    @Test fun dynamicAndMalformedAreReadonlyAndDeletedIsExcluded() {
        val dynamic = entry(value = """{"id":"uc-a","name":"Dynamic","added":[620],"filterSpec":{"tags":[1]}}""")
        assertTrue(parseSteamCollection(dynamic)!!.dynamic)
        assertFalse(parseSteamCollection(dynamic)!!.editable)
        for (value in listOf("bad json", """{"id":"other","added":[620]}""", """{"id":"uc-a","added":["620"]}""", """{"id":"uc-a","added":[-1]}""")) {
            val snapshot = SteamCollectionsSnapshot(1, listOf(entry(value = value)))
            assertFalse(snapshot.collections.single().editable); assertFalse(snapshot.canIdentifyUnfiled)
        }
        assertNull(parseSteamCollection(entry().copy(deleted = true)))
        assertNull(parseSteamCollection(entry().copy(key = "other.setting")))
    }
    @Test fun editsOnlySpecifiedMembersAndPreservesUnknownData() {
        val value = changedCollectionValue(parseSteamCollection(entry())!!,
            SteamCollectionChange.Membership(entry(), mapOf(620 to false, 570 to true)))
        val json = Json.parseToJsonElement(value).jsonObject
        assertEquals(buildJsonObject { put("keep", true) }, json["custom"])
        assertEquals(setOf(570, 999), parseSteamCollection(entry(value = value))!!.members)
        assertTrue(620 in json["removed"]!!.jsonArray.map { it.jsonPrimitive.int })
    }
    @Test fun filtersAllUnfiledAndNamedCollections() {
        val snapshot = SteamCollectionsSnapshot(8, listOf(entry()))
        val ids = setOf(620, 570, 999, 12)
        assertEquals(ids, collectionGames(ids, null, snapshot))
        assertEquals(setOf(12, 570), collectionGames(ids, "", snapshot))
        assertEquals(setOf(620, 999), collectionGames(ids, "uc-a", snapshot))
    }
    @Test fun renameUploadsExactlyOneEntryWithCurrentVersionsAndMonotonicFixedTimestamp() {
        val target = entry(); val other = entry("uc-b")
        val gateway = Gateway(listOf(target, other))
        val result = service(gateway).save(account, SteamCollectionChange.Rename(target, "  Friends  ")) { true }
        val changed = result.collections.first { it.id == "uc-a" }
        assertEquals("Friends", changed.name); assertEquals(101L, changed.entry.timestamp)
        assertEquals(other, result.entries.first { it.key == other.key }); assertEquals(1, gateway.uploads)
        val upload = SteamProtoReader(SteamProtoReader(gateway.upload!!).parseAll().single().bytes!!).parseAll()
        val fields = SteamProtoReader(upload.single { it.number == 3 }.bytes!!).parse()
        assertEquals(5, fields[4]?.wireType); assertEquals(7L, fields[5]?.asLong)
        assertEquals(Json.parseToJsonElement(target.value).jsonObject["custom"], Json.parseToJsonElement(changed.entry.value).jsonObject["custom"])
    }
    @Test fun createAndDeleteKeepOtherEntries() {
        val old = entry(); val gateway = Gateway(listOf(old))
        val created = service(gateway).save(account, SteamCollectionChange.Create("New")) { true }
        assertEquals(2, created.collections.size); assertEquals(old, created.entries.first())
        val target = created.collections.first { it.id == "uc-new" }
        val deleted = service(gateway).save(account, SteamCollectionChange.Delete(target.entry)) { true }
        assertEquals(listOf("uc-a"), deleted.collections.map { it.id }); assertEquals(2, gateway.uploads)
    }
    @Test fun preflightTargetChangeRefusesWrite() {
        val original = entry(); val gateway = Gateway(listOf(original.copy(version = 9)))
        assertThrows(SteamCollectionConflict::class.java) { service(gateway).save(account, SteamCollectionChange.Rename(original, "New")) { true } }
        assertEquals(0, gateway.uploads)
    }
    @Test fun unrelatedChangeDoesNotPreventTargetEdit() {
        val original = entry(); val other = entry("uc-b").copy(version = 77)
        val gateway = Gateway(listOf(original, other))
        val result = service(gateway).save(account, SteamCollectionChange.Rename(original, "New")) { true }
        assertEquals(other, result.entries.first { it.key == other.key })
    }
    @Test fun accountChangedAfterFreshReadPreventsUpload() {
        val original = entry(); val gateway = Gateway(listOf(original)); var current = true
        gateway.afterDownload = { current = false }
        assertThrows(CancellationException::class.java) { service(gateway).save(account, SteamCollectionChange.Delete(original)) { current } }
        assertEquals(0, gateway.uploads)
    }
    @Test fun accountChangedAtFinalGuardPreventsUpload() {
        val original = entry(); val gateway = Gateway(listOf(original)); var checks = 0
        assertThrows(CancellationException::class.java) { service(gateway).save(account, SteamCollectionChange.Delete(original)) { ++checks == 1 } }
        assertEquals(0, gateway.uploads)
    }
    @Test fun timeoutAfterServerApplyIsReconciledWithoutReplay() {
        val original = entry(); val gateway = Gateway(listOf(original)).apply { failure = IOException("timeout") }
        assertEquals("New", service(gateway).save(account, SteamCollectionChange.Rename(original, "New")) { true }.collections.single().name)
        assertEquals(1, gateway.uploads)
    }
    @Test fun lostWriteOrUnreadableReadbackIsUnknownWithoutReplay() {
        for (readFailure in listOf(false, true)) {
            val original = entry(); val gateway = Gateway(listOf(original)).apply {
                apply = false; failure = IOException("timeout"); failReadback = readFailure
            }
            assertThrows(SteamCollectionUnknown::class.java) { service(gateway).save(account, SteamCollectionChange.Rename(original, "New")) { true } }
            assertEquals(1, gateway.uploads)
        }
    }
    @Test fun explicitRejectedWriteKeepsActionableError() {
        val original = entry(); val error = SteamApiException("Denied", eResult = 15)
        val gateway = Gateway(listOf(original)).apply { apply = false; failure = error }
        assertSame(error, assertThrows(SteamApiException::class.java) { service(gateway).save(account, SteamCollectionChange.Rename(original, "New")) { true } })
    }
    @Test fun readonlyCollectionCannotBeMutated() {
        val target = entry(value = """{"id":"uc-a","name":"Dynamic","added":[620],"filterSpec":{}}""")
        val gateway = Gateway(listOf(target))
        assertThrows(IllegalArgumentException::class.java) { service(gateway).save(account, SteamCollectionChange.Delete(target)) { true } }
        assertEquals(0, gateway.uploads)
    }
    @Test fun noOpDoesNotUpload() {
        val target = entry(); val gateway = Gateway(listOf(target))
        service(gateway).save(account, SteamCollectionChange.Rename(target, "Co-op")) { true }
        assertEquals(0, gateway.uploads)
    }
    @Test fun duplicateKeysAndScalarFieldsAreRejected() {
        val duplicate = SteamProtoWriter().apply { writeMessage(1, SteamProtoWriter().apply {
            writeVarint(1, 1); writeUint64(2, 1); writeMessage(3, Gateway.encoded(entry())); writeMessage(3, Gateway.encoded(entry()))
        }) }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { decodeCollectionsDownload(duplicate) }
        val scalar = SteamProtoWriter().apply { writeMessage(1, SteamProtoWriter().apply {
            writeVarint(1, 1); writeUint64(2, 1); writeUint64(2, 2)
        }) }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { decodeCollectionsDownload(scalar) }
    }
    @Test fun invalidNamesAndTimestampOverflowDoNotUpload() {
        for (name in listOf(" ", "a\nb", "a".repeat(129))) assertThrows(IllegalArgumentException::class.java) { validCollectionName(name) }
        val target = entry().copy(timestamp = 0xffffffffL); val gateway = Gateway(listOf(target))
        assertThrows(IllegalArgumentException::class.java) { service(gateway).save(account, SteamCollectionChange.Rename(target, "New")) { true } }
        assertEquals(0, gateway.uploads)
    }
}
