package takagi.ru.monica.steam.workshop

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamWorkshopParserTest {
    private fun fixture() = requireNotNull(javaClass.getResource("/workshop/browse-ssr.html")).readText()

    @Test fun parsesCurrentValveJsonDataBlockWithoutJavaScript() {
        val html = requireNotNull(javaClass.getResource("/workshop/browse-valve-ssr-data.html")).readText()
        val batch = SteamWorkshopParser.browse(html, 550, 1)
        assertEquals(listOf("1001", "1002"), batch.items.map { it.id })
        assertTrue(batch.items.all { it.appId == 550 })
        assertTrue(batch.hasMore)
        assertTrue(batch.total > batch.items.size)
        assertEquals(listOf("Campaigns"), batch.items.first().tags)
    }

    @Test fun valveJsonFormatPreservesGamePageTagAndItemValidation() {
        val legacyContext = Json.parseToJsonElement(SteamWorkshopParser.jsonStringAfter(
            fixture(), "window.SSR.renderContext=JSON.parse("
        )).jsonObject
        val html = valveHtml(legacyContext)
        val batch = SteamWorkshopParser.browse(html, 294100, 1)
        assertEquals(SteamWorkshopParser.browse(fixture(), 294100, 1), batch)
        assertThrows(WorkshopException::class.java) { SteamWorkshopParser.browse(html, 550, 1) }
        assertThrows(WorkshopException::class.java) { SteamWorkshopParser.browse(html, 294100, 2) }
    }

    @Test fun malformedNewDataCannotFallBackToStaleLegacyDataOrBecomeAnEmptySuccess() {
        listOf("{", "[]", "{}", """{"renderContext":null}""", """{"renderContext":{"queryData":"{}"}}""").forEach { invalid ->
            val html = "<script type=\"application/json\" id=\"valve-ssr-data\">$invalid</script>" + fixture()
            assertTrue(runCatching { SteamWorkshopParser.browse(html, 294100, 1) }.isFailure)
        }
    }

    @Test fun valveJsonBlockMustHaveTheExpectedIdAndType() {
        val html = requireNotNull(javaClass.getResource("/workshop/browse-valve-ssr-data.html")).readText()
        listOf(html.replace("valve-ssr-data", "unrelated-data"),
            html.replace("application/json", "text/plain")).forEach { unrelated ->
            assertThrows(WorkshopException::class.java) { SteamWorkshopParser.browse(unrelated, 550, 1) }
        }
    }

    private fun valveHtml(context: JsonObject): String =
        "<script type=\"application/json\" id=\"valve-ssr-data\">" +
            buildJsonObject { put("renderContext", context) } + "</script>"

    @Test fun parsesCurrentSteamHydrationAndGameSpecificTags() {
        val batch = SteamWorkshopParser.browse(fixture(), 294100, 1)
        assertEquals(2, batch.items.size)
        assertTrue(batch.items.all { it.appId == 294100 && it.id.toULongOrNull() != null })
        assertTrue(batch.hasMore)
        assertTrue(batch.tags.any { it.value == "Mod" })
        assertTrue(batch.tags.any { it.value == "1.6" })
        assertNull(batch.items.first().subscribed)
    }

    @Test fun rejectsWrongGameWrongPageAndUnexpectedMarkup() {
        listOf({ SteamWorkshopParser.browse(fixture(), 440, 1) },
            { SteamWorkshopParser.browse(fixture(), 294100, 2) },
            { SteamWorkshopParser.browse("<html>Sign in</html>", 294100, 1) }).forEach { action ->
            assertThrows(WorkshopException::class.java) { action() }
        }
    }

    @Test fun escapedJsonCannotTerminateTheHydrationParser() {
        val value = "a quote \" and slash \\ and );window.evil() and </script>"
        val script = "JSON.parse(" + JsonPrimitive(value).toString() + ");ignored"
        assertEquals(value, SteamWorkshopParser.jsonStringAfter(script, "JSON.parse("))
        assertThrows(WorkshopException::class.java) { SteamWorkshopParser.jsonStringAfter("JSON.parse(\"unfinished", "JSON.parse(") }
    }

    @Test fun queryPreservesUnicodeAndMultipleTagsWithoutManualUrlEncoding() {
        val params = SteamWorkshopService.browseParameters(294100,
            WorkshopQuery(search = "  中文 & mods  ", tags = setOf("1.6", "Mod"), days = 30), 3)
        assertEquals("中文 & mods", params["searchtext"])
        assertEquals("1.6", params["requiredtags[0]"])
        assertEquals("Mod", params["requiredtags[1]"])
        assertEquals("3", params["p"])
        assertEquals("30", params["days"])
    }

    @Test fun subscriptionStatusNeedsTheRequestedFileAndPreservesUnsignedIds() {
        val id = "18446744073709551614"
        val payload = SteamProtoWriter().apply { writeMessage(1, SteamProtoWriter().apply {
            writeFixed64(1, id.toULong().toLong()); writeBool(2, true)
        }) }.toByteArray()
        assertTrue(SteamWorkshopProtocol.status(payload, id))
        assertThrows(WorkshopException::class.java) { SteamWorkshopProtocol.status(payload, "123") }
        assertThrows(WorkshopException::class.java) { SteamWorkshopProtocol.status(byteArrayOf(), id) }
    }

    @Test fun mutationWireFormatNotifiesSteamAndNeverRemovesDependencies() {
        val item = WorkshopItem("123", 294100, "Mod")
        val sub = takagi.ru.monica.steam.network.SteamProtoReader(
            SteamWorkshopProtocol.subscriptionRequest(item, true, true).toByteArray()).parse()
        assertEquals(123L, sub[1]?.asLong)
        assertEquals(1, sub[2]?.asInt)
        assertEquals(294100, sub[3]?.asInt)
        assertEquals(true, sub[4]?.asBool)
        assertEquals(true, sub[5]?.asBool)
        val unsub = takagi.ru.monica.steam.network.SteamProtoReader(
            SteamWorkshopProtocol.subscriptionRequest(item, false, true).toByteArray()).parse()
        assertFalse(unsub.containsKey(5))
    }

    @Test fun malformedAndCrossGameItemDetailsFailClosed() {
        val bad = buildJsonObject { put("publishedfileid", "123"); put("consumer_appid", 440); put("title", "Item") }
        assertThrows(WorkshopException::class.java) { SteamWorkshopParser.item(bad, 294100) }
        assertThrows(WorkshopException::class.java) { SteamWorkshopProtocol.details(byteArrayOf(), 294100, "123") }
    }

    @Test fun localSubscriptionSortUsesMetadataRatherThanUnverifiedServerSortNames() {
        val first = WorkshopItem("1", 294100, "Older", created = 100, subscriptions = 300, updated = 500)
        val second = WorkshopItem("2", 294100, "Newer", created = 200, subscriptions = 100, updated = 600)
        assertEquals("2", sortSubscriptions(listOf(first, second), WorkshopSort.NEWEST).first().id)
        assertEquals("1", sortSubscriptions(listOf(first, second), WorkshopSort.MOST_SUBSCRIBED).first().id)
        assertEquals("2", sortSubscriptions(listOf(first, second), WorkshopSort.UPDATED).first().id)
        assertTrue(WorkshopQuery(subscribedOnly = true, sort = WorkshopSort.NEWEST).scansSubscriptions())
    }
}
