package takagi.ru.monica.steam.navigation.liquidglass.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamGlassModalBottomSheet(
    onDismissRequest: () -> Unit, modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true, shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor), tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit
) {
    val source = LocalSteamPopupBackdrop.current
    val active = source != null && steamGlassSupported()
    ModalBottomSheet(onDismissRequest,
        modifier,
        sheetState, sheetMaxWidth, sheetGesturesEnabled, shape,
        if (active) Color.Transparent else containerColor, contentColor,
        if (active) 0.dp else tonalElevation, scrimColor, if (active) null else dragHandle,
        { if (active) WindowInsets(0) else contentWindowInsets() }, properties) {
        if (active) {
            // Material3 offsets the sheet inside its modifier chain. A clipped backdrop
            // on the public modifier would clip the translated sheet at its old position.
            // The floating panel lives inside Material's translated sheet, so its
            // rounded lower edge and inset move with drag / IME / dismiss as one unit.
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 10.dp)) {
                Column(Modifier.fillMaxWidth().steamGlassSurface(RoundedCornerShape(32.dp), containerColor,
                    SteamGlassRole.PANEL, source).windowInsetsPadding(contentWindowInsets())) {
                    if (dragHandle != null) GlassSheetHandle(sheetState, sheetGesturesEnabled,
                        onDismissRequest, dragHandle)
                    content()
                }
            }
        } else content()
    }
}

