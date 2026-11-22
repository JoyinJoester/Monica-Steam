package takagi.ru.monica.steam.friends.chat.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassAlertDialog as AlertDialog

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.data.SteamAccountSourceState
import takagi.ru.monica.steam.data.hasAuthenticatedSession
import takagi.ru.monica.steam.foundation.ui.SteamAccountSwitcherSheet
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatViewModel
import takagi.ru.monica.steam.friends.groupchat.ui.SteamGroupChatDialogsHost
import takagi.ru.monica.steam.friends.presentation.SteamFriendsUiState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.presentation.SteamVoiceCallRuntime

@Composable
internal fun SteamChatScreenDialogs(
    standalone: Boolean,
    showAccounts: Boolean,
    showCreateGroup: Boolean,
    showInviteFriend: Boolean,
    initialGroupInvitees: Set<String>,
    selectedAccount: SteamAccount?,
    accountSourceState: SteamAccountSourceState,
    friendsState: SteamFriendsUiState,
    groupChatState: SteamGroupChatUiState,
    voiceState: SteamVoiceCallState,
    accountSourceRepository: SteamAccountSourceRepository,
    groupChatViewModel: SteamGroupChatViewModel,
    voiceRuntime: SteamVoiceCallRuntime,
    runVoiceAction: (() -> Unit) -> Unit,
    onAddSteamAccount: () -> Unit,
    onShowAccountsChange: (Boolean) -> Unit,
    onShowCreateGroupChange: (Boolean) -> Unit,
    onShowInviteFriendChange: (Boolean) -> Unit,
    onInitialGroupInviteesChange: (Set<String>) -> Unit
) {
    voiceState.incomingRequest?.let { request ->
        val incomingFriendName = friendsState.snapshot?.friends
            ?.firstOrNull { it.steamId == request.partnerSteamId }
            ?.displayName
            .orEmpty()
            .ifBlank { request.partnerSteamId }
        AlertDialog(
            onDismissRequest = voiceRuntime::rejectIncoming,
            title = { Text("收到 Steam 语音邀请") },
            text = { Text("$incomingFriendName 邀请进行语音聊天") },
            confirmButton = {
                TextButton(
                    onClick = {
                        runVoiceAction {
                            selectedAccount?.let { account ->
                                voiceRuntime.acceptIncoming(account, incomingFriendName)
                            }
                        }
                    }
                ) { Text("接听") }
            },
            dismissButton = {
                TextButton(onClick = voiceRuntime::rejectIncoming) { Text("拒绝") }
            }
        )
    }

    if (standalone && showAccounts) {
        val sessionAccounts = accountSourceState.accounts.filter { it.hasAuthenticatedSession }
        SteamAccountSwitcherSheet(
            accounts = sessionAccounts,
            selectedAccountId = selectedAccount?.id,
            storageSource = accountSourceState.storageSource,
            mdbxDatabases = accountSourceState.mdbxDatabases,
            loading = accountSourceState.loading,
            errorMessage = accountSourceState.errorMessage,
            onSelectStorageSource = accountSourceRepository::selectStorageSource,
            onSelectAccount = { accountId ->
                accountSourceRepository.selectAccount(accountId)
                onShowAccountsChange(false)
            },
            onAddAccount = onAddSteamAccount,
            onRefresh = accountSourceRepository::refreshCurrentSource,
            onDismiss = { onShowAccountsChange(false) }
        )
    }

    SteamGroupChatDialogsHost(
        state = groupChatState,
        friends = friendsState.snapshot?.acceptedFriends.orEmpty(),
        showCreateGroup = showCreateGroup,
        showInviteFriend = showInviteFriend,
        initialInviteeSteamIds = initialGroupInvitees,
        onCreate = groupChatViewModel::createGroup,
        onInvite = {
            groupChatViewModel.inviteFriend(it)
            onShowInviteFriendChange(false)
        },
        onDismissCreate = {
            if (!groupChatState.creatingGroup) {
                onShowCreateGroupChange(false)
                onInitialGroupInviteesChange(emptySet())
            }
        },
        onDismissInvite = { onShowInviteFriendChange(false) }
    )
}
