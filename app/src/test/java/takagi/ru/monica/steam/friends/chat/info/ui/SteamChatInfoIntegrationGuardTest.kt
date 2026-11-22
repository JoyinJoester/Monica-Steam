package takagi.ru.monica.steam.friends.chat.info.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatInfoIntegrationGuardTest {
    @Test
    fun infoAndSearchRemainIndependentSafeAreaPages() {
        val info = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatInfoScreen.kt"
        ).readText()
        val hero = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatInfoHero.kt"
        ).readText()
        val search = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatHistorySearchScreen.kt"
        ).readText()

        // The cover photo is edge-to-edge, so the hero owns the status bar inset and the
        // scrolling page owns the navigation bar inset.
        assertTrue(hero.contains("statusBarsPadding()"))
        assertTrue(info.contains("navigationBarsPadding()"))
        assertTrue(info.contains("Modifier.size(48.dp)") || hero.contains("Modifier.size(48.dp)"))
        assertTrue(info.contains("Switch("))
        assertTrue(info.contains("rememberLauncherForActivityResult"))
        assertTrue(info.contains("avatarPicker.launch(\"image/*\")"))
        val chrome = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatHistorySearchChrome.kt"
        ).readText()
        // The shared expressive top bar owns the status bar inset for this page.
        assertTrue(chrome.contains("statusBarsPadding()"))
        assertTrue(search.contains("onOpenMessage"))
    }

    @Test
    fun directAndGroupHeadersOpenTheSharedInfoFlow() {
        val direct = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatThread.kt"
        ).readText()
        val group = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatSelectedContent.kt"
        ).readText()

        assertTrue(direct.contains("onOpenInfo"))
        assertTrue(group.contains("onOpenInfo"))
        assertTrue(host.contains("SteamChatInfoScreen("))
        assertTrue(!host.contains("SteamChatHistorySearchScreen("))
        assertTrue(direct.contains("SteamThreadSearchHeader("))
        assertTrue(group.contains("SteamThreadSearchHeader("))
        assertTrue(host.contains("onCreateGroupFromFriend(partnerSteamId)"))
        assertTrue(host.contains("onUpdateGroupAvatar = groupChatViewModel::updateGroupAvatar"))
    }

    @Test
    fun groupAvatarEditAffordanceLivesInTheHeroHeader() {
        val info = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatInfoScreen.kt"
        ).readText()
        val hero = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatInfoHero.kt"
        ).readText()

        // The avatar itself is clipped by the hero frame, so the edit control stays in the
        // header row instead of sitting on the avatar's clipped corner.
        assertTrue(info.contains("HeroIconButton("))
        assertTrue(hero.contains("Modifier.size(48.dp)"))
        assertTrue(!hero.contains("clip(CircleShape)") || hero.indexOf("IconButton(") < hero.indexOf("clip(CircleShape)"))
    }

    @Test
    fun historyPageCategorisesAndCountsResults() {
        val search = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatHistorySearchScreen.kt"
        ).readText()
        val rows = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatHistorySearchRows.kt"
        ).readText()
        val info = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatInfoScreen.kt"
        ).readText()
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatSelectedContent.kt"
        ).readText()
        val mapping = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatHistoryMapping.kt"
        ).readText()

        // Category strip with per-tab counters, day grouping and in-place highlights.
        assertTrue(search.contains("SteamChatHistoryCategory.filters"))
        assertTrue(search.contains("SteamListFilterRow("))
        assertTrue(search.contains("historyEntries("))
        assertTrue(rows.contains("HistoryDayHeader("))
        assertTrue(rows.contains("highlighted("))
        assertTrue(search.contains("initialCategory"))

        // The info page exposes the same buckets as shortcuts carrying their message counts.
        assertTrue(info.contains("steamSharedHistory("))
        assertTrue(projectFile("app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamSharedHistory.kt").readText().contains("SteamChatHistoryCategory.shortcuts"))
        assertTrue(info.contains("sharedHistoryItems"))
        assertTrue(info.contains("onOpenSharedMessage"))

        // Both thread types feed one classifier so the counters and results agree.
        assertTrue(mapping.contains("SteamChatHistoryClassifier.item("))
        assertTrue(mapping.contains("groupMessages: List<SteamGroupChatMessage>"))
        assertTrue(host.contains("buildSteamChatHistory("))
    }

    @Test
    fun searchPageFollowsTheMaterialExpressiveShape() {
        val search = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatHistorySearchScreen.kt"
        ).readText()
        val chrome = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatHistorySearchChrome.kt"
        ).readText()

        // Large title whose action pill grows into the search field, chips that morph on select
        // and a tonal bottom bar that names the active filters instead of hiding them in icons.
        assertTrue(search.contains("HistorySearchTopBar("))
        assertTrue(search.contains("HistoryBottomBar("))
        assertTrue(search.contains("MatchNavRail("))
        assertTrue(search.contains("SteamListFilterRow("))
        assertTrue(chrome.contains("TelegramSocialSearchTopBar("))
        val filters = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/ui/SteamListFilterRow.kt"
        ).readText()
        assertTrue(search.contains("count = { counts[it] ?: 0 }"))
        // Shared capsule keeps category counts, drag selection and 48dp targets.
        assertTrue(filters.contains("SteamGlassSelectionRow("))
        assertTrue(filters.contains("FilterLabel(label(option), count(option), active"))
        assertTrue(filters.contains("heightIn(min = 48.dp)"))
        val chip = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/ui/SteamListFilterChip.kt"
        ).readText()
        assertTrue(chip.contains("MaterialFilterChip(selected, onClick, label"))
        assertTrue(chip.contains("heightIn(min = 48.dp)"))
        assertTrue(chrome.contains("AssistChip("))
        assertTrue(chrome.contains("SteamChatHistoryRange.values()"))
        // Enter on the keyboard opens the selected hit.
        assertTrue(chrome.contains("onSearch = onSearch"))
        val searchBar = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/telegram/TelegramSocialSearchTopBar.kt"
        ).readText()
        assertTrue(searchBar.contains("onSearch = { onSearch(); focus.clearFocus() }"))
        // The media wall stays a three-column grid.
        assertTrue(search.contains("GridCells.Fixed(3)"))
    }

    @Test
    fun searchResultsScrollTheThreadToTheMessageAndHighlightIt() {
        val list = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/telegram/TelegramMessageList.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreen.kt"
        ).readText()
        val direct = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatThread.kt"
        ).readText()
        val group = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()
        val jump = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatJumpRequest.kt"
        ).readText()

        // Picking the same result twice must scroll again, hence a nonce rather than a bare id.
        assertTrue(jump.contains("nonce++"))
        assertTrue(screen.contains("SteamChatJumpRequest("))
        assertTrue(direct.contains("else targetMessageNonce"))
        assertTrue(group.contains("else targetMessageNonce"))
        assertTrue(list.contains("requestedNonce: Int = 0"))
        // A hit is centred, not pinned to the top edge, and keeps the accent tint briefly.
        assertTrue(list.contains("centerOnMessage("))
        assertTrue(direct.contains("TelegramMessageHighlight("))
        assertTrue(group.contains("TelegramMessageHighlight("))
        assertTrue(screen.contains("jump.highlightId"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null && !File(directory, "settings.gradle").exists()) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
