package takagi.ru.monica.steam.space.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassRole
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassSurface
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.LocalSteamAvatarShape
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.ui.FriendAvatar

internal val SteamSpaceHeaderCollapsedFraction = SemanticsPropertyKey<Float>("SteamSpaceHeaderCollapsedFraction")
private var SemanticsPropertyReceiver.spaceHeaderCollapsedFraction by SteamSpaceHeaderCollapsedFraction

/** The same measured row and avatar composition stay mounted from expanded to compact. */
@Composable
internal fun SteamSpacePlayingHeader(
    friends: List<SteamFriend>,
    loading: Boolean,
    refreshing: Boolean,
    failure: Boolean,
    stale: Boolean,
    onFriendClick: (SteamFriend) -> Unit,
    behavior: SteamSpaceHeaderScrollBehavior,
    modifier: Modifier = Modifier
) {
    if (friends.isEmpty()) return
    val progress = behavior.collapsedFraction
    val rowState = rememberLazyListState()
    val density = LocalDensity.current
    val collapsedRowPx = with(density) { 56.dp.roundToPx() }
    val slotWidth = (88f - 40f * progress).dp
    val avatarSize = (64f - 24f * progress).toInt()
    val textModifier = Modifier.graphicsLayer { alpha = 1f - progress }
        .then(if (progress >= 1f) Modifier.clearAndSetSemantics { } else Modifier)
    Surface(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp, bottom = 8.dp)
        .steamGlassSurface(RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceContainer,
            SteamGlassRole.FILTER), shape = RoundedCornerShape(28.dp), color = androidx.compose.ui.graphics.Color.Transparent) {
        Layout(
            // The parent clip bounds both drawing and hit testing of the naturally tall row;
            // hidden labels cannot intercept taps in the feed below the compact header.
            modifier = Modifier.fillMaxWidth().clipToBounds().testTag("steam-space-playing-header")
                .semantics { spaceHeaderCollapsedFraction = progress },
            content = {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("steam-space-playing-expanded-text").then(textModifier)) {
                    Text(stringResource(R.string.steam_space_playing), style = MaterialTheme.typography.titleMedium)
                    if (stale) HeaderCaption(stringResource(R.string.steam_space_last_known))
                    if (loading || refreshing) HeaderCaption(stringResource(R.string.steam_space_presence_loading))
                    else if (failure) HeaderCaption(stringResource(R.string.steam_space_presence_unavailable))
                }
                LazyRow(
                    state = rowState,
                    modifier = Modifier.testTag("steam-space-playing-row"),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy((12f - 8f * progress).dp)
                ) {
                    items(friends, key = { it.steamId }) { friend ->
                        val gameName = friend.gameName.ifBlank { stringResource(R.string.steam_space_game_unknown) }
                        val activityDescription = if (stale) stringResource(R.string.steam_space_last_known) + ": " + gameName
                            else stringResource(R.string.steam_space_playing_game, gameName)
                        val description = friend.displayName + ", " + activityDescription
                        Column(
                            modifier = Modifier.width(slotWidth).clip(RoundedCornerShape(20.dp))
                                .testTag("steam-space-playing-target-${friend.steamId}")
                                .semantics(mergeDescendants = true) { contentDescription = description }
                                .clickable(role = Role.Button) { onFriendClick(friend) }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.height(78.dp), contentAlignment = Alignment.TopCenter) {
                                Box(Modifier.testTag("steam-space-playing-avatar-${friend.steamId}")
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    .padding((5f - 3f * progress).dp)) {
                                    CompositionLocalProvider(LocalSteamAvatarShape provides CircleShape) {
                                        FriendAvatar(friend, size = avatarSize, showStatus = false)
                                    }
                                }
                            }
                            Column(textModifier.requiredWidth(88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(Modifier.height(8.dp))
                                Text(friend.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge)
                                Text(gameName, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        ) { measurables, constraints ->
            val loose = constraints.copy(minHeight = 0)
            val heading = measurables[0].measure(loose)
            // Measuring the full natural content keeps font-scale and status changes in the range;
            // only this parent owns the visible height. There is no second height animation.
            val row = measurables[1].measure(loose.copy(maxHeight = Constraints.Infinity))
            val expanded = heading.height + row.height
            behavior.updateRange(expanded, collapsedRowPx)
            val fraction = behavior.collapsedFraction
            val visibleHeading = (heading.height * (1f - fraction)).roundToInt()
            val height = (expanded - behavior.collapseRangePx * fraction).roundToInt()
            layout(constraints.maxWidth, constraints.constrainHeight(height)) {
                heading.placeRelative(0, visibleHeading - heading.height)
                row.placeRelative(0, visibleHeading)
            }
        }
    }
}

@Composable
private fun HeaderCaption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
