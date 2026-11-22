package takagi.ru.monica.steam.friends.chat.telegram

import android.graphics.Bitmap
import android.view.View
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.monica.telegramchat.SocialTheme
import com.monica.telegramchat.upstream.DialogCell
import com.monica.telegramchat.upstream.UserCell
import com.monica.telegramchat.upstream.ProfileActionsView
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteImage
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.R

private class ConversationPress {
    var position = Offset.Zero
    var active = false
}

@Composable
internal fun nativeSocialTheme(): SocialTheme {
    val c = MaterialTheme.colorScheme
    return remember(c) { SocialTheme(c.onSurface.toArgb(), c.onSurfaceVariant.toArgb(), c.primary.toArgb(), c.onPrimary.toArgb(), c.surface.toArgb(), c.outlineVariant.toArgb()) }
}

@Composable
private fun steamAvatarBitmap(url: String): Bitmap? {
    val context = LocalContext.current
    // Key the state container as well as the coroutine, so a recycled row never
    // displays the preceding friend's bitmap while the new request is pending.
    return key(url) {
        val bitmap by produceState<Bitmap?>(null, url) {
            if (url.isNotBlank()) value = loadSteamRemoteImage(context, url)?.asAndroidBitmap()
        }
        bitmap
    }
}

@Composable
internal fun TelegramNativeContactRow(friend: SteamFriend, status: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val theme = nativeSocialTheme()
    val bitmap = steamAvatarBitmap(friend.avatarUrl)
    val textScale = LocalDensity.current.fontScale
    key(theme) {
        AndroidView(
            modifier = modifier.fillMaxWidth().heightIn(min = 58.dp),
            factory = { UserCell(it, theme) },
            update = { view ->
                view.setTextScale(textScale)
                view.bind(friend.displayName, status, friend.isOnlineOrPlaying, false)
                view.setAvatar(bitmap)
                view.setOnClickListener { onClick() }
            }, onRelease = { it.setAvatar(null); it.setOnClickListener(null) }
        )
    }
}

@Composable
internal fun TelegramNativeConversationRow(
    title: String, subtitle: String, timestamp: String, unreadCount: Int, pinned: Boolean,
    avatarUrl: String,
    onClick: () -> Unit,
    onLongClick: ((Offset?) -> Unit)? = null,
    /** Pre-rendered avatar (composite group discs); takes the cell's own avatar slot. */
    avatarOverride: Bitmap? = null,
    modifier: Modifier = Modifier
) {
    val theme = nativeSocialTheme()
    val textScale = LocalDensity.current.fontScale
    val unreadDescription = if (unreadCount > 0) stringResource(R.string.steam_chat_unread_count, unreadCount) else ""
    val pinnedDescription = if (pinned) stringResource(R.string.steam_chat_filter_pinned) else ""
    val description = listOf(title, subtitle, timestamp, unreadDescription, pinnedDescription)
        .filter(String::isNotBlank).joinToString(", ")
    // Keep the loaded picture while a selection bitmap occupies the native slot, so cancelling
    // selection restores it in the same frame instead of briefly rebuilding the placeholder.
    val remoteBitmap = steamAvatarBitmap(avatarUrl)
    key(theme) {
        // Plain fields avoid rebinding/rebuilding the native text layouts for every MOVE event.
        val press = remember { ConversationPress() }
        AndroidView(
            modifier = modifier.fillMaxWidth().heightIn(min = 78.dp),
            factory = { context -> DialogCell(context, theme) },
            update = { cell ->
                cell.setTextScale(textScale)
                cell.setDialog(DialogCell.CustomDialog().apply {
                    name = title; message = subtitle; unread_count = unreadCount; this.pinned = pinned
                }, timestamp)
                cell.contentDescription = description
                cell.setAvatar(avatarOverride ?: remoteBitmap)
                cell.setOnClickListener { onClick() }
                cell.setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { press.active = true; press.position = Offset(event.x, event.y) }
                        MotionEvent.ACTION_MOVE -> press.position = Offset(event.x, event.y)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> press.active = false
                    }
                    false
                }
                cell.setOnLongClickListener(onLongClick?.let { action ->
                    View.OnLongClickListener { action(press.position.takeIf { press.active }); true }
                })
            },
            onRelease = { cell ->
                press.active = false
                cell.setAvatar(null); cell.setOnClickListener(null); cell.setOnLongClickListener(null); cell.setOnTouchListener(null)
            }
        )
    }
}

@Composable
internal fun TelegramNativeProfileActions(message: String, profile: String, copy: String, onMessage: () -> Unit, onProfile: () -> Unit, onCopy: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val actionColor = c.surfaceContainerHigh.toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(94.dp),
        factory = { ProfileActionsView(it, com.monica.telegramchat.TelegramUi.dp(94f)) },
        update = { view ->
            view.setActions(arrayOf(message, profile, copy),
                intArrayOf(com.monica.telegramchat.R.drawable.telegram_filled_profile_message_24, com.monica.telegramchat.R.drawable.telegram_filled_profile_edit_24, com.monica.telegramchat.R.drawable.telegram_msg_copy_filled),
                intArrayOf(com.monica.telegramchat.R.drawable.telegram_outline_profile_message_24, com.monica.telegramchat.R.drawable.telegram_outline_profile_edit_24, com.monica.telegramchat.R.drawable.telegram_msg_copy))
            view.setActionColor(actionColor)
            view.setActionTextColor(c.onSurface.toArgb())
            view.setOnActionClickListener { action, _, _ -> when (action) { 0 -> onMessage(); 1 -> onProfile(); 2 -> onCopy() } }
        }, onRelease = { it.setOnActionClickListener(null) }
    )
}
