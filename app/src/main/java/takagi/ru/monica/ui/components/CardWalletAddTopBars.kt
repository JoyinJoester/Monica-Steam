package takagi.ru.monica.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.ui.screens.CardWalletTab

@Composable
fun CardWalletAddTypeChip(
    current: CardWalletTab,
    onSelect: (CardWalletTab) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    drawContainer: Boolean = true,
    contentColorOverride: Color? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val chipContentDescription = stringResource(R.string.nav_card_wallet)
    val interactionSource = remember { MutableInteractionSource() }
    val options = remember {
        listOf(
            CardWalletTab.BANK_CARDS,
            CardWalletTab.DOCUMENTS,
            CardWalletTab.BILLING_ADDRESSES
        )
    }

    val targetContent = contentColorOverride ?: if (drawContainer) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "CardWalletAddTypeChipArrow"
    )
    val containerColor = if (drawContainer) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = { expanded = true }
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = chipContentDescription }
    ) {
        Icon(
            imageVector = cardWalletAddTypeIcon(current),
            contentDescription = null,
            tint = targetContent,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = cardWalletAddTypeLabel(current),
            color = targetContent,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            tint = targetContent,
            modifier = Modifier
                .size(18.dp)
                .rotate(arrowRotation)
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        options.forEach { option ->
            val isCurrent = option == current
            DropdownMenuItem(
                text = {
                    Text(
                        text = cardWalletAddTypeLabel(option),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = cardWalletAddTypeIcon(option),
                        contentDescription = null,
                        tint = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                trailingIcon = if (isCurrent) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    null
                },
                onClick = {
                    expanded = false
                    if (!isCurrent) onSelect(option)
                }
            )
        }
    }
}

@Composable
private fun cardWalletAddTypeLabel(type: CardWalletTab): String = stringResource(
    when (type) {
        CardWalletTab.DOCUMENTS -> R.string.item_type_document
        CardWalletTab.BILLING_ADDRESSES -> R.string.billing_address
        else -> R.string.item_type_bank_card
    }
)

private fun cardWalletAddTypeIcon(type: CardWalletTab): ImageVector = when (type) {
    CardWalletTab.DOCUMENTS -> Icons.Default.Badge
    CardWalletTab.BILLING_ADDRESSES -> Icons.Default.Home
    else -> Icons.Default.CreditCard
}
