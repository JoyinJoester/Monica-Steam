package takagi.ru.monica.steam.foundation.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import takagi.ru.monica.R
import takagi.ru.monica.ui.LocalReduceAnimations

internal class SteamCardPreviewRequest(
    val origin: Rect?,
    val anchor: SteamCardPreviewAnchor,
    val cardContent: (@Composable (close: () -> Unit, dismiss: () -> Unit, fraction: Float) -> Unit)?,
    val content: @Composable (close: () -> Unit) -> Unit
)

private val LocalSteamCardPreview = staticCompositionLocalOf<((SteamCardPreviewRequest) -> Unit)?> { null }

internal class SteamCardPreviewAnchor {
    private var coordinates: LayoutCoordinates? = null
    var lifted by mutableStateOf(false)
    val modifier = Modifier.onGloballyPositioned { coordinates = it }
        .drawWithContent { if (!lifted) drawContent() }

    fun request(
        cardContent: (@Composable (close: () -> Unit, dismiss: () -> Unit, fraction: Float) -> Unit)?,
        content: @Composable (close: () -> Unit) -> Unit
    ): SteamCardPreviewRequest {
        val source = coordinates?.takeIf { it.isAttached }?.let {
            Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
        }
        return SteamCardPreviewRequest(source, this, cardContent, content)
    }
}

@Composable
internal fun rememberSteamCardPreviewAction(
    fallback: () -> Unit,
    cardContent: (@Composable (close: () -> Unit, dismiss: () -> Unit, fraction: Float) -> Unit)? = null,
    content: @Composable (close: () -> Unit) -> Unit
): Pair<Modifier, () -> Unit> {
    val show = LocalSteamCardPreview.current
    val anchor = remember { SteamCardPreviewAnchor() }
    return anchor.modifier to { if (show == null) fallback() else show(anchor.request(cardContent, content)) }
}

