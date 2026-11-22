package takagi.ru.monica.steam.friends.groupchat.avatar.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteImage
import takagi.ru.monica.steam.friends.domain.SteamFriend

@Composable
internal fun SteamGroupAvatarImage(
    url: String,
    members: List<SteamFriend> = emptyList(),
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        val normalizedUrl = url.trim()
        value = if (normalizedUrl.isBlank()) {
            null
        } else {
            loadSteamGroupAvatarWithRetry {
                loadSteamRemoteImage(context, normalizedUrl)
            }
        }
    }
    Surface(
        modifier = modifier,
        shape = shape,
        // Match the native conversation rows' placeholder avatars (accent + accentText):
        // secondaryContainer is too close to the dark background and reads as see-through.
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (members.isNotEmpty()) {
            SteamGroupMemberAvatarGrid(
                members = members,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(0.52f)
                )
            }
        }
    }
}

/**
 * Composite disc as a plain [Bitmap], for hosts that draw avatars themselves (the native
 * Telegram row). Loading the member pictures happens here; the drawing and the circular mask
 * live in [renderSteamGroupAvatarBitmap].
 */
@Composable
internal fun rememberSteamGroupAvatarBitmap(
    members: List<SteamFriend>,
    emptyLabel: String,
    sizeDp: Dp,
    tileColor: Color = MaterialTheme.colorScheme.primary,
    labelColor: Color = MaterialTheme.colorScheme.onPrimary,
    dividerColor: Color = MaterialTheme.colorScheme.surface
): Bitmap {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { sizeDp.roundToPx() }
    val tile = tileColor.toArgb()
    val label = labelColor.toArgb()
    val divider = dividerColor.toArgb()
    val visible = steamGroupAvatarMembers(members)
    // Show this group's disc immediately, including while photos load or the row is recycled.
    // Visual member values key the fallback; presence changes keep the same bitmap.
    val fallback = remember(visible, sizePx, tile, label, divider, emptyLabel) {
        renderSteamGroupAvatarBitmap(visible, emptyMap(), sizePx, tile, label, divider, emptyLabel)
    }
    var bitmap by remember(fallback) { mutableStateOf(fallback) }
    LaunchedEffect(visible, context, fallback) {
        // At most four requests and one completed composite. Photo identity is explicit, and
        // cancellation prevents a previous group's image from reaching a rebound row.
        val photos = coroutineScope {
            visible.filter { it.avatarUrl.isNotBlank() }.map { member ->
                async {
                    loadSteamRemoteImage(context, member.avatarUrl)?.asAndroidBitmap()?.let { member.steamId to it }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        if (photos.isNotEmpty()) {
            bitmap = withContext(Dispatchers.Default) {
                renderSteamGroupAvatarBitmap(visible, photos, sizePx, tile, label, divider, emptyLabel)
            }
        }
    }
    return bitmap
}

/** Only these values affect pixels. Presence, game and other profile updates reuse the image. */
internal data class SteamGroupAvatarMember(val steamId: String, val displayName: String, val avatarUrl: String)

internal fun steamGroupAvatarMembers(members: List<SteamFriend>): List<SteamGroupAvatarMember> =
    members.distinctBy(SteamFriend::steamId).take(4).map {
        SteamGroupAvatarMember(it.steamId, it.displayName, it.avatarUrl.trim())
    }

internal suspend fun <T> loadSteamGroupAvatarWithRetry(
    retryDelaysMillis: List<Long> = GROUP_AVATAR_RETRY_DELAYS_MILLIS,
    delayBlock: suspend (Long) -> Unit = { delay(it) },
    load: suspend () -> T?
): T? {
    retryDelaysMillis.forEach { delayMillis ->
        if (delayMillis > 0L) delayBlock(delayMillis)
        load()?.let { return it }
    }
    return null
}

private val GROUP_AVATAR_RETRY_DELAYS_MILLIS = listOf(0L, 1_000L, 4_000L, 12_000L)
