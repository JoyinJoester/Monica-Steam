package takagi.ru.monica.steam.store.purchase.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.domain.SteamStoreDetail
import takagi.ru.monica.steam.store.ui.SteamStoreImage

@Composable
internal fun SteamPurchaseSheetHeader(detail: SteamStoreDetail) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (detail.headerImageUrl.isNotBlank()) {
            SteamStoreImage(detail.headerImageUrl,
                Modifier.fillMaxWidth().aspectRatio(460f / 215f).clip(RoundedCornerShape(24.dp))
                    .testTag("purchase_sheet_cover"), contentDescription = null)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.steam_store_package_options),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(detail.name, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
