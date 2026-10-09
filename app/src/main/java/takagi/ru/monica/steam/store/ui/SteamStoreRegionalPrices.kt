package takagi.ru.monica.steam.store.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamRegionalPrice
import takagi.ru.monica.steam.steamdb.data.SteamDbRepository
import takagi.ru.monica.steam.steamdb.domain.SteamDbQuery
import takagi.ru.monica.steam.steamdb.ui.SteamDbPriceSection
import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.ui.LocalReduceAnimations
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamStoreRegionalPriceSheet(appId: Int, gameName: String, historyCountryCode: String?,
    prices: List<SteamRegionalPrice>, loading: Boolean, fromCache: Boolean, failure: SteamLibraryFailureReason?,
    onRetry: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        SteamStoreRegionalPriceContent(appId, gameName, historyCountryCode, prices, loading, fromCache, failure,
            onRetry, onDismiss, Modifier.fillMaxWidth().fillMaxHeight(.92f))
    }
}

@Composable
internal fun SteamStoreRegionalPriceContent(appId: Int, gameName: String, currentCountryCode: String?,
    prices: List<SteamRegionalPrice>, loading: Boolean, fromCache: Boolean, failure: SteamLibraryFailureReason?,
    onRetry: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier,
    repository: SteamDbRepository = SteamDbRepository.shared) {
    val current = normalizedStoreCountry(currentCountryCode)
    val sorted = remember(prices, current) { sortedStoreRegionalPrices(prices, current) }
    var expanded by rememberSaveable(appId, current) { mutableStateOf<String?>(null) }
    Column(modifier.testTag("store_regional_prices")) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.steam_library_regional_prices), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(gameName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRetry, enabled = !loading) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
        }
        SteamStoreRegionalPriceHeader()
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("store_regional_list"), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (current != null && !storePricesContainCurrentRegion(sorted, current)) item(key = "current_status") {
                Text(stringResource(if (loading) R.string.store_detail_region_loading else R.string.store_detail_region_missing, regionalCountryName(current)),
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
            if (failure != null) item(key = "error") {
                Text(storeRegionalPriceFailureLabel(failure), Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (!loading && sorted.isEmpty() && failure == null) item(key = "empty") {
                Text(stringResource(R.string.steam_library_regional_prices_empty), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
            itemsIndexed(sorted, key = ::steamStoreRegionalPriceLazyKey) { _, price ->
                val country = normalizedStoreCountry(price.countryCode)
                SteamStoreRegionalPriceRow(appId, price, country == current && current != null, expanded == country,
                    onToggle = { expanded = if (expanded == country) null else country }, repository = repository)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
            }
            item(key = "note") {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (fromCache) Text(stringResource(R.string.steam_store_cached), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.steam_library_regional_price_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SteamStoreRegionalPriceHeader() {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 36.dp, top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, weight) in listOf(R.string.store_detail_region to .29f, R.string.store_detail_current_discount to .40f, R.string.store_detail_original to .31f)) {
            Text(stringResource(label), Modifier.weight(weight), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SteamStoreRegionalPriceRow(appId: Int, price: SteamRegionalPrice, current: Boolean, expanded: Boolean,
    onToggle: () -> Unit, repository: SteamDbRepository) {
    val reduceAnimations = LocalReduceAnimations.current
    val discount = if (price.isAvailable && price.originalPriceMinor > price.finalPriceMinor && price.originalPriceMinor > 0)
        ((1.0 - price.finalPriceMinor.toDouble() / price.originalPriceMinor) * 100).roundToInt().coerceIn(0, 100) else 0
    val unavailable = stringResource(R.string.store_detail_region_unavailable)
    val state = stringResource(if (expanded) R.string.collapse else R.string.expand)
    val currency = price.currency.uppercase(Locale.ROOT)
    val localFinal = formatStoreRegionalPrice(currency, price.finalPriceMinor)
    val localOriginal = formatStoreRegionalPrice(currency, price.originalPriceMinor)
    val finalText = when {
        !price.isAvailable -> unavailable
        price.finalPriceMinor == 0L -> stringResource(R.string.steam_library_free)
        price.cnyFinalPriceMinor != null -> formatStoreRegionalPrice("CNY", price.cnyFinalPriceMinor)
        else -> localFinal
    }
    val originalText = when {
        !price.isAvailable -> "—"
        price.cnyOriginalPriceMinor != null -> formatStoreRegionalPrice("CNY", price.cnyOriginalPriceMinor)
        else -> localOriginal
    }
    Surface(color = if (current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.background) {
        Column {
            Row(Modifier.fillMaxWidth().testTag("regional_row_${price.countryCode}")
                .semantics { stateDescription = state }
                .clickable(enabled = price.isAvailable, role = Role.Button, onClick = onToggle)
                .heightIn(min = 76.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(.29f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(storeRegionFlag(price.countryCode), style = MaterialTheme.typography.bodySmall)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(regionalCountryName(price.countryCode), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (current) stringResource(R.string.store_detail_current_region) else currency,
                        style = MaterialTheme.typography.labelSmall, color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(.40f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        Text(finalText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (discount > 0) SteamStoreDiscountBadge(discount)
                    }
                    if (price.isAvailable && price.finalPriceMinor > 0) Text(if (currency == "CNY") currency else storeLocalPriceLabel(currency, price.finalPriceMinor),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(.31f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(originalText, Modifier.testTag("regional_original_${price.countryCode}"),
                        style = MaterialTheme.typography.bodySmall, textDecoration = if (discount > 0) TextDecoration.LineThrough else null)
                    if (price.isAvailable && price.originalPriceMinor > 0 && currency != "CNY") Text(storeLocalPriceLabel(currency, price.originalPriceMinor), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(16.dp),
                    tint = if (price.isAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant)
            }
            AnimatedVisibility(expanded, enter = if (reduceAnimations) EnterTransition.None else expandVertically(),
                exit = if (reduceAnimations) ExitTransition.None else shrinkVertically()) {
                SteamDbPriceSection(SteamDbQuery(appId, price.countryCode, price.currency, price.isAvailable && price.finalPriceMinor == 0L),
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp).testTag("regional_history_${price.countryCode}"), repository, compact = true)
            }
        }
    }
}
