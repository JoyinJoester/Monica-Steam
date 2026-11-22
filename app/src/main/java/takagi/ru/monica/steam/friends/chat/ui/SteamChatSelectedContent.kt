package takagi.ru.monica.steam.friends.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import takagi.ru.monica.steam.navigation.steamPageTransition

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
    targetMessageNonce: Int = 0,
    highlightMessageId: String? = null,
    onHighlightConsumed: () -> Unit = {},
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
    val localDeletion = rememberSteamChatLocalDeletion(chatState.accountSteamId, partnerSteamId)
    val visibleThread = remember(chatState.thread, partnerSteamId, localDeletion, localDeletion.markers, localDeletion.failed) {
        if (partnerSteamId == null) chatState.thread else localDeletion.visibleState(chatState).thread
    }
    val visibleChatState = if (partnerSteamId != null && localDeletion.markers != null) {
        chatState.copy(thread = visibleThread)
    } else {
        if (partnerSteamId == null) chatState else localDeletion.visibleState(chatState)
    }
    // Classify history only when a consumer needs it, so opening the thread does not parse
    // every cached message a second time for counters which are not displayed there.
    val needsHistory = currentSubpage == SteamChatSubpage.INFO
    val historyItems = remember(needsHistory, partnerSteamId, visibleChatState.thread, groupChatState.thread, friendsState.snapshot) {
        if (!needsHistory) emptyList() else buildSteamChatHistory(
            partnerSteamId = partnerSteamId,
            selectedFriend = selectedFriend,
            friendsById = friendsState.snapshot?.friends.orEmpty().associateBy(SteamFriend::steamId),
            directMessages = visibleChatState.thread?.messages.orEmpty(),
            directAccountSteamId = chatState.accountSteamId,
            groupMessages = groupChatState.thread?.messages.orEmpty(),
            groupAccountSteamId = groupChatState.accountSteamId
        )
    }
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
            sharedHistoryItems = historyItems,
            onOpenSharedMessage = onOpenTargetMessage,
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
        null -> if (partnerSteamId != null) {
            SteamChatThread(
                state = visibleChatState,
                onDeleteMessageLocally = if (localDeletion.markers != null && !localDeletion.saving) localDeletion.delete else null,
                wallpaperPreset = conversationPreferences.wallpaper,
                richMediaState = richMediaState,
                friend = selectedFriend,
                targetMessageId = targetMessageId,
                targetMessageNonce = targetMessageNonce,
                highlightMessageId = highlightMessageId,
                onHighlightConsumed = onHighlightConsumed,
                gameShareDraft = gameShareDraft,
                onNavigateBack = chatViewModel::closeThread,
                onOpenInfo = { onSubpageChange(SteamChatSubpage.INFO) },
                onRefresh = { localDeletion.revision++; chatViewModel.refreshThread() },
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
                wallpaperPreset = conversationPreferences.wallpaper,
                richMediaState = richMediaState,
                friends = friendsState.snapshot?.friends.orEmpty(),
                targetMessageId = targetMessageId,
                targetMessageNonce = targetMessageNonce,
                highlightMessageId = highlightMessageId,
                onHighlightConsumed = onHighlightConsumed,
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
    sharedHistoryItems: List<SteamChatHistoryItem>,
    onOpenSharedMessage: (String) -> Unit,
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
            steamPageTransition(reduceAnimations, forward = targetState != null)
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
                account = selectedAccount,
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
                sharedHistoryItems = sharedHistoryItems,
                onOpenSharedMessage = onOpenSharedMessage,
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
