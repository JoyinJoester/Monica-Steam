package takagi.ru.monica.steam.workshop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteImage
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.steamDockActionClearance
import takagi.ru.monica.steam.navigation.ui.steamWindowTopPadding
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.ui.common.selection.SelectionActionBar
import takagi.ru.monica.ui.common.selection.SelectionActionBarAction
import takagi.ru.monica.steam.workshop.download.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamWorkshopScreen(
    appId: Int, gameName: String, account: SteamAccount?, source: SteamStorageSource,
    onBack: () -> Unit, modifier: Modifier = Modifier,
    initialShareCode: String? = null, onInitialShareConsumed: () -> Unit = {}
) {
    var activeAppId by rememberSaveable(appId) { mutableIntStateOf(appId) }
    var shareCode by rememberSaveable(appId) { mutableStateOf<String?>(null) }
    var importDialog by rememberSaveable { mutableStateOf(false) }
    val acceptShare: (WorkshopShare) -> Unit = { share ->
        activeAppId = share.appId
        shareCode = WorkshopShareCode.encode(share)
        importDialog = false
    }
    LaunchedEffect(initialShareCode) {
        initialShareCode?.let { code ->
            try { acceptShare(withContext(Dispatchers.Default) { WorkshopShareCode.decode(code) }) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: WorkshopShareCodeException) { importDialog = true }
            onInitialShareConsumed()
        }
    }
    val activeGameName by produceState(if (activeAppId == appId) gameName else "", activeAppId, gameName) {
        value = if (activeAppId == appId) gameName else ""
        if (value.isBlank()) {
            try { value = SteamWorkshopService().gameName(activeAppId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = "" }
        }
    }
    val title = activeGameName.ifBlank { "App $activeAppId" }
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val startBackgroundTask: (() -> Unit) -> Unit = { start ->
        start()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val repository = remember(context) { SteamAccountSourceRepository.get(context.applicationContext) }
    val handle = remember(account?.id, account?.steamId, source) {
        account?.let { repository.sessionHandleForSource(it, source) }
    }
    val scopeKey = "workshop_${handle?.stableKey ?: "guest_${source}_${account?.steamId}"}_$activeAppId"
    val factory = remember(scopeKey) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SteamWorkshopViewModel(
                activeAppId, account, SteamWorkshopService(), handle?.let { captured ->
                    SteamAccountSessionResolver { current, force ->
                        repository.sessionManager.resolve(captured.copy(account = current), force).account
                    }
                }, bulkController = handle?.let {
                    SteamWorkshopBulkTasks.get(context.applicationContext).controller(it, activeAppId, title)
                } ?: InMemoryWorkshopBulkController(activeAppId, null, SteamWorkshopService()),
                presetRepository = SteamWorkshopPresets.get(context.applicationContext)
            ) as T
        }
    }
    val model: SteamWorkshopViewModel = viewModel(key = scopeKey, factory = factory)
    val downloads = remember(context) { WorkshopDownloads.get(context.applicationContext) }
    val downloadRows by downloads.rows.collectAsStateWithLifecycle()
    var showDownloads by rememberSaveable { mutableStateOf(false) }
    var downloadItem by remember { mutableStateOf<WorkshopItem?>(null) }
    var retryDownload by remember { mutableStateOf<WorkshopDownloadRecord?>(null) }
    val state by model.state.collectAsStateWithLifecycle()
    val presetState by model.presets.state.collectAsStateWithLifecycle()
    var menu by remember(scopeKey, state.selected?.id, state.imported?.share, presetState.visible) { mutableStateOf(false) }
    var importDependencies by rememberSaveable(scopeKey, state.imported?.share) { mutableStateOf(false) }
    var confirmBulk by remember(scopeKey) { mutableStateOf<Boolean?>(null) }
    val subscribeIds = remember(state.items, state.selectedIds, state.query.subscribedOnly) { state.selectedBulkIds(true) }
    val unsubscribeIds = remember(state.items, state.selectedIds) { state.selectedBulkIds(false) }
    val selecting = state.selectionMode && state.selected == null && state.imported == null && !presetState.visible
    val listState = rememberSaveable(scopeKey, saver = LazyListState.Saver) { LazyListState() }
    val importListState = rememberSaveable(scopeKey, state.imported?.share, saver = LazyListState.Saver) { LazyListState() }
    var filters by rememberSaveable(scopeKey) { mutableStateOf(false) }
    val clearance = LocalSteamDockContentClearance.current
    DisposableEffect(model) {
        model.start()
        onDispose { model.stopBrowsing() }
    }
    LaunchedEffect(model, account?.accessToken, account?.refreshToken) { model.updateAccount(account) }
    LaunchedEffect(model, shareCode) {
        shareCode?.let { code ->
            val share = withContext(Dispatchers.Default) { WorkshopShareCode.decode(code) }
            model.state.first { it.bulk?.running != true }
            if (model.state.value.imported?.share != share) model.openImport(share)
            shareCode = null
        }
    }
    LaunchedEffect(state.query) { listState.scrollToItem(0) }
    val back = {
        when {
            showDownloads -> showDownloads = false
            state.selected != null -> model.closeDetail()
            state.imported != null -> { model.closeImport(); shareCode = null }
            state.selectionMode -> model.endSelection()
            presetState.visible -> model.presets.hide()
            else -> onBack()
        }
    }
    val accountName = account?.displayName?.ifBlank { account.accountName }.orEmpty()
    val rootPage = state.selected == null && state.imported == null
    val selectedTab = if (presetState.visible) 2 else if (state.query.subscribedOnly) 1 else 0
    val canReadSubscriptions = account != null && state.bulk?.running != true && state.pending.isEmpty() &&
        state.export?.loading != true && !presetState.readingSubscriptions
    val canRefresh = !state.loading && !state.loadingDetail && state.imported?.loading != true &&
        state.pending.isEmpty() && state.bulk?.running != true
    val filtersActive = state.query.tags.isNotEmpty() ||
        state.query.sort != (if (state.query.subscribedOnly) WorkshopSort.UPDATED else WorkshopSort.POPULAR) ||
        (!state.query.subscribedOnly && state.query.sort == WorkshopSort.POPULAR && state.query.days != 7)
    val refresh = { when {
        state.selected != null -> model.open(requireNotNull(state.selected))
        state.imported != null -> model.retryImport()
        presetState.visible -> model.presets.reload()
        else -> model.refresh()
    } }
    BackHandler(onBack = back)

    if (showDownloads) WorkshopDownloadsScreen(downloadRows, { showDownloads = false }, downloads::cancel,
        { record ->
            retryDownload = record
            downloadItem = WorkshopItem(record.itemId, record.appId, record.title, preview = record.preview)
        }, modifier)
    else Box(modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = { Column {
                TopAppBar(
                    modifier = Modifier.steamWindowTopPadding(), windowInsets = WindowInsets(0, 0, 0, 0),
                    title = { Column {
                        Text(if (activeAppId == WALLPAPER_ENGINE_APP_ID && state.selected == null) "Wallpaper Engine" else stringResource(when {
                            state.selected != null -> R.string.workshop_detail_title
                            state.imported != null -> R.string.workshop_import_title
                            else -> R.string.workshop_title
                        }), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (activeAppId != WALLPAPER_ENGINE_APP_ID || state.selected != null)
                            Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } },
                    navigationIcon = { IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.workshop_back))
                    } },
                    actions = {
                        IconButton(onClick = { showDownloads = true }) {
                            Icon(Icons.Default.Download, stringResource(R.string.wallpaper_downloads))
                        }
                        if ((rootPage && presetState.visible) || (state.selected == null && state.imported != null)) {
                            IconButton(onClick = refresh, enabled = if (presetState.visible && rootPage) !presetState.library.loading else canRefresh) {
                                Icon(Icons.Default.Refresh, stringResource(R.string.workshop_refresh))
                            }
                        } else Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.workshop_manage)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                when {
                                    state.selected != null -> DropdownMenuItem(
                                        text = { Text(stringResource(R.string.workshop_open_steam)) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                        onClick = { menu = false; state.selected?.let { uri.openUri(it.url) } })
                                    else -> {
                                        if (!state.selectionMode) DropdownMenuItem(
                                            text = { Text(stringResource(R.string.workshop_select)) },
                                            leadingIcon = { Icon(Icons.Default.Checklist, null) },
                                            enabled = state.bulk?.running != true && state.items.isNotEmpty(),
                                            onClick = { menu = false; model.beginSelection() })
                                        if (state.query.subscribedOnly) {
                                            DropdownMenuItem(text = { Text(stringResource(R.string.workshop_share_all)) },
                                                leadingIcon = { Icon(Icons.Default.Share, null) }, enabled = canReadSubscriptions,
                                                onClick = { menu = false; model.exportSubscriptions() })
                                            DropdownMenuItem(text = { Text(stringResource(R.string.workshop_preset_save_subscriptions)) },
                                                leadingIcon = { Icon(Icons.Default.BookmarkAdd, null) }, enabled = canReadSubscriptions,
                                                onClick = { menu = false; model.presets.fromSubscriptions() })
                                        }
                                        DropdownMenuItem(text = { Text(stringResource(R.string.workshop_import_share)) },
                                            leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                                            enabled = state.bulk?.running != true,
                                            onClick = { menu = false; importDialog = true })
                                    }
                                }
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(stringResource(R.string.workshop_refresh)) },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null) }, enabled = canRefresh,
                                    onClick = { menu = false; refresh() })
                            }
                        }
                    }
                )
                if (!state.bulkDialogVisible) state.bulk?.let { WorkshopBulkBanner(it, model::showBulk) }
                if (rootPage) PrimaryTabRow(selectedTabIndex = selectedTab) {
                    listOf(R.string.workshop_browse, R.string.workshop_subscriptions_tab, R.string.workshop_presets)
                        .forEachIndexed { index, label ->
                            Tab(selected = selectedTab == index, onClick = {
                                menu = false
                                if (selectedTab != index) when (index) {
                                    0 -> { model.presets.hide(); model.query(state.query.copy(subscribedOnly = false)) }
                                    1 -> { model.presets.hide(); model.query(state.query.copy(subscribedOnly = true, sort = WorkshopSort.UPDATED)) }
                                    else -> { shareCode = null; model.showPresets() }
                                }
                            }, selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) })
                        }
                }
            } }
        ) { padding ->
            if (state.selected != null) {
                key(scopeKey, state.selected?.id) {
                    SteamWorkshopDetail(state, model::subscribe, { model.open(requireNotNull(state.selected)) },
                        account?.displayName?.ifBlank { account.accountName }.orEmpty(),
                        model::openDependency,
                        Modifier.padding(padding).fillMaxSize(),
                        onDownload = { retryDownload = null; downloadItem = state.selected })
                }
            } else if (state.imported != null) {
                WorkshopImportPreview(requireNotNull(state.imported), accountName, model::toggleImported, model::selectImported,
                    { dependencies -> startBackgroundTask { model.subscribeImported(false, dependencies) } },
                    model::retryImport, model::open, dependencies = importDependencies,
                    changeDependencies = { importDependencies = it }, listState = importListState,
                    modifier = Modifier.padding(padding), busy = state.bulk?.running == true,
                    savePreset = model::saveImportAsPreset)
            } else if (presetState.visible) {
                WorkshopPresetsPanel(presetState.library,
                    canReadSubscriptions = canReadSubscriptions,
                    create = { model.presets.fromSubscriptions() }, importCode = { importDialog = true },
                    preview = model.presets::preview, share = model::exportPreset, rename = model.presets::rename,
                    replace = { model.presets.fromSubscriptions(it) }, delete = model.presets::delete,
                    retry = model.presets::reload, modifier = Modifier.padding(padding))
            } else Column(Modifier.padding(padding).fillMaxSize()) {
                OutlinedTextField(
                    value = state.query.search, onValueChange = { model.query(state.query.copy(search = it.take(128))) },
                    placeholder = { Text(stringResource(R.string.workshop_search), maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.query.search.isNotEmpty()) IconButton(onClick = { model.query(state.query.copy(search = "")) }) {
                            Icon(Icons.Default.Close, stringResource(R.string.workshop_clear_search))
                        }
                        IconButton(onClick = { filters = true }) {
                            BadgedBox(badge = {
                                if (state.query.tags.isNotEmpty()) Badge { Text(state.query.tags.size.toString()) }
                                else if (filtersActive) Badge()
                            }) {
                                Icon(Icons.Default.Tune, stringResource(if (filtersActive) R.string.workshop_filters_active else R.string.workshop_filters))
                            }
                        }
                    } }, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.workshop_count, if (state.query.subscribedOnly && state.query.search.isNotBlank()) state.items.size else state.total),
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(stringResource(when (state.query.sort) {
                        WorkshopSort.POPULAR -> R.string.workshop_popular
                        WorkshopSort.MOST_SUBSCRIBED -> R.string.workshop_most_subscribed
                        WorkshopSort.NEWEST -> R.string.workshop_newest
                        WorkshopSort.UPDATED -> R.string.workshop_updated
                    }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(state = listState, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = clearance + if (selecting) 80.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.query.subscribedOnly) item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AccountCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.workshop_account, accountName), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.loading) item { WorkshopLoading(stringResource(R.string.workshop_loading)) }
                    if (activeAppId == WALLPAPER_ENGINE_APP_ID && !selecting) {
                        items(state.items.chunked(2), key = { it.first().id }) { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { item -> WallpaperTile(item, { model.open(item) }, Modifier.weight(1f)) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else items(state.items, key = { it.id }) { item -> WorkshopItemCard(item,
                        selecting = state.selectionMode, checked = item.id in state.selectedIds,
                        onSelect = { model.toggleSelection(item.id) },
                        onClick = { if (state.selectionMode) model.toggleSelection(item.id) else model.open(item) }) }
                    if (state.failure != null) item {
                        WorkshopError(requireNotNull(state.failure)) { if (state.hasMore) model.loadMore() else model.refresh() }
                    }
                    if (!state.loading && !state.loadingMore && state.failure == null && state.items.isEmpty()) item {
                        Text(stringResource(if (state.query.subscribedOnly && state.query.search.isBlank() && state.query.tags.isEmpty())
                            R.string.workshop_no_subscriptions else R.string.workshop_no_results), Modifier.padding(vertical = 24.dp))
                    }
                    if (state.loadingMore) item { WorkshopLoading(
                        if (state.query.scansSubscriptions()) stringResource(R.string.workshop_searching_subs, state.scanned)
                        else stringResource(R.string.workshop_loading)) }
                    if (state.hasMore && !state.loadingMore && state.failure == null) item {
                        OutlinedButton(onClick = model::loadMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.workshop_load_more)) }
                    }
                }
            }
        }
        if (selecting) {
            val idle = state.bulk?.running != true && state.pending.isEmpty() && state.export?.loading != true && !presetState.readingSubscriptions
            SelectionActionBar(
                modifier = Modifier.align(Alignment.BottomStart).steamDockActionClearance().padding(16.dp),
                selectedCount = state.selectedIds.size,
                onExit = model::endSelection,
                onSelectAll = model::selectLoaded,
                selectAllContentDescription = stringResource(R.string.workshop_select_loaded),
                exitContentDescription = stringResource(R.string.workshop_clear_selection),
                actions = buildList {
                    if (!state.query.subscribedOnly) add(SelectionActionBarAction(
                        Icons.Default.AddCircleOutline, stringResource(R.string.workshop_bulk_subscribe),
                        enabled = idle && account != null && subscribeIds.isNotEmpty(),
                        onClick = { confirmBulk = true }))
                    add(SelectionActionBarAction(
                        Icons.Default.RemoveCircleOutline, stringResource(R.string.workshop_bulk_unsubscribe),
                        enabled = idle && account != null && unsubscribeIds.isNotEmpty(),
                        onClick = { confirmBulk = false }))
                    add(SelectionActionBarAction(
                        Icons.Default.Share, stringResource(R.string.workshop_share_selected),
                        enabled = idle && state.selectedIds.isNotEmpty(), onClick = model::exportSelected))
                }
            )
        }
    }
    downloadItem?.let { item ->
        WorkshopDownloadDialog(item, handle?.takeUnless { it.account.isTemporary }, retryDownload,
            onDismiss = { downloadItem = null; retryDownload = null },
            onQueued = {
                downloadItem = null; retryDownload = null; showDownloads = true
                startBackgroundTask { }
            })
    }
    if (filters) WorkshopFilters(state.query, state.tags, { filters = false }) {
        filters = false
        model.query(it)
    }
    if (importDialog) WorkshopImportCodeDialog({ importDialog = false }, acceptShare)
    state.export?.let { WorkshopExportDialog(it, title, model::dismissExport, model::exportSubscriptions, model::saveExportAsPreset) }
    presetState.draft?.let { WorkshopPresetDraftDialog(it, model.presets::name, model.presets::save,
        model.presets::retryDraft, model.presets::dismissDraft) }
    presetState.preview?.let { preview -> WorkshopPresetSwitchDialog(preview, accountName,
        apply = { startBackgroundTask(model::applyPreset) }, retry = { model.presets.preview(preview.preset) },
        dismiss = model.presets::dismissPreview) }
    if (state.bulkDialogVisible) state.bulk?.let {
        WorkshopBulkDialog(it, model::stopBulk, { startBackgroundTask(model::retryBulk) }, model::dismissBulk,
            targetLabel = "$title · $accountName")
    }
    confirmBulk?.let { subscribe -> WorkshopBulkConfirmation(subscribe, if (subscribe) subscribeIds.size else unsubscribeIds.size, accountName,
        { confirmBulk = null }, { dependencies ->
            confirmBulk = null
            startBackgroundTask { model.manageSelected(subscribe, dependencies) }
        }) }
}

