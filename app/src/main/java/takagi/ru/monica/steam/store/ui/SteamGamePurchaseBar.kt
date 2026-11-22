package takagi.ru.monica.steam.store.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.liquidglass.ui.*
import takagi.ru.monica.steam.store.domain.SteamStoreDetail
import takagi.ru.monica.steam.store.domain.formatSteamPrice
import takagi.ru.monica.steam.store.purchase.domain.*

/** Only section content is recorded; the floating bar cannot sample itself. */
@Composable
internal fun SteamGamePurchaseHost(
    bottomBar: (@Composable () -> Unit)?,
    visible: Boolean = true,
    content: @Composable () -> Unit
) {
    if (bottomBar == null) { content(); return }
    val density = LocalDensity.current
    var height by remember { mutableIntStateOf(0) }
    SteamGlassHost(Modifier.fillMaxSize(), content = {
        CompositionLocalProvider(LocalSteamDockContentClearance provides if (visible) with(density) { height.toDp() } else 0.dp) {
            content()
        }
    }, overlay = {
        if (visible) Box(Modifier.align(Alignment.BottomCenter).widthIn(max = 640.dp).fillMaxWidth()
            .onSizeChanged { height = it.height }
            .navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) { bottomBar() }
    })
}

@Composable
internal fun SteamGamePurchaseBar(
    detail: SteamStoreDetail,
    inWishlist: Boolean,
    wishlistAvailable: Boolean,
    wishlistMutating: Boolean,
    onWishlist: () -> Unit,
    onPurchase: () -> Unit,
    modifier: Modifier = Modifier,
    alreadyOwned: Boolean = false
) {
    val offer = remember(detail) { steamPurchaseOffer(detail) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(offer.endEpochSeconds, alreadyOwned) {
        while (!alreadyOwned && offer.endEpochSeconds != null && now < offer.endEpochSeconds) {
            now = System.currentTimeMillis() / 1000
            delay(1000)
        }
    }
    val remaining = steamDiscountRemaining(offer.endEpochSeconds, now)
    val available = detail.availableInAccountRegion != false
    val wishlistLabel = stringResource(if (inWishlist) R.string.steam_store_remove_wishlist else R.string.steam_store_add_wishlist)
    Row(modifier.testTag("game_purchase_bar").steamGlassSurface(RoundedCornerShape(28.dp), role = SteamGlassRole.PANEL)
        .padding(6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!alreadyOwned) FilledTonalIconButton(onClick = onWishlist,
            enabled = (available || inWishlist) && wishlistAvailable && !wishlistMutating,
            modifier = Modifier.size(52.dp).testTag("game_purchase_wishlist").semantics { selected = inWishlist }) {
            if (wishlistMutating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(if (inWishlist) Icons.Default.Favorite else Icons.Default.FavoriteBorder, wishlistLabel)
        }
        Button(onClick = onPurchase, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("game_purchase_open"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), shape = RoundedCornerShape(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val label = when {
                    alreadyOwned -> stringResource(R.string.steam_store_owned)
                    !available || offer.expired(now) -> stringResource(R.string.steam_store_package_options)
                    detail.isFree -> stringResource(R.string.steam_store_free_license)
                    offer.priceCents != null -> stringResource(R.string.steam_store_price_buy, formatSteamPrice(offer.priceCents, detail.currency))
                    else -> stringResource(R.string.steam_store_package_options)
                }
                Text(label, style = MaterialTheme.typography.titleMedium)
                if (alreadyOwned) {
                    Text(stringResource(R.string.steam_store_package_options), style = MaterialTheme.typography.labelSmall)
                } else if (available && remaining != null && offer.discountPercent > 0) {
                    val countdown = stringResource(when (remaining.unit) {
                        SteamDiscountRemaining.Unit.DAYS -> R.string.steam_store_offer_days
                        SteamDiscountRemaining.Unit.HOURS -> R.string.steam_store_offer_hours
                        SteamDiscountRemaining.Unit.MINUTES -> R.string.steam_store_offer_minutes
                    }, remaining.amount)
                    Text("−${offer.discountPercent}% · $countdown", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
