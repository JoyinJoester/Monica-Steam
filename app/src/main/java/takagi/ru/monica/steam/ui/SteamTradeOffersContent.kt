package takagi.ru.monica.steam.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.trade.SteamTradeOffer
import takagi.ru.monica.steam.trade.SteamTradeOfferAction
import takagi.ru.monica.steam.trade.SteamTradeOfferDirection
import takagi.ru.monica.steam.trade.SteamTradeOfferItem
import takagi.ru.monica.steam.trade.SteamTradeOfferState
import takagi.ru.monica.ui.common.pull.PullToSearchStateHandle
import takagi.ru.monica.ui.components.MonicaExpressiveFilterChip
import takagi.ru.monica.ui.components.MonicaModalBottomSheet

private enum class TradeOfferFilter {
    ALL,
    RECEIVED,
    SENT
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SteamTradeOffersContent(
    account: SteamAccount?,
    accounts: List<SteamAccount>,
    state: SteamTradeOffersUiState,
    visibleOffers: List<SteamTradeOffer>,
    hasSearchQuery: Boolean,
    pullToSearch: PullToSearchStateHandle,
    onSelectAccount: (Long) -> Unit,
    onRefresh: () -> Unit,
    onRequestAction: (SteamTradeOffer, SteamTradeOfferAction) -> Unit
) {
    var filterName by rememberSaveable { mutableStateOf(TradeOfferFilter.ALL.name) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var pendingAccountSwitchId by remember { mutableStateOf<Long?>(null) }
    var detailOffer by remember { mutableStateOf<SteamTradeOffer?>(null) }
    val filter = TradeOfferFilter.entries.firstOrNull { it.name == filterName }
        ?: TradeOfferFilter.ALL
    val filteredOffers = remember(visibleOffers, filter) {
        when (filter) {
            TradeOfferFilter.ALL -> visibleOffers
            TradeOfferFilter.RECEIVED -> visibleOffers.filter {
                it.direction == SteamTradeOfferDirection.RECEIVED
            }
            TradeOfferFilter.SENT -> visibleOffers.filter {
                it.direction == SteamTradeOfferDirection.SENT
            }
        }
    }

    LaunchedEffect(showAccountPicker, pendingAccountSwitchId) {
        val accountId = pendingAccountSwitchId
        if (!showAccountPicker && accountId != null) {
            pendingAccountSwitchId = null
            onSelectAccount(accountId)
        }
    }

    if (showAccountPicker) {
        SteamConfirmationAccountPickerSheet(
            accounts = accounts,
            selectedAccountId = account?.id,
            onSelectAccount = { selected ->
                pendingAccountSwitchId = selected.id
                showAccountPicker = false
            },
            onDismissRequest = { showAccountPicker = false }
        )
    }

    detailOffer?.let { offer ->
        SteamTradeOfferDetailSheet(
            offer = offer,
            actionLoading = state.actionLoadingOfferId == offer.id,
            onDismissRequest = { detailOffer = null },
            onAction = { action ->
                detailOffer = null
                onRequestAction(offer, action)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, pullToSearch.currentOffset.toInt()) }
    ) {
        SteamConfirmationAccountCard(
            account = account,
            onClick = { showAccountPicker = true },
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
        )

        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TradeOfferFilter.entries.forEach { option ->
                MonicaExpressiveFilterChip(
                    selected = filter == option,
                    onClick = { filterName = option.name },
                    label = stringResource(
                        when (option) {
                            TradeOfferFilter.ALL -> R.string.steam_trade_filter_all
                            TradeOfferFilter.RECEIVED -> R.string.steam_trade_filter_received
                            TradeOfferFilter.SENT -> R.string.steam_trade_filter_sent
                        }
                    ),
                    leadingIcon = when (option) {
                        TradeOfferFilter.ALL -> Icons.Default.SwapHoriz
                        TradeOfferFilter.RECEIVED -> Icons.Default.ArrowDownward
                        TradeOfferFilter.SENT -> Icons.Default.ArrowUpward
                    }
                )
            }
        }

        when {
            account == null -> TradeOfferMessage(stringResource(R.string.steam_trade_no_accounts))
            account.accessToken.isNullOrBlank() -> TradeOfferMessage(
                stringResource(R.string.steam_trade_offers_session_required)
            )
            state.loading && state.snapshot == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            state.error != null && state.snapshot == null -> TradeOfferError(
                message = state.error,
                onRetry = onRefresh
            )
            filteredOffers.isEmpty() -> TradeOfferMessage(
                if (hasSearchQuery || visibleOffers.isNotEmpty()) {
                    stringResource(R.string.no_results)
                } else {
                    stringResource(R.string.steam_trade_offers_empty)
                }
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pullToSearch.nestedScrollConnection),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredOffers, key = SteamTradeOffer::id) { offer ->
                    SteamTradeOfferCard(
                        offer = offer,
                        actionLoading = state.actionLoadingOfferId == offer.id,
                        onClick = { detailOffer = offer }
                    )
                }
            }
        }
    }
}

@Composable
private fun TradeOfferMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TradeOfferError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.size(12.dp))
        OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.steam_trade_retry))
        }
    }
}

