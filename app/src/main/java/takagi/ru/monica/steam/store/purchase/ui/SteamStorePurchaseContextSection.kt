package takagi.ru.monica.steam.store.purchase.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.domain.SteamStoreDetail
import takagi.ru.monica.steam.store.domain.formatSteamPrice
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreOwnershipStatus
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePackageOption
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContext
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContextFailure
import takagi.ru.monica.steam.store.ui.SteamStoreImage

@Composable
fun SteamStorePurchaseContextSection(
    detail: SteamStoreDetail,
    context: SteamStorePurchaseContext?,
    contextFromCache: Boolean,
    loadingContext: Boolean,
    contextFailure: SteamStorePurchaseContextFailure?,
    selectedPackageId: Int?,
    onSelectPackage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showPackageOptions: Boolean = true,
    showPackageHeading: Boolean = true
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        steamStoreOwnershipStatusForDisplay(detail, context)?.let {
            OwnershipStatusCard(
                context = context,
                fromCache = contextFromCache,
                loading = loadingContext,
                failure = contextFailure
            )
        }
        if (showPackageOptions && detail.packageOptions.isNotEmpty()) {
            PackageOptionsCard(
                options = detail.packageOptions,
                currency = detail.currency,
                selectedPackageId = selectedPackageId,
                onSelectPackage = onSelectPackage,
                showHeading = showPackageHeading
            )
        }
    }
}

internal fun steamStoreOwnershipStatusForDisplay(
    detail: SteamStoreDetail,
    context: SteamStorePurchaseContext?
): SteamStoreOwnershipStatus? = context?.ownership?.takeUnless {
    detail.isDlc ||
    it == SteamStoreOwnershipStatus.UNKNOWN
}

@Composable
private fun OwnershipStatusCard(
    context: SteamStorePurchaseContext?,
    fromCache: Boolean,
    loading: Boolean,
    failure: SteamStorePurchaseContextFailure?
) {
    val status = context?.ownership ?: SteamStoreOwnershipStatus.UNKNOWN
    val visuals = ownershipVisuals(status)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = visuals.container,
        contentColor = visuals.content
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(visuals.icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(visuals.label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val supporting = when {
                    status == SteamStoreOwnershipStatus.FAMILY_SHARED &&
                        context?.ownerSteamIds?.isNotEmpty() == true -> stringResource(
                            R.string.steam_store_family_owner_count,
                            context.ownerSteamIds.size
                        )
                    failure != null -> purchaseFailureLabel(failure)
                    fromCache -> stringResource(R.string.steam_store_purchase_context_cached)
                    else -> null
                }
                supporting?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = visuals.content.copy(alpha = 0.76f)
                    )
                }
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun PackageOptionsCard(
    options: List<SteamStorePackageOption>,
    currency: String,
    selectedPackageId: Int?,
    onSelectPackage: (Int) -> Unit,
    showHeading: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showHeading) Text(
            stringResource(R.string.steam_store_package_options),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        options.forEach { option ->
            val selected = option.packageId == selectedPackageId
            Surface(
                onClick = { onSelectPackage(option.packageId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = RoundedCornerShape(12.dp)
                    ),
                shape = RoundedCornerShape(12.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (option.imageUrl.isNotBlank()) {
                        SteamStoreImage(
                            url = option.imageUrl,
                            modifier = Modifier.width(112.dp).height(64.dp),
                            contentDescription = option.title
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            option.title.ifBlank {
                                stringResource(
                                    R.string.steam_store_package_number,
                                    option.packageId
                                )
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        option.description.takeIf(String::isNotBlank)?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (option.discountPercent > 0) {
                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ) {
                                    Text(
                                        text = "-${option.discountPercent}%",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = if (option.isFreeLicense || option.canGetFreeLicense) {
                                    stringResource(R.string.steam_store_free_license)
                                } else {
                                    formatSteamPrice(option.priceCents, currency)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun ownershipVisuals(status: SteamStoreOwnershipStatus): OwnershipVisuals = when (status) {
    SteamStoreOwnershipStatus.OWNED -> OwnershipVisuals(
        icon = Icons.Default.CheckCircle,
        label = R.string.steam_store_owned,
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer
    )
    SteamStoreOwnershipStatus.FAMILY_SHARED -> OwnershipVisuals(
        icon = Icons.Default.FamilyRestroom,
        label = R.string.steam_store_family_shared,
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer
    )
    SteamStoreOwnershipStatus.NOT_OWNED -> OwnershipVisuals(
        icon = Icons.Default.Inventory2,
        label = R.string.steam_store_not_owned,
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = MaterialTheme.colorScheme.onSurface
    )
    SteamStoreOwnershipStatus.UNKNOWN -> OwnershipVisuals(
        icon = Icons.AutoMirrored.Filled.HelpOutline,
        label = R.string.steam_store_ownership_unknown,
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer
    )
}

@Composable
private fun purchaseFailureLabel(failure: SteamStorePurchaseContextFailure): String =
    stringResource(
        when (failure) {
            SteamStorePurchaseContextFailure.SESSION_REQUIRED ->
                R.string.steam_store_purchase_context_session
            SteamStorePurchaseContextFailure.RATE_LIMITED ->
                R.string.steam_store_purchase_context_rate_limited
            SteamStorePurchaseContextFailure.NETWORK ->
                R.string.steam_store_purchase_context_network
            SteamStorePurchaseContextFailure.INVALID_RESPONSE ->
                R.string.steam_store_purchase_context_invalid
        }
    )

private data class OwnershipVisuals(
    val icon: ImageVector,
    val label: Int,
    val container: Color,
    val content: Color
)
