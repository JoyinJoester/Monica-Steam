package takagi.ru.monica.steam.design.telegram

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

@Composable
internal fun SteamTelegramTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp)
        .then(if (steamGlassAvailable()) Modifier.steamGlassSurface(RectangleShape,
            MaterialTheme.colorScheme.surface, SteamGlassRole.TOOLBAR) else Modifier)
        .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
        }
        Text(title, Modifier.weight(1f).padding(start = if (onBack == null) 8.dp else 4.dp, end = 8.dp),
            style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/** Compatibility entry; all list and modal searches share the library field. */
@Composable
internal fun TelegramSearchField(
    value: String, onValueChange: (String) -> Unit, placeholder: String,
    modifier: Modifier = Modifier, onSearch: () -> Unit = {}, autoFocus: Boolean = false,
    trailingContent: @Composable () -> Unit = {}
) = takagi.ru.monica.steam.foundation.ui.SteamListSearchField(
    value, onValueChange, placeholder, modifier, onSearch, autoFocus, trailingContent)