@Composable
private fun SteamTradeOfferCard(
    offer: SteamTradeOffer,
    actionLoading: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !actionLoading, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (offer.direction == SteamTradeOfferDirection.RECEIVED) {
                        Icons.Default.ArrowDownward
                    } else {
                        Icons.Default.ArrowUpward
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (offer.direction == SteamTradeOfferDirection.RECEIVED) {
                                R.string.steam_trade_received_from
                            } else {
                                R.string.steam_trade_sent_to
                            },
                            offer.partnerSteamId.takeLast(10)
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = steamTradeOfferStateLabel(offer),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (actionLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TradeOfferCount(
                    label = stringResource(R.string.steam_trade_you_give),
                    count = offer.itemsToGive.sumOf { it.amount },
                    modifier = Modifier.weight(1f)
                )
                TradeOfferCount(
                    label = stringResource(R.string.steam_trade_you_receive),
                    count = offer.itemsToReceive.sumOf { it.amount },
                    modifier = Modifier.weight(1f)
                )
            }
            if (offer.message.isNotBlank()) {
                Text(
                    text = offer.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TradeOfferCount(label: String, count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = count.toString(), style = MaterialTheme.typography.titleMedium)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteamTradeOfferDetailSheet(
    offer: SteamTradeOffer,
    actionLoading: Boolean,
    onDismissRequest: () -> Unit,
    onAction: (SteamTradeOfferAction) -> Unit
) {
    MonicaModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.steam_trade_offer_details),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            if (offer.direction == SteamTradeOfferDirection.RECEIVED) {
                                R.string.steam_trade_received_from
                            } else {
                                R.string.steam_trade_sent_to
                            },
                            offer.partnerSteamId
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = steamTradeOfferStateLabel(offer),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (offer.message.isNotBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Text(
                            text = offer.message,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            item {
                TradeOfferItemsSection(
                    title = stringResource(R.string.steam_trade_you_give),
                    items = offer.itemsToGive
                )
            }
            item {
                TradeOfferItemsSection(
                    title = stringResource(R.string.steam_trade_you_receive),
                    items = offer.itemsToReceive
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (offer.createdAt > 0L) {
                        Text(
                            text = stringResource(
                                R.string.steam_trade_created_at,
                                formatTradeTime(offer.createdAt)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (offer.expirationTime > 0L) {
                        Text(
                            text = stringResource(
                                R.string.steam_trade_expires_at,
                                formatTradeTime(offer.expirationTime)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (offer.isActive) {
                item {
                    when (offer.direction) {
                        SteamTradeOfferDirection.RECEIVED -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onAction(SteamTradeOfferAction.DECLINE) },
                                enabled = !actionLoading,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                            ) {
                                Text(stringResource(R.string.steam_trade_decline))
                            }
                            Button(
                                onClick = { onAction(SteamTradeOfferAction.ACCEPT) },
                                enabled = !actionLoading,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                            ) {
                                Text(stringResource(R.string.steam_trade_accept))
                            }
                        }
                        SteamTradeOfferDirection.SENT -> OutlinedButton(
                            onClick = { onAction(SteamTradeOfferAction.CANCEL) },
                            enabled = !actionLoading,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) {
                            Text(stringResource(R.string.steam_trade_cancel_offer))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TradeOfferItemsSection(title: String, items: List<SteamTradeOfferItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.steam_trade_no_items),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            items.forEach { item -> SteamTradeOfferItemRow(item) }
        }
    }
}

@Composable
private fun SteamTradeOfferItemRow(item: SteamTradeOfferItem) {
    val context = LocalContext.current
    var image by remember(item.iconUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.iconUrl) {
        image = loadSteamConfirmationImage(context, item.iconUrl)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                image?.let {
                    Image(
                        bitmap = it,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name.ifBlank { stringResource(R.string.steam_trade_unknown_item) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.type.isNotBlank()) {
                    Text(
                        text = item.type,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (item.amount > 1) {
                Text(text = "×${item.amount}", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun steamTradeOfferStateLabel(offer: SteamTradeOffer): String {
    if (offer.state == SteamTradeOfferState.UNKNOWN) {
        return stringResource(R.string.steam_trade_state_unknown, offer.rawStateCode)
    }
    return stringResource(
        when (offer.state) {
            SteamTradeOfferState.INVALID -> R.string.steam_trade_state_invalid
            SteamTradeOfferState.ACTIVE -> R.string.steam_trade_state_active
            SteamTradeOfferState.ACCEPTED -> R.string.steam_trade_state_accepted
            SteamTradeOfferState.COUNTERED -> R.string.steam_trade_state_countered
            SteamTradeOfferState.EXPIRED -> R.string.steam_trade_state_expired
            SteamTradeOfferState.CANCELED -> R.string.steam_trade_state_canceled
            SteamTradeOfferState.DECLINED -> R.string.steam_trade_state_declined
            SteamTradeOfferState.INVALID_ITEMS -> R.string.steam_trade_state_invalid_items
            SteamTradeOfferState.NEEDS_CONFIRMATION -> R.string.steam_trade_state_needs_confirmation
            SteamTradeOfferState.CANCELED_BY_SECOND_FACTOR -> R.string.steam_trade_state_canceled_confirmation
            SteamTradeOfferState.IN_ESCROW -> R.string.steam_trade_state_escrow
            SteamTradeOfferState.UNKNOWN -> R.string.steam_trade_state_invalid
        }
    )
}

private fun formatTradeTime(seconds: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(seconds * 1000L))
}
