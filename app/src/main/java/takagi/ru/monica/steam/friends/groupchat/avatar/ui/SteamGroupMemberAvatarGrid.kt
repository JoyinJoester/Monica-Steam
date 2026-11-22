package takagi.ru.monica.steam.friends.groupchat.avatar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlin.math.min
import takagi.ru.monica.steam.friends.domain.SteamFriend

/** Uses the same masked bitmap as the native inbox avatar, including its ink placement. */
@Composable
internal fun SteamGroupMemberAvatarGrid(
    members: List<SteamFriend>,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val side = minOf(maxWidth, maxHeight)
        if (side.value.isFinite() && side.value > 0f) {
            val bitmap = rememberSteamGroupAvatarBitmap(members, "", side)
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/** A member's share of the disc: the rectangle it covers plus the sector bounds to clip to. */
internal data class GroupAvatarSlot(
    val bounds: Rect,
    val startAngle: Float,
    val sweepAngle: Float,
    /** Font size as a fraction of the slot's shortest edge. */
    val initialScale: Float,
    val isWholeDisc: Boolean = false
)

/** Number of member tiles actually drawn; more than four is unreadable at avatar sizes. */
internal fun steamGroupAvatarTileCount(memberCount: Int): Int =
    memberCount.coerceIn(0, MAX_GROUP_AVATAR_TILES)

/**
 * Pure geometry: no [Path] is built here so the layout stays unit-testable off-device.
 * `startAngle` is degrees clockwise from 3 o'clock, matching [Path.arcTo].
 */
internal fun steamGroupAvatarSlots(circle: Rect, memberCount: Int): List<GroupAvatarSlot> {
    val count = steamGroupAvatarTileCount(memberCount)
    if (count <= 0 || circle.width <= 0f || circle.height <= 0f) return emptyList()
    val oval = circle.fitSquare()
    val center = oval.center
    val left = oval.left
    val right = oval.right
    val top = oval.top
    val bottom = oval.bottom
    return when (count) {
        1 -> listOf(GroupAvatarSlot(oval, 0f, 360f, 0.40f, isWholeDisc = true))
        2 -> listOf(
            GroupAvatarSlot(Rect(left, top, right, center.y), 180f, 180f, 0.72f),
            GroupAvatarSlot(Rect(left, center.y, right, bottom), 0f, 180f, 0.72f)
        )
        3 -> listOf(
            GroupAvatarSlot(Rect(left, top, right, center.y), 180f, 180f, 0.72f),
            GroupAvatarSlot(Rect(left, center.y, center.x, bottom), 90f, 90f, 0.70f),
            GroupAvatarSlot(Rect(center.x, center.y, right, bottom), 0f, 90f, 0.70f)
        )
        else -> listOf(
            GroupAvatarSlot(Rect(left, top, center.x, center.y), 180f, 90f, 0.70f),
            GroupAvatarSlot(Rect(center.x, top, right, center.y), 270f, 90f, 0.70f),
            GroupAvatarSlot(Rect(left, center.y, center.x, bottom), 90f, 90f, 0.70f),
            GroupAvatarSlot(Rect(center.x, center.y, right, bottom), 0f, 90f, 0.70f)
        )
    }
}

/** Largest centred square inside [this], so the avatar stays circular in non-square slots. */
internal fun Rect.fitSquare(): Rect {
    val side = min(width, height)
    return Rect(
        offset = Offset(center.x - side / 2f, center.y - side / 2f),
        size = Size(side, side)
    )
}

internal fun GroupAvatarSlot.toPath(circle: Rect): Path {
    val oval = circle.fitSquare()
    return Path().apply {
        if (isWholeDisc) {
            addOval(oval)
        } else {
            // Every sector is a wedge from the centre, so one arc plus close() is enough:
            // for halves the two radii collapse onto the diameter and the wedge stays exact.
            moveTo(oval.center.x, oval.center.y)
            arcTo(oval, startAngle, sweepAngle, false)
            close()
        }
    }
}

private const val MAX_GROUP_AVATAR_TILES = 4
