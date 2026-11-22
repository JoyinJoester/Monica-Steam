package takagi.ru.monica.steam.library.collections

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameOwnership
import takagi.ru.monica.steam.library.SteamLibrarySnapshot

class SteamCollectionLibraryMembershipTest {
    private val library = SteamLibrarySnapshot(1, listOf(
        SteamGame(620, "Owned", 0, 0),
        SteamGame(12, "Shared", 0, 0, ownership = SteamGameOwnership.FAMILY_SHARED)
    ), 0)

    @Test fun ownedAndSharedGamesCanBeFiledTogether() {
        assertTrue(library.allowsCollectionMembership(1, setOf(620, 12)))
        assertTrue(library.allowsCollectionMembership(1, setOf(12)))
    }
    @Test fun anotherAccountsLibraryDoesNotAuthorizeMembershipChanges() {
        assertFalse(library.allowsCollectionMembership(2, setOf(12)))
        assertFalse(library.allowsCollectionMembership(2, setOf(620)))
    }
    @Test fun anUnknownGameRejectsTheWholeRequestedChange() {
        assertFalse(library.allowsCollectionMembership(1, setOf(12, 999)))
        assertFalse(library.copy(games = emptyList()).allowsCollectionMembership(1, setOf(12)))
    }
    @Test fun unavailableExistingMembersArePreservedWhenSharedGameIsAddedOrRemoved() {
        val entry = SteamCloudCollectionEntry("user-collections.uc-a",
            """{"id":"uc-a","name":"Mixed","added":[620,999],"custom":42}""", false, 1, 1)
        val change = SteamCollectionChange.Membership(entry, mapOf(12 to true))
        assertTrue(library.allowsCollectionMembership(1, change.changes.keys))
        val added = entry.copy(value = changedCollectionValue(parseSteamCollection(entry)!!, change))
        assertEquals(setOf(620, 999, 12), parseSteamCollection(added)!!.members)
        val removed = added.copy(value = changedCollectionValue(parseSteamCollection(added)!!,
            SteamCollectionChange.Membership(added, mapOf(12 to false))))
        assertEquals(setOf(620, 999), parseSteamCollection(removed)!!.members)
        assertTrue(removed.value.contains("\"custom\":42"))
    }
}
