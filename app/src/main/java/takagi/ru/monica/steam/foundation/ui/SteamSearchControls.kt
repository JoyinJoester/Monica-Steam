package takagi.ru.monica.steam.foundation.ui

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.monica.telegramchat.TelegramEditText
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.liquidglass.ui.*
import androidx.compose.ui.graphics.RectangleShape

/** The actual TG EditTextBoldCursor handles selection, composing text, IME and accessibility. */
@Composable
internal fun SteamListSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
    autoFocus: Boolean = false,
    trailingContent: @Composable () -> Unit = {},
    showClear: Boolean = true
) {
    val currentChange by rememberUpdatedState(onValueChange)
    val currentSearch by rememberUpdatedState(onSearch)
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val editorHeight = (56f + (density.fontScale - 1f).coerceAtLeast(0f) * 22f).dp
    var editor by remember { mutableStateOf<TelegramEditText?>(null) }
    LaunchedEffect(editor, autoFocus) {
        val view = editor
        if (autoFocus && view != null) {
            withFrameNanos { }
            view.requestFocus()
            (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    Row(modifier.fillMaxWidth().steamGlassSurface(RoundedCornerShape(28.dp), scheme.surfaceContainerHigh, SteamGlassRole.TOOLBAR)
        .padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        AndroidView(
            modifier = Modifier.weight(1f).height(editorHeight).padding(horizontal = 12.dp),
            factory = { context ->
                TelegramEditText(context).apply {
                    background = null
                    setSingleLine(true)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, 0)
                    inputType = InputType.TYPE_CLASS_TEXT
                    imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                    setSelectAllOnFocus(false)
                    // TextView can retain a vertical cursor scroll after a font/height change.
                    // This one-line editor only scrolls horizontally; keep the centered line visible.
                    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                        if (view.scrollY != 0) view.scrollTo(view.scrollX, 0)
                    }
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            if (tag != PROGRAMMATIC_TEXT) currentChange(s?.toString().orEmpty())
                        }
                        override fun afterTextChanged(s: Editable?) {}
                    })
                    setOnEditorActionListener { _, action, _ ->
                        if (action == EditorInfo.IME_ACTION_SEARCH) {
                            currentSearch()
                            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                .hideSoftInputFromWindow(windowToken, 0)
                            clearFocus()
                            true
                        } else false
                    }
                    editor = this
                }
            },
            update = {
                it.hint = placeholder
                it.setTextColor(scheme.onSurface.toArgb())
                it.setHintTextColor(scheme.onSurfaceVariant.toArgb())
                it.setCursorColor(scheme.primary.toArgb())
                it.setTextSize(TypedValue.COMPLEX_UNIT_PX, 16f * density.density * density.fontScale)
                // Never reset matching text: doing so drops a composing region or moves the cursor.
                if (it.text?.toString() != value) {
                    val selection = if (it.text.isNullOrEmpty()) value.length else it.selectionStart.coerceIn(0, value.length)
                    it.tag = PROGRAMMATIC_TEXT
                    try { it.setText(value); it.setSelection(selection) } finally { it.tag = null }
                }
            },
            onRelease = { it.setOnEditorActionListener(null); if (editor === it) editor = null }
        )
        if (showClear && value.isNotEmpty()) IconButton(onClick = { currentChange(""); editor?.requestFocus() }) {
            Icon(Icons.Rounded.Close, stringResource(R.string.steam_chat_close_search), tint = scheme.onSurfaceVariant)
        }
        trailingContent()
    }
}

private const val PROGRAMMATIC_TEXT = "steam-search-text-update"
