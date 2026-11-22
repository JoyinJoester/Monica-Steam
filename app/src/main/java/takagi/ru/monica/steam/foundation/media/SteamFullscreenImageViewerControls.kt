package takagi.ru.monica.steam.foundation.media

import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassAvailable
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassSurface
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassRole

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ImageViewerTopControls(
    title: String,
    downloading: Boolean,
    closeDescription: String,
    downloadDescription: String,
    downloadingDescription: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColors = IconButtonDefaults.iconButtonColors(
        contentColor = Color.White,
        disabledContentColor = Color.White.copy(alpha = 0.38f)
    )
    val glass = steamGlassAvailable()
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .then(if (glass) Modifier.steamGlassSurface(shape, Color.Black, SteamGlassRole.PANEL) else Modifier),
        shape = shape,
        color = if (glass) Color.Transparent else Color.Black.copy(alpha = 0.78f),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp),
                colors = iconColors
            ) {
                Icon(Icons.Default.Close, contentDescription = closeDescription)
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = onDownload,
                enabled = !downloading,
                modifier = Modifier.size(48.dp),
                colors = iconColors
            ) {
                if (downloading) {
                    LoadingIndicator(
                        modifier = Modifier
                            .size(28.dp)
                            .semantics { contentDescription = downloadingDescription },
                        color = Color.White
                    )
                } else {
                    Icon(Icons.Default.Download, contentDescription = downloadDescription)
                }
            }
        }
    }
}

@Composable
internal fun ImageViewerPageControls(
    currentIndex: Int,
    imageCount: Int,
    positionText: String,
    previousDescription: String,
    nextDescription: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColors = IconButtonDefaults.iconButtonColors(
        contentColor = Color.White,
        disabledContentColor = Color.White.copy(alpha = 0.28f)
    )
    val glass = steamGlassAvailable()
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = modifier.navigationBarsPadding().padding(16.dp)
            .then(if (glass) Modifier.steamGlassSurface(shape, Color.Black, SteamGlassRole.PANEL) else Modifier),
        shape = shape,
        color = if (glass) Color.Transparent else Color.Black.copy(alpha = 0.78f),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = currentIndex > 0,
                modifier = Modifier.size(48.dp),
                colors = iconColors
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = previousDescription
                )
            }
            Text(
                text = positionText,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(
                onClick = onNext,
                enabled = currentIndex < imageCount - 1,
                modifier = Modifier.size(48.dp),
                colors = iconColors
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = nextDescription
                )
            }
        }
    }
}
