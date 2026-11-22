package takagi.ru.monica.steam.foundation.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount

/** Shared account entry for store, library and chat headers. */
@Composable
internal fun SteamAccountAvatarButton(
    account: SteamAccount?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String = stringResource(R.string.steam_switch_account)
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = description }
    ) {
        if (account != null) {
            SteamAvatarImage(
                account = account,
                size = 32.dp,
                shape = CircleShape,
                modifier = Modifier.clearAndSetSemantics { }
            )
        } else {
            Icon(Icons.Default.AccountCircle, contentDescription = null)
        }
    }
}
