package takagi.ru.monica.steam.library.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.SteamListFilterRow
import takagi.ru.monica.steam.library.SteamAchievementSet
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassHost

private data class AchievementSetChoice(val id: Long?)

/** Collection progress and completion status use independent shared-material controls. */
@Composable
internal fun SteamAchievementSetSelector(
    sets: List<SteamAchievementSet>, selectedGroupId: Long?, onSelect: (Long?) -> Unit,
    progress: (SteamAchievementSet) -> Pair<Int, Int>, modifier: Modifier = Modifier
) {
    if (sets.isEmpty()) return
    val choices = remember(sets) { listOf(AchievementSetChoice(null)) + sets.map { AchievementSetChoice(it.groupId) } }
    val selected = choices.firstOrNull { it.id == selectedGroupId } ?: choices.first()
    val current = sets.firstOrNull { it.groupId == selected.id }
    SteamGlassHost(modifier.fillMaxWidth().testTag("achievement-sets"),
        backdropColor = MaterialTheme.colorScheme.background, content = {}, overlay = {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.steam_achievement_sets),
                    Modifier.padding(horizontal = 8.dp).testTag("achievement-sets-title"),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(14.dp))
                SteamListFilterRow(choices, selected, { onSelect(it.id) }, label = { choice ->
                    val set = sets.firstOrNull { it.groupId == choice.id }
                    if (set == null) stringResource(R.string.steam_achievement_sets_all) else {
                        val name = set.name.ifBlank {
                            if (set.groupId == 0L) stringResource(R.string.steam_achievement_set_base)
                            else stringResource(R.string.steam_achievement_set_unnamed, set.groupId)
                        }
                        val (completed, total) = progress(set)
                        "$name · ${stringResource(R.string.steam_achievement_set_progress, completed, total)}"
                    }
                }, floatingSelection = true, fillWidth = false, modifier = Modifier.testTag("achievement-set-filters"))
                current?.let { set ->
                    val labels = listOfNotNull(
                        if (set.isCompleted == true) stringResource(R.string.steam_achievement_set_complete)
                        else if (set.isAchievable == false) stringResource(R.string.steam_achievement_set_unavailable) else null,
                        if (set.archived) stringResource(R.string.steam_achievement_set_archived)
                        else if (set.dlcAppId != null) stringResource(R.string.steam_achievement_set_dlc) else null)
                    if (labels.isNotEmpty()) Text(labels.joinToString(" · "), Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        })
}
