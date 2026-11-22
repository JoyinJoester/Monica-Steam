package takagi.ru.monica.steam.library

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.profile.viewer.domain.buildSteamAchievementComparison

class SteamAchievementSetsTest {
    private fun group(id: Long, name: String, order: Long = 0, dlc: Long = 0,
                      developer: Boolean = false, public: Boolean = true) = SteamProtoWriter().apply {
        writeVarint(1, id); writeString(2, name); writeVarint(3, dlc)
        writeBool(5, developer); writeVarint(6, order); writeBool(7, public)
        writeVarint(8, 3); writeVarint(9, 2)
    }
    private fun achievement(key: Long, name: String, group: Long, archived: Boolean = false) = SteamProtoWriter().apply {
        writeString(1, name); writeString(2, name); writeVarint(8, key)
        writeVarint(11, group); writeBool(12, archived)
    }
    @Test fun parsesLatestGroupsAndServerCompletionWithUnsignedIds() {
        val defs = SteamProtoWriter().apply {
            writeMessage(3, group(0xffffffffL, "DLC", 2, 730))
            writeMessage(3, group(0, "Base", 1))
        }
        val user = SteamProtoWriter().apply { writeMessage(4, SteamProtoWriter().apply {
            writeVarint(1, 0); writeBool(2, true); writeVarint(3, 1)
            writeBool(4, true); writeFixed32(9, 0x80000001L)
        }) }
        val parsed = SteamAchievementSetsParser.parse(defs.toByteArray(), user.toByteArray())
        assertEquals(listOf(0L, 0xffffffffL), parsed.sets.map { it.groupId })
        assertEquals(true, parsed.sets[0].isCompleted)
        assertEquals(0x80000001L, parsed.sets[0].timeCompletedSeconds)
        assertNull(parsed.sets[1].isCompleted)
        assertEquals(730L, parsed.sets[1].dlcAppId)
    }
    @Test fun membershipArchiveAndPrivateSetsSurviveFallbackParser() {
        val defs = SteamProtoWriter().apply {
            writeMessage(1, achievement(1, "Base win", 0))
            writeMessage(1, achievement(2, "Old task", 0, true))
            writeMessage(1, achievement(3, "DLC win", 7))
            writeMessage(1, achievement(4, "Developer secret", 8))
            writeMessage(3, group(0, "Base")); writeMessage(3, group(7, "DLC", dlc = 730))
            writeMessage(3, group(8, "Private", developer = true))
        }
        val parsed = SteamGameLibraryService.parseAchievementResponses(1, 730, "Fixture", defs.toByteArray(), byteArrayOf())
        assertEquals(listOf(0L, 0L, 7L), parsed.achievements.map { it.groupId })
        assertTrue(parsed.achievements[1].archived)
        assertEquals(listOf("Base", "DLC"), parsed.sets.map { it.name })
        assertEquals(listOf("DLC win"), parsed.achievementsInSet(7).map { it.displayName })
        assertEquals(2, parsed.achievementsInSet(0).size)
    }
    @Test fun completedSetRemainsCompleteAndArchivedAchievementsDoNotIncreaseRequirement() {
        val old = SteamAchievement("old", "old", "", false, null, null, null, archived = true, hidden = true)
        val win = old.copy(apiName = "win", achieved = true, archived = false)
        val dlc = old.copy(apiName = "dlc", groupId = 7, archived = false)
        val data = SteamGameAchievements(1, 1, "Fixture", listOf(old, win, dlc), 0,
            listOf(SteamAchievementSet(0, "Base", completionAchievements = 1, isCompleted = true),
                SteamAchievementSet(7, "DLC")))
        assertEquals(1 to 1, data.setProgress(data.sets[0]))
        assertEquals(0 to 1, data.setProgress(data.sets[1]))
        assertEquals(1 to 1, data.setProgress(data.sets[0].copy(completionAchievements = null, isCompleted = null)))
        assertEquals(1 to 2, data.setProgress(data.sets[0].copy(completionAchievements = 2, completedAchievements = 1)))
        val comparison = buildSteamAchievementComparison("1", "2", data, data)
        assertEquals(data.sets, comparison.targetSets)
        assertEquals(7L, comparison.achievements.last().groupId)
        assertTrue(comparison.achievements.first().hidden)
    }
    @Test fun oldCacheAndLegacyResponseKeepUncategorizedBehavior() {
        val old = """{"accountId":1,"appId":730,"gameName":"Fixture","achievements":[],"fetchedAt":0}"""
        assertTrue(Json.decodeFromString<SteamGameAchievements>(old).sets.isEmpty())
        val defs = SteamProtoWriter().apply { writeMessage(1, achievement(1, "Win", 0)) }
        val data = SteamGameLibraryService.parseAchievementResponses(1, 730, "Fixture", defs.toByteArray(), byteArrayOf())
        assertTrue(data.selectableSets.isEmpty())
        assertEquals("Win", data.achievements.single().displayName)
    }
}
