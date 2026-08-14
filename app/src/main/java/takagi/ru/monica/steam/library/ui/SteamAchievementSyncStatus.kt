package takagi.ru.monica.steam.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

@Composable
internal fun SteamAchievementSyncStatus(
    syncing: Boolean,
    failureMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!syncing && failureMessage == null) return
    val failed = failureMessage != null
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (failed) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (failed) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (failed) Icons.Default.ErrorOutline else Icons.Default.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = failureMessage
                        ?: stringResource(R.string.steam_library_syncing_achievements),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            if (syncing) {
                Text(
                    text = stringResource(R.string.steam_library_syncing_achievements_background),
                    style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                FilledTonalButton(onClick = onRetry) {
                    Text(stringResource(R.string.steam_library_retry))
                }
            }
        }
    }
}
