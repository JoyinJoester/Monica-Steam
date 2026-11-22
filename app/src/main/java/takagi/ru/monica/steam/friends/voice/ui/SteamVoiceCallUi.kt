package takagi.ru.monica.steam.friends.voice.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassDropdownMenu as DropdownMenu
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import takagi.ru.monica.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoom
import takagi.ru.monica.steam.friends.ui.FriendAvatar
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceAudioRoute
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceConnectionState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceParticipant

@Composable
internal fun SteamVoiceStatusBanner(
    state: SteamVoiceCallState,
    fallbackTitle: String,
    activeMemberCount: Int = 0,
    onJoin: (() -> Unit)? = null,
    onLeave: (() -> Unit)? = null,
    onToggleMicrophone: (() -> Unit)? = null,
    onToggleOutput: (() -> Unit)? = null,
    onSelectAudioRoute: ((SteamVoiceAudioRoute) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val localCall = state.isActive
    val localParticipantCount = if (localCall) {
        (state.participants.map(SteamVoiceParticipant::steamId) + state.accountSteamId)
            .filter(String::isNotBlank)
            .distinct()
            .size
    } else 0
    val participantCount = maxOf(localParticipantCount, activeMemberCount)
    val emphasized = state.isConnected || (!localCall && participantCount > 0)
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
        shape = MaterialTheme.shapes.large,
        color = if (emphasized) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val partner = state.target?.partnerSteamId
            if (partner != null && localCall) {
                SteamVoiceAvatar(partner, LocalSteamVoiceFriends.current[partner],
                    enabled = state.isConnected && !state.outputMuted &&
                        state.participants.none { it.steamId == partner && (!it.joined || it.micMuted || it.hasNoMic) },
                    avatarSize = 28)
            } else Icon(
                if (emphasized) Icons.Default.Call else Icons.Default.Headset,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.target?.title ?: fallbackTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                SteamVoiceStatusText(
                    text = when {
                        state.isConnected -> stringResource(R.string.steam_voice_status_connected, participantCount.coerceAtLeast(1))
                        state.state == SteamVoiceConnectionState.WAITING_FOR_ACCEPT -> stringResource(R.string.steam_voice_status_waiting)
                        state.state == SteamVoiceConnectionState.RECONNECTING -> stringResource(R.string.steam_voice_status_reconnecting)
                        localCall -> stringResource(R.string.steam_voice_status_connecting)
                        participantCount > 0 -> stringResource(R.string.steam_voice_status_members, participantCount)
                        else -> stringResource(R.string.steam_voice_status_channel)
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (localCall && onToggleMicrophone != null) {
                SteamVoiceControl(
                    icon = if (state.microphoneMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    description = if (state.microphoneMuted) "打开麦克风" else "静音",
                    onClick = onToggleMicrophone,
                    emphasized = state.microphoneMuted
                )
            }
            if (localCall && onToggleOutput != null) {
                VoiceOutputMenu(state, onToggleOutput, onSelectAudioRoute)
            }
            when {
                localCall && onLeave != null -> SteamVoiceControl(
                    icon = Icons.Default.CallEnd, description = "离开语音",
                    onClick = onLeave, destructive = true
                )
                onJoin != null -> FilledTonalButton(onClick = onJoin) { Text("加入") }
            }
        }
    }
}

@Composable
internal fun SteamVoiceChannelPanel(
    room: SteamGroupChatRoom,
    state: SteamVoiceCallState,
    friends: List<SteamFriend>,
    accountSteamId: String,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onToggleOutput: () -> Unit,
    onSelectAudioRoute: (SteamVoiceAudioRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val localRoom = state.target?.chatId == room.chatId && state.isActive
    val callElsewhere = state.isActive && !localRoom
    val friendsById = remember(friends) { friends.associateBy(SteamFriend::steamId) }
    val participantsById = remember(state.participants) {
        state.participants.associateBy(SteamVoiceParticipant::steamId)
    }
    val memberIds = remember(room.voiceMemberSteamIds, state.participants, localRoom, accountSteamId) {
        linkedSetOf<String>().apply {
            addAll(room.voiceMemberSteamIds)
            if (localRoom) {
                addAll(state.participants.map(SteamVoiceParticipant::steamId))
                accountSteamId.takeIf(String::isNotBlank)?.let(::add)
            }
        }.toList()
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Headset,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Text(
            room.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        SteamVoiceStatusText(
            text = when {
                localRoom && state.isConnected -> stringResource(R.string.steam_voice_status_joined)
                localRoom && state.state == SteamVoiceConnectionState.RECONNECTING -> stringResource(R.string.steam_voice_status_reconnecting)
                localRoom -> stringResource(R.string.steam_voice_status_connecting)
                memberIds.isNotEmpty() -> stringResource(R.string.steam_voice_status_members, memberIds.size)
                else -> stringResource(R.string.steam_voice_status_empty)
            },
            style = MaterialTheme.typography.bodyMedium
        )
        if (callElsewhere) {
            Text(
                "当前正在“${state.target?.title.orEmpty()}”中通话，结束后可加入此频道",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (localRoom) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalIconButton(onClick = onToggleMicrophone) {
                    Icon(
                        if (state.microphoneMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (state.microphoneMuted) "打开麦克风" else "静音"
                    )
                }
                VoiceOutputMenu(state, onToggleOutput, onSelectAudioRoute)
                Button(onClick = onLeave) {
                    Icon(Icons.Default.CallEnd, contentDescription = null)
                    Text("离开", Modifier.padding(start = 7.dp))
                }
            }
        } else {
            Button(onClick = onJoin, enabled = !callElsewhere) {
                if (state.target?.chatId == room.chatId && !state.isConnected) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Call, contentDescription = null)
                }
                Text("加入语音", Modifier.padding(start = 7.dp))
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            if (memberIds.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("频道暂时无人", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(memberIds, key = { it }) { steamId ->
                        VoiceParticipantRow(
                            steamId = steamId,
                            friend = friendsById[steamId],
                            participant = if (localRoom && steamId == accountSteamId) {
                                (participantsById[steamId] ?: SteamVoiceParticipant(steamId)).copy(micMuted = state.microphoneMuted)
                            } else participantsById[steamId],
                            isSelf = steamId == accountSteamId,
                            speakingEnabled = localRoom && state.isConnected &&
                                (if (steamId == accountSteamId) !state.microphoneMuted else !state.outputMuted)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceOutputMenu(
    state: SteamVoiceCallState,
    onToggleOutput: () -> Unit,
    onSelectAudioRoute: ((SteamVoiceAudioRoute) -> Unit)?
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SteamVoiceControl(
            icon = if (state.outputMuted) Icons.Default.VolumeOff else routeIcon(state.audioRoute),
            description = "声音与播放设备", onClick = { expanded = true },
            emphasized = state.outputMuted
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (state.outputMuted) "打开声音" else "静音声音") },
                leadingIcon = {
                    Icon(
                        if (state.outputMuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = null
                    )
                },
                onClick = {
                    expanded = false
                    onToggleOutput()
                }
            )
            if (onSelectAudioRoute != null) {
                state.availableAudioRoutes.forEach { route ->
                    DropdownMenuItem(
                        text = {
                            val suffix = when {
                                route == state.audioRoute -> " · 当前"
                                route == state.requestedAudioRoute -> " · 正在切换"
                                else -> ""
                            }
                            Text(routeLabel(route) + suffix)
                        },
                        leadingIcon = {
                            Icon(routeIcon(route), contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            onSelectAudioRoute(route)
                        }
                    )
                }
            }
        }
    }
}

private fun routeLabel(route: SteamVoiceAudioRoute): String = when (route) {
    SteamVoiceAudioRoute.AUTO -> "自动选择"
    SteamVoiceAudioRoute.EARPIECE -> "听筒"
    SteamVoiceAudioRoute.SPEAKER -> "扬声器"
    SteamVoiceAudioRoute.WIRED -> "有线耳机"
    SteamVoiceAudioRoute.BLUETOOTH -> "蓝牙耳机"
}

private fun routeIcon(route: SteamVoiceAudioRoute) = when (route) {
    SteamVoiceAudioRoute.EARPIECE -> Icons.Default.Call
    SteamVoiceAudioRoute.SPEAKER -> Icons.Default.VolumeUp
    SteamVoiceAudioRoute.AUTO,
    SteamVoiceAudioRoute.WIRED,
    SteamVoiceAudioRoute.BLUETOOTH -> Icons.Default.Headset
}

@Composable
private fun VoiceParticipantRow(
    steamId: String,
    friend: SteamFriend?,
    participant: SteamVoiceParticipant?,
    isSelf: Boolean,
    speakingEnabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SteamVoiceAvatar(steamId, friend, enabled = speakingEnabled &&
                participant?.micMuted != true && participant?.hasNoMic != true && participant?.joined != false)
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        isSelf -> "${friend?.displayName ?: "本账号"} · 自己"
                        friend != null -> friend.displayName
                        else -> steamId
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (friend == null && !isSelf) {
                    Text(
                        steamId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                if (participant?.hasNoMic == true || participant?.micMuted == true) {
                    Icons.Default.MicOff
                } else {
                    Icons.Default.Mic
                },
                contentDescription = null,
                tint = if (participant?.hasNoMic == true || participant?.micMuted == true) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}
