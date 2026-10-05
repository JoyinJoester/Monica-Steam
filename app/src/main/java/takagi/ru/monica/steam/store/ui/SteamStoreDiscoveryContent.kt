package takagi.ru.monica.steam.store.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.domain.SteamStoreBrowseFilter
import takagi.ru.monica.steam.store.domain.SteamStoreEvent
import takagi.ru.monica.steam.store.domain.SteamStoreHome
import takagi.ru.monica.steam.store.domain.SteamStoreItem
import takagi.ru.monica.steam.store.domain.visibleStoreCollections
import takagi.ru.monica.steam.store.hints.domain.SteamStoreHintKind

/** Each collection is a lazy item; browsing never composes the whole home at once. */
internal fun androidx.compose.foundation.lazy.LazyListScope.steamStoreDiscoveryItems(
    home: SteamStoreHome,
    itemHints: (Int) -> List<SteamStoreHintKind>,
    onOpenGame: (SteamStoreItem) -> Unit,
    onOpenEvent: (String) -> Unit,
    onSeeAll: (SteamStoreBrowseFilter) -> Unit
) {
    val specials = home.specials
    if (specials.isNotEmpty()) item(key = "store_specials") {
        StoreSection(
            title = storeBrowseFilterLabel(SteamStoreBrowseFilter.SPECIALS), games = specials,
            itemHints = itemHints,
            onOpen = { id -> specials.firstOrNull { it.appId == id }?.let(onOpenGame) },
            onSeeAll = { onSeeAll(SteamStoreBrowseFilter.SPECIALS) }
        )
    }
    if (home.topSellers.isNotEmpty()) {
        item(key = "store_top_heading") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(storeBrowseFilterLabel(SteamStoreBrowseFilter.TOP_SELLERS), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { onSeeAll(SteamStoreBrowseFilter.TOP_SELLERS) }) {
                    Text(stringResource(R.string.store_home_see_all))
                }
            }
        }
        home.topSellers.take(3).forEachIndexed { index, game ->
            item(key = "store_top_${game.appId}_$index") {
                SearchResultCard(game, itemHints(game.appId), emptyList()) { onOpenGame(game) }
            }
        }
    }
    if (home.events.isNotEmpty()) item(key = "store_events") { SteamStoreEventSection(home.events, onOpenEvent) }
    for ((filter, games) in listOf(SteamStoreBrowseFilter.NEW_RELEASES to home.newReleases, SteamStoreBrowseFilter.COMING_SOON to home.comingSoon)) {
        if (games.isNotEmpty()) item(key = "store_collection_${filter.name}") {
            StoreSection(
                title = storeBrowseFilterLabel(filter), games = games, itemHints = itemHints,
                onOpen = { id -> games.firstOrNull { it.appId == id }?.let(onOpenGame) },
                onSeeAll = { onSeeAll(filter) }
            )
        }
    }
    if (home.specials.isEmpty() && home.topSellers.isEmpty() && home.newReleases.isEmpty() && home.comingSoon.isEmpty() && home.events.isEmpty()) item(key = "store_discovery_empty") {
        Text(stringResource(R.string.steam_store_filter_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
    }
}

@Composable
private fun SteamStoreEventSection(
    events: List<SteamStoreEvent>,
    onOpenEvent: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.steam_store_events),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(events, key = SteamStoreEvent::url) { event ->
                SteamStoreEventCard(event, onClick = { onOpenEvent(event.url) })
            }
        }
    }
}

@Composable
private fun SteamStoreEventCard(event: SteamStoreEvent, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(280.dp).heightIn(min = 180.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.fillMaxWidth()) {
            SteamStoreImage(
                url = event.imageUrl,
                modifier = Modifier.fillMaxWidth().height(116.dp),
                contentScale = ContentScale.Crop
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (event.badge.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            text = event.badge,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun storeBrowseFilterLabel(filter: SteamStoreBrowseFilter): String = stringResource(
    when (filter) {
        SteamStoreBrowseFilter.ALL -> R.string.steam_store_browse_all
        SteamStoreBrowseFilter.SPECIALS -> R.string.steam_store_specials
        SteamStoreBrowseFilter.TOP_SELLERS -> R.string.steam_store_top_sellers
        SteamStoreBrowseFilter.NEW_RELEASES -> R.string.steam_store_new_releases
        SteamStoreBrowseFilter.COMING_SOON -> R.string.steam_store_coming_soon
        SteamStoreBrowseFilter.FREE -> R.string.steam_store_free_games
    }
)

internal fun storeBrowseFilterIcon(filter: SteamStoreBrowseFilter): ImageVector = when (filter) {
    SteamStoreBrowseFilter.ALL -> Icons.Default.SportsEsports
    SteamStoreBrowseFilter.SPECIALS -> Icons.Default.LocalOffer
    SteamStoreBrowseFilter.TOP_SELLERS -> Icons.Default.LocalFireDepartment
    SteamStoreBrowseFilter.NEW_RELEASES -> Icons.Default.NewReleases
    SteamStoreBrowseFilter.COMING_SOON -> Icons.Default.Schedule
    SteamStoreBrowseFilter.FREE -> Icons.Default.CardGiftcard
}
