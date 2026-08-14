package takagi.ru.monica.steam.friends.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.actions.presentation.SteamChatMessageActionViewModel
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationPreferences
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryItem
import takagi.ru.monica.steam.friends.chat.info.ui.SteamChatFriendDetailScreen
import takagi.ru.monica.steam.friends.chat.info.ui.SteamChatHistorySearchScreen
import takagi.ru.monica.steam.friends.chat.info.ui.SteamChatInfoScreen
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatViewModel
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaViewModel
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatViewModel
import takagi.ru.monica.steam.friends.groupchat.ui.SteamGroupAdminScreen
import takagi.ru.monica.steam.friends.groupchat.ui.SteamGroupChatThreadHost
import takagi.ru.monica.steam.friends.presentation.SteamFriendsUiState
import takagi.ru.monica.steam.friends.presentation.SteamFriendsViewModel
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.presentation.SteamVoiceCallRuntime
import takagi.ru.monica.steam.store.share.domain.SteamStoreGameShare
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit

@Composable
internal fun SteamChatSelectedContent(
    partnerSteamId: String?,
    currentSubpage: SteamChatSubpage?,
    selectedAccount: SteamAccount?,
    selectedFriend: SteamFriend?,
    friendsState: SteamFriendsUiState,
    chatState: SteamChatUiState,
    groupChatState: SteamGroupChatUiState,
    richMediaState: SteamChatRichMediaUiState,
    voiceState: SteamVoiceCallState,
    conversationPreferences: SteamChatConversationPreferences,
    targetMessageId: String?,
    gameShareDraft: SteamStoreGameShare?,
    chatViewModel: SteamChatViewModel,
    friendsViewModel: SteamFriendsViewModel,
    groupChatViewModel: SteamGroupChatViewModel,
    richMediaViewModel: SteamChatRichMediaViewModel,
    messageActionViewModel: SteamChatMessageActionViewModel,
    voiceRuntime: SteamVoiceCallRuntime,
    runVoiceAction: (() -> Unit) -> Unit,
    onSubpageChange: (SteamChatSubpage?) -> Unit,
    onCreateGroupFromFriend: (String) -> Unit,
    onInviteFriend: () -> Unit,
    onPreferencesChange: (SteamChatConversationPreferences) -> Unit,
    onOpenTargetMessage: (String) -> Unit,
    onConsumeGameShareDraft: () -> Unit,
    onOpenStoreApp: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (currentSubpage) {
        SteamChatSubpage.INFO -> SteamChatInfoContent(
            partnerSteamId = partnerSteamId,
            selectedFriend = selectedFriend,
            selectedAccount = selectedAccount,
            friendsState = friendsState,
            groupChatState = groupChatState,
            conversationPreferences = conversationPreferences,
            chatViewModel = chatViewModel,
            friendsViewModel = friendsViewModel,
            groupChatViewModel = groupChatViewModel,
            voiceState = voiceState,
            voiceRuntime = voiceRuntime,
            runVoiceAction = runVoiceAction,
            onSubpageChange = onSubpageChange,
            onCreateGroupFromFriend = onCreateGroupFromFriend,
            onInviteFriend = onInviteFriend,
            onPreferencesChange = onPreferencesChange,
            modifier = modifier
        )
        SteamChatSubpage.ADMIN -> SteamChatAdminContent(
            friendsState = friendsState,
            groupChatState = groupChatState,
            groupChatViewModel = groupChatViewModel,
            onSubpageChange = onSubpageChange,
            modifier = modifier
        )
        SteamChatSubpage.SEARCH -> SteamChatSearchContent(
            partnerSteamId = partnerSteamId,
            selectedFriend = selectedFriend,
            friendsState = friendsState,
            chatState = chatState,
            groupChatState = groupChatState,
            onSubpageChange = onSubpageChange,
            onOpenTargetMessage = onOpenTargetMessage,
            modifier = modifier
        )
        null -> if (partnerSteamId != null) {
            SteamChatThread(
                state = chatState,
                richMediaState = richMediaState,
                friend = selectedFriend,
                targetMessageId = targetMessageId,
                gameShareDraft = gameShareDraft,
                onNavigateBack = chatViewModel::closeThread,
                onOpenInfo = { onSubpageChange(SteamChatSubpage.INFO) },
                onRefresh = chatViewModel::refreshThread,
                onLoadOlder = chatViewModel::loadOlder,
                onSend = chatViewModel::sendMessage,
                onRetryMessage = chatViewModel::retryMessage,
                onReact = { message, emoticon ->
                    messageActionViewModel.react(partnerSteamId, message, emoticon)
                },
                onStickerReply = { message, stickerCode ->
                    chatViewModel.sendReply(stickerCode, message.stableId)
                },
                onReport = { message, reason ->
                    messageActionViewModel.report(partnerSteamId, message, reason)
                },
                onAttachmentSelected = richMediaViewModel::selectAttachment,
                onAttachmentSpoilerChanged = richMediaViewModel::setAttachmentSpoiler,
                onUploadAttachment = richMediaViewModel::uploadAttachment,
                onClearAttachment = richMediaViewModel::clearAttachment,
                onClearAttachmentFailure = richMediaViewModel::clearAttachmentFailure,
                onRefreshCatalogs = richMediaViewModel::refreshCatalogs,
                onConsumeGameShareDraft = onConsumeGameShareDraft,
                voiceState = voiceState,
                onStartVoice = {
                    val friendName = selectedFriend?.displayName ?: partnerSteamId
                    runVoiceAction {
                        selectedAccount?.let { account ->
                            voiceRuntime.startDirect(account, partnerSteamId, friendName)
                        }
                    }
                },
                onStopVoice = voiceRuntime::stop,
                onToggleVoiceMicrophone = voiceRuntime::toggleMicrophone,
                onToggleVoiceOutput = voiceRuntime::toggleOutput,
                onSelectVoiceAudioRoute = voiceRuntime::selectAudioRoute,
                onOpenStoreApp = onOpenStoreApp,
                modifier = modifier.fillMaxSize()
            )
        } else {
            SteamGroupChatThreadHost(
                state = groupChatState,
                richMediaState = richMediaState,
                friends = friendsState.snapshot?.friends.orEmpty(),
                targetMessageId = targetMessageId,
                onBack = groupChatViewModel::closeRoom,
                onOpenInfo = { onSubpageChange(SteamChatSubpage.INFO) },
                onOpenRoom = groupChatViewModel::openRoom,
                onLoadOlder = groupChatViewModel::loadOlder,
                onSend = groupChatViewModel::sendMessage,
                onRetryMessage = groupChatViewModel::retryMessage,
                onInvite = onInviteFriend,
                onAttachmentSelected = richMediaViewModel::selectAttachment,
                onAttachmentSpoilerChanged = richMediaViewModel::setAttachmentSpoiler,
                onUploadAttachment = richMediaViewModel::uploadAttachment,
                onClearAttachment = richMediaViewModel::clearAttachment,
                onClearAttachmentFailure = richMediaViewModel::clearAttachmentFailure,
                onRefreshCatalogs = richMediaViewModel::refreshCatalogs,
                onUpdateReaction = groupChatViewModel::updateMessageReaction,
                onReportMessage = groupChatViewModel::reportMessage,
                onDeleteMessage = groupChatViewModel::deleteMessage,
                voiceState = voiceState,
                onJoinVoice = { chatId ->
                    val group = groupChatState.groups.firstOrNull {
                        it.groupId == groupChatState.selectedGroupId
                    }
                    runVoiceAction {
                        selectedAccount?.let { account ->
                            voiceRuntime.startGroup(
                                account,
                                groupChatState.selectedGroupId.orEmpty(),
                                chatId,
                                group?.name ?: "Steam 语音"
                            )
                        }
                    }
                },
                onLeaveVoice = voiceRuntime::stop,
                onToggleVoiceMicrophone = voiceRuntime::toggleMicrophone,
                onToggleVoiceOutput = voiceRuntime::toggleOutput,
                onSelectVoiceAudioRoute = voiceRuntime::selectAudioRoute,
                onOpenStoreApp = onOpenStoreApp,
                modifier = modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SteamChatInfoContent(
    partnerSteamId: String?,
    selectedFriend: SteamFriend?,
    selectedAccount: SteamAccount?,
    friendsState: SteamFriendsUiState,
    groupChatState: SteamGroupChatUiState,
    conversationPreferences: SteamChatConversationPreferences,
    chatViewModel: SteamChatViewModel,
    friendsViewModel: SteamFriendsViewModel,
    groupChatViewModel: SteamGroupChatViewModel,
    voiceState: SteamVoiceCallState,
    voiceRuntime: SteamVoiceCallRuntime,
    runVoiceAction: (() -> Unit) -> Unit,
    onSubpageChange: (SteamChatSubpage?) -> Unit,
    onCreateGroupFromFriend: (String) -> Unit,
    onInviteFriend: () -> Unit,
    onPreferencesChange: (SteamChatConversationPreferences) -> Unit,
    modifier: Modifier
) {
    val reduceAnimations = LocalReduceAnimations.current
    val group = groupChatState.groups.firstOrNull { it.groupId == groupChatState.selectedGroupId }
    val friendMap = friendsState.snapshot?.friends.orEmpty().associateBy { it.steamId }
    val groupMembers = group?.topMemberSteamIds.orEmpty().mapNotNull(friendMap::get)
    var detailFriendSteamId by remember(partnerSteamId, groupChatState.selectedGroupId) {
        mutableStateOf<String?>(null)
    }
    BackHandler(enabled = detailFriendSteamId != null) {
        detailFriendSteamId = null
    }
    AnimatedContent(
        targetState = detailFriendSteamId,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            easyNotesScreenEnter(reduceAnimations)
                .togetherWith(easyNotesScreenExit(reduceAnimations))
        },
        label = "SteamChatInfoFriendDetails"
    ) { animatedSteamId ->
        val animatedFriend = animatedSteamId?.let(friendMap::get)
        if (animatedFriend != null) {
            SteamChatFriendDetailScreen(
                friend = animatedFriend,
                actionInProgress = friendsState.actionSteamId == animatedFriend.steamId,
                onBack = { detailFriendSteamId = null },
                onStartChat = {
                    detailFriendSteamId = null
                    onSubpageChange(null)
                    if (partnerSteamId != animatedFriend.steamId) {
                        groupChatViewModel.closeRoom()
                        chatViewModel.openThread(animatedFriend.steamId)
                    }
                },
                onChangeRelationship = { action ->
                    friendsViewModel.changeRelationship(animatedFriend, action)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            SteamChatInfoScreen(
                title = if (partnerSteamId != null) "聊天信息" else "群聊信息",
                directFriend = if (partnerSteamId != null) selectedFriend else null,
                group = group,
                members = groupMembers,
                preferences = conversationPreferences,
                canEditGroup = group?.ownerAccountId?.let { owner ->
                    owner > 0L && accountIdFromSteamId(groupChatState.accountSteamId) == owner
                } == true,
                updatingGroup = groupChatState.updatingGroup,
                updatingGroupAvatar = groupChatState.updatingGroupAvatar,
                onBack = { onSubpageChange(null) },
                onAddMember = {
                    if (partnerSteamId != null) onCreateGroupFromFriend(partnerSteamId)
                    else onInviteFriend()
                },
                onOpenFriendDetails = { detailFriendSteamId = it.steamId },
                onSearchHistory = { onSubpageChange(SteamChatSubpage.SEARCH) },
                onOpenGroupAdmin = { onSubpageChange(SteamChatSubpage.ADMIN) },
                onPreferencesChange = onPreferencesChange,
                onUpdateGroup = groupChatViewModel::updateGroup,
                onUpdateGroupAvatar = groupChatViewModel::updateGroupAvatar,
                channelActionLoading = groupChatState.channelActionLoading,
                voiceState = voiceState,
                onCreateChannel = groupChatViewModel::createChannel,
                onRenameChannel = groupChatViewModel::renameChannel,
                onDeleteChannel = groupChatViewModel::deleteChannel,
                onReorderChannel = groupChatViewModel::reorderChannel,
                onJoinVoiceChat = { chatId ->
                    group?.let { targetGroup ->
                        runVoiceAction {
                            selectedAccount?.let { account ->
                                voiceRuntime.startGroup(
                                    account,
                                    targetGroup.groupId,
                                    chatId,
                                    targetGroup.name
                                )
                            }
                        }
                    }
                },
                onLeaveVoiceChat = voiceRuntime::stop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SteamChatAdminContent(
    friendsState: SteamFriendsUiState,
    groupChatState: SteamGroupChatUiState,
    groupChatViewModel: SteamGroupChatViewModel,
    onSubpageChange: (SteamChatSubpage?) -> Unit,
    modifier: Modifier
) {
    val group = groupChatState.groups.firstOrNull { it.groupId == groupChatState.selectedGroupId }
        ?: return
    SteamGroupAdminScreen(
        group = group,
        snapshot = groupChatState.adminSnapshot,
        friends = friendsState.snapshot?.friends.orEmpty(),
        loading = groupChatState.adminLoading,
        actionLoading = groupChatState.adminActionLoading,
        canEdit = group.ownerAccountId.let { owner ->
            owner > 0L && accountIdFromSteamId(groupChatState.accountSteamId) == owner
        },
        createdInviteLink = groupChatState.createdInviteLink,
        onBack = { onSubpageChange(SteamChatSubpage.INFO) },
        onRefresh = groupChatViewModel::refreshAdminSnapshot,
        onCreateInviteLink = groupChatViewModel::createInviteLink,
        onDeleteInviteLink = groupChatViewModel::deleteInviteLink,
        onRevokeInvite = groupChatViewModel::revokeInvite,
        onSetBanState = groupChatViewModel::setUserBanState,
        onKick = groupChatViewModel::kickUser,
        onMute = groupChatViewModel::muteUser,
        onCreateRole = groupChatViewModel::createRole,
        onRenameRole = groupChatViewModel::renameRole,
        onDeleteRole = groupChatViewModel::deleteRole,
        onReplaceRoleActions = groupChatViewModel::replaceRoleActions,
        onAddRoleToUser = groupChatViewModel::addRoleToUser,
        onRemoveRoleFromUser = groupChatViewModel::removeRoleFromUser,
        onClearCreatedInviteLink = groupChatViewModel::clearCreatedInviteLink,
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun SteamChatSearchContent(
    partnerSteamId: String?,
    selectedFriend: SteamFriend?,
    friendsState: SteamFriendsUiState,
    chatState: SteamChatUiState,
    groupChatState: SteamGroupChatUiState,
    onSubpageChange: (SteamChatSubpage?) -> Unit,
    onOpenTargetMessage: (String) -> Unit,
    modifier: Modifier
) {
    val friendsById = friendsState.snapshot?.friends.orEmpty().associateBy { it.steamId }
    val items = if (partnerSteamId != null) {
        chatState.thread?.messages.orEmpty().map { message ->
            SteamChatHistoryItem(
                id = message.stableId,
                senderName = if (message.senderSteamId == chatState.accountSteamId) "我"
                    else selectedFriend?.displayName ?: message.senderSteamId,
                body = message.body,
                timestamp = message.timestamp
            )
        }
    } else {
        groupChatState.thread?.messages.orEmpty().map { message ->
            SteamChatHistoryItem(
                id = message.stableId,
                senderName = if (message.senderSteamId == groupChatState.accountSteamId) "我"
                    else friendsById[message.senderSteamId]?.displayName ?: message.senderSteamId,
                body = message.body,
                timestamp = message.timestamp
            )
        }
    }
    SteamChatHistorySearchScreen(
        items = items,
        onBack = { onSubpageChange(SteamChatSubpage.INFO) },
        onOpenMessage = onOpenTargetMessage,
        modifier = modifier.fillMaxSize()
    )
}
