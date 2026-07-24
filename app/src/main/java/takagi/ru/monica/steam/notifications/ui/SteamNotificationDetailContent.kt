package takagi.ru.monica.steam.notifications.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.notifications.domain.SteamNotificationDetailField

@Composable
internal fun SteamNotificationDetailContent(fields: List<SteamNotificationDetailField>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.steam_notification_detail_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                fields.forEachIndexed { index, field ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = notificationDetailFieldLabel(field.key),
                            modifier = Modifier.weight(0.42f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = notificationDetailFieldValue(field),
                            modifier = Modifier.weight(0.58f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun notificationDetailFieldLabel(key: String): String {
    return when (key.leafKey()) {
        "discount", "discount_percent", "discount_percentage", "discount_pct" ->
            stringResource(R.string.steam_notification_detail_discount)
        "price", "final_price", "price_final", "formatted_price" ->
            stringResource(R.string.steam_notification_detail_price)
        "friend_invite_state" -> stringResource(R.string.steam_notification_friend_state)
        else -> humanizeNotificationDetailKey(key)
    }
}

@Composable
private fun notificationDetailFieldValue(field: SteamNotificationDetailField): String {
    val discountKeys = setOf("discount", "discount_percent", "discount_percentage", "discount_pct")
    if (field.key.leafKey() == "friend_invite_state") {
        return when (field.value.lowercase()) {
            "pending" -> stringResource(R.string.steam_notification_friend_state_pending)
            "accepted" -> stringResource(R.string.steam_notification_friend_state_accepted)
            "ignored" -> stringResource(R.string.steam_notification_friend_state_ignored)
            else -> field.value
        }
    }
    return if (field.key.leafKey() in discountKeys &&
        '%' !in field.value && field.value.toDoubleOrNull() != null
    ) {
        "${field.value}%"
    } else {
        field.value
    }
}

private fun humanizeNotificationDetailKey(key: String): String {
    return key.substringAfterLast('.')
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .replace('_', ' ')
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .joinToString(" ") { word ->
            word.replaceFirstChar { character -> character.titlecase() }
        }
}

private fun String.leafKey(): String =
    substringAfterLast('.').substringBefore('[').lowercase()
