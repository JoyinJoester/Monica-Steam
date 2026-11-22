package takagi.ru.monica.steam.library.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import takagi.ru.monica.R
import takagi.ru.monica.steam.library.SteamAchievement
import takagi.ru.monica.steam.library.steamAchievementImageUrls
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassRole
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassSurface
import takagi.ru.monica.steam.profile.SteamRemoteImageCache

/** One expressive achievement row shared by library details and personal statistics. */
@Composable
internal fun AchievementItem(achievement: SteamAchievement, appId: Int = 0, compact: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val concealed = achievement.hidden && !achievement.achieved
    val shape = RoundedCornerShape(20.dp)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
        .then(if (compact) Modifier else Modifier.steamGlassSurface(shape, colors.surfaceContainerLow, SteamGlassRole.PANEL))
        .testTag("achievement_row_${achievement.groupId}_${achievement.apiName}")
        .padding(if (compact) 4.dp else 14.dp).heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SteamAchievementIcon(achievement, appId, concealed)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(if (concealed) stringResource(R.string.steam_achievement_hidden) else achievement.displayName,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                modifier = Modifier.testTag("achievement_title_${achievement.groupId}_${achievement.apiName}"))
            if (!concealed && achievement.description.isNotBlank()) Text(achievement.description,
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            if (achievement.archived) Text(stringResource(R.string.steam_achievement_archived),
                style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            achievement.unlockTimeSeconds?.let {
                Text(stringResource(R.string.steam_library_unlocked_at,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it * 1000L))),
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
        if (achievement.achieved) Icon(Icons.Default.CheckCircle, null, tint = colors.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SteamAchievementIcon(achievement: SteamAchievement, appId: Int, concealed: Boolean) {
    val context = LocalContext.current
    val urls = if (concealed) emptyList() else steamAchievementImageUrls(appId, achievement)
    val cache = remember(context) { SteamRemoteImageCache.get(context.applicationContext) }
    val image by produceState<ImageBitmap?>(null, urls) {
        value = urls.firstNotNullOfOrNull { cache.load(it)?.asImageBitmap() }
    }
    Surface(Modifier.size(56.dp).testTag("achievement_icon_${achievement.groupId}_${achievement.apiName}"),
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        if (!concealed && image != null) Image(requireNotNull(image), contentDescription = achievement.displayName,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.EmojiEvents, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        }
    }
}
