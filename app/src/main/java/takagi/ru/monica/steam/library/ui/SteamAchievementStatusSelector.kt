package takagi.ru.monica.steam.library.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.SteamListFilterRow
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassHost

@Composable
internal fun SteamAchievementStatusSelector(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SteamGlassHost(modifier.fillMaxWidth(), backdropColor = MaterialTheme.colorScheme.background, content = {}, overlay = {
        SteamListFilterRow(listOf(0, 1, 2), selected, onSelect, label = {
            stringResource(when (it) {
                1 -> R.string.steam_game_stats_completed
                2 -> R.string.steam_game_stats_incomplete
                else -> R.string.steam_game_stats_all
            })
        }, floatingSelection = true, fillWidth = false, modifier = Modifier.testTag("achievement-status-filters"))
    })
}
