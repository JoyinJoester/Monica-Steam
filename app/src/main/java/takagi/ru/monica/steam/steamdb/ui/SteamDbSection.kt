package takagi.ru.monica.steam.steamdb.ui

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import takagi.ru.monica.R
import takagi.ru.monica.steam.steamdb.data.SteamDbRepository
import takagi.ru.monica.steam.steamdb.domain.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamDbDetailsEntry(query: SteamDbQuery, gameName: String, modifier: Modifier = Modifier,
    repository: SteamDbRepository = SteamDbRepository.shared) {
    var expanded by rememberSaveable(query.appId) { mutableStateOf(false) }
    Surface(onClick = { expanded = true }, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.testTag("steamdb_entry")) {
        ListItem(headlineContent = { Text(stringResource(R.string.steamdb_title)) },
            supportingContent = { Text(stringResource(R.string.steamdb_entry_summary)) },
            leadingContent = { Icon(Icons.Default.QueryStats, null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = { Icon(Icons.Default.ExpandMore, null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
    }
    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            var reload by remember(query) { mutableIntStateOf(0) }
            val state = remember(query) { mutableStateOf(SteamDbState()) }
            LaunchedEffect(query, reload, repository) {
                state.value = SteamDbState()
                repository.observe(query, force = reload > 0).collect { state.value = it }
            }
            SteamDbPanel(query, gameName, state.value, onRefresh = { reload++ },
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp))
        }
    }
}

@Composable
fun SteamDbPriceSection(query: SteamDbQuery, modifier: Modifier = Modifier, repository: SteamDbRepository = SteamDbRepository.shared, compact: Boolean = false) {
    var reload by remember(query) { mutableIntStateOf(0) }
    val result = remember(query) { mutableStateOf<SteamDbResult<SteamDbLowestPrice>>(SteamDbResult.Loading) }
    LaunchedEffect(query, reload, repository) {
        result.value = SteamDbResult.Loading
        result.value = repository.price(query, force = reload > 0)
    }
    if (compact) {
        SteamDbCompactPriceContent(query, result.value, onRetry = { reload++ }, modifier = modifier)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.steamdb_low_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (result.value is SteamDbResult.Failed && (result.value as SteamDbResult.Failed).reason !in
                listOf(SteamDbFailure.FREE, SteamDbFailure.UNKNOWN_REGION)) {
                TextButton(onClick = { reload++ }) { Text(stringResource(R.string.steamdb_retry)) }
            }
        }
        SteamDbPriceContent(result.value)
        Text(stringResource(R.string.steamdb_price_source, query.priceRegion ?: "—"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SteamDbSourceButton(query.appId)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SteamDbCompactPriceContent(query: SteamDbQuery, result: SteamDbResult<SteamDbLowestPrice>, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.store_detail_low), style = MaterialTheme.typography.labelMedium)
                if (result is SteamDbResult.Ready) {
                    Text(result.value.price, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    result.value.discount?.let { Text("−$it%", style = MaterialTheme.typography.labelSmall) }
                    result.value.occurrences?.let { Text(stringResource(R.string.steamdb_occurrences, number(it)), style = MaterialTheme.typography.labelSmall) }
                }
            }
            if (result is SteamDbResult.Ready) {
                result.value.twoYearLow?.let { Text(stringResource(R.string.steamdb_two_year_low, it), style = MaterialTheme.typography.bodySmall) }
                result.value.lastAt?.let { Text(stringResource(R.string.store_detail_low_date, date(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            ResultStatus(result)
        }
        if (result is SteamDbResult.Failed && result.reason !in listOf(SteamDbFailure.FREE, SteamDbFailure.UNKNOWN_REGION)) {
            IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, stringResource(R.string.steamdb_retry), Modifier.size(20.dp)) }
        }
        SteamDbSourceButton(query.appId, compact = true)
    }
}

@Composable
internal fun SteamDbPanel(query: SteamDbQuery, gameName: String, state: SteamDbState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val loading = listOf(state.info, state.players, state.rating, state.price).any { it is SteamDbResult.Loading }
    Column(modifier.testTag("steamdb_panel"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.steamdb_title), style = MaterialTheme.typography.titleLarge)
                Text(gameName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Default.Refresh, stringResource(R.string.steamdb_refresh)) }
        }
        Text(stringResource(R.string.steamdb_region, countryName(query.country), query.priceRegion ?: "—"),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.steamdb_low_title), style = MaterialTheme.typography.labelLarge)
                SteamDbPriceContent(state.price)
            }
        }
        val info = (state.info as? SteamDbResult.Ready)?.value
        val official = state.players as? SteamDbResult.Ready
        val online = official?.value ?: info?.currentPlayers
        val playerSource = if (official != null) R.string.steamdb_players_official else R.string.steamdb_players_fallback
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Metric(R.string.steamdb_players, number(online), stringResource(playerSource))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Metric(R.string.steamdb_peak_today, number(info?.peakToday), modifier = Modifier.weight(1f))
                Metric(R.string.steamdb_peak_all, number(info?.peakAll), modifier = Modifier.weight(1f))
            }
            Metric(R.string.steamdb_followers, number(info?.followers))
            val rating = (state.rating as? SteamDbResult.Ready)?.value
            Metric(R.string.steamdb_rating, rating?.score?.let { String.format(Locale.getDefault(), "%.2f%%", it) } ?: "—",
                rating?.let { stringResource(R.string.steamdb_rating_scope, number(it.total)) })
            Metric(R.string.steamdb_updated, info?.updatedAt?.let(::date) ?: "—", stringResource(R.string.steamdb_updated_scope))
        }
        if (official != null || online == null) ResultStatus(state.players)
        ResultStatus(state.info)
        ResultStatus(state.rating)
        SteamDbSourceButton(query.appId)
        Text(stringResource(R.string.steamdb_attribution), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SteamDbSourceButton(appId: Int, compact: Boolean = false) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val open = {
        try { uriHandler.openUri("https://steamdb.info/app/$appId/") }
        catch (_: ActivityNotFoundException) { Toast.makeText(context, R.string.steamdb_open_failed, Toast.LENGTH_SHORT).show() }
    }
    if (compact) {
        IconButton(onClick = open) { Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.steamdb_open), Modifier.size(20.dp)) }
        return
    }
    FilledTonalButton(onClick = open, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.steamdb_open))
    }
}

