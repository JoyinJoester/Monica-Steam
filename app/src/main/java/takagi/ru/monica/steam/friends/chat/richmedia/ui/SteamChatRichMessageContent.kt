package takagi.ru.monica.steam.friends.chat.richmedia.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentKind
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContentParser

@Composable
internal fun SteamChatRichMessageContent(
    body: String,
    modifier: Modifier = Modifier
) {
    when (val content = remember(body) { SteamChatRichContentParser.parse(body) }) {
        is SteamChatRichContent.Text -> SteamChatEmoticonText(content.body, modifier)
        is SteamChatRichContent.GameInvite -> GameInviteContent(content, modifier)
        is SteamChatRichContent.SystemMessage -> SteamSystemMessageContent(content, modifier)
        is SteamChatRichContent.Sticker -> SteamChatRemoteImage(
            url = content.imageUrl,
            contentDescription = content.name,
            modifier = modifier.size(148.dp)
        )
        is SteamChatRichContent.Attachment -> AttachmentContent(content, modifier)
    }
}

@Composable
private fun SteamSystemMessageContent(
    content: SteamChatRichContent.SystemMessage,
    modifier: Modifier
) {
    val context = LocalContext.current
    val open = {
        content.url?.let { url ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
        Unit
    }
    Surface(
        modifier = modifier
            .widthIn(min = 180.dp, max = 280.dp)
            .clickable(enabled = content.url != null, onClick = open),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
            Text(
                text = content.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GameInviteContent(
    content: SteamChatRichContent.GameInvite,
    modifier: Modifier
) {
    val context = LocalContext.current
    val open = {
        content.url?.let { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        Unit
    }
    Surface(
        modifier = modifier
            .widthIn(min = 190.dp, max = 280.dp)
            .clickable(enabled = content.url != null, onClick = open),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.SportsEsports, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = content.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = content.appId?.let { "App $it" } ?: "Steam invitation",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f)
                )
            }
            if (content.url != null) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
            }
        }
    }
}

@Composable
private fun SteamChatEmoticonText(body: String, modifier: Modifier) {
    val matches = remember(body) { emoticonPattern.findAll(body).toList() }
    if (matches.isEmpty()) {
        Text(text = body, modifier = modifier, style = MaterialTheme.typography.bodyLarge)
        return
    }
    val annotated = remember(body, matches) {
        buildAnnotatedString {
            var cursor = 0
            matches.forEachIndexed { index, match ->
                append(body.substring(cursor, match.range.first))
                appendInlineContent("steam-emoticon-$index", match.value)
                cursor = match.range.last + 1
            }
            append(body.substring(cursor))
        }
    }
    val inline = remember(matches) {
        matches.mapIndexed { index, match ->
            val name = match.groupValues[1]
            "steam-emoticon-$index" to InlineTextContent(
                placeholder = Placeholder(1.35.em, 1.35.em, PlaceholderVerticalAlign.Center)
            ) {
                SteamChatRemoteImage(
                    url = SteamChatEmoticon(name).imageUrl,
                    contentDescription = name,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }.toMap()
    }
    Text(
        text = annotated,
        inlineContent = inline,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun AttachmentContent(
    content: SteamChatRichContent.Attachment,
    modifier: Modifier
) {
    val context = LocalContext.current
    val open = {
        runCatching {
            val uri = Uri.parse(content.url)
            if (uri.scheme == "https") {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }
        Unit
    }
    Column(
        modifier = modifier.widthIn(min = 180.dp, max = 260.dp).clickable(onClick = open),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (content.kind == SteamChatAttachmentKind.IMAGE) {
            SteamChatRemoteImage(
                url = content.url,
                contentDescription = content.label,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = when (content.kind) {
                    SteamChatAttachmentKind.IMAGE -> Icons.Default.Image
                    SteamChatAttachmentKind.VIDEO -> Icons.Default.Movie
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                },
                contentDescription = null
            )
            Text(
                text = content.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val emoticonPattern = Regex("(?<![A-Za-z0-9]):([A-Za-z0-9_+\\-]{2,64}):(?![A-Za-z0-9])")
