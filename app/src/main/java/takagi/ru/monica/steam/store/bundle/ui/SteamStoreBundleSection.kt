package takagi.ru.monica.steam.store.bundle.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassModalBottomSheet as ModalBottomSheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.bundle.domain.SteamStoreBundle
import takagi.ru.monica.steam.store.bundle.domain.SteamStoreBundleItem
import takagi.ru.monica.steam.store.domain.formatSteamPrice
import takagi.ru.monica.steam.store.ui.SteamStoreImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamStoreBundleSection(
    bundles: List<SteamStoreBundle>,
    currency: String,
    onOpenApp: (Int) -> Unit,
    onOpenBundle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (bundles.isEmpty()) return
    var selectedBundle by remember(bundles) { mutableStateOf<SteamStoreBundle?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.steam_store_bundles),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        bundles.forEach { bundle ->
            BundleCard(bundle, currency, onClick = { selectedBundle = bundle })
        }
    }

    selectedBundle?.let { bundle ->
        ModalBottomSheet(
            onDismissRequest = { selectedBundle = null },
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            tonalElevation = 0.dp
        ) {
            BundleDetail(
                bundle = bundle,
                currency = currency,
                onOpenBundle = { onOpenBundle(bundle.storeUrl) },
                onOpenApp = { appId ->
                    selectedBundle = null
                    onOpenApp(appId)
                }
            )
        }
    }
}

@Composable
private fun BundleCard(
    bundle: SteamStoreBundle,
    currency: String,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("store_bundle_${bundle.bundleId}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        BundleArtwork(bundle)
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(bundle.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            val names = bundle.items.take(3).mapNotNull { it.name.takeIf(String::isNotBlank) }
            if (names.isNotEmpty()) Text(names.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.steam_store_bundle_items, bundle.items.size),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { BundlePrice(bundle, currency) }
                Icon(Icons.AutoMirrored.Filled.ArrowForward,
                    stringResource(R.string.steam_store_bundle_open_item), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Included-game art reveals the combination; no unrelated cover is substituted. */
@Composable
private fun BundleArtwork(bundle: SteamStoreBundle) {
    val images = bundle.items.mapNotNull { it.imageUrl.takeIf(String::isNotBlank) }.distinct().take(3)
        .ifEmpty { listOfNotNull(bundle.imageUrl.takeIf(String::isNotBlank)) }
    if (images.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(8.dp).testTag("bundle_artwork_${bundle.bundleId}"),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        images.forEach { url ->
            SteamStoreImage(url, Modifier.weight(1f).aspectRatio(460f / 215f).clip(RoundedCornerShape(16.dp)),
                contentDescription = null)
        }
    }
}

@Composable
private fun BundlePrice(bundle: SteamStoreBundle, currency: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bundle.discountPercent > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Text(
                    text = stringResource(
                        R.string.steam_store_bundle_discount,
                        bundle.discountPercent
                    ),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = formatSteamPrice(bundle.finalPriceCents, currency),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BundleDetail(
    bundle: SteamStoreBundle,
    currency: String,
    onOpenBundle: () -> Unit,
    onOpenApp: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp).testTag("bundle_detail_${bundle.bundleId}"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BundleArtwork(bundle)
                Text(
                    text = bundle.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(
                            R.string.steam_store_bundle_contents,
                            bundle.items.size
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    BundlePrice(bundle, currency)
                }
                Button(
                    onClick = onOpenBundle,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(stringResource(R.string.steam_store_bundle_buy))
                }
            }
        }
        items(bundle.items, key = SteamStoreBundleItem::appId) { item ->
            Surface(
                onClick = { onOpenApp(item.appId) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("bundle_item_${item.appId}"),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.imageUrl.isNotBlank()) SteamStoreImage(
                        url = item.imageUrl,
                        modifier = Modifier.width(88.dp).height(48.dp).clip(RoundedCornerShape(12.dp)),
                        contentDescription = null
                    )
                    Text(
                        text = item.name.ifBlank { "App ${item.appId}" },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.steam_store_bundle_open_item)
                    )
                }
            }
        }
    }
}
