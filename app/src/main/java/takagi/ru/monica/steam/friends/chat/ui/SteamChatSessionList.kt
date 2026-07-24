package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatFailureReason
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.ui.FriendAvatar
import takagi.ru.monica.steam.friends.ui.label

@Composable
internal fun SteamChatSessionList(
    state: SteamChatUiState,
    friends: List<SteamFriend>,
    query: String,
    onOpenThread: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val normalizedQuery = query.trim()
    val friendById = friends.associateBy(SteamFriend::steamId)
    val sessions = state.sessions?.sessions.orEmpty()
    val sessionByPartner = sessions.associateBy(SteamChatSession::partnerSteamId)
    val visiblePartnerIds = buildList {
        sessions.filter { session ->
            val friend = friendById[session.partnerSteamId]
            normalizedQuery.isBlank() || friend?.matchesChatQuery(normalizedQuery) == true ||
                session.partnerSteamId.contains(normalizedQuery, ignoreCase = true)
        }.forEach { add(it.partnerSteamId) }
        if (normalizedQuery.isNotBlank()) {
            friends.filter { it.matchesChatQuery(normalizedQuery) }
                .forEach { if (it.steamId !in this) add(it.steamId) }
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "chat-summary") {
                ChatSessionSummary(
                    conversationCount = sessions.size,
                    unreadCount = state.unreadCount
                )
            }
            state.sessionsFailure?.let { failure ->
                item(key = "chat-error") {
                    ChatFailureBanner(failure = failure, onRetry = onRetry)
                }
            }
            if (state.sessionsLoading && state.sessions == null) {
                items(5, key = { "chat-loading-$it" }) {
                    ChatSessionLoadingRow()
                }
            } else if (visiblePartnerIds.isEmpty()) {
                item(key = "chat-empty") {
                    ChatEmptyState(hasQuery = normalizedQuery.isNotBlank(), onRetry = onRetry)
                }
            } else {
                items(visiblePartnerIds, key = { it }) { partnerSteamId ->
                    ChatSessionRow(
                        friend = friendById[partnerSteamId],
                        partnerSteamId = partnerSteamId,
                        session = sessionByPartner[partnerSteamId],
                        onClick = { onOpenThread(partnerSteamId) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
        if (state.sessionsRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun ChatSessionSummary(conversationCount: Int, unreadCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.steam_chat_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.steam_chat_conversation_count, conversationCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (unreadCount > 0) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    text = stringResource(R.string.steam_chat_unread_count, unreadCount),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ChatSessionRow(
    friend: SteamFriend?,
    partnerSteamId: String,
    session: SteamChatSession?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if ((session?.unreadCount ?: 0) > 0) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (friend != null) {
                FriendAvatar(friend = friend, size = 50)
            } else {
                Surface(
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = friend?.displayName ?: partnerSteamId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = friend?.let { it.personaState.label() }
                        ?: stringResource(R.string.steam_chat_open_conversation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if ((session?.lastMessageTimestamp ?: 0L) > 0L) {
                    Text(
                        text = formatChatSessionTime(session?.lastMessageTimestamp ?: 0L),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if ((session?.unreadCount ?: 0) > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text((session?.unreadCount ?: 0).coerceAtMost(99).toString())
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatFailureBanner(failure: SteamChatFailureReason, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(failure.chatMessageRes()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            if (failure != SteamChatFailureReason.ACCOUNT_REQUIRED) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                }
            }
        }
    }
}

@Composable
private fun ChatEmptyState(hasQuery: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(30.dp))
            }
        }
        Text(
            text = stringResource(
                if (hasQuery) R.string.steam_chat_empty_search else R.string.steam_chat_empty
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!hasQuery) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
            }
        }
    }
}

@Composable
private fun ChatSessionLoadingRow() {
    Surface(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(Modifier.size(48.dp), CircleShape, MaterialTheme.colorScheme.surfaceContainerHighest) {}
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
}

private fun SteamFriend.matchesChatQuery(query: String): Boolean =
    displayName.contains(query, ignoreCase = true) ||
        realName.contains(query, ignoreCase = true) ||
        steamId.contains(query, ignoreCase = true)

private fun formatChatSessionTime(timestampSeconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(timestampSeconds * 1_000L))

internal fun SteamChatFailureReason.chatMessageRes(): Int = when (this) {
    SteamChatFailureReason.ACCOUNT_REQUIRED -> R.string.steam_chat_account_required
    SteamChatFailureReason.SESSION_REQUIRED -> R.string.steam_chat_session_required
    SteamChatFailureReason.NETWORK -> R.string.steam_chat_network_error
    SteamChatFailureReason.UNAVAILABLE -> R.string.steam_chat_unavailable
}
