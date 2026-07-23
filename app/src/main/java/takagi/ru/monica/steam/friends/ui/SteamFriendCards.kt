package takagi.ru.monica.steam.friends.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.domain.SteamFriendsSnapshot
import takagi.ru.monica.ui.theme.GoogleSansFlexFontFamily

@Composable
internal fun FriendsSummaryCard(snapshot: SteamFriendsSnapshot?, account: SteamAccount?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(
                        R.string.steam_friends_summary,
                        snapshot?.acceptedFriends?.size ?: 0,
                        snapshot?.onlineCount ?: 0
                    ),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansFlexFontFamily),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = account?.displayName?.ifBlank { account.accountName }
                        ?: stringResource(R.string.steam_friends_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (snapshot != null &&
                    (snapshot.incomingRequests.isNotEmpty() || snapshot.outgoingRequests.isNotEmpty())
                ) {
                    Text(
                        text = stringResource(
                            R.string.steam_friends_requests_summary,
                            snapshot.incomingRequests.size,
                            snapshot.outgoingRequests.size
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
internal fun SteamFriendCard(
    friend: SteamFriend,
    actionInProgress: Boolean,
    actionsEnabled: Boolean,
    onClick: () -> Unit,
    onRespondToInvite: (Boolean) -> Unit
) {
    val isIncoming = friend.relationship == SteamFriendRelationship.REQUEST_INCOMING
    val isOutgoing = friend.relationship == SteamFriendRelationship.REQUEST_OUTGOING
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isIncoming -> MaterialTheme.colorScheme.tertiaryContainer
                friend.isPlaying -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FriendAvatar(friend = friend, size = 52)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = friend.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isIncoming -> stringResource(R.string.steam_friend_incoming_request)
                            isOutgoing -> stringResource(R.string.steam_friend_outgoing_request)
                            friend.isPlaying -> stringResource(
                                R.string.steam_friend_playing,
                                friend.gameName.ifBlank { friend.gameId }
                            )
                            else -> friend.personaState.label()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (friend.isPlaying) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (actionInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Surface(
                        modifier = Modifier.size(12.dp),
                        shape = CircleShape,
                        color = friend.statusColor()
                    ) {}
                }
            }
            if (isIncoming) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        onClick = { onRespondToInvite(false) },
                        enabled = actionsEnabled,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.steam_friend_ignore))
                    }
                    Button(
                        onClick = { onRespondToInvite(true) },
                        enabled = actionsEnabled,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.steam_friend_accept))
                    }
                }
            }
        }
    }
}

@Composable
internal fun FriendLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth().height(78.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {}
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(modifier = Modifier.fillMaxWidth(0.58f).height(14.dp), shape = RoundedCornerShape(7.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {}
                Surface(modifier = Modifier.fillMaxWidth(0.38f).height(10.dp), shape = RoundedCornerShape(5.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {}
            }
        }
    }
}
