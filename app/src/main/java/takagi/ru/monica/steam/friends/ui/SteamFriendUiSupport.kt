package takagi.ru.monica.steam.friends.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.domain.SteamFriendsFilter
import takagi.ru.monica.steam.friends.domain.SteamPersonaState
import takagi.ru.monica.steam.friends.presentation.SteamFriendsFailureReason
import takagi.ru.monica.steam.profile.SteamRemoteImageCache

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamFriendAccountSheet(
    accounts: List<SteamAccount>,
    selectedAccountId: Long?,
    onSelectAccount: (Long) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.steam_friends_switch_account),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            accounts.forEach { account ->
                FriendAccountRow(
                    account = account,
                    selected = account.id == selectedAccountId,
                    onClick = { onSelectAccount(account.id) }
                )
            }
        }
    }
}

@Composable
private fun FriendAccountRow(account: SteamAccount, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        headlineContent = {
            Text(
                account.displayName.ifBlank { account.accountName }.ifBlank { account.visibleSteamId },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                account.accountName.ifBlank { account.visibleSteamId },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        account.displayName.ifBlank { account.accountName }.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        trailingContent = {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        )
    )
}

@Composable
internal fun FriendAvatar(friend: SteamFriend, size: Int) {
    val avatar = rememberSteamFriendAvatar(friend.avatarUrl)
    Box(modifier = Modifier.size(size.dp)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            if (avatar != null) {
                Image(
                    bitmap = avatar,
                    contentDescription = friend.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        friend.displayName.take(1).uppercase(),
                        style = if (size >= 80) {
                            MaterialTheme.typography.headlineLarge
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Surface(
            modifier = Modifier
                .size((size / 4).coerceAtLeast(10).dp)
                .align(Alignment.BottomEnd),
            shape = CircleShape,
            color = friend.statusColor()
        ) {}
    }
}

@Composable
private fun rememberSteamFriendAvatar(url: String): ImageBitmap? {
    val context = LocalContext.current
    val cache = remember(context) { SteamRemoteImageCache.get(context.applicationContext) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        value = url.takeIf(String::isNotBlank)?.let { cache.load(it)?.asImageBitmap() }
    }
    return bitmap
}

@Composable
internal fun SteamPersonaState.label(): String = stringResource(
    when (this) {
        SteamPersonaState.ONLINE -> R.string.steam_friend_online
        SteamPersonaState.BUSY -> R.string.steam_friend_busy
        SteamPersonaState.AWAY -> R.string.steam_friend_away
        SteamPersonaState.SNOOZE -> R.string.steam_friend_snooze
        SteamPersonaState.LOOKING_TO_TRADE -> R.string.steam_friend_looking_to_trade
        SteamPersonaState.LOOKING_TO_PLAY -> R.string.steam_friend_looking_to_play
        SteamPersonaState.OFFLINE,
        SteamPersonaState.INVISIBLE -> R.string.steam_friend_offline
    }
)

@Composable
internal fun SteamFriend.statusColor() = when {
    isPlaying -> MaterialTheme.colorScheme.tertiary
    personaState.isOnline -> MaterialTheme.colorScheme.primary
    relationship == SteamFriendRelationship.REQUEST_INCOMING -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.outline
}

internal fun SteamFriendsFilter.labelRes(): Int = when (this) {
    SteamFriendsFilter.ALL -> R.string.steam_friends_filter_all
    SteamFriendsFilter.ONLINE -> R.string.steam_friends_filter_online
    SteamFriendsFilter.PLAYING -> R.string.steam_friends_filter_playing
    SteamFriendsFilter.REQUESTS -> R.string.steam_friends_filter_requests
}

internal fun SteamFriendsFailureReason.messageRes(): Int = when (this) {
    SteamFriendsFailureReason.ACCOUNT_REQUIRED -> R.string.steam_friends_account_required
    SteamFriendsFailureReason.SESSION_REQUIRED -> R.string.steam_friends_session_required
    SteamFriendsFailureReason.NETWORK -> R.string.steam_friends_network_error
    SteamFriendsFailureReason.UNAVAILABLE -> R.string.steam_friends_unavailable
}

internal fun formatSteamFriendTime(timestampSeconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(timestampSeconds * 1_000L))

internal fun openSteamProfile(context: Context, friend: SteamFriend) {
    val url = friend.profileUrl.takeIf { it.startsWith("https://steamcommunity.com/") }
        ?: "https://steamcommunity.com/profiles/${friend.steamId}"
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
