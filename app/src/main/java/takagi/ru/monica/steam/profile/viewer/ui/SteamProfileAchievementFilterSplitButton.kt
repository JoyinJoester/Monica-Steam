package takagi.ru.monica.steam.profile.viewer.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassDropdownMenu as DropdownMenu
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.profile.viewer.domain.SteamAchievementComparisonFilter

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SteamProfileAchievementFilterSplitButton(
    selectedFilter: SteamAchievementComparisonFilter,
    onSelectFilter: (SteamAchievementComparisonFilter) -> Unit,
    selfProfile: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "steam_profile_achievement_filter_rotation"
    )
    val availableFilters = if (selfProfile) {
        listOf(
            SteamAchievementComparisonFilter.ALL,
            SteamAchievementComparisonFilter.BOTH,
            SteamAchievementComparisonFilter.NEITHER
        )
    } else {
        SteamAchievementComparisonFilter.entries
    }
    Box(modifier = modifier.wrapContentSize(Alignment.TopStart)) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.TonalLeadingButton(onClick = { expanded = true }) {
                    Icon(
                        Icons.AutoMirrored.Filled.CompareArrows,
                        contentDescription = null,
                        modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize)
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = steamProfileAchievementFilterLabel(selectedFilter, selfProfile),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            trailingButton = {
                SplitButtonDefaults.TonalTrailingButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it }
                ) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.steam_profile_change_achievement_filter),
                        modifier = Modifier
                            .size(SplitButtonDefaults.TrailingIconSize)
                            .rotate(rotation)
                    )
                }
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(220.dp),
            offset = DpOffset(0.dp, 6.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                availableFilters.forEach { filter ->
                    Surface(
                        onClick = {
                            expanded = false
                            onSelectFilter(filter)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (filter == selectedFilter) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        }
                    ) {
                        Text(
                            text = steamProfileAchievementFilterLabel(filter, selfProfile),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun steamProfileAchievementFilterLabel(
    filter: SteamAchievementComparisonFilter,
    selfProfile: Boolean
): String = stringResource(
    when {
        selfProfile && filter == SteamAchievementComparisonFilter.BOTH ->
            R.string.steam_profile_achievement_completed
        selfProfile && filter == SteamAchievementComparisonFilter.NEITHER ->
            R.string.steam_profile_achievement_incomplete
        else -> when (filter) {
        SteamAchievementComparisonFilter.ALL -> R.string.steam_profile_achievement_filter_all
        SteamAchievementComparisonFilter.BOTH -> R.string.steam_profile_achievement_filter_both
        SteamAchievementComparisonFilter.VIEWER_ONLY ->
            R.string.steam_profile_achievement_filter_viewer_only
        SteamAchievementComparisonFilter.TARGET_ONLY ->
            R.string.steam_profile_achievement_filter_target_only
        SteamAchievementComparisonFilter.NEITHER ->
            R.string.steam_profile_achievement_filter_neither
        }
    }
)
