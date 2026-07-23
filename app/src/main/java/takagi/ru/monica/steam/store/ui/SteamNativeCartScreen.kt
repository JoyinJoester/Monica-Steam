package takagi.ru.monica.steam.store.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.store.domain.*
import java.text.DateFormat
import java.util.Date
import takagi.ru.monica.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamNativeCartScreen(
    cartItems: List<SteamCartItem>,
    wishlistItems: List<SteamWishlistItem>,
    selectedTab: SteamStoreCollectionTab,
    loadingWishlist: Boolean,
    wishlistFromCache: Boolean,
    wishlistError: String?,
    onTabSelected: (SteamStoreCollectionTab) -> Unit,
    onBack: () -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onCheckout: () -> Unit,
    onRefreshWishlist: () -> Unit,
    onOpenWishlistItem: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.steam_store_collection_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        when (selectedTab) {
                            SteamStoreCollectionTab.CART -> {
                                if (cartItems.isNotEmpty()) {
                                    TextButton(onClick = onClear) {
                                        Text(stringResource(R.string.steam_store_cart_clear))
                                    }
                                }
                            }
                            SteamStoreCollectionTab.WISHLIST -> {
                                IconButton(
                                    onClick = onRefreshWishlist,
                                    enabled = !loadingWishlist
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = stringResource(
                                            R.string.steam_store_wishlist_refresh
                                        )
                                    )
                                }
                            }
                        }
                    }
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    SteamStoreCollectionTab.entries.forEachIndexed { index, tab ->
                        SegmentedButton(
                            selected = selectedTab == tab,
                            onClick = { onTabSelected(tab) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = SteamStoreCollectionTab.entries.size
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            label = {
                                Text(
                                    text = stringResource(
                                        when (tab) {
                                            SteamStoreCollectionTab.CART -> {
                                                R.string.steam_store_cart_tab
                                            }
                                            SteamStoreCollectionTab.WISHLIST -> {
                                                R.string.steam_store_wishlist_tab
                                            }
                                        },
                                        when (tab) {
                                            SteamStoreCollectionTab.CART -> cartItems.size
                                            SteamStoreCollectionTab.WISHLIST -> wishlistItems.size
                                        }
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (selectedTab == SteamStoreCollectionTab.CART && cartItems.isNotEmpty()) {
                SteamCheckoutBar(items = cartItems, onCheckout = onCheckout)
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "SteamStoreCollectionTab"
        ) { tab ->
            when (tab) {
                SteamStoreCollectionTab.CART -> SteamCartContent(
                    items = cartItems,
                    onRemove = onRemove
                )
                SteamStoreCollectionTab.WISHLIST -> SteamWishlistContent(
                    items = wishlistItems,
                    loading = loadingWishlist,
                    fromCache = wishlistFromCache,
                    error = wishlistError,
                    onRefresh = onRefreshWishlist,
                    onOpenItem = onOpenWishlistItem
                )
            }
        }
    }
}

@Composable
private fun SteamCartContent(
    items: List<SteamCartItem>,
    onRemove: (Int) -> Unit
) {
    if (items.isEmpty()) {
        SteamCollectionEmptyState(
            icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
            title = stringResource(R.string.steam_store_cart_empty_title),
            description = stringResource(R.string.steam_store_cart_empty_description)
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(items, key = ::steamCartLazyKey) { _, item ->
            SteamCartItemCard(item = item, onRemove = { onRemove(item.appId) })
        }
    }
}

@Composable
private fun SteamWishlistContent(
    items: List<SteamWishlistItem>,
    loading: Boolean,
    fromCache: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpenItem: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (fromCache) {
            Text(
                text = stringResource(R.string.steam_store_wishlist_cached),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        if (error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(error, modifier = Modifier.weight(1f), maxLines = 3)
                    TextButton(onClick = onRefresh) {
                        Text(stringResource(R.string.steam_store_retry))
                    }
                }
            }
        }
        if (items.isEmpty() && !loading && error == null) {
            SteamCollectionEmptyState(
                icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                title = stringResource(R.string.steam_store_wishlist_empty_title),
                description = stringResource(R.string.steam_store_wishlist_empty_description)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(items, key = ::steamWishlistLazyKey) { _, item ->
                    SteamWishlistItemCard(item = item, onClick = { onOpenItem(item.appId) })
                }
            }
        }
    }
}

@Composable
private fun SteamCartItemCard(item: SteamCartItem, onRemove: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SteamStoreImage(
                item.imageUrl,
                Modifier
                    .size(width = 104.dp, height = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    item.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    item.formattedPrice,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (item.packageId == null) {
                    Text(
                        stringResource(R.string.steam_store_cart_manual_add),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.steam_store_cart_remove)
                )
            }
        }
    }
}

@Composable
private fun SteamWishlistItemCard(item: SteamWishlistItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SteamStoreImage(
                item.imageUrl,
                Modifier
                    .size(width = 104.dp, height = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    item.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.discountPercent > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "-${item.discountPercent}%",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        item.formattedFinalPrice.ifBlank {
                            stringResource(R.string.steam_store_price_unavailable)
                        },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.addedAtEpochSeconds > 0L) {
                    Text(
                        text = stringResource(
                            R.string.steam_store_wishlist_added,
                            DateFormat.getDateInstance(DateFormat.MEDIUM)
                                .format(Date(item.addedAtEpochSeconds * 1000L))
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.steam_store_open_detail),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SteamCollectionEmptyState(
    icon: @Composable () -> Unit,
    title: String,
    description: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SteamCheckoutBar(items: List<SteamCartItem>, onCheckout: () -> Unit) {
    val currency = items.map { it.currency }.distinct().singleOrNull()
    val total = steamCartTotalCents(items)
    Surface(
        tonalElevation = 5.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.steam_store_cart_summary, items.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (currency == null) {
                            stringResource(R.string.steam_store_cart_mixed_currency)
                        } else {
                            formatSteamPrice(total, currency)
                        },
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = onCheckout,
                    enabled = items.any { it.packageId != null },
                    modifier = Modifier.heightIn(min = 56.dp),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.steam_store_checkout))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = stringResource(R.string.steam_store_security_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
