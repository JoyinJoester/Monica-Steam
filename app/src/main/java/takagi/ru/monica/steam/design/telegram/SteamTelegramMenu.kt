package takagi.ru.monica.steam.design.telegram

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.*
import com.monica.telegramchat.TelegramPopupLayout
import takagi.ru.monica.steam.friends.chat.telegram.telegramChatColors
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.SteamDockStyle

internal data class SteamTelegramMenuAction(
    val label: String,
    val icon: Int = 0,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/** Reuses Telegram's packaged popup background, menu rows and cascade/exit animations. */
@Composable
internal fun SteamTelegramMenu(
    actions: List<SteamTelegramMenuAction>,
    onDismiss: () -> Unit,
    positionProvider: PopupPositionProvider? = null
) {
    val colors = telegramChatColors()
    val source = LocalSteamTelegramSourceView.current
    val liquid = LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS
    val reduced = LocalReduceAnimations.current
    val error = MaterialTheme.colorScheme.error.toArgb()
    val native = remember { arrayOfNulls<TelegramPopupLayout>(1) }
    val currentActions by rememberUpdatedState(actions)
    val currentDismiss by rememberUpdatedState(onDismiss)
    val fallback = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width
                    else anchorBounds.left
                return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                    anchorBounds.bottom.coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0)))
            }
        }
    }
    Popup(popupPositionProvider = positionProvider ?: fallback,
        onDismissRequest = { native[0]?.dismiss { currentDismiss() } ?: currentDismiss() },
        properties = PopupProperties(focusable = true)) {
        key(colors.native, actions.map { listOf(it.label, it.icon, it.destructive, it.enabled) }) {
            AndroidView(
                modifier = Modifier.width((LocalConfiguration.current.screenWidthDp - 24).coerceIn(200, 280).dp),
                factory = { context -> TelegramPopupLayout(context, colors.native).apply {
                    native[0] = this
                    setReduceAnimations(reduced)
                    actions.forEachIndexed { index, item ->
                        addAction(item.label, item.icon, colors.native, item.enabled) { currentActions.getOrNull(index)?.onClick?.invoke() }
                        if (item.destructive) {
                            fun tint(view: android.view.View) {
                                if (view is android.widget.TextView) view.setTextColor(error)
                                if (view is android.widget.ImageView) view.setColorFilter(error)
                                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) tint(view.getChildAt(i))
                            }
                            tint(getItemAt(index))
                        }
                    }
                } },
                update = { it.setReduceAnimations(reduced); it.setGlassSource(source, liquid) },
                onRelease = { if (native[0] === it) native[0] = null }
            )
        }
    }
}

