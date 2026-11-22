package takagi.ru.monica.steam.friends.chat.info.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassModalBottomSheet as ModalBottomSheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatWallpaperPreset
import takagi.ru.monica.steam.friends.chat.telegram.TelegramChatWallpaper
import takagi.ru.monica.steam.friends.chat.telegram.TelegramMessageSurface
import takagi.ru.monica.steam.friends.chat.telegram.telegramChatColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamChatWallpaperPicker(selected: SteamChatWallpaperPreset, onSelect: (SteamChatWallpaperPreset) -> Unit, onDismiss: () -> Unit) {
    var choice by remember(selected) { mutableStateOf(selected) }
    val colors = telegramChatColors()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.steam_chat_wallpaper_title), style = MaterialTheme.typography.titleLarge)
            Box(Modifier.fillMaxWidth().height(144.dp)) {
                TelegramChatWallpaper(Modifier.matchParentSize(), choice)
                TelegramMessageSurface(outgoing = true, color = colors.outgoing,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp)) {
                    Text(stringResource(R.string.steam_chat_wallpaper_preview), Modifier.padding(horizontal = 18.dp, vertical = 12.dp), color = colors.outgoingText)
                }
            }
            SteamChatWallpaperPreset.entries.forEach { preset ->
                val label = stringResource(when (preset) {
                    SteamChatWallpaperPreset.TELEGRAM_PATTERN -> R.string.steam_chat_wallpaper_pattern
                    SteamChatWallpaperPreset.TELEGRAM_SOFT -> R.string.steam_chat_wallpaper_soft
                    SteamChatWallpaperPreset.THEME_PLAIN -> R.string.steam_chat_wallpaper_plain
                })
                ListItem(headlineContent = { Text(label) }, leadingContent = {
                    RadioButton(selected = choice == preset, onClick = { choice = preset })
                }, modifier = Modifier.clickable { choice = preset })
            }
            Button(onClick = { onSelect(choice) }, modifier = Modifier.align(Alignment.End).padding(bottom = 16.dp)) {
                Text(stringResource(R.string.steam_chat_wallpaper_apply))
            }
        }
    }
}
