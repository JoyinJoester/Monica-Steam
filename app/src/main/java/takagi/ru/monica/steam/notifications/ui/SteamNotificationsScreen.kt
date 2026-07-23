package takagi.ru.monica.steam.notifications.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.gifts.domain.*
import takagi.ru.monica.steam.notifications.domain.*

private enum class SteamNotificationFilter {
    ALL,
    UNREAD,
    ACTIONS
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SteamNotificationsScreen(
    account: SteamAccount?,
    state: SteamNotificationsUiState,
    searchQuery: String,
    onGiftAction: (SteamPendingGift, SteamGiftAction) -> Unit,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filterName by rememberSaveable(account?.id) {
        mutableStateOf(SteamNotificationFilter.ALL.name)
    }
    var selectedNotification by remember { mutableStateOf<SteamNotification?>(null) }
    val filter = SteamNotificationFilter.entries.firstOrNull { it.name == filterName }
        ?: SteamNotificationFilter.ALL
    val snapshot = state.snapshot
    val notifications = remember(snapshot, filter, searchQuery) {
        snapshot?.notifications.orEmpty().filter { notification ->
            val matchesFilter = when (filter) {
                SteamNotificationFilter.ALL -> true
                SteamNotificationFilter.UNREAD -> !notification.read
                SteamNotificationFilter.ACTIONS -> notification.kind == SteamNotificationKind.GIFT
            }
            val query = searchQuery.trim()
            val matchesQuery = query.isBlank() || listOf(
                notification.title,
                notification.summary,
                notification.bodyData
            ).any { it.contains(query, ignoreCase = true) }
            matchesFilter && matchesQuery
        }
    }
    val webSessionAvailable = account != null && (
        !account.steamLoginSecure.isNullOrBlank() || !account.accessToken.isNullOrBlank()
    )

    LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "notification_summary") {
                SteamNotificationSummaryCard(state)
            }
            item(key = "notification_filters") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SteamNotificationFilter.entries, key = { it.name }) { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filterName = option.name },
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            SteamNotificationFilter.ALL -> R.string.steam_notifications_filter_all
                                            SteamNotificationFilter.UNREAD -> R.string.steam_notifications_unread
                                            SteamNotificationFilter.ACTIONS -> R.string.steam_notifications_filter_actions
                                        }
                                    )
                                )
                            }
                        )
                    }
                }
            }

            state.error?.takeIf(String::isNotBlank)?.let { message ->
                item(key = "notification_error") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null)
                            Text(message, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            val visibleGifts = snapshot?.pendingGifts.orEmpty().filter { gift ->
                val query = searchQuery.trim()
                query.isBlank() || listOf(gift.name, gift.senderName, gift.message)
                    .any { it.contains(query, ignoreCase = true) }
            }
            if (filter != SteamNotificationFilter.UNREAD) {
                itemsIndexed(
                    visibleGifts,
                    key = { index, gift -> "notification-gift-${gift.id}-$index" }
                ) { _, gift ->
                    SteamGiftActionCard(
                        gift = gift,
                        actionInProgress = state.actionGiftId == gift.id,
                        actionsEnabled = state.actionGiftId == null,
                        webSessionAvailable = webSessionAvailable,
                        onAction = { action -> onGiftAction(gift, action) },
                        onOpenWeb = onOpenWeb
                    )
                }
            }

            itemsIndexed(
                notifications,
                key = { index, notification -> "notification-${notification.id}-$index" }
            ) { _, notification ->
                SteamNotificationListCard(
                    notification = notification,
                    onClick = { selectedNotification = notification }
                )
            }

            if (!state.loading && notifications.isEmpty() &&
                (filter == SteamNotificationFilter.UNREAD || visibleGifts.isEmpty())
            ) {
                item(key = "notification_empty") {
                    Text(
                        text = stringResource(R.string.steam_notifications_empty),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

    selectedNotification?.let { notification ->
        val relatedGift = snapshot?.pendingGifts.orEmpty().firstOrNull { gift ->
            gift.id == notification.relatedId
        } ?: snapshot?.pendingGifts.orEmpty().singleOrNull()
            ?.takeIf { notification.kind == SteamNotificationKind.GIFT }
        ModalBottomSheet(onDismissRequest = { selectedNotification = null }) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "notification_detail_title") {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (notification.timestamp > 0L) {
                    item(key = "notification_detail_time") {
                        Text(
                            text = formatSteamNotificationTime(notification.timestamp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (notification.summary.isNotBlank()) {
                    item(key = "notification_detail_summary") {
                        Text(notification.summary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                notification.bodyData.takeIf { body ->
                    body.isNotBlank() && body != notification.summary
                }?.let { body ->
                    item(key = "notification_detail_body") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Text(
                                text = body,
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                relatedGift?.let { gift ->
                    item(key = "notification_detail_gift_${gift.id}") {
                        SteamGiftActionCard(
                            gift = gift,
                            actionInProgress = state.actionGiftId == gift.id,
                            actionsEnabled = state.actionGiftId == null,
                            webSessionAvailable = webSessionAvailable,
                            onAction = { action -> onGiftAction(gift, action) },
                            onOpenWeb = onOpenWeb
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SteamNotificationSummaryCard(state: SteamNotificationsUiState) {
    val snapshot = state.snapshot
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.steam_notifications_unread_summary,
                        snapshot?.unreadCount ?: 0
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (state.fromCache) {
                        stringResource(R.string.steam_notifications_cached)
                    } else {
                        stringResource(
                            R.string.steam_notifications_action_summary,
                            snapshot?.pendingGiftCount ?: 0,
                            snapshot?.pendingFriendCount ?: 0
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SteamNotificationListCard(
    notification: SteamNotification,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.read) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = if (notification.read) {
                    MaterialTheme.colorScheme.outlineVariant
                } else {
                    MaterialTheme.colorScheme.primary
                }
            ) {}
            Column(Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (notification.read) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                notification.summary.takeIf(String::isNotBlank)?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.steam_notifications_open_detail),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SteamGiftActionCard(
    gift: SteamPendingGift,
    actionInProgress: Boolean,
    actionsEnabled: Boolean,
    webSessionAvailable: Boolean,
    onAction: (SteamGiftAction) -> Unit,
    onOpenWeb: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CardGiftcard, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(gift.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    gift.senderName.takeIf(String::isNotBlank)?.let { sender ->
                        Text(
                            sender,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        )
                    }
                }
                if (actionInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            gift.message.takeIf(String::isNotBlank)?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            if (gift.requiresWeb) {
                OutlinedButton(
                    onClick = onOpenWeb,
                    enabled = actionsEnabled && webSessionAvailable,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.steam_gift_handle_on_steam))
                }
            }
            if (gift.actions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (SteamGiftAction.ADD_TO_LIBRARY in gift.actions) {
                        Button(
                            onClick = { onAction(SteamGiftAction.ADD_TO_LIBRARY) },
                            enabled = actionsEnabled,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.steam_gift_add_to_library))
                        }
                    }
                    if (SteamGiftAction.KEEP_IN_INVENTORY in gift.actions) {
                        FilledTonalButton(
                            onClick = { onAction(SteamGiftAction.KEEP_IN_INVENTORY) },
                            enabled = actionsEnabled,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.steam_gift_keep_inventory))
                        }
                    }
                    if (SteamGiftAction.DECLINE in gift.actions) {
                        OutlinedButton(
                            onClick = { onAction(SteamGiftAction.DECLINE) },
                            enabled = actionsEnabled,
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.steam_reject))
                        }
                    }
                }
            }
        }
    }
}

private fun formatSteamNotificationTime(timestampSeconds: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(timestampSeconds * 1_000L))
}
