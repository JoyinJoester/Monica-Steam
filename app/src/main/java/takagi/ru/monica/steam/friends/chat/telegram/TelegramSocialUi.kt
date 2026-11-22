package takagi.ru.monica.steam.friends.chat.telegram

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassDropdownMenu as DropdownMenu
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import takagi.ru.monica.steam.foundation.ui.LocalSteamAvatarShape
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.ui.FriendAvatar
import takagi.ru.monica.steam.design.telegram.SteamTelegramTheme
import takagi.ru.monica.steam.design.telegram.SteamTelegramTopBar
import takagi.ru.monica.steam.design.telegram.TelegramSearchField

/** Chat-local typography inherits the application palette and the user's text scale. */
@Composable
internal fun TelegramSocialTheme(content: @Composable () -> Unit) {
    SteamTelegramTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}

@Composable
internal fun TelegramFriendAvatar(friend: SteamFriend, size: Int = 56) {
    CompositionLocalProvider(LocalSteamAvatarShape provides CircleShape) { FriendAvatar(friend, size, showStatus = false) }
}

@Composable
internal fun TelegramSocialDivider(inset: Int = 80) {
    HorizontalDivider(
        modifier = Modifier.padding(start = inset.dp),
        thickness = .5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)
    )
}

@Composable
internal fun TelegramSocialTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    SteamTelegramTopBar(title, modifier, onBack, actions)
}

@Composable
internal fun TelegramSocialSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface
    ) {
    Column {
        val theme = nativeSocialTheme()
        key(theme) {
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { com.monica.telegramchat.upstream.HeaderCell(it, theme) },
                update = { it.setText(title) }
            )
        }
        Column(content = content)
        Spacer(Modifier.height(10.dp))
    }
    }
}

@Composable
internal fun TelegramSocialTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    takagi.ru.monica.steam.foundation.ui.SteamListFilterRow(labels.indices.toList(), selected, onSelect,
        label = { labels[it] }, modifier = modifier)
}

@Composable
internal fun TelegramSocialSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    TelegramSearchField(query, onQueryChange, hint,
        modifier = modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        trailingContent = { trailing?.invoke() })
}

@Composable
internal fun RowScope.TelegramSocialAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(27.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun TelegramContactsFilter(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    takagi.ru.monica.steam.foundation.ui.SteamListFilterRow(labels.indices.toList(), selected, onSelect,
        label = { labels[it] }, fillWidth = false)
}

@Composable
internal fun TelegramSocialInfoRow(
    label: String,
    value: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    multiline: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val theme = nativeSocialTheme()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        key(theme) {
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier.weight(1f),
                factory = { com.monica.telegramchat.upstream.TextDetailSettingsCell(it, theme) },
                update = { view ->
                    view.setTextAndValue(label, value.orEmpty(), false)
                    if (multiline) view.setMultilineDetail(true)
                    view.isEnabled = enabled
                    view.alpha = if (enabled) 1f else .45f
                    view.isClickable = onClick != null
                    view.setOnClickListener(if (onClick == null) null else android.view.View.OnClickListener { if (enabled) onClick() })
                    if (onClick != null) view.background = theme.selector()
                }, onRelease = { it.setOnClickListener(null) }
            )
        }
        trailing?.invoke()
    }
}

/**
 * Settings row with a single centred title. `TextDetailSettingsCell` pins its title to the top of a
 * 64dp box because the second line is reserved for a value, so a title-only row drawn with it looks
 * like text floating above an empty hole. Telegram uses a 52dp centred row for this case.
 */
@Composable
internal fun TelegramSocialTitleRow(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showChevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(
                if (onClick == null) Modifier
                else Modifier.clickable(enabled = enabled) { onClick() }
            )
            .padding(start = 21.dp, end = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Values sit before the chevron, the way Telegram prints a count next to its arrow.
        trailing?.invoke()
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
