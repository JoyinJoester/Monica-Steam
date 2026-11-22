package takagi.ru.monica.steam.design.telegram

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Used only inside chat surfaces; colours and system font scale remain owned by the host. */
@Composable
internal fun SteamTelegramTheme(content: @Composable () -> Unit) {
    val original = MaterialTheme.typography
    val typography = original.copy(
        headlineLarge = original.headlineLarge.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
        headlineMedium = original.headlineMedium.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
        headlineSmall = original.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
        titleLarge = original.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
        titleMedium = original.titleMedium.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium),
        bodyLarge = original.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
        bodyMedium = original.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = original.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp)
    )
    MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = typography, shapes = shapes, content = content)
}
