package takagi.ru.monica.steam.foundation.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.profile.SteamRemoteImageCache

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SteamFullscreenImagePage(
    url: String,
    contentDescription: String,
    loadFailedText: String,
    onZoomChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val cache = remember(context) {
        SteamRemoteImageCache.get(context.applicationContext)
    }
    val imageState by produceState<FullscreenImageState>(
        initialValue = FullscreenImageState.Loading,
        key1 = url
    ) {
        value = url
            .takeIf(String::isNotBlank)
            ?.let { cache.load(it)?.asImageBitmap() }
            ?.let(FullscreenImageState::Loaded)
            ?: FullscreenImageState.Failed
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 8.dp, vertical = 84.dp),
        contentAlignment = Alignment.Center
    ) {
        when (val state = imageState) {
            FullscreenImageState.Loading -> LoadingIndicator(
                modifier = Modifier.size(56.dp),
                color = Color.White
            )
            FullscreenImageState.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color.White.copy(alpha = 0.72f)
                )
                Text(
                    text = loadFailedText,
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            is FullscreenImageState.Loaded -> ZoomableSteamImage(
                image = state.image,
                contentDescription = contentDescription,
                onZoomChanged = onZoomChanged
            )
        }
    }
}

@Composable
private fun ZoomableSteamImage(
    image: ImageBitmap,
    contentDescription: String,
    onZoomChanged: (Boolean) -> Unit
) {
    var scale by remember(image) { mutableFloatStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    SideEffect { onZoomChanged(scale > 1f) }
    val transformableState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale <= 1f) Offset.Zero else offset + panChange
    }

    Image(
        bitmap = image,
        contentDescription = contentDescription,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
            .pointerInput(image) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero
                        }
                    }
                )
            }
            .transformable(
                state = transformableState,
                canPan = { scale > 1f },
                lockRotationOnZoomPan = true
            ),
        contentScale = ContentScale.Fit
    )
}

private sealed interface FullscreenImageState {
    data object Loading : FullscreenImageState
    data object Failed : FullscreenImageState
    data class Loaded(val image: ImageBitmap) : FullscreenImageState
}
