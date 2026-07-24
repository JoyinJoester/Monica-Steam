package takagi.ru.monica.steam.notifications.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalContext
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
    onOpenStoreApp: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var filterName by rememberSaveable(account?.id) {
        mutableStateOf(SteamNotificationFilter.ALL.name)
    }
    var selectedNotification by remember { mutableStateOf<SteamNotification?>(null) }
    val filter = SteamNotificationFilter.entries.firstOrNull { it.name == filterName }
        ?: SteamNotificationFilter.ALL
    val snapshot = state.snapshot
    val pendingGifts = snapshot?.pendingGifts.orEmpty()
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
                notification.bodyData,
                notification.actorContent?.displayName.orEmpty(),
                notification.itemContent?.name.orEmpty()
            ).any { it.contains(query, ignoreCase = true) }
            val representedByGiftAction = notification.kind == SteamNotificationKind.GIFT && (
                pendingGifts.any { gift -> gift.id == notification.relatedId } ||
                    (notification.relatedId.isNullOrBlank() && pendingGifts.size == 1)
                )
            matchesFilter && matchesQuery && !representedByGiftAction
        }
    }
    val webSessionAvailable = account != null && (
        !account.steamLoginSecure.isNullOrBlank() || !account.accessToken.isNullOrBlank()
    )
    val context = LocalContext.current

    LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
            bottom = 16.dp
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

            val visibleGifts = pendingGifts.filter { gift ->
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
        val details = remember(
            notification.id,
            notification.bodyData,
            notification.title,
            notification.summary
        ) {
            SteamNotificationDetailParser.parse(
                bodyData = notification.bodyData,
                title = notification.title,
                summary = notification.summary,
                kind = notification.kind
            )
        }
        val hasSummary = isMeaningfulNotificationText(notification.summary)
        val hasAppContent = notification.appContent.isNotEmpty()
        val hasResolvedNotificationContent = hasAppContent ||
            notification.actorContent != null ||
            notification.itemContent != null ||
            details.inventoryReference != null
        ModalBottomSheet(onDismissRequest = { selectedNotification = null }) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "notification_detail_title") {
                    Text(
                        text = notificationDisplayTitle(notification),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
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
                if (hasSummary) {
                    item(key = "notification_detail_summary") {
                        SelectionContainer {
                            Text(notification.summary, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                details.message
                    ?.takeIf(::isMeaningfulNotificationText)
                    ?.let { message ->
                        item(key = "notification_detail_message") {
                            SelectionContainer {
                                Text(message, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                if (notification.kind == SteamNotificationKind.WISHLIST &&
                    !hasSummary && details.message == null && hasAppContent
                ) {
                    item(key = "notification_detail_wishlist_message") {
                        Text(
                            text = stringResource(R.string.steam_notification_wishlist_update_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                notification.actorContent?.let { actor ->
                    item(key = "notification_detail_actor_${actor.steamId}") {
                        SteamNotificationActorCard(
                            actor = actor,
                            onOpenProfile = {
                                val profileUrl = actor.profileUrl
                                    .takeIf { it.startsWith("https://steamcommunity.com/") }
                                    ?: "https://steamcommunity.com/profiles/${actor.steamId}"
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)))
                                }
                            }
                        )
                    }
                }
                if (notification.itemContent != null || details.inventoryReference != null) {
                    item(key = "notification_detail_item") {
                        SteamNotificationInventoryItemCard(
                            item = notification.itemContent,
                            reference = details.inventoryReference
                        )
                    }
                }
                items(
                    items = notification.appContent,
                    key = { content -> "notification_app_${content.appId}" }
                ) { content ->
                    SteamNotificationAppContentCard(
                        content = content,
                        onOpenStore = {
                            selectedNotification = null
                            onOpenStoreApp(content.appId)
                        }
                    )
                }
                if (details.fields.isNotEmpty() && !hasResolvedNotificationContent) {
                    item(key = "notification_detail_fields") {
                        SteamNotificationDetailContent(details.fields)
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
                if (!hasSummary && details.message == null && details.fields.isEmpty() &&
                    relatedGift == null && !hasResolvedNotificationContent
                ) {
                    item(key = "notification_detail_unavailable") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Text(
                                text = stringResource(R.string.steam_notification_detail_unavailable),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
        shape = RoundedCornerShape(16.dp),
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.read) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
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
                    text = notificationDisplayTitle(notification),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (notification.read) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                notification.summary
                    .takeIf(::isMeaningfulNotificationText)
                    .orEmpty()
                    .ifBlank {
                        notification.actorContent?.displayName.orEmpty()
                    }
                    .ifBlank {
                        notification.itemContent?.name.orEmpty()
                    }
                    .ifBlank {
                        notification.appContent.joinToString { content -> content.name }
                    }
                    .takeIf(String::isNotBlank)
                    ?.let { summary ->
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(
                        Icons.Default.CardGiftcard,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(gift.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    gift.senderName.takeIf(String::isNotBlank)?.let { sender ->
                        Text(
                            sender,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (actionInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            gift.message.takeIf(::isMeaningfulNotificationText)?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            if (gift.requiresWeb) {
                Button(
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
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.steam_reject))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun notificationDisplayTitle(notification: SteamNotification): String {
    if (!notification.usesFallbackTitle()) return notification.title
    return stringResource(
        when (notification.kind) {
            SteamNotificationKind.GIFT -> R.string.steam_notification_kind_gift
            SteamNotificationKind.COMMENT -> R.string.steam_notification_kind_comment
            SteamNotificationKind.ITEM -> R.string.steam_notification_kind_item
            SteamNotificationKind.FRIEND_INVITE -> R.string.steam_notification_kind_friend_invite
            SteamNotificationKind.SALE -> R.string.steam_notification_kind_sale
            SteamNotificationKind.PRELOAD -> R.string.steam_notification_kind_preload
            SteamNotificationKind.WISHLIST -> R.string.steam_notification_kind_wishlist
            SteamNotificationKind.TRADE_OFFER -> R.string.steam_notification_kind_trade_offer
            SteamNotificationKind.GENERAL -> R.string.steam_notification_kind_general
            SteamNotificationKind.HELP_REQUEST -> R.string.steam_notification_kind_help_request
            SteamNotificationKind.ASYNC_GAME -> R.string.steam_notification_kind_game_update
            SteamNotificationKind.CHAT_MESSAGE -> R.string.steam_notification_kind_chat_message
            SteamNotificationKind.MODERATOR_MESSAGE -> R.string.steam_notification_kind_moderator_message
            SteamNotificationKind.FAMILY -> R.string.steam_notification_kind_family
            SteamNotificationKind.PARENTAL -> R.string.steam_notification_kind_parental
            SteamNotificationKind.GAME_INVITE -> R.string.steam_notification_kind_game_invite
            SteamNotificationKind.TRADE_REVERSED -> R.string.steam_notification_kind_trade_reversed
            SteamNotificationKind.UNKNOWN -> R.string.steam_notification_kind_general
        }
    )
}

private fun SteamNotification.usesFallbackTitle(): Boolean = title == when (kind) {
    SteamNotificationKind.GIFT -> "Steam gift"
    SteamNotificationKind.COMMENT -> "New comment"
    SteamNotificationKind.ITEM -> "New item"
    SteamNotificationKind.FRIEND_INVITE -> "Friend invitation"
    SteamNotificationKind.SALE -> "Steam sale"
    SteamNotificationKind.PRELOAD -> "Preload available"
    SteamNotificationKind.WISHLIST -> "Wishlist update"
    SteamNotificationKind.TRADE_OFFER -> "Trade offer"
    SteamNotificationKind.GENERAL -> "Steam notification"
    SteamNotificationKind.HELP_REQUEST -> "Steam Support"
    SteamNotificationKind.ASYNC_GAME -> "Game update"
    SteamNotificationKind.CHAT_MESSAGE -> "Chat message"
    SteamNotificationKind.MODERATOR_MESSAGE -> "Moderator message"
    SteamNotificationKind.FAMILY -> "Steam Family"
    SteamNotificationKind.PARENTAL -> "Parental controls"
    SteamNotificationKind.GAME_INVITE -> "Game invitation"
    SteamNotificationKind.TRADE_REVERSED -> "Trade update"
    SteamNotificationKind.UNKNOWN -> "Steam notification"
}

private fun isMeaningfulNotificationText(value: String): Boolean {
    val normalized = value.trim().removeSuffix(":").trim()
    return normalized.isNotBlank() && !normalized.equals("A gift from", ignoreCase = true)
}

private fun formatSteamNotificationTime(timestampSeconds: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(timestampSeconds * 1_000L))
}