@Composable
private fun Metric(label: Int, value: String, supporting: String? = null, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SteamDbPriceContent(result: SteamDbResult<SteamDbLowestPrice>) {
    when (result) {
        is SteamDbResult.Ready -> {
            Text(result.value.price, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            val price = result.value
            price.twoYearLow?.let { Text(stringResource(R.string.steamdb_two_year_low, it), style = MaterialTheme.typography.bodyMedium) }
            val details = listOfNotNull(price.discount?.let { "−$it%" },
                price.occurrences?.let { stringResource(R.string.steamdb_occurrences, number(it)) })
            if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            price.lastAt?.let { Text(stringResource(R.string.steamdb_last_low, date(it)), style = MaterialTheme.typography.bodySmall) }
            ResultStatus(result)
        }
        else -> ResultStatus(result)
    }
}

@Composable
private fun ResultStatus(result: SteamDbResult<*>) {
    when (result) {
        SteamDbResult.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.steamdb_loading), style = MaterialTheme.typography.bodySmall)
        }
        is SteamDbResult.Ready -> if (result.stale || result.cached) Text(
            stringResource(if (result.stale) R.string.steamdb_stale else R.string.steamdb_cached,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(result.fetchedAt))),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is SteamDbResult.Failed -> Text(stringResource(when (result.reason) {
            SteamDbFailure.NETWORK -> R.string.steamdb_network_error
            SteamDbFailure.SERVICE -> R.string.steamdb_service_error
            SteamDbFailure.RATE_LIMITED -> R.string.steamdb_rate_limited
            SteamDbFailure.NO_DATA -> R.string.steamdb_no_data
            SteamDbFailure.UNKNOWN_REGION -> R.string.steamdb_unknown_region
            SteamDbFailure.FREE -> R.string.steamdb_free
        }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun number(value: Long?): String = value?.let { NumberFormat.getIntegerInstance().format(it) } ?: "—"
private fun date(seconds: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(seconds * 1_000))
private fun countryName(country: String?): String = country?.let { Locale("", it).getDisplayCountry(Locale.getDefault()) } ?: "—"
