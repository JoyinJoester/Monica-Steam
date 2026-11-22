package takagi.ru.monica.steam.friends.chat.telegram

import android.text.SpannableString
import android.text.Spanned
import android.text.style.URLSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.monica.telegramchat.TelegramMessageTextView
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent
import java.text.DateFormat
import java.util.Date

/** Inline Steam emoticons and BBCode retain their existing rich renderer. */
internal fun SteamChatRichContent.nativeTelegramText(): SteamChatRichContent.Text? =
    (this as? SteamChatRichContent.Text)?.takeUnless {
        Regex("\\[[^]\\n]+]|:[A-Za-z0-9_]+:").containsMatchIn(it.body)
    }

@Composable
internal fun TelegramTextMessage(
    content: SteamChatRichContent.Text,
    timestamp: Long,
    outgoing: Boolean,
    delivery: Int,
    groupedWithPrevious: Boolean,
    groupedWithNext: Boolean,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBubble: Boolean = true
) {
    val colors = telegramChatColors()
    val scale = LocalDensity.current.fontScale
    val text = remember(content) {
        SpannableString(content.body).apply {
            content.links.filter { it.start >= 0 && it.endExclusive <= length && it.endExclusive > it.start }
                .forEach { setSpan(URLSpan(it.url), it.start, it.endExclusive, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
    }
    val time = remember(timestamp) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp * 1000)) }
    AndroidView(
        modifier = modifier,
        factory = { TelegramMessageTextView(it) },
        update = { view ->
            view.setColors(colors.native)
            view.setTextScale(scale)
            view.setGrouped(groupedWithPrevious, groupedWithNext)
            view.setShowBubble(showBubble)
            view.setMessage(text, time, outgoing, delivery)
            view.setOnLongClickListener { onLongClick(); true }
        }
    )
}
