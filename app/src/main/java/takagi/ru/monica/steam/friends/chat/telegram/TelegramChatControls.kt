package takagi.ru.monica.steam.friends.chat.telegram

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.semantics.clearAndSetSemantics
import takagi.ru.monica.steam.foundation.ui.SteamRefreshActionIcon
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.SteamDockStyle

import android.graphics.drawable.BitmapDrawable
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.monica.telegramchat.TelegramChatHeaderView
import com.monica.telegramchat.TelegramChatComposerView
import com.monica.telegramchat.TelegramPopupLayout
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteImage

internal data class TelegramChatAction(val label: String, val action: () -> Unit, val enabled: Boolean = true)

@Composable
internal fun TelegramChatHeader(
    title: String,
    subtitle: String,
    avatarUrl: String? = null,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    actions: List<TelegramChatAction>,
    sourceView: View?,
    typing: Boolean = false,
    refreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val menuLabel = androidx.compose.ui.res.stringResource(takagi.ru.monica.R.string.more_options)
    val busyLabel = androidx.compose.ui.res.stringResource(takagi.ru.monica.R.string.loading)
    val liquid = LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS
    val colors = telegramChatColors()
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, avatarUrl) {
        value = null
        if (!avatarUrl.isNullOrBlank()) value = loadSteamRemoteImage(context, avatarUrl)
    }
    val drawable = remember(image, title, colors) {
        image?.let { BitmapDrawable(context.resources, it.asAndroidBitmap()) } ?: object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun draw(canvas: android.graphics.Canvas) {
                paint.color = colors.native.accent
                canvas.drawOval(android.graphics.RectF(bounds), paint)
                paint.color = colors.native.accentText
                paint.textAlign = android.graphics.Paint.Align.CENTER
                paint.textSize = bounds.height() * .48f
                canvas.drawText(title.take(1).uppercase(), bounds.exactCenterX(), bounds.exactCenterY() - (paint.ascent() + paint.descent()) / 2, paint)
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
            @Deprecated("Drawable API") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
    }
    Box(modifier.fillMaxWidth()) {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { TelegramChatHeaderView(it) },
        onRelease = { it.setSourceView(null) },
        update = { view ->
            view.setColors(colors.native)
            view.setLiquidStyle(liquid)
            view.setTitle(title)
            view.setSubtitle(subtitle)
            view.setTyping(typing)
            (view.menuAnchor as android.widget.ImageView).imageAlpha = if (refreshing) 0 else 255
            view.menuAnchor.contentDescription = menuLabel
            androidx.core.view.ViewCompat.setStateDescription(view.menuAnchor, if (refreshing) busyLabel else null)
            view.setAvatar(drawable)
            view.setSourceView(sourceView)
            view.setCallbacks(onBack, onInfo) {
                val popup = TelegramPopupLayout(view.context, colors.native)
                popup.setGlassSource(sourceView, liquid)
                actions.forEach { item ->
                    popup.addAction(item.label, 0, colors.native, item.enabled) {
                        view.dismissActionMenu()
                        item.action()
                    }
                }
                view.showActionMenu(popup)
            }
        }
    )
    if (refreshing) Box(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(46.dp).clearAndSetSemantics {},
        contentAlignment = Alignment.Center) {
        SteamRefreshActionIcon(true, Icons.Default.MoreVert, "")
    }
    }
}

@Composable
internal fun TelegramChatInput(
    text: String,
    hint: String,
    canSend: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onEmoji: () -> Unit,
    onAttach: () -> Unit,
    onFocus: () -> Unit,
    sourceView: View?,
    modifier: Modifier = Modifier
) {
    val liquid = LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS
    val colors = telegramChatColors()
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { TelegramChatComposerView(it) },
        onRelease = { it.setSourceView(null) },
        update = { view ->
            view.setColors(colors.native)
            view.setLiquidStyle(liquid)
            view.setHint(hint)
            view.setSourceView(sourceView)
            view.setBotVisible(false) // Steam has no Telegram bot commands.
            view.setTextIfChanged(text)
            view.setSendContent(canSend)
            view.setSendEnabled(canSend)
            val hideKeyboard = {
                view.editText.clearFocus()
                (view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(view.windowToken, 0)
                Unit
            }
            view.setCallbacks(onTextChanged, onSend,
                { hideKeyboard(); onEmoji() }, { hideKeyboard(); onAttach() }, null)
            view.editText.setOnFocusChangeListener { _, focused -> if (focused) onFocus() }
        }
    )
}
