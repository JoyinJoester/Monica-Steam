package takagi.ru.monica.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import takagi.ru.monica.R

/**
 * Google Sans Flex instances used for the expressive Steam account data
 * hierarchy. CJK text falls back to the platform font automatically.
 */
val GoogleSansFlexFontFamily = FontFamily(
    Font(R.font.google_sans_flex_regular, weight = FontWeight.Normal),
    Font(R.font.google_sans_flex_metric, weight = FontWeight.Bold),
    Font(R.font.google_sans_flex_display, weight = FontWeight.ExtraBold)
)

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)