@Composable
private fun WorkshopItemCard(item: WorkshopItem, selecting: Boolean, checked: Boolean, onSelect: () -> Unit, onClick: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onSelect,
        onLongClickLabel = stringResource(R.string.workshop_select)).semantics {
            if (selecting) { role = Role.Checkbox; toggleableState = ToggleableState(checked) }
        }, colors = CardDefaults.outlinedCardColors(containerColor = if (selecting && checked)
            MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, if (selecting && checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) Checkbox(checked = checked, onCheckedChange = null)
            WorkshopImage(item.preview, Modifier.size(if (selecting) 64.dp else 80.dp).clip(MaterialTheme.shapes.medium))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.author.isNotBlank()) Text(item.author, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.tags.isNotEmpty()) Text(item.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.subscribed == true) Text(stringResource(R.string.workshop_subscribed), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun WorkshopImage(url: String, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, url) {
        value = null
        value = if (url.isNotBlank()) loadSteamRemoteImage(context, url) else null
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, stringResource(R.string.workshop_preview), Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Icon(Icons.Default.Extension, null, Modifier.size(32.dp))
    }
}

@Composable
internal fun WorkshopLoading(label: String) {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun WorkshopError(failure: WorkshopFailure, retry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(workshopFailureResource(failure)), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = retry) { Text(stringResource(R.string.workshop_retry)) }
    }
}

internal fun workshopFailureResource(failure: WorkshopFailure): Int = when (failure) {
            WorkshopFailure.LOGIN -> R.string.workshop_login
            WorkshopFailure.RATE_LIMIT -> R.string.workshop_rate_limit
            WorkshopFailure.UNAVAILABLE -> R.string.workshop_unavailable
            WorkshopFailure.NETWORK -> R.string.workshop_network
            WorkshopFailure.INVALID_RESPONSE -> R.string.workshop_invalid
            WorkshopFailure.UNCONFIRMED -> R.string.workshop_unconfirmed
            WorkshopFailure.BACKGROUND -> R.string.workshop_background_unavailable
            WorkshopFailure.SUBSCRIPTIONS_CHANGED -> R.string.workshop_subscriptions_changed
}
