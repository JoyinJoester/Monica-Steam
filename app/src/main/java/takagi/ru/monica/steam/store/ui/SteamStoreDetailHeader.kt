package takagi.ru.monica.steam.store.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.domain.SteamStoreDetail

@Composable
internal fun SteamStoreDetailTopBar(name: String, onBack: () -> Unit, onShare: () -> Unit,
    onPurchase: () -> Unit, onReviews: () -> Unit, onOfficial: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).testTag("store_detail_topbar"), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, stringResource(R.string.share)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(R.string.store_detail_purchase to onPurchase, R.string.store_detail_reviews to onReviews,
                        R.string.store_detail_official to onOfficial).forEach { (label, action) ->
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { menu = false; action() })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SteamStoreDetailHeader(detail: SteamStoreDetail, loading: Boolean,
    onRegionalPrices: () -> Unit, onImage: () -> Unit, modifier: Modifier = Modifier,
    tags: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().testTag("store_detail_header"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (detail.headerImageUrl.isNotBlank()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                SteamStoreImage(detail.headerImageUrl, Modifier.fillMaxWidth().height((maxWidth * (215f / 460f)).coerceAtMost(184.dp))
                    .clip(RoundedCornerShape(16.dp)).clickable(onClick = onImage), contentScale = ContentScale.Fit,
                    contentDescription = stringResource(R.string.steam_store_header_image_description))
            }
        }
        val metadata = listOfNotNull(detail.releaseDate.takeIf(String::isNotBlank), "Windows".takeIf { detail.windows },
            "macOS".takeIf { detail.mac }, "Linux".takeIf { detail.linux })
        if (metadata.isNotEmpty()) Text(metadata.joinToString(" · "), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        tags()
        Surface(onClick = onRegionalPrices, shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().testTag("store_detail_current_price")) {
            Row(Modifier.heightIn(min = 76.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val region = detail.accountCountryCode ?: detail.priceCountryCode
                    Text(listOfNotNull(region?.let { regionalCountryName(it) }, stringResource(R.string.store_detail_current_region)).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (detail.availableInAccountRegion == false) {
                        Text(stringResource(R.string.steam_store_unavailable_account_region), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        detail.priceCountryCode?.let {
                            Text(stringResource(R.string.steam_store_reference_region_price, regionalCountryName(it)) + " · " + detail.formattedFinalPrice,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
                            itemVerticalAlignment = Alignment.CenterVertically) {
                            Text(if (detail.isFree) stringResource(R.string.steam_library_free) else detail.formattedFinalPrice,
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            if (detail.discountPercent > 0) {
                                SteamStoreDiscountBadge(detail.discountPercent)
                                Text(detail.formattedInitialPrice, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.store_detail_compare), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
internal fun SteamStoreDiscountBadge(discount: Int) {
    Surface(shape = RoundedCornerShape(5.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text("−$discount%", Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}
