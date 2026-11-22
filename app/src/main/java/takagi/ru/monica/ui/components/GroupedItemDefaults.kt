package takagi.ru.monica.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Shared by adjacent list rows and their swipe surfaces; no enclosing group surface is needed. */
internal object GroupedItemDefaults {
    val Spacing = 2.dp
    val SingleShape = RoundedCornerShape(20.dp)
    private val firstShape = RoundedCornerShape(
        topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp,
    )
    private val middleShape = RoundedCornerShape(4.dp)
    private val lastShape = RoundedCornerShape(
        topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp,
    )

    fun shape(index: Int, count: Int): RoundedCornerShape = when {
        count <= 1 -> SingleShape
        index == 0 -> firstShape
        index == count - 1 -> lastShape
        else -> middleShape
    }
}
