package takagi.ru.monica.steam.friends.chat.telegram

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import takagi.ru.monica.R
import takagi.ru.monica.ui.components.ExpressiveTopBar

/** Searchable social destinations use the same headline/search header as the library. */
@Composable
internal fun TelegramSocialSearchTopBar(
    title: String, searchQuery: String, onSearchQueryChange: (String) -> Unit,
    isSearchExpanded: Boolean, onSearchExpandedChange: (Boolean) -> Unit,
    searchHint: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null,
    onSearch: () -> Unit = {},
    beforeSearch: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val focus = LocalFocusManager.current
    ExpressiveTopBar(title, searchQuery, onSearchQueryChange, isSearchExpanded,
        onSearchExpandedChange, searchHint, modifier,
        navigationIcon = onBack?.let { back -> {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        } }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(); focus.clearFocus() }),
        actions = {
            beforeSearch()
            IconButton(onClick = { onSearchExpandedChange(true) }) {
                Icon(Icons.Rounded.Search, stringResource(R.string.topbar_search_hint))
            }
            actions()
        })
}
