package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.ui.FriendAvatar
import takagi.ru.monica.steam.friends.ui.label

@Composable
internal fun SteamChatThread(
    state: SteamChatUiState,
    richMediaState: SteamChatRichMediaUiState,
    friend: SteamFriend?,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadOlder: () -> Unit,
    onSend: (String) -> Unit,
    onRetryMessage: (String) -> Unit,
    onAttachmentSelected: (String) -> Unit,
    onAttachmentSpoilerChanged: (Boolean) -> Unit,
    onUploadAttachment: () -> Unit,
    onClearAttachment: () -> Unit,
    onClearAttachmentFailure: () -> Unit,
    onRefreshCatalogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages = state.thread?.messages.orEmpty()
    val listState = rememberLazyListState()
    val shouldLoadOlder by remember(listState, state.thread?.moreAvailable, state.loadingOlder) {
        derivedStateOf {
            state.thread?.moreAvailable == true &&
                !state.loadingOlder &&
                listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index?.let { it <= 2 } == true
        }
    }

    LaunchedEffect(shouldLoadOlder) {
        if (shouldLoadOlder) onLoadOlder()
    }

    LaunchedEffect(messages.lastOrNull()?.stableId) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = modifier) {
        ChatThreadHeader(
            friend = friend,
            partnerSteamId = state.selectedPartnerSteamId.orEmpty(),
            refreshing = state.threadRefreshing,
            onNavigateBack = onNavigateBack,
            onRefresh = onRefresh
        )
        if (state.threadRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.threadFailure?.let { failure ->
            ChatFailureBanner(
                failure = failure,
                onRetry = onRefresh
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.threadLoading && state.thread == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                messages.isEmpty() -> {
                    ChatThreadEmptyState(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (state.loadingOlder) {
                            item(key = "chat-history-loading") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                        itemsIndexed(
                            items = messages,
                            key = { _, message -> message.stableId }
                        ) { index, message ->
                            val previous = messages.getOrNull(index - 1)
                            val next = messages.getOrNull(index + 1)
                            val showDate = previous == null || !sameChatDay(
                                previous.timestamp,
                                message.timestamp
                            )
                            if (showDate) {
                                ChatDateSeparator(timestampSeconds = message.timestamp)
                            }
                            SteamChatMessageBubble(
                                message = message,
                                accountSteamId = state.accountSteamId,
                                groupedWithPrevious = previous?.senderSteamId == message.senderSteamId &&
                                    sameChatDay(previous.timestamp, message.timestamp),
                                groupedWithNext = next?.senderSteamId == message.senderSteamId &&
                                    sameChatDay(next.timestamp, message.timestamp),
                                onRetry = { onRetryMessage(message.clientMessageId) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
        SteamChatComposer(
            richMediaState = richMediaState,
            onSend = onSend,
            onAttachmentSelected = onAttachmentSelected,
            onAttachmentSpoilerChanged = onAttachmentSpoilerChanged,
            onUploadAttachment = onUploadAttachment,
            onClearAttachment = onClearAttachment,
            onClearAttachmentFailure = onClearAttachmentFailure,
            onRefreshCatalogs = onRefreshCatalogs
        )
    }
}

@Composable
private fun ChatThreadHeader(
    friend: SteamFriend?,
    partnerSteamId: String,
    refreshing: Boolean,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back)
            )
        }
        if (friend != null) {
            FriendAvatar(friend = friend, size = 42)
        } else {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = friend?.displayName ?: partnerSteamId,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = friend?.let { it.personaState.label() }
                    ?: stringResource(R.string.steam_chat_conversation),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRefresh, enabled = !refreshing) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
        }
    }
}

@Composable
private fun ChatDateSeparator(timestampSeconds: Long) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Text(
                text = DateFormat.getDateInstance(DateFormat.MEDIUM)
                    .format(Date(timestampSeconds * 1_000L)),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun ChatThreadEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(30.dp))
            }
        }
        Text(
            text = stringResource(R.string.steam_chat_thread_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun sameChatDay(firstSeconds: Long, secondSeconds: Long): Boolean {
    val formatter = DateFormat.getDateInstance(DateFormat.SHORT)
    return formatter.format(Date(firstSeconds * 1_000L)) ==
        formatter.format(Date(secondSeconds * 1_000L))
}
