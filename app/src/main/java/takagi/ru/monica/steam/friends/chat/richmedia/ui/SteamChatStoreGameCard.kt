package takagi.ru.monica.steam.friends.chat.richmedia.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.gameinvite.data.SteamChatGameInviteMetadataRepository
import takagi.ru.monica.steam.friends.chat.gameinvite.domain.STEAM_GAME_HEADER_ASPECT_RATIO
import takagi.ru.monica.steam.friends.chat.gameinvite.domain.SteamChatGameInviteMetadata
import takagi.ru.monica.steam.friends.chat.gameinvite.domain.steamGameInviteHeaderUrl
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent

@Composable
internal fun SteamChatStoreGameCard(
    content: SteamChatRichContent.StoreGameShare,
    onOpenStoreApp: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val language = remember(locale.language) {
        if (locale.language.equals("zh", ignoreCase = true)) "schinese" else "english"
    }
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        SteamChatGameInviteMetadataRepository.get(context.applicationContext)
    }
    val metadata by produceState<SteamChatGameInviteMetadata?>(
        initialValue = null,
        key1 = content.appId,
        key2 = language
    ) {
        value = repository.resolve(content.appId, language)
    }
    val title = metadata?.name?.takeIf(String::isNotBlank)
        ?: content.label?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.steam_chat_store_game_app_id, content.appId)
    val artworkUrl = metadata?.headerImageUrl?.takeIf(String::isNotBlank)
        ?: steamGameInviteHeaderUrl(content.appId)

    Surface(
        modifier = modifier
            .widthIn(min = 232.dp, max = 304.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SteamChatRemoteImage(
                    url = artworkUrl,
                    contentDescription = stringResource(
                        R.string.steam_chat_store_game_artwork,
                        title
                    ),
                    modifier = Modifier
                        .width(112.dp)
                        .aspectRatio(STEAM_GAME_HEADER_ASPECT_RATIO)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    mode = SteamChatRemoteImageMode.ARTWORK,
                    fallbackIcon = Icons.Default.SportsEsports
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.steam_chat_store_game_share),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.steam_chat_store_game_app_id, content.appId),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content.caption?.takeIf(String::isNotBlank)?.let { caption ->
                Text(
                    text = caption,
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            FilledTonalButton(
                onClick = { onOpenStoreApp(content.appId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.steam_chat_store_game_view),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
