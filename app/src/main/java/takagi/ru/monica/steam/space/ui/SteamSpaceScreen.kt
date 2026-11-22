package takagi.ru.monica.steam.space.ui

import takagi.ru.monica.steam.foundation.media.steamImageThumbnail
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassFloatingActionButton
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.SteamDockStyle

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassModalBottomSheet as ModalBottomSheet

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.SportsEsports
import kotlinx.coroutines.launch
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassSurface
import takagi.ru.monica.steam.store.ui.gallery.SteamStoreScreenshotViewer
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import kotlinx.coroutines.delay
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.LocalSteamAvatarShape
import takagi.ru.monica.steam.foundation.ui.SteamCardPreviewHost
import takagi.ru.monica.steam.foundation.ui.rememberSteamCardPreviewAction
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteImage
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.presentation.SteamFriendsUiState
import takagi.ru.monica.steam.friends.ui.FriendAvatar
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.friends.chat.gameinvite.domain.steamGameInviteHeaderUrl
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRemoteImage
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRemoteImageMode
import takagi.ru.monica.steam.navigation.ui.steamWindowTopPadding
import takagi.ru.monica.steam.space.domain.*
import takagi.ru.monica.steam.space.presentation.*

/** All content is supplied by Steam; empty/error states never synthesize activity. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamSpaceScreen(
    state: SteamSpaceUiState,
    friendsState: SteamFriendsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenOriginal: (String) -> Unit,
    onOpenGame: (Int) -> Unit,
    onMessage: (String) -> Unit,
    onManageAccounts: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    hiddenKinds: Set<SteamSpaceEventKind> = emptySet(),
    onSetKindVisible: (SteamSpaceEventKind, Boolean) -> Unit = { _, _ -> },
    onShowAllKinds: () -> Unit = {},
    contentSettingsSaving: Boolean = false,
    contentSettingsFailed: Boolean = false,
    onRetryContentSettings: () -> Unit = {},
    onPublish: (() -> Unit)? = null
) {
    var selectedFriendId by remember(state.accountId, state.accountSteamId) { mutableStateOf<String?>(null) }
    var friendRequiresPlaying by remember(state.accountId, state.accountSteamId) { mutableStateOf(true) }
    var showContentSettings by remember(state.accountId, state.accountSteamId) { mutableStateOf(false) }
    val visibleItems = remember(state.items, hiddenKinds) { state.items.filter { it.kind !in hiddenKinds } }
    val zone = ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(visibleItems, zone) {
        visibleItems.map { item ->
            item.occurredAtEpochSeconds.takeIf { it > 0 }?.let { Instant.ofEpochSecond(it).atZone(zone).toLocalDate() }
        }
    }
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val friends = friendsState.snapshot?.acceptedFriends.orEmpty()
    val friendById = remember(friends) { friends.associateBy { it.steamId } }
    val playing = remember(friends) { friends.filter { it.isPlaying } }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val stalePresence = friendsState.fromCache || friendsState.failure != null || friendsState.refreshing ||
        (friendsState.snapshot?.fetchedAt ?: 0L) < now - 120_000L
    val selectedFriend = friends.firstOrNull { it.steamId == selectedFriendId && (!friendRequiresPlaying || it.isPlaying) }
    LaunchedEffect(selectedFriend) { if (selectedFriend == null) selectedFriendId = null }
    val paginationNeedsLogin = state.loadMoreFailure == SteamSpaceFailureReason.ACCOUNT_REQUIRED ||
        state.loadMoreFailure == SteamSpaceFailureReason.SESSION_REQUIRED
    val feedState = key(state.accountId, state.accountSteamId) { rememberLazyListState() }
    val scope = rememberCoroutineScope()
    val reduced = LocalReduceAnimations.current
    var observedIds by remember(state.accountId, state.accountSteamId) { mutableStateOf<Set<String>?>(null) }
    var observedNewest by remember(state.accountId, state.accountSteamId) { mutableLongStateOf(0L) }
    var observedKinds by remember(state.accountId, state.accountSteamId) { mutableStateOf(hiddenKinds) }
    var pendingIds by remember(state.accountId, state.accountSteamId) { mutableStateOf(emptySet<String>()) }
    var imageGallery by remember(state.accountId, state.accountSteamId) { mutableStateOf<Pair<List<String>, Int>?>(null) }
    LaunchedEffect(visibleItems) {
        val current = visibleItems.mapTo(linkedSetOf()) { it.id }
        val old = observedIds
        // Pagination adds older events; only items ahead of the previous head are new.
        val arrivals = if (old == null || observedKinds != hiddenKinds) emptySet() else visibleItems
            .filter { it.id !in old && it.occurredAtEpochSeconds >= observedNewest }
            .mapTo(linkedSetOf()) { it.id }
        pendingIds = if (observedKinds != hiddenKinds || (feedState.firstVisibleItemIndex == 0 && feedState.firstVisibleItemScrollOffset == 0))
            emptySet() else (pendingIds + arrivals).intersect(current)
        observedIds = current
        observedNewest = visibleItems.maxOfOrNull { it.occurredAtEpochSeconds } ?: 0L
        observedKinds = hiddenKinds
    }
    LaunchedEffect(feedState) {
        snapshotFlow { feedState.firstVisibleItemIndex == 0 && feedState.firstVisibleItemScrollOffset == 0 }
            .collect { if (it) pendingIds = emptySet() }
    }
    val headerScroll = rememberSteamSpaceHeaderScrollBehavior(feedState, enabled = playing.isNotEmpty())
    BackHandler {
        when {
            showContentSettings -> showContentSettings = false
            selectedFriend != null -> selectedFriendId = null
            else -> onBack()
        }
    }
    SteamCardPreviewHost(state.accountId to state.accountSteamId) {
        val pullState = rememberPullToRefreshState()
        SteamSpaceChrome(
            modifier = modifier,
            header = {
                Column {
                    SteamSpaceTopBar(
                        filtered = hiddenKinds.isNotEmpty(), busy = state.loading || state.refreshing,
                        onBack = onBack, onFilters = { showContentSettings = true }, onRefresh = onRefresh
                    )
                    SteamSpacePlayingHeader(
                        friends = playing, loading = friendsState.loading, refreshing = friendsState.refreshing,
                        failure = friendsState.failure != null, stale = stalePresence,
                        onFriendClick = { friendRequiresPlaying = true; selectedFriendId = it.steamId }, behavior = headerScroll
                    )
                }
            },
            overlay = { headerClearance ->
                if (onPublish != null) SteamGlassFloatingActionButton(
                    onClick = onPublish,
                    shape = if (LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS) CircleShape else RoundedCornerShape(16.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = LocalSteamDockContentClearance.current + 16.dp)
                        .size(56.dp).testTag("steam-space-publish")
                ) { Icon(Icons.Rounded.Edit, stringResource(R.string.steam_space_publish)) }
                AnimatedVisibility(pendingIds.isNotEmpty(),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = headerClearance + 8.dp),
                    enter = if (reduced) androidx.compose.animation.EnterTransition.None else fadeIn() + slideInVertically { -it / 2 },
                    exit = if (reduced) androidx.compose.animation.ExitTransition.None else fadeOut() + slideOutVertically { -it / 2 }) {
                    Surface(shape = CircleShape, color = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("steam-space-new-activity")
                            .steamGlassSurface(CircleShape, MaterialTheme.colorScheme.primaryContainer)
                            .clickable(role = Role.Button) {
                                scope.launch {
                                    if (reduced) feedState.scrollToItem(0) else feedState.animateScrollToItem(0)
                                    pendingIds = emptySet()
                                }
                            }) {
                        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.steam_space_new_activity, pendingIds.size), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        ) { headerClearance ->
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, state = pullState,
                modifier = Modifier.fillMaxSize(), indicator = {}) {
                LazyColumn(
                    state = feedState,
                    modifier = Modifier.fillMaxSize().nestedScroll(headerScroll.nestedScrollConnection).testTag("steam-space-feed"),
                    contentPadding = PaddingValues(top = headerClearance,
                        bottom = LocalSteamDockContentClearance.current + if (onPublish != null) 88.dp else 24.dp)
                ) {
                    item("feed-heading") {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(stringResource(R.string.steam_space_recent), Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            if (state.stale) { Spacer(Modifier.height(8.dp)); Caption(stringResource(R.string.steam_space_previous)) }
                            if (contentSettingsFailed) Text(stringResource(R.string.steam_space_content_settings_failed),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (state.loading) item("loading") {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.height(12.dp))
                            Caption(stringResource(R.string.steam_space_loading))
                        }
                    }
                    state.failure?.let { failure -> item("failure") {
                        val accountFailure = failure == SteamSpaceFailureReason.ACCOUNT_REQUIRED || failure == SteamSpaceFailureReason.SESSION_REQUIRED
                        SpaceNotice(stringResource(failure.messageResource()), onRetry = if (accountFailure && onManageAccounts != null) onManageAccounts else onRefresh,
                            actionLabel = if (accountFailure && onManageAccounts != null) stringResource(R.string.steam_add_method_login) else null)
                    } }
                    if (state.loaded && !state.loading && state.failure == null && !state.partial && state.items.isEmpty()) item("empty") {
                        SpaceNotice(stringResource(R.string.steam_space_empty))
                    }
                    if (state.items.isNotEmpty() && visibleItems.isEmpty()) item("filtered-empty") {
                        SpaceNotice(stringResource(R.string.steam_space_content_no_match),
                            onRetry = { showContentSettings = true }, actionLabel = stringResource(R.string.steam_space_content_settings))
                    }
                    itemsIndexed(visibleItems, key = { _, event -> event.id }) { index, event ->
                        Column(
                            Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null,
                                placementSpec = if (reduced) null else androidx.compose.animation.core.tween(180))
                                .padding(horizontal = 12.dp).padding(bottom = 8.dp)
                        ) {
                            val date = dates[index]
                            if (date != null && (index == 0 || dates[index - 1] != date)) {
                                Text(dateFormat.format(date), Modifier.padding(start = 6.dp, top = 12.dp, bottom = 10.dp),
                                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SpaceEventCard(
                                event = event,
                                friend = friendById[event.actorSteamId],
                                onOpen = onOpenOriginal,
                                onOpenGame = onOpenGame,
                                onActor = {
                                    if (event.actorSteamId in friendById) {
                                        friendRequiresPlaying = false
                                        selectedFriendId = event.actorSteamId
                                    }
                                },
                                onImage = { urls, imageIndex -> imageGallery = urls to imageIndex }
                            )
                        }
                    }
                    if (state.partial) item("partial") {
                        SpaceNotice(stringResource(R.string.steam_space_partial))
                    }
                    if (state.loadMoreFailure != null) item("more-failure") {
                        if (paginationNeedsLogin) SpaceNotice(stringResource(state.loadMoreFailure.messageResource()),
                            onRetry = onManageAccounts, actionLabel = stringResource(R.string.steam_add_method_login))
                        else SpaceNotice(stringResource(R.string.steam_space_load_more_failed))
                    }
                    item("footer") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (state.loadingMore) CircularProgressIndicator(Modifier.size(28.dp))
                            else if (state.nextCursor != null && !paginationNeedsLogin) OutlinedButton(onClick = onLoadMore, enabled = !state.refreshing && !state.loading) {
                                Text(stringResource(if (state.loadMoreFailure == null) R.string.steam_space_load_more else R.string.steam_space_retry))
                            }
                            else if (state.items.isNotEmpty() && !state.partial && state.nextCursor == null) Caption(stringResource(R.string.steam_space_end))
                            TextButton(onClick = { onOpenOriginal("https://steamcommunity.com/my/home/") }) {
                                Text(stringResource(R.string.steam_space_open_feed))
                            }
                        }
                    }
                }
            }
        }
    }
    imageGallery?.let { (urls, index) ->
        SteamStoreScreenshotViewer(stringResource(R.string.steam_space_title), urls, index) { imageGallery = null }
    }
    if (showContentSettings) SteamSpaceContentFilters(
        hiddenKinds = hiddenKinds,
        saving = contentSettingsSaving,
        failed = contentSettingsFailed,
        onSetKindVisible = onSetKindVisible,
        onShowAll = onShowAllKinds,
        onRetry = onRetryContentSettings,
        onDismiss = { showContentSettings = false }
    )
    if (selectedFriend != null) ModalBottomSheet(onDismissRequest = { selectedFriendId = null }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            CompositionLocalProvider(LocalSteamAvatarShape provides CircleShape) { FriendAvatar(selectedFriend, 80, false) }
            Spacer(Modifier.height(16.dp))
            Text(selectedFriend.displayName, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (stalePresence) Caption(stringResource(R.string.steam_space_last_known))
            if (selectedFriend.isPlaying) Text(stringResource(R.string.steam_space_playing_game, selectedFriend.gameName.ifBlank { stringResource(R.string.steam_space_game_unknown) }),
                modifier = Modifier.padding(vertical = 12.dp), textAlign = TextAlign.Center)
            val appId = selectedFriend.gameId.toIntOrNull()?.takeIf { selectedFriend.isPlaying && it in 1..0xFFFFFF }
            if (appId != null) FilledTonalButton(onClick = { selectedFriendId = null; onOpenGame(appId) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.steam_space_view_game))
            }
            Button(onClick = { selectedFriendId = null; onMessage(selectedFriend.steamId) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.steam_space_message))
            }
        }
    }
}

@Composable
private fun SpaceEventCard(
    event: SteamSpaceEvent,
    friend: SteamFriend?,
    onOpen: (String) -> Unit,
    onOpenGame: (Int) -> Unit,
    onActor: () -> Unit,
    onImage: (List<String>, Int) -> Unit
) {
    val (anchor, preview) = rememberSteamCardPreviewAction(fallback = { onOpen(event.sourceUrl) }) { close ->
        SpaceEventHeader(event, friend, onActor = { close(); onActor() }, preview = null)
        SpaceEventBody(
            event, expanded = true,
            onGame = { close(); onOpenGame(it) },
            onImage = { images, index -> close(); onImage(images, index) },
            onOpen = { close(); onOpen(event.sourceUrl) }
        )
        Button(onClick = { close(); onOpen(event.sourceUrl) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.steam_space_view_original))
        }
    }
    val previewLabel = stringResource(R.string.steam_space_event_details)
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().then(anchor).clip(shape)
            .testTag("steam-space-event-${event.id}")
            .combinedClickable(role = Role.Button, onClickLabel = previewLabel, onClick = preview,
                onLongClickLabel = previewLabel, onLongClick = preview),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SpaceEventHeader(event, friend, onActor, preview)
            SpaceEventBody(event, expanded = false, onGame = null, onImage = onImage, onOpen = { onOpen(event.sourceUrl) })
        }
    }
}

@Composable
private fun SpaceEventHeader(
    event: SteamSpaceEvent,
    friend: SteamFriend?,
    onActor: () -> Unit,
    preview: (() -> Unit)?
) {
    val locale = LocalConfiguration.current.locales[0]
    val actor = friend?.takeIf { it.personaName.isNotBlank() || it.nickname.isNotBlank() }?.displayName
        ?: event.actorName.takeIf(String::isNotBlank) ?: stringResource(R.string.steam_space_unknown_actor)
    val shownFriend = friend?.copy(personaName = friend.personaName.ifBlank { event.actorName },
        avatarUrl = friend.avatarUrl.ifBlank { event.actorAvatarUrl })
        ?: SteamFriend(event.actorSteamId, personaName = actor, avatarUrl = event.actorAvatarUrl)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.clip(CircleShape).then(if (friend != null)
            Modifier.clickable(role = Role.Button, onClick = onActor) else Modifier)) {
            CompositionLocalProvider(LocalSteamAvatarShape provides CircleShape) {
                FriendAvatar(shownFriend, 38, false)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(actor, modifier = if (friend != null) Modifier.clickable(onClick = onActor) else Modifier,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(event.kind.labelResource()), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        if (event.occurredAtEpochSeconds > 0) {
            val time = remember(event.occurredAtEpochSeconds, locale) {
                DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(event.occurredAtEpochSeconds * 1000))
            }
            val fullTime = remember(event.occurredAtEpochSeconds, locale) {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
                    .format(Date(event.occurredAtEpochSeconds * 1000))
            }
            Text(time, Modifier.semantics { contentDescription = fullTime },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (preview != null) {
            IconButton(onClick = preview) {
                Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.steam_space_event_details))
            }
        }
    }
}

@Composable
private fun SpaceEventBody(
    event: SteamSpaceEvent,
    expanded: Boolean,
    onGame: ((Int) -> Unit)?,
    onImage: (List<String>, Int) -> Unit,
    onOpen: () -> Unit
) {
    val gameActivity = event.appIds.isNotEmpty() && event.kind != SteamSpaceEventKind.UNKNOWN
    if (!gameActivity || event.appNames.isEmpty()) {
        event.title?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
        }
    }
    if (gameActivity) {
        event.appIds.take(if (expanded) event.appIds.size else 2).forEach { appId ->
            val name = event.appNames[appId]?.takeIf(String::isNotBlank)
            SpaceGameRow(appId, name, onGame?.let { action -> { action(appId) } })
        }
        if (!expanded && event.appIds.size > 2) SpaceMore()
    }
    event.body?.takeIf(String::isNotBlank)?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis)
    }
    val images = remember(event.media) { event.media.filterNot { it.sensitive }.map { it.url } }
    val shownMedia = event.media.take(if (expanded) 4 else 2)
    shownMedia.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pair.forEach { media ->
                Box(Modifier.weight(1f)) {
                    if (media.sensitive) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 84.dp).clickable(role = Role.Button, onClick = onOpen)
                                .padding(10.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.steam_space_sensitive_media), textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        SpaceImage(media, square = shownMedia.size > 1,
                            onClick = { onImage(images, images.indexOf(media.url).coerceAtLeast(0)) })
                    }
                }
            }
            if (pair.size == 1 && shownMedia.size > 1) Spacer(Modifier.weight(1f))
        }
    }
    if (shownMedia.size < event.media.size) SpaceMore()
    event.achievements.take(if (expanded) event.achievements.size else 2).forEach { achievement ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SpaceAchievementIcon(event.appIds.singleOrNull() ?: 0, achievement.iconUrl)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(achievement.displayName.ifBlank { achievement.name }, style = MaterialTheme.typography.titleSmall)
                if (expanded) achievement.description?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    if (!expanded && event.achievements.size > 2) SpaceMore()
}

@Composable
private fun SpaceMore() {
    Icon(Icons.Rounded.OpenInFull, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SpaceGameRow(appId: Int, name: String?, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape).then(
            if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
        ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (name != null) {
                SteamChatRemoteImage(steamGameInviteHeaderUrl(appId), null,
                    Modifier.size(68.dp, 36.dp).clip(RoundedCornerShape(6.dp)),
                    mode = SteamChatRemoteImageMode.ARTWORK, fallbackIcon = Icons.Rounded.SportsEsports)
            } else Icon(Icons.Rounded.SportsEsports, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Text(name ?: stringResource(R.string.steam_space_app_id, appId),
                Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (onClick != null) Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SpaceAchievementIcon(appId: Int, iconUrl: String?) {
    // Cached feed entries can still contain the retired image route. Normalize at display time too.
    val resolvedUrl = takagi.ru.monica.steam.space.data.SteamSpaceLinks.achievementIcon(appId, iconUrl)
    val context = LocalContext.current.applicationContext
    // A replaced achievement must not retain the previous URL's bitmap while loading.
    key(resolvedUrl) {
        val bitmap by produceState<ImageBitmap?>(null, context, resolvedUrl) {
            value = resolvedUrl?.takeIf { it.isNotBlank() }?.let { loadSteamRemoteImage(context, it) }
        }
        Surface(Modifier.size(56.dp), shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                val image = bitmap
                if (image != null) {
                    Image(image, contentDescription = null, modifier = Modifier.fillMaxSize().testTag("steam-space-achievement-image"), contentScale = ContentScale.Fit)
                } else {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = null, modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun SpaceImage(media: SteamSpaceMedia, square: Boolean = false, onClick: () -> Unit = {}) {
    val context = LocalContext.current
    var loading by remember(media.url) { mutableStateOf(true) }
    val bitmap by produceState<ImageBitmap?>(null, media.url) {
        try { value = loadSteamRemoteImage(context, media.url) } finally { loading = false }
    }
    val ratio = if ((media.width ?: 0) > 0 && (media.height ?: 0) > 0) (media.width!!.toFloat() / media.height!!).coerceIn(0.75f, 2f) else 1.6f
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (square) 1f else ratio), contentAlignment = Alignment.Center) {
            val image = bitmap
            if (image != null) Image(image, stringResource(R.string.steam_space_media), Modifier.fillMaxSize().steamImageThumbnail(media.url, image), contentScale = ContentScale.Fit)
            else Caption(stringResource(if (loading) R.string.steam_space_media_loading else R.string.steam_space_media_unavailable))
        }
    }
}

@Composable
private fun Caption(text: String) { Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun SpaceNotice(text: String, onRetry: (() -> Unit)? = null, actionLabel: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) OutlinedButton(onClick = onRetry) { Text(actionLabel ?: stringResource(R.string.steam_space_retry)) }
    }
}

private fun SteamSpaceFailureReason.messageResource() = when (this) {
    SteamSpaceFailureReason.ACCOUNT_REQUIRED -> R.string.steam_space_account_required
    SteamSpaceFailureReason.SESSION_REQUIRED -> R.string.steam_space_session_required
    else -> R.string.steam_space_unavailable
}

private fun SteamSpaceEventKind.labelResource() = when (this) {
    SteamSpaceEventKind.ACHIEVEMENT -> R.string.steam_space_event_achievement
    SteamSpaceEventKind.SCREENSHOT -> R.string.steam_space_event_screenshot
    SteamSpaceEventKind.VIDEO -> R.string.steam_space_event_video
    SteamSpaceEventKind.STATUS -> R.string.steam_space_event_status
    SteamSpaceEventKind.REVIEW -> R.string.steam_space_event_review
    SteamSpaceEventKind.GAME_ADDED -> R.string.steam_space_event_game_added
    SteamSpaceEventKind.WISHLISTED -> R.string.steam_space_event_wishlisted
    SteamSpaceEventKind.FIRST_PLAY -> R.string.steam_space_event_first_play
    SteamSpaceEventKind.ANNOUNCEMENT -> R.string.steam_space_event_announcement
    SteamSpaceEventKind.UNKNOWN -> R.string.steam_space_event_unknown
}
