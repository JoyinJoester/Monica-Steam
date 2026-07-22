package takagi.ru.monica.steam.store

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamNativeCartScreen(
    items: List<SteamCartItem>,
    onBack: () -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = items.map { it.currency }.distinct().singleOrNull()
    val total = steamCartTotalCents(items)
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(
            title = { Text("购物车") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            actions = { if (items.isNotEmpty()) TextButton(onClick = onClear) { Text("清空") } }
        ) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("合计", style = MaterialTheme.typography.labelLarge)
                            Text(if (currency == null && items.isNotEmpty()) "币种不一致" else formatSteamPrice(total, currency ?: "CNY"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        Button(onClick = onCheckout, enabled = items.any { it.packageId != null }, modifier = Modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(20.dp)) {
                            Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text("Steam 官方结算")
                        }
                    }
                    Text("登录、订单和支付均由 Steam 官方页面处理", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    ) { padding ->
        if (items.isEmpty()) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("购物车还是空的", style = MaterialTheme.typography.titleLarge)
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.appId }) { item ->
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        SteamStoreImage(item.imageUrl, Modifier.size(width = 112.dp, height = 54.dp).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text(item.formattedPrice, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            if (item.packageId == null) Text("需在商品页手动加入 Steam", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { onRemove(item.appId) }) { Icon(Icons.Default.Delete, "移除") }
                    }
                }
            }
        }
    }
}
