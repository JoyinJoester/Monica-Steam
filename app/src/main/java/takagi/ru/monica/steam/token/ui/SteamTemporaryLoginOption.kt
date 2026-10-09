package takagi.ru.monica.steam.token.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

@Composable
internal fun SteamTemporaryLoginOption(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val toggleShape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 4.dp)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Surface(
            shape = toggleShape,
            color = if (checked) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
                .testTag("temporary_login")
                .clip(toggleShape)
                .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.steam_temporary_login), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.steam_temporary_login_summary), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = checked, onCheckedChange = null, enabled = enabled)
            }
        }
        Surface(
            shape = RoundedCornerShape(4.dp, 4.dp, 20.dp, 20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Text(
                stringResource(R.string.steam_temporary_login_description),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
