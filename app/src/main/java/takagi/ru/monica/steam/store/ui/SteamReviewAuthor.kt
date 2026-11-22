package takagi.ru.monica.steam.store.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.LocalSteamAvatarShape
import takagi.ru.monica.steam.profile.SteamMiniProfileBackgroundService
import takagi.ru.monica.steam.profile.SteamMiniProfileDecor
import takagi.ru.monica.steam.profile.SteamMiniProfileDecorRepository
import takagi.ru.monica.steam.profile.viewer.ui.rememberSteamProfileViewerImage

internal fun isSteamReviewAuthorId(id: String): Boolean =
    id.matches(Regex("7656119\\d{10}")) &&
        SteamMiniProfileBackgroundService.steamIdToAccountId(id)?.let { it in 1..0xffff_ffffL } == true

private object ReviewAuthorRequests {
    private val slots = Semaphore(4)
    suspend fun load(context: Context, id: String): SteamMiniProfileDecor? = slots.withPermit {
        withContext(Dispatchers.IO) {
            val result = SteamMiniProfileDecorRepository.get(context).load(id)
            ensureActive()
            result
        }
    }
}

@Composable
internal fun SteamReviewAuthor(
    steamId: String,
    onOpen: (String) -> Unit,
    loader: (suspend (String) -> SteamMiniProfileDecor?)? = null,
    votedUp: Boolean? = null,
    metadata: String? = null,
) {
    // Replace the entire identity state when a card is reused for another author.
    key(steamId) {
        val context = LocalContext.current.applicationContext
        val valid = isSteamReviewAuthorId(steamId)
        val latestLoader by rememberUpdatedState(loader)
        val author by produceState<SteamMiniProfileDecor?>(null, steamId) {
            if (valid) {
                val load = latestLoader
                val result = if (load != null) load(steamId) else ReviewAuthorRequests.load(context, steamId)
                coroutineContext.ensureActive()
                value = result
            }
        }
        val name = author?.personaName?.takeIf(String::isNotBlank)
            ?: steamId.takeIf { valid } ?: stringResource(R.string.steam_store_review_steam_player)
        val avatar = rememberSteamProfileViewerImage(author?.avatarUrl)
        Surface(onClick = { if (valid) onOpen(steamId) }, enabled = valid,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("review_author_$steamId"),
            color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(Modifier.size(40.dp).testTag("review_avatar_$steamId"), shape = LocalSteamAvatarShape.current,
                    color = MaterialTheme.colorScheme.secondaryContainer) {
                    if (avatar != null) Image(avatar!!, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.AccountCircle, null, Modifier.size(28.dp)) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        votedUp?.let { recommended ->
                            SteamReviewRecommendation(recommended)
                        }
                    }
                    metadata?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (valid) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    stringResource(R.string.steam_store_review_view_author), Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SteamReviewRecommendation(recommended: Boolean) {
    val accent = if (recommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(Modifier.widthIn(max = 140.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(if (recommended) Icons.Default.ThumbUp else Icons.Default.ThumbDown,
            contentDescription = null, modifier = Modifier.size(14.dp), tint = accent)
        Text(stringResource(if (recommended) R.string.steam_store_review_recommended
            else R.string.steam_store_review_not_recommended),
            modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium, color = accent)
    }
}
