package takagi.ru.monica.steam.space.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.steamWindowTopPadding
import takagi.ru.monica.steam.space.domain.STEAM_SPACE_POST_LIMIT
import takagi.ru.monica.steam.space.presentation.SteamSpaceComposePhase
import takagi.ru.monica.steam.space.presentation.SteamSpaceComposeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamSpaceComposeScreen(
    account: SteamAccount,
    state: SteamSpaceComposeState,
    onTextChange: (String) -> Unit,
    onPublish: () -> Unit,
    onClose: () -> Unit,
    onOpenWeb: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val sending = state.phase == SteamSpaceComposePhase.SENDING
    val unknown = state.phase == SteamSpaceComposePhase.UNKNOWN
    var confirmDiscard by remember(account.id, account.steamId) { mutableStateOf(false) }
    val close = { if (!sending) { if (state.text.isNotBlank()) confirmDiscard = true else onClose() } }
    BackHandler { close() }
    Scaffold(modifier.fillMaxSize().testTag("steam-space-compose"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.steam_space_publish)) },
                navigationIcon = { IconButton(onClick = close, enabled = !sending) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.steam_space_back))
                } }, windowInsets = WindowInsets(0, 0, 0, 0), modifier = Modifier.steamWindowTopPadding())
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 12.dp, bottom = LocalSteamDockContentClearance.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(account.displayName.ifBlank { account.accountName }, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.steam_space_post_audience), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = state.text, onValueChange = onTextChange,
                enabled = state.phase == SteamSpaceComposePhase.EDITING,
                label = { Text(stringResource(R.string.steam_space_post_text)) },
                supportingText = { Text(stringResource(R.string.steam_space_post_count, state.text.length, STEAM_SPACE_POST_LIMIT)) },
                isError = state.text.length > STEAM_SPACE_POST_LIMIT,
                minLines = 6, modifier = Modifier.fillMaxWidth().testTag("steam-space-post-text"))
            if (state.preparationFailed) Text(stringResource(R.string.steam_space_post_prepare_failed),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("steam-space-post-error"))
            if (unknown) Text(stringResource(R.string.steam_space_post_unknown),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("steam-space-post-unknown"))
            Button(onClick = onPublish, enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("steam-space-post-send")) {
                if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (sending) R.string.steam_space_post_sending else R.string.steam_space_publish))
            }
            if (onOpenWeb != null) TextButton(onClick = onOpenWeb, enabled = !sending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("steam-space-post-web")) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (unknown) R.string.steam_space_post_check_web else R.string.steam_space_post_use_web))
            }
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.steam_space_post_discard_title)) },
        text = { Text(stringResource(if (unknown) R.string.steam_space_post_discard_unknown else R.string.steam_space_post_discard_body)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) {
            Text(stringResource(R.string.steam_space_post_discard))
        } }, dismissButton = { TextButton(onClick = { confirmDiscard = false }) {
            Text(stringResource(R.string.steam_space_post_keep_editing))
        } })
}
