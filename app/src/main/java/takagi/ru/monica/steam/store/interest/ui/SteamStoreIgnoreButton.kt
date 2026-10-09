package takagi.ru.monica.steam.store.interest.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

@Composable
internal fun SteamStoreIgnoreButton(
    ignored: Boolean,
    enabled: Boolean,
    mutating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val content: @Composable () -> Unit = {
        if (mutating) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = if (ignored) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    if (ignored) R.string.steam_store_unignore_game
                    else R.string.steam_store_ignore_game
                )
            )
        }
    }
    val buttonModifier = modifier.heightIn(min = if (compact) 48.dp else 52.dp)
    if (compact) {
        TextButton(onClick = onClick, enabled = enabled && !mutating, modifier = buttonModifier) { content() }
        return
    }
    if (ignored) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled && !mutating,
            modifier = buttonModifier,
            shape = RoundedCornerShape(18.dp),
            content = { content() }
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled && !mutating,
            modifier = buttonModifier,
            shape = RoundedCornerShape(18.dp),
            content = { content() }
        )
    }
}
