package takagi.ru.monica.steam.web.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.web.domain.SteamWebFailureKind
import takagi.ru.monica.steam.web.domain.SteamWebPageFailure
import takagi.ru.monica.steam.web.domain.SteamWebSessionProblem

@Composable
internal fun SteamWebSessionError(problem: SteamWebSessionProblem?) {
    val message = stringResource(
        when (problem) {
            SteamWebSessionProblem.TEMPORARY_SESSION -> R.string.steam_temporary_web_unavailable
            SteamWebSessionProblem.IDENTITY_MISMATCH ->
                R.string.steam_web_session_identity_mismatch
            SteamWebSessionProblem.INVALID_SESSION -> R.string.steam_web_session_invalid
            SteamWebSessionProblem.EXPECTED_ACCOUNT_REQUIRED ->
                R.string.steam_web_session_account_required
            SteamWebSessionProblem.AUTHENTICATED_SESSION_REQUIRED,
            null -> R.string.steam_web_session_login_required
        }
    )
    SteamWebMessageContent(
        icon = { modifier ->
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = modifier,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = stringResource(R.string.steam_web_session_unavailable),
        message = message
    )
}

@Composable
internal fun SteamWebFailureContent(
    failure: SteamWebPageFailure,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    val title = stringResource(
        when (failure.kind) {
            SteamWebFailureKind.NETWORK -> R.string.steam_web_error_network
            SteamWebFailureKind.HTTP -> R.string.steam_web_error_http
            SteamWebFailureKind.SSL -> R.string.steam_web_error_ssl
            SteamWebFailureKind.UNSAFE_NAVIGATION -> R.string.steam_web_error_unsafe
            SteamWebFailureKind.RENDERER -> R.string.steam_web_error_renderer
        }
    )
    val detail = when {
        failure.kind == SteamWebFailureKind.HTTP && failure.statusCode != null ->
            stringResource(R.string.steam_web_error_http_code, failure.statusCode)
        !failure.description.isNullOrBlank() -> failure.description
        else -> stringResource(R.string.steam_web_error_generic_detail)
    }
    SteamWebMessageContent(
        icon = { modifier ->
            Icon(
                imageVector = when (failure.kind) {
                    SteamWebFailureKind.NETWORK -> Icons.Default.CloudOff
                    SteamWebFailureKind.SSL -> Icons.Default.Lock
                    SteamWebFailureKind.UNSAFE_NAVIGATION -> Icons.Default.Warning
                    SteamWebFailureKind.HTTP,
                    SteamWebFailureKind.RENDERER -> Icons.Default.ErrorOutline
                },
                contentDescription = null,
                modifier = modifier,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = title,
        message = detail,
        actions = {
            FilledTonalButton(onClick = onRetry) {
                Text(stringResource(R.string.steam_web_retry))
            }
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.steam_web_return_to_monica))
            }
        }
    )
}

@Composable
private fun SteamWebMessageContent(
    icon: @Composable (Modifier) -> Unit,
    title: String,
    message: String,
    actions: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon(Modifier.size(48.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (actions != null) {
            Row(
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }
        }
    }
}
