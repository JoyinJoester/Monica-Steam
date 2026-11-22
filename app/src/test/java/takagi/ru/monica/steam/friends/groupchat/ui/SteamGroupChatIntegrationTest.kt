package takagi.ru.monica.steam.friends.groupchat.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGroupChatIntegrationTest {
    @Test
    fun groupChatIsAnIndependentSteamBackedModule() {
        val root = projectFile("app/src/main/java/takagi/ru/monica/steam/friends/groupchat")
        assertTrue(root.resolve("domain").isDirectory)
        assertTrue(root.resolve("data").isDirectory)
        assertTrue(root.resolve("presentation").isDirectory)
        assertTrue(root.resolve("ui").isDirectory)

        val service = root.resolve("data/SteamGroupChatService.kt").readText()
        val attachmentTargets = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/data/SteamChatAttachmentTargetFields.kt"
        ).readText()
        assertTrue(service.contains("ChatRoom.\$method#1"))
        assertTrue(service.contains("CreateChatRoomGroup"))
        assertTrue(service.contains("GetMyChatRoomGroups"))
        assertTrue(service.contains("GetMessageHistory"))
        assertTrue(service.contains("SendChatMessage"))
        assertTrue(service.contains("InviteFriendToChatRoomGroup"))
        assertTrue(attachmentTargets.contains("chat_group_id"))
        assertTrue(attachmentTargets.contains("chat_id"))
    }

    @Test
    fun chatPageExposesGroupListCreateInviteAndFullScreenThread() {
        val chatScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreen.kt"
        ).readText()
        val chatRoot = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatRootContent.kt"
        ).readText()
        val selectedContent = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatSelectedContent.kt"
        ).readText()
        val chatDialogs = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreenDialogs.kt"
        ).readText()
        val threadLifecycle = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatThreadLifecycle.kt"
        ).readText()
        val groupList = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatList.kt"
        ).readText()
        val thread = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()
        val dialogs = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatDialogs.kt"
        ).readText()

        assertTrue(chatScreen.contains("SteamGroupChatViewModel"))
        assertTrue(chatRoot.contains("SteamConversationList("))
        assertFalse(chatRoot.contains("SteamGroupChatList("))
        assertTrue(selectedContent.contains("SteamGroupChatThreadHost("))
        assertTrue(chatDialogs.contains("SteamGroupChatDialogsHost("))
        assertTrue(chatScreen.contains("groupChatState.selectedChatId != null"))
        assertTrue(chatScreen.contains("SteamChatThreadLifecycle("))
        assertTrue(threadLifecycle.contains("richMediaViewModel.selectGroupRoom"))
        assertTrue(threadLifecycle.contains("groupChatViewModel.refreshThread()"))
        assertTrue(groupList.contains("SteamExpressivePullToRefresh"))
        assertTrue(groupList.contains("ExtendedFloatingActionButton"))
        assertTrue(thread.contains("SteamGroupChatThread("))
        assertTrue(thread.contains("SteamChatRichMessageContent"))
        assertTrue(thread.contains("SteamChatComposer("))
        assertTrue(thread.contains("onUploadAttachment"))
        assertTrue(thread.contains("steamWindowTopPadding()"))
        assertTrue(dialogs.contains("SteamCreateGroupDialog("))
        assertTrue(dialogs.contains("SteamInviteFriendDialog("))
        assertTrue(dialogs.contains("FriendSelectionList"))
        assertTrue(chatScreen.contains("groupChatViewModel.openRoom(createdGroup.groupId"))
    }

    @Test
    fun channelNavigationKeepsSteamRoomsAndPreferredEntryRoomInNativeGlass() {
        val conversationList = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamConversationList.kt"
        ).readText()
        val thread = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()

        assertTrue(thread.contains("TelegramGroupChannelPill("))
        assertTrue(thread.contains("TelegramGlassView(context)"))
        assertTrue(thread.contains("onSelect(room.chatId)"))
        assertTrue(thread.contains("room.unread"))
        assertFalse(thread.contains("SteamGroupChannelQuickFilter("))
        assertTrue(conversationList.contains("group.preferredChatId"))
    }

    @Test
    fun groupInfoExposesOfficialChannelManagementActions() {
        val management = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChannelManagement.kt"
        ).readText()
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/data/SteamGroupChatService.kt"
        ).readText()
        val voiceService = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/voice/data/SteamVoiceService.kt"
        ).readText()
        val infoScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatInfoScreen.kt"
        ).readText()

        assertTrue(management.contains("onCreate:"))
        assertTrue(management.contains("onDelete:"))
        assertTrue(management.contains("onReorder:"))
        assertTrue(management.contains("onJoinVoice:"))
        assertTrue(infoScreen.contains("onCreateChannel"))
        assertTrue(service.contains("CreateChatRoom"))
        assertTrue(service.contains("DeleteChatRoom"))
        assertTrue(voiceService.contains("JoinVoiceChat"))
    }

    @Test
    fun groupAdminScreenExposesInvitesRolesBansAndMemberActions() {
        val adminScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupAdminScreen.kt"
        ).readText()
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/data/SteamGroupChatService.kt"
        ).readText()
        val selectedContent = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatSelectedContent.kt"
        ).readText()

        assertTrue(adminScreen.contains("分享链接"))
        assertTrue(adminScreen.contains("已邀请账户"))
        assertTrue(adminScreen.contains("角色与权限"))
        assertTrue(adminScreen.contains("封禁账户"))
        assertTrue(adminScreen.contains("MemberRolesDialog"))
        assertTrue(service.contains("GetInviteLinksForGroup"))
        assertTrue(service.contains("SetUserBanState"))
        assertTrue(service.contains("ReplaceRoleActions"))
        assertTrue(selectedContent.contains("SteamGroupAdminScreen("))
    }

    @Test
    fun groupMessagesExposeSteamReactionReportCopyAndDeleteActions() {
        val thread = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/data/SteamGroupChatService.kt"
        ).readText()

        assertTrue(thread.contains("SteamChatMessageActionMenu("))
        assertTrue(thread.contains("SteamChatReactionPicker("))
        assertTrue(thread.contains("SteamChatReportDialog("))
        assertTrue(thread.contains("onDeleteMessage"))
        assertTrue(service.contains("UpdateMessageReaction"))
        assertTrue(service.contains("ReportMessage"))
        assertTrue(service.contains("DeleteChatMessages"))
    }

    @Test
    fun inviteDialogPinsActionsInsideSafeScreenBounds() {
        val dialogs = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatDialogs.kt"
        ).readText()

        assertTrue(dialogs.contains("usePlatformDefaultWidth = false"))
        assertTrue(dialogs.contains("windowInsetsPadding(WindowInsets.safeDrawing)"))
        assertTrue(dialogs.contains("Modifier.weight(1f)"))
    }

    @Test
    fun groupThreadResizesForImeWithoutPanningTheHeader() {
        val thread = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val scaffold = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/telegram/TelegramChatSurface.kt"
        ).readText()

        assertTrue(thread.contains("TelegramChatScaffold("))
        assertTrue(thread.contains("contentPadding = contentPadding"))
        assertTrue(scaffold.contains("imePadding()"))
        assertTrue(manifest.contains("android:windowSoftInputMode=\"adjustResize\""))
    }

    @Test
    fun groupListsKeepCompositeAvatarsAndNativeHeaderReceivesTheSteamAvatar() {
        val groupList = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatList.kt"
        ).readText()
        val conversationList = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamConversationList.kt"
        ).readText()
        val conversationRow = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamConversationRow.kt"
        ).readText()
        val thread = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()
        val info = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatInfoScreen.kt"
        ).readText()

        assertTrue(groupList.contains("SteamGroupAvatarImage("))
        // The inbox row is a native cell, so the composite has to reach it as a bitmap in the
        // cell's own avatar slot; a composable overlay drifts off that slot.
        assertTrue(conversationRow.contains("rememberSteamGroupAvatarBitmap("))
        // Selection also goes through the same slot: the tick picture replaces the avatar, it is
        // not an overlay that can drift off the cell frame.
        assertTrue(conversationRow.contains("avatarOverride = selectionAvatar ?: groupAvatar"))
        assertTrue(conversationRow.contains("rememberSteamSelectionAvatarBitmap("))
        assertFalse(conversationRow.contains("Modifier.padding(start = 10.dp, top = 10.dp)"))
        assertFalse(conversationRow.contains("SteamGroupAvatarImage("))
        assertTrue(thread.contains("TelegramChatHeader("))
        assertTrue(thread.contains("avatarUrl = group.avatarUrl"))
        assertTrue(info.contains("SteamGroupAvatarImage("))
        assertTrue(groupList.contains("members = groupMembers"))
        assertTrue(conversationRow.contains("members = entry.groupMembers"))
        assertTrue(info.contains("members = members"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null && !File(directory, "settings.gradle").exists()) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
