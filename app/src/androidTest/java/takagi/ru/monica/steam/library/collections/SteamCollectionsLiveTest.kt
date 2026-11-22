package takagi.ru.monica.steam.library.collections

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.library.SteamGameLibraryService
import takagi.ru.monica.steam.network.*

/** Explicit opt-in: caller supplies an authorized test account in app-private storage. */
class SteamCollectionsLiveTest {
    @Test fun authorizedTemporaryCollectionRoundTrip() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        require(InstrumentationRegistry.getArguments().getString("liveCollections") == "true")
        val context = instrumentation.targetContext
        val input = File(context.filesDir, "collections-session.json")
        val receipt = linkedMapOf<String, JsonElement>()
        fun record(key: String, value: Boolean) { receipt[key] = JsonPrimitive(value) }
        fun count(key: String, value: Int) { receipt[key] = JsonPrimitive(value) }
        fun saveReceipt() { File(context.getExternalFilesDir(null), "collections/live-result.json").apply {
            parentFile!!.mkdirs(); writeText(JsonObject(receipt).toString())
        } }
        val id = "uc-${UUID.randomUUID()}"
        val key = "user-collections.$id"
        File(context.filesDir, "collections-test-key.txt").writeText(key)
        var account: SteamAccount? = null
        var before: SteamCollectionsSnapshot? = null
        val service = SteamCollectionsService(newId = { id })
        var attemptedCreate = false
        var error: String? = null
        try {
            val json = Json.parseToJsonElement(input.readText()).jsonObject
            val initial = SteamAccount(91001, json.getValue("steamId").jsonPrimitive.content,
                json.getValue("accountName").jsonPrimitive.content, "Collections test account", "android:collections-test", "",
                null, null, null, json["accessToken"]?.jsonPrimitive?.contentOrNull,
                json["refreshToken"]?.jsonPrimitive?.contentOrNull, null, "{}", true, 0, 0, 0, isTemporary = true)
            account = initial
            SteamSessionRefreshService().refreshIfNeeded(initial)?.let {
                account = account!!.copy(accessToken = it.accessToken, refreshToken = it.refreshToken ?: account!!.refreshToken)
            }
            val current = account!!
            val initialSnapshot = service.download(current)
            before = initialSnapshot
            record("downloadPassed", true); count("originalEntries", initialSnapshot.entries.size)
            attemptedCreate = true
            val created = service.save(current, SteamCollectionChange.Create("Monica 同步测试")) { true }
            var target = created.collections.single { it.id == id }
            assertEquals("Monica 同步测试", target.name); record("createVerified", true)
            val renamed = service.save(current, SteamCollectionChange.Rename(target.entry, "Monica 同步测试 · 合作")) { true }
            target = renamed.collections.single { it.id == id }
            assertEquals("Monica 同步测试 · 合作", target.name); record("renameVerified", true)
            val owned = SteamGameLibraryService.parseOwnedGames(SteamApiClient().callProtobuf(
                iface = "IPlayerService", method = "GetOwnedGames", accessToken = current.accessToken, useGet = true,
                request = SteamProtoWriter().apply { writeUint64(1, current.steamId); writeBool(2, true); writeBool(3, true); writeString(7, "english") }))
            count("ownedGames", owned.size)
            if (owned.isNotEmpty()) {
                val appId = owned.first().appId
                val added = service.save(current, SteamCollectionChange.Membership(target.entry, mapOf(appId to true))) { true }
                target = added.collections.single { it.id == id }
                assertEquals(setOf(appId), target.members); record("membershipAddVerified", true)
                val removed = service.save(current, SteamCollectionChange.Membership(target.entry, mapOf(appId to false))) { true }
                target = removed.collections.single { it.id == id }
                assertTrue(target.members.isEmpty()); record("membershipRemoveVerified", true)
            }
            // An independent service instance downloads again through the server protocol.
            val independent = SteamCollectionsService().download(current)
            assertEquals(target.name, independent.collections.single { it.id == id }.name)
            record("independentReadVerified", true)
        } catch (e: Throwable) {
            error = e.javaClass.simpleName
            (e as? SteamApiException)?.let { error += ":http=${it.httpStatusCode}:eresult=${it.eResult}" }
            receipt["failure"] = JsonPrimitive(error!!)
        } finally {
            if (attemptedCreate && account != null) try {
                val fresh = service.download(account!!)
                fresh.collections.firstOrNull { it.id == id }?.let {
                    service.save(account!!, SteamCollectionChange.Delete(it.entry)) { true }
                }
                val final = service.download(account!!)
                assertFalse(final.collections.any { it.id == id })
                record("temporaryCollectionRemoved", true)
                before?.let { old ->
                    assertEquals(old.entries.filterNot { it.key == key }.toSet(), final.entries.filterNot { it.key == key }.toSet())
                    record("originalEntriesUnchanged", true)
                }
            } catch (e: Throwable) {
                receipt["cleanupFailure"] = JsonPrimitive(e.javaClass.simpleName)
                error = error ?: "Cleanup could not be verified"
            }
            input.delete()
            saveReceipt()
        }
        if (error != null) fail("Live collections test failed: $error; see sanitized receipt")
    }
}
