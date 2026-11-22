package takagi.ru.monica.steam.friends.chat.richmedia.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.gameinvite.ui.SteamChatGameInviteCard
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContentParser
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatTextLink
import takagi.ru.monica.steam.richtext.domain.SteamRichTextLink
import takagi.ru.monica.steam.richtext.domain.SteamRichTextParser
import takagi.ru.monica.steam.richtext.ui.SteamRichText
import takagi.ru.monica.steam.richtext.ui.SteamRichTextInlineRange

@Composable
internal fun SteamChatRichMessageContent(
    body: String,
    onOpenStoreApp: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    messageSenderSteamId: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDeleteMessage: (() -> Unit)? = null
) {
    when (val content = remember(body, messageSenderSteamId) { SteamChatRichContentParser.parse(body, messageSenderSteamId) }) {
        is SteamChatRichContent.Text -> SteamChatEmoticonText(
            body = content.body,
            links = content.links,
            modifier = modifier
        )
        is SteamChatRichContent.Action -> Text(
            text = content.body,
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        is SteamChatRichContent.GameInvite -> SteamChatGameInviteCard(
            content = content,
            onOpenStoreApp = onOpenStoreApp,
            modifier = modifier,
            onLongClick = onLongClick,
            onDeleteMessage = onDeleteMessage
        )
        is SteamChatRichContent.StoreGameShare -> SteamChatStoreGameCard(
            content = content,
            onOpenStoreApp = onOpenStoreApp,
            modifier = modifier,
            onMessageActions = onLongClick,
            onDeleteMessage = onDeleteMessage
        )
        is SteamChatRichContent.OfficialMessage -> SteamOfficialMessageContent(
            content.message, modifier, onMessageActions = onLongClick, onDeleteMessage = onDeleteMessage
        )
        is SteamChatRichContent.Sticker -> SteamChatRemoteImage(
            url = content.imageUrl,
            contentDescription = content.name,
            modifier = modifier.size(184.dp),
            mode = SteamChatRemoteImageMode.STICKER
        )
        is SteamChatRichContent.Attachment -> AttachmentContent(content, modifier)
    }
}

@Composable
private fun SteamChatEmoticonText(
    body: String,
    links: List<SteamChatTextLink>,
    modifier: Modifier
) {
    val context = LocalContext.current
    val document = remember(body, links) {
        SteamRichTextParser.parse(
            source = body,
            sourceLinks = links.map { link ->
                SteamRichTextLink(
                    start = link.start,
                    endExclusive = link.endExclusive,
                    url = link.url,
                )
            },
        )
    }
    val matches = remember(document.text) { emoticonPattern.findAll(document.text).toList() }
    if (document.links.isEmpty() && matches.size == 1 && matches.single().value == document.text.trim()) {
        val name = matches.single().groupValues[1]
        SteamChatRemoteImage(
            url = SteamChatEmoticon(name).imageUrl,
            contentDescription = name,
            modifier = modifier.size(60.dp),
            mode = SteamChatRemoteImageMode.EMOTICON
        )
        return
    }
    val inline = remember(matches) {
        matches.mapIndexed { index, match ->
            val name = match.groupValues[1]
            "steam-emoticon-$index" to InlineTextContent(
                placeholder = Placeholder(1.8.em, 1.8.em, PlaceholderVerticalAlign.Center)
            ) {
                SteamChatRemoteImage(
                    url = SteamChatEmoticon(name).imageUrl,
                    contentDescription = name,
                    modifier = Modifier.fillMaxWidth(),
                    mode = SteamChatRemoteImageMode.EMOTICON
                )
            }
        }.toMap()
    }
    val inlineRanges = remember(matches) {
        matches.mapIndexed { index, match ->
            SteamRichTextInlineRange(
                id = "steam-emoticon-$index",
                start = match.range.first,
                endExclusive = match.range.last + 1,
            )
        }
    }
    SteamRichText(
        document = document,
        onOpenLink = { url -> openSteamChatLink(context, url) },
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        inlineContent = inline,
        inlineRanges = inlineRanges,
    )
}

private fun openSteamChatLink(context: android.content.Context, url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    if (uri.scheme?.lowercase() !in setOf("http", "https", "steam")) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

private val emoticonPattern = Regex("(?<![A-Za-z0-9]):([A-Za-z0-9_+\\-]{2,64}):(?![A-Za-z0-9])")

internal fun isSingleSteamEmoticonMessage(body: String): Boolean =
    emoticonPattern.matchEntire(body.trim()) != null
