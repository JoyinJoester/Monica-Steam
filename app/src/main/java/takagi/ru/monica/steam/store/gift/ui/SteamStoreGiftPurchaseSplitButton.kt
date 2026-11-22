package takagi.ru.monica.steam.store.gift.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassDropdownMenu as DropdownMenu
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.domain.SteamCartItem

private val PurchaseActionHeight = 52.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SteamStoreGiftPurchaseSplitButton(
    cartItem: SteamCartItem?,
    canAdd: Boolean,
    alreadyOwned: Boolean,
    onAddForSelf: () -> Unit,
    onAddAsGift: () -> Unit,
    onOpenCart: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    canGift: Boolean = canAdd
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "steam_store_purchase_mode_rotation"
    )
    val interactive = cartItem != null || canAdd
    val recipient = cartItem?.giftRecipient

    Box(modifier = modifier) {
        SplitButtonLayout(
            modifier = Modifier.fillMaxWidth(),
            leadingButton = {
                SplitButtonDefaults.TonalLeadingButton(
                    onClick = if (cartItem == null) onAddForSelf else onOpenCart,
                    enabled = interactive,
                    modifier = Modifier
                        .widthIn(min = 220.dp, max = 320.dp)
                        .height(PurchaseActionHeight)
                ) {
                    Icon(
                        imageVector = when {
                            recipient != null -> Icons.Default.CardGiftcard
                            cartItem != null -> Icons.Default.ShoppingCart
                            else -> Icons.Default.ShoppingCart
                        },
                        contentDescription = null,
                        modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize)
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = when {
                            recipient != null -> stringResource(
                                R.string.steam_store_gift_added_for,
                                recipient.displayName
                            )
                            cartItem != null -> stringResource(R.string.steam_store_cart_open)
                            alreadyOwned -> stringResource(R.string.steam_store_already_owned)
                            else -> stringResource(R.string.steam_store_add_cart)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            trailingButton = {
                SplitButtonDefaults.TonalTrailingButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it },
                    enabled = cartItem != null || canGift,
                    modifier = Modifier.size(PurchaseActionHeight)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.steam_store_purchase_mode),
                        modifier = Modifier
                            .size(SplitButtonDefaults.TrailingIconSize)
                            .rotate(rotation)
                    )
                }
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(272.dp),
            offset = DpOffset(0.dp, 8.dp),
            shape = RoundedCornerShape(22.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SteamStorePurchaseModeMenuItem(
                    enabled = canAdd,
                    label = stringResource(R.string.steam_store_purchase_for_self),
                    icon = Icons.Default.Person,
                    selected = cartItem != null && recipient == null,
                    onClick = {
                        expanded = false
                        onAddForSelf()
                    }
                )
                SteamStorePurchaseModeMenuItem(
                    enabled = canGift,
                    label = stringResource(R.string.steam_store_purchase_as_gift),
                    icon = Icons.Default.CardGiftcard,
                    selected = recipient != null,
                    onClick = {
                        expanded = false
                        onAddAsGift()
                    }
                )
                if (cartItem != null) {
                    SteamStorePurchaseModeMenuItem(
                        label = stringResource(R.string.steam_store_cart_remove),
                        icon = Icons.Default.DeleteOutline,
                        selected = false,
                        onClick = {
                            expanded = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SteamStorePurchaseModeMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