/** Keep the standard handle's click and accessibility actions inside the moving material. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.GlassSheetHandle(
    state: SheetState, gesturesEnabled: Boolean, onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val dismissLabel = stringResource(androidx.compose.material3.R.string.m3c_bottom_sheet_dismiss_description)
    val expandLabel = stringResource(androidx.compose.material3.R.string.m3c_bottom_sheet_expand_description)
    val collapseLabel = stringResource(androidx.compose.material3.R.string.m3c_bottom_sheet_collapse_description)
    fun dismissSheet() { scope.launch { state.hide(); if (!state.isVisible) onDismiss() } }
    Box(Modifier.align(Alignment.CenterHorizontally).clickable {
        when (state.currentValue) {
            SheetValue.Expanded -> dismissSheet()
            SheetValue.PartiallyExpanded -> scope.launch { state.expand() }
            else -> scope.launch { state.show() }
        }
    }.semantics(mergeDescendants = true) {
        if (gesturesEnabled) {
            dismiss(dismissLabel) { dismissSheet(); true }
            if (state.currentValue == SheetValue.PartiallyExpanded) {
                expand(expandLabel) { scope.launch { state.expand() }; true }
            } else if (state.hasPartiallyExpandedState) {
                collapse(collapseLabel) { scope.launch { state.partialExpand() }; true }
            }
        }
    }) { content() }
}

@Composable
internal fun SteamGlassModalDrawerSheet(
    modifier: Modifier = Modifier, drawerShape: Shape = DrawerDefaults.shape,
    drawerContainerColor: Color = DrawerDefaults.modalContainerColor,
    drawerContentColor: Color = contentColorFor(drawerContainerColor),
    drawerTonalElevation: Dp = DrawerDefaults.ModalDrawerElevation,
    windowInsets: WindowInsets = DrawerDefaults.windowInsets,
    content: @Composable ColumnScope.() -> Unit
) {
    val source = LocalSteamPopupBackdrop.current
    val active = source != null && steamGlassSupported()
    ModalDrawerSheet(modifier.then(if (active) Modifier.steamGlassSurface(drawerShape,
        drawerContainerColor, SteamGlassRole.PANEL, source) else Modifier), drawerShape,
        if (active) Color.Transparent else drawerContainerColor, drawerContentColor,
        if (active) 0.dp else drawerTonalElevation, windowInsets, content)
}

@Composable
internal fun SteamGlassAlertDialog(
    onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null, shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    val source = LocalSteamPopupBackdrop.current
    val active = source != null && steamGlassSupported()
    AlertDialog(onDismissRequest, confirmButton,
        modifier.then(if (active) Modifier.steamGlassSurface(shape, containerColor,
            SteamGlassRole.PANEL, source) else Modifier),
        dismissButton, icon, title, text, shape,
        if (active) Color.Transparent else containerColor,
        iconContentColor, titleContentColor, textContentColor,
        if (active) 0.dp else tonalElevation, properties)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamGlassTopAppBar(
    title: @Composable () -> Unit, modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    contentPadding: PaddingValues = TopAppBarDefaults.ContentPadding
) {
    val active = steamGlassAvailable()
    val materialColor = colors.containerColor.let {
        if (it == Color.Unspecified || it == Color.Transparent) MaterialTheme.colorScheme.surface else it
    }
    TopAppBar(title = title,
        modifier = modifier.then(if (active) Modifier.steamGlassSurface(RectangleShape,
            materialColor, SteamGlassRole.TOOLBAR) else Modifier),
        navigationIcon = navigationIcon, actions = actions, expandedHeight = expandedHeight,
        windowInsets = windowInsets, scrollBehavior = scrollBehavior, contentPadding = contentPadding,
        colors = if (active) colors.copy(containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent) else colors)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamGlassCenterAlignedTopAppBar(
    title: @Composable () -> Unit, modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    contentPadding: PaddingValues = TopAppBarDefaults.ContentPadding
) {
    val active = steamGlassAvailable()
    val materialColor = colors.containerColor.let {
        if (it == Color.Unspecified || it == Color.Transparent) MaterialTheme.colorScheme.surface else it
    }
    CenterAlignedTopAppBar(title = title,
        modifier = modifier.then(if (active) Modifier.steamGlassSurface(RectangleShape,
            materialColor, SteamGlassRole.TOOLBAR) else Modifier),
        navigationIcon = navigationIcon, actions = actions, expandedHeight = expandedHeight,
        windowInsets = windowInsets, scrollBehavior = scrollBehavior, contentPadding = contentPadding,
        colors = if (active) colors.copy(containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent) else colors)
}

@Composable
internal fun SteamGlassFloatingActionButton(
    onClick: () -> Unit, modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.shape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    val active = steamGlassAvailable() && containerColor != Color.Transparent
    FloatingActionButton(onClick, modifier.then(if (active) Modifier.steamGlassSurface(shape, containerColor) else Modifier),
        shape, if (active) Color.Transparent else containerColor, contentColor,
        if (active) FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp) else elevation,
        interactionSource, content)
}

@Composable
internal fun SteamGlassExtendedFloatingActionButton(
    text: @Composable () -> Unit, icon: @Composable () -> Unit, onClick: () -> Unit,
    modifier: Modifier = Modifier, expanded: Boolean = true,
    shape: Shape = FloatingActionButtonDefaults.extendedFabShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null
) {
    val active = steamGlassAvailable() && containerColor != Color.Transparent
    ExtendedFloatingActionButton(text, icon, onClick,
        modifier.then(if (active) Modifier.steamGlassSurface(shape, containerColor) else Modifier),
        expanded, shape, if (active) Color.Transparent else containerColor, contentColor,
        if (active) FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp) else elevation, interactionSource)
}

@Composable
internal fun SteamGlassExtendedFloatingActionButton(
    onClick: () -> Unit, modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.extendedFabShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val active = steamGlassAvailable() && containerColor != Color.Transparent
    ExtendedFloatingActionButton(onClick,
        modifier.then(if (active) Modifier.steamGlassSurface(shape, containerColor) else Modifier),
        shape, if (active) Color.Transparent else containerColor, contentColor,
        if (active) FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp) else elevation,
        interactionSource, content)
}

@Composable
internal fun SteamGlassDropdownMenu(
    expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp), scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true), shape: Shape = MenuDefaults.shape,
    containerColor: Color = MenuDefaults.containerColor,
    tonalElevation: Dp = MenuDefaults.TonalElevation, shadowElevation: Dp = MenuDefaults.ShadowElevation,
    border: BorderStroke? = null, content: @Composable ColumnScope.() -> Unit
) {
    val source = LocalSteamPopupBackdrop.current
    val active = source != null && steamGlassSupported()
    DropdownMenu(expanded, onDismissRequest,
        modifier.then(if (active) Modifier.steamGlassSurface(shape, containerColor,
            SteamGlassRole.PANEL, source) else Modifier),
        offset, scrollState, properties, shape, if (active) Color.Transparent else containerColor,
        if (active) 0.dp else tonalElevation, shadowElevation, border, content)
}
