package takagi.ru.monica.steam.friends.chat.telegram

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.monica.telegramchat.TelegramPopupLayout
import takagi.ru.monica.steam.friends.chat.actions.ui.MessageAnchoredPositionProvider
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.SteamDockStyle

internal data class TelegramMenuAction(val label: String, val icon: Int, val action: () -> Unit)

/** Composition keeps message anchoring; native Telegram code owns menu rows and motion. */
@Composable
internal fun TelegramActionMenu(actions: List<TelegramMenuAction>, onDismiss: () -> Unit) {
    val colors = telegramChatColors()
    val source = LocalTelegramSourceView.current
    val liquid = LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS
    val native = remember { arrayOfNulls<TelegramPopupLayout>(1) }
    Popup(
        popupPositionProvider = remember { MessageAnchoredPositionProvider() },
        onDismissRequest = { native[0]?.dismiss(onDismiss) ?: onDismiss() },
        properties = PopupProperties(focusable = true)
    ) {
        key(colors.native) { AndroidView(
            modifier = Modifier.width(220.dp),
            factory = { context ->
                TelegramPopupLayout(context, colors.native).apply {
                    native[0] = this
                    actions.forEach { addAction(it.label, it.icon, colors.native, true, it.action) }
                }
            },
            update = { it.setGlassSource(source, liquid) },
            onRelease = { if (native[0] === it) native[0] = null }
        ) }
    }
}