/** The blur applies only to the live screen layer. Preview content is drawn above it. */
@Composable
internal fun SteamCardPreviewHost(
    scopeKey: Any?,
    content: @Composable () -> Unit
) {
    var request by remember(scopeKey) { mutableStateOf<SteamCardPreviewRequest?>(null) }
    var closing by remember(scopeKey) { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    val focus = LocalFocusManager.current
    val reduced = LocalReduceAnimations.current
    val hardwareBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val progress = remember(scopeKey) { Animatable(0f) }
    val active = request
    val dismiss = { if (reduced) request = null else closing = true }
    val show: (SteamCardPreviewRequest) -> Unit = remember(scopeKey, owner, focus) {
        { next ->
            if (request == null && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                focus.clearFocus()
                closing = false
                request = next
            }
        }
    }
    DisposableEffect(owner, scopeKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) request = null
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(active) {
        onDispose { active?.anchor?.lifted = false }
    }
    LaunchedEffect(active, closing, reduced) {
        when {
            active == null -> progress.snapTo(0f)
            reduced -> progress.snapTo(1f)
            closing -> {
                progress.animateTo(0f, tween(if (active.cardContent != null) 300 else 190, easing = FastOutSlowInEasing))
                request = null
            }
            else -> {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(if (active.cardContent != null) 360 else 280, easing = FastOutSlowInEasing))
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().then(
                if (active != null) Modifier.graphicsLayer {
                    val radius = 18.dp.toPx() * if (reduced) 1f else progress.value
                    renderEffect = if (hardwareBlur && radius >= 0.5f) {
                        BlurEffect(radius, radius, TileMode.Clamp)
                    } else null
                }.clearAndSetSemantics { hideFromAccessibility() }
                else Modifier
            )
        ) {
            CompositionLocalProvider(LocalSteamCardPreview provides show, content = content)
        }
        if (active != null) {
            BackHandler(onBack = dismiss)
            val closeLabel = stringResource(R.string.steam_preview_close)
            Box(
                Modifier.matchParentSize()
                    .drawBehind {
                        val opacity = if (hardwareBlur) 0.48f else 0.68f
                        drawRect(Color.Black.copy(alpha = opacity * if (reduced) 1f else progress.value))
                    }
                    .testTag("steam-preview-backdrop")
                    .clickable(remember { MutableInteractionSource() }, indication = null,
                        onClickLabel = closeLabel, onClick = dismiss)
            )
            if (active.cardContent != null) SteamExpandingCardPanel(
                request = active,
                fraction = { if (reduced) 1f else progress.value },
                onDismiss = dismiss,
                onAction = { request = null }
            ) else SteamCardPreviewPanel(
                request = active,
                fraction = { if (reduced) 1f else progress.value },
                onDismiss = dismiss,
                onAction = { request = null }
            )
        }
    }
}

@Composable
private fun SteamExpandingCardPanel(
    request: SteamCardPreviewRequest,
    fraction: () -> Float,
    onDismiss: () -> Unit,
    onAction: () -> Unit
) {
    var windowOffset by remember(request) { mutableStateOf<Offset?>(null) }
    val content = requireNotNull(request.cardContent)
    val paneLabel = stringResource(R.string.steam_preview_open)
    // Measure the destination without placing it. Only the moving card is drawn;
    // its text and artwork are laid out at the current width, never cross-faded copies.
    SubcomposeLayout(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)
            .onGloballyPositioned { windowOffset = it.positionInWindow() }
    ) { constraints ->
        val targetWidth = minOf(constraints.maxWidth, 440.dp.roundToPx())
        val target = subcompose("destination") {
            Box(Modifier.fillMaxWidth().clearAndSetSemantics { }) { content({}, {}, 1f) }
        }.single().measure(Constraints(
            minWidth = targetWidth, maxWidth = targetWidth, maxHeight = constraints.maxHeight
        ))
        val end = Rect(
            Offset((constraints.maxWidth - target.width) / 2f, (constraints.maxHeight - target.height) / 2f),
            Size(target.width.toFloat(), target.height.toFloat())
        )
        val start = request.origin?.takeUnless { it.isEmpty }?.translate(-(windowOffset ?: Offset.Zero)) ?: end
        val progress = fraction().coerceIn(0f, 1f)
        val bounds = lerp(start, end, progress)
        val panel = subcompose("moving-card") {
            Surface(
                modifier = Modifier.testTag("steam-card-preview").semantics { paneTitle = paneLabel }
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = (16f * progress).dp
            ) { content(onAction, onDismiss, progress) }
        }.single().measure(Constraints.fixed(
            bounds.width.roundToInt().coerceAtLeast(1), bounds.height.roundToInt().coerceAtLeast(1)
        ))
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (windowOffset != null) {
                request.anchor.lifted = true
                panel.place(bounds.left.roundToInt(), bounds.top.roundToInt())
            }
        }
    }
}

@Composable
private fun SteamCardPreviewPanel(
    request: SteamCardPreviewRequest,
    fraction: () -> Float,
    onDismiss: () -> Unit,
    onAction: () -> Unit
) {
    var target by remember(request) { mutableStateOf<Rect?>(null) }
    val closeFocus = remember(request) { FocusRequester() }
    val paneLabel = stringResource(R.string.steam_preview_open)
    val closeLabel = stringResource(R.string.steam_preview_close)
    Box(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.widthIn(max = 440.dp).fillMaxWidth().onGloballyPositioned {
            target = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
        }) {
            Surface(
                modifier = Modifier.fillMaxWidth()
                    .graphicsLayer {
                        val lift = fraction()
                        val source = request.origin
                        val end = target
                        alpha = (lift * 3f).coerceIn(0f, 1f)
                        if (source != null && end != null && !source.isEmpty && !end.isEmpty) {
                            val delta = source.center - end.center
                            translationX = delta.x * (1f - lift)
                            translationY = delta.y * (1f - lift)
                            val startScale = (source.width / end.width).coerceIn(0.65f, 1f)
                            scaleX = startScale + (1f - startScale) * lift
                            scaleY = scaleX
                        } else {
                            scaleX = 0.96f + 0.04f * lift
                            scaleY = scaleX
                        }
                    }
                    .testTag("steam-card-preview")
                    .semantics { paneTitle = paneLabel }
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 16.dp
            ) {
                Column {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp, end = 6.dp)
                            .focusRequester(closeFocus)
                    ) { Icon(Icons.Rounded.Close, closeLabel) }
                    Column(
                        Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                            .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) { request.content(onAction) }
                }
            }
        }
    }
    LaunchedEffect(request) { closeFocus.requestFocus() }
}
