package takagi.ru.monica.steam.friends.chat.telegram

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.monica.telegramchat.TelegramRecyclerView
import takagi.ru.monica.steam.friends.chat.position.data.SteamChatReadingPositionStore
import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatReadingPosition
import takagi.ru.monica.steam.friends.chat.position.domain.steamChatMessagesBelow
import takagi.ru.monica.steam.friends.chat.position.ui.SteamChatReadingUiState

/** How long a search hit stays pinned to the viewport centre while layout settles. */
private const val CENTER_HOLD_MILLIS = 600L

/** Native list lifecycle and Steam persistence; does not own a Steam protocol session. */
internal class TelegramChatListState internal constructor(
    private val conversationKey: String,
    private val store: SteamChatReadingPositionStore
) {
    var readingUi by mutableStateOf(SteamChatReadingUiState(false, 0, null))
        private set
    var sourceView: View? by mutableStateOf(null)
        private set

    private var recycler: TelegramRecyclerView? = null
    private var messageIds = emptyList<String>()
    private var leadingItemCount = 0
    private var restored = false
    private var handledRequestedMessageId: String? = null
    private var handledRequestNonce = Int.MIN_VALUE
    private var requestedMessageId: String? = null
    private var loadingOlder = false
    private var moreAvailable = false
    private var onLoadOlder: () -> Unit = {}
    private var requestedOlderBoundary: String? = null
    private var lastVisiblePosition: SteamChatReadingPosition? = null
    private var layoutPending = false
    private var pendingCenterMessageId: String? = null
    private var centerTargetMessageId: String? = null
    private var centerTargetDeadline = 0L
    private var followBottom = true
    private var updateGeneration = 0
    private var pendingUpdate: Runnable? = null
    private var savedPosition = store.load(conversationKey)
    private val savePosition = Runnable { persistPosition() }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
            if (!layoutPending) updateVisiblePosition()
        }

        override fun onScrollStateChanged(view: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                followBottom = false
                // The reader took over; stop re-pinning the search hit.
                centerTargetMessageId = null
            }
            if (newState == RecyclerView.SCROLL_STATE_IDLE && !layoutPending) updateVisiblePosition()
        }
    }
    private val resizeListener = View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
        if (restored && !layoutPending && followBottom && bottom - top != oldBottom - oldTop && oldBottom > oldTop) {
            // The IME, multiline editor and attachment preview all resize this viewport.
            // Preserve the bottom only when the user was already following it.
            jumpToLatest(animated = false)
        }
        if (!layoutPending) updateVisiblePosition()
    }

    internal fun attach(view: TelegramRecyclerView) {
        check(recycler == null || recycler === view)
        if (recycler === view) return
        recycler = view
        sourceView = view
        view.addOnScrollListener(scrollListener)
        view.addOnLayoutChangeListener(resizeListener)
        view.setBeforeViewportResize {
            if (restored && !layoutPending && !followBottom) {
                (lastVisiblePosition ?: visiblePosition())?.let { anchor ->
                    val index = messageIds.indexOf(anchor.messageId)
                    if (index >= 0) manager()?.scrollToPositionWithOffset(index + leadingItemCount, -anchor.scrollOffset)
                }
            }
        }
    }

    internal fun detach(view: TelegramRecyclerView) {
        if (recycler !== view) return
        persistPosition()
        view.removeCallbacks(savePosition)
        pendingUpdate?.let(view::removeCallbacks)
        pendingUpdate = null
        view.removeOnScrollListener(scrollListener)
        view.removeOnLayoutChangeListener(resizeListener)
        view.setBeforeViewportResize(null)
        view.adapter = null
        recycler = null
        sourceView = null
        centerTargetMessageId = null
        // A voice/text switch may recreate the native view while retaining this
        // conversation state. Restore the most recent persisted anchor on attach.
        restored = false
        readingUi = SteamChatReadingUiState(false, 0, null)
        messageIds = emptyList()
        leadingItemCount = 0
        layoutPending = false
        updateGeneration++
    }

    internal fun <T> update(
        adapter: TelegramMessageAdapter<T>,
        items: List<T>,
        itemKey: (T) -> String,
        requestedMessageId: String?,
        requestedNonce: Int,
        loadingOlder: Boolean,
        moreAvailable: Boolean,
        latestMessageOutgoing: Boolean,
        onLoadOlder: () -> Unit,
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int
    ) {
        val view = recycler ?: return
        // Only the newest queued snapshot may bind. A later recomposition can
        // restore the current adapter contents before a previous post executes.
        pendingUpdate?.let(view::removeCallbacks)
        pendingUpdate = null
        this.onLoadOlder = onLoadOlder
        this.moreAvailable = moreAvailable
        this.loadingOlder = loadingOlder
        val nextItems = items.toList()
        val nextIds = nextItems.map(itemKey)
        val dataChanged = adapter.items != nextItems || messageIds != nextIds ||
            leadingItemCount != (if (loadingOlder) 1 else 0)
        val paddingChanged = view.paddingLeft != paddingLeft || view.paddingTop != paddingTop ||
            view.paddingRight != paddingRight || view.paddingBottom != paddingBottom
        val requestChanged = this.requestedMessageId != requestedMessageId ||
            handledRequestNonce != requestedNonce
        if (requestChanged) {
            handledRequestedMessageId = null
            handledRequestNonce = requestedNonce
        }
        this.requestedMessageId = requestedMessageId
        if (!dataChanged && !requestChanged && !paddingChanged) {
            // A server response can update only moreAvailable after the initial
            // cached messages were rendered. It still needs pagination handling.
            view.post { if (recycler === view) updateVisiblePosition() }
            return
        }
        val generation = ++updateGeneration
        val applyUpdate = applyUpdate@{
            if (recycler !== view || generation != updateGeneration) return@applyUpdate
            val anchor = (if (layoutPending) null else visiblePosition()) ?: lastVisiblePosition
            val scrollPlan = planTelegramListScroll(
                previousIds = messageIds,
                messageIds = nextIds,
                requestedMessageId = requestedMessageId,
                handledRequestedMessageId = handledRequestedMessageId,
                savedPosition = savedPosition,
                visiblePosition = anchor,
                restored = restored,
                latestMessageOutgoing = latestMessageOutgoing,
                messagesBelow = readingUi.messagesBelow,
                followingLatest = followBottom && restored
            )
            val oldCount = messageIds.size
            val appendOnly = oldCount > 0 && nextIds.size > oldCount && nextIds.take(oldCount) == messageIds
            val wasRestored = restored
            layoutPending = true
            if (paddingChanged) view.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
            messageIds = nextIds
            leadingItemCount = if (loadingOlder) 1 else 0
            adapter.submit(nextItems, nextIds, loadingOlder)
            if (nextIds.isNotEmpty()) restored = true
            when (scrollPlan) {
                is TelegramListScrollPlan.Anchor -> {
                    // Header/composer measurement may queue another padding update
                    // before the first restored row has completed layout.
                    lastVisiblePosition = scrollPlan.position
                    if (scrollPlan.requested) handledRequestedMessageId = scrollPlan.position.messageId
                    val index = nextIds.indexOf(scrollPlan.position.messageId)
                    manager()?.scrollToPositionWithOffset(
                        index + leadingItemCount,
                        -scrollPlan.position.scrollOffset.coerceAtLeast(0)
                    )
                    // Telegram centres the message a search result points at; pinning it to the
                    // very top hides the surrounding context that makes the hit meaningful.
                    if (scrollPlan.requested) {
                        pendingCenterMessageId = scrollPlan.position.messageId
                        centerTargetMessageId = scrollPlan.position.messageId
                        // Header and composer heights settle over the next couple of layouts; a
                        // single pass left the hit two rows below the crosshair.
                        centerTargetDeadline = SystemClock.uptimeMillis() + CENTER_HOLD_MILLIS
                    }
                    followBottom = false
                }
                TelegramListScrollPlan.Latest -> jumpToLatest(animated = wasRestored && appendOnly)
                TelegramListScrollPlan.None -> Unit
            }
            view.doOnNextLayout {
                if (recycler === view && generation == updateGeneration) {
                    layoutPending = false
                    val target = pendingCenterMessageId ?: centerTargetMessageId
                    pendingCenterMessageId = null
                    if (target != null) {
                        if (centerTargetMessageId != null &&
                            SystemClock.uptimeMillis() > centerTargetDeadline
                        ) {
                            // The viewport has settled; stop re-pinning so the reader can scroll.
                            centerTargetMessageId = null
                        } else {
                            centerOnMessage(target)
                        }
                    }
                    if (appendOnly && wasRestored) {
                        view.itemsEnterAnimator.showItemsAnimated(oldCount + leadingItemCount)
                    }
                    updateVisiblePosition()
                }
            }
            view.requestLayout()
        }
        if (view.isComputingLayout) {
            pendingUpdate = Runnable {
                pendingUpdate = null
                applyUpdate()
            }.also(view::post)
        } else applyUpdate()
    }

    fun jumpToLatest() = jumpToLatest(animated = true)

    private fun jumpToLatest(animated: Boolean) {
        val view = recycler ?: return
        val manager = manager() ?: return
        if (messageIds.isEmpty()) return
        followBottom = true
        val target = messageIds.lastIndex + leadingItemCount
        val generation = updateGeneration
        if (animated && view.isLaidOut) {
            manager.startSmoothScroll(object : LinearSmoothScroller(view.context) {
                override fun getVerticalSnapPreference() = SNAP_TO_END
            }.apply { targetPosition = target })
        } else {
            manager.scrollToPositionWithOffset(target, 0)
            view.doOnNextLayout {
                if (recycler !== view || generation != updateGeneration || !followBottom) return@doOnNextLayout
                manager.findViewByPosition(target)?.let { child ->
                    view.scrollBy(0, manager.getDecoratedBottom(child) - (view.height - view.paddingBottom))
                }
                updateVisiblePosition()
            }
            view.requestLayout()
        }
    }

    private fun manager() = recycler?.layoutManager as? LinearLayoutManager

    /** Puts [messageId] in the middle of the viewport; RecyclerView clamps the move at the ends. */
    private fun centerOnMessage(messageId: String) {
        val view = recycler ?: return
        val manager = manager() ?: return
        val index = messageIds.indexOf(messageId)
        if (index < 0) return
        val child = manager.findViewByPosition(index + leadingItemCount) ?: return
        val viewportCenter = (view.paddingTop + view.height - view.paddingBottom) / 2
        val itemCenter = (manager.getDecoratedTop(child) + manager.getDecoratedBottom(child)) / 2
        val offset = itemCenter - viewportCenter
        if (offset != 0) view.scrollBy(0, offset)
    }

    private fun visiblePosition(): SteamChatReadingPosition? {
        val view = recycler ?: return null
        val manager = manager() ?: return null
        val first = manager.findFirstVisibleItemPosition().coerceAtLeast(leadingItemCount)
        val id = messageIds.getOrNull(first - leadingItemCount) ?: return null
        val child = manager.findViewByPosition(first) ?: return null
        return SteamChatReadingPosition(id, (view.paddingTop - manager.getDecoratedTop(child)).coerceAtLeast(0))
    }

    private fun updateVisiblePosition() {
        val view = recycler ?: return
        if (layoutPending || !restored) return
        val manager = manager() ?: return
        val position = visiblePosition() ?: return
        lastVisiblePosition = position
        val lastId = messageIds.getOrNull(manager.findLastVisibleItemPosition() - leadingItemCount)
        val below = steamChatMessagesBelow(messageIds, lastId)
        readingUi = SteamChatReadingUiState(restored = true, messagesBelow = below, lastVisibleMessageId = lastId)
        if (view.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
            followBottom = below == 0 && !view.canScrollVertically(1)
        }
        view.removeCallbacks(savePosition)
        view.postDelayed(savePosition, 250)
        val first = manager.findFirstVisibleItemPosition() - leadingItemCount
        if (first > 2) requestedOlderBoundary = null
        if (first <= 2 && moreAvailable && !loadingOlder &&
            messageIds.firstOrNull() != requestedOlderBoundary
        ) {
            requestedOlderBoundary = messageIds.firstOrNull()
            // Steam state changes may synchronously cause composition; dispatch after layout.
            view.post { if (recycler === view && moreAvailable && !this.loadingOlder) onLoadOlder() }
        }
    }

    private fun persistPosition() {
        if (!restored) return
        (if (layoutPending) lastVisiblePosition else visiblePosition() ?: lastVisiblePosition)
            ?.let {
                savedPosition = it
                store.save(conversationKey, it)
            }
    }
}

@Composable
internal fun rememberTelegramChatListState(conversationKey: String): TelegramChatListState {
    val context = LocalContext.current
    return remember(context, conversationKey) {
        TelegramChatListState(conversationKey, SteamChatReadingPositionStore(context))
    }
}

/** AndroidX native rows driven by the actual Telegram RecyclerItemsEnterAnimator. */
@Composable
internal fun <T> TelegramMessageList(
    items: List<T>,
    itemKey: (T) -> String,
    state: TelegramChatListState,
    conversationKey: String,
    requestedMessageId: String?,
    loadingOlder: Boolean,
    moreAvailable: Boolean,
    latestMessageOutgoing: Boolean,
    onLoadOlder: () -> Unit,
    /** Bumped by the host on every jump request so the same message can be found twice. */
    requestedNonce: Int = 0,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit
) {
    val composition = rememberCompositionContext()
    val content = rememberUpdatedState(itemContent)
    val theme = rememberUpdatedState(MaterialTheme.colorScheme)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    key(conversationKey) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                TelegramRecyclerView(context).apply {
                    layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
                    adapter = TelegramMessageAdapter(composition, content, theme)
                    // Telegram owns entry animations. Disable RecyclerView's change cross-fade,
                    // which otherwise resets media and reaction compositions while rebinding.
                    itemAnimator = null
                    clipToPadding = false
                    // Native stretch transforms the viewport outside View.draw(), so
                    // glass cannot sample that transform. Keep both layers at the real edge.
                    overScrollMode = View.OVER_SCROLL_NEVER
                    state.attach(this)
                }
            },
            onRelease = state::detach,
            update = { view ->
                @Suppress("UNCHECKED_CAST")
                state.update(
                    adapter = view.adapter as TelegramMessageAdapter<T>,
                    items = items,
                    itemKey = itemKey,
                    requestedMessageId = requestedMessageId,
                    requestedNonce = requestedNonce,
                    loadingOlder = loadingOlder,
                    moreAvailable = moreAvailable,
                    latestMessageOutgoing = latestMessageOutgoing,
                    onLoadOlder = onLoadOlder,
                    paddingLeft = with(density) { contentPadding.calculateLeftPadding(layoutDirection).roundToPx() },
                    paddingTop = with(density) { contentPadding.calculateTopPadding().roundToPx() },
                    paddingRight = with(density) { contentPadding.calculateRightPadding(layoutDirection).roundToPx() },
                    paddingBottom = with(density) { contentPadding.calculateBottomPadding().roundToPx() }
                )
            }
        )
    }
}

internal class TelegramMessageAdapter<T>(
    private val composition: CompositionContext,
    private val content: State<@Composable (T) -> Unit>,
    private val theme: State<ColorScheme>
) : RecyclerView.Adapter<TelegramMessageAdapter.Holder<T>>() {
    var items: List<T> = emptyList()
        private set
    private var keys = emptyList<String>()
    private var loadingOlder = false
    private val stableIds = mutableMapOf<String, Long>()
    private var nextStableId = 0L
    private val leadingCount get() = if (loadingOlder) 1 else 0

    init { setHasStableIds(true) }

    fun submit(newItems: List<T>, newKeys: List<String>, newLoadingOlder: Boolean) {
        val oldItems = items
        val oldKeys = keys
        val oldLeading = leadingCount
        val newLeading = if (newLoadingOlder) 1 else 0
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldItems.size + oldLeading
            override fun getNewListSize() = newItems.size + newLeading
            override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean =
                oldKeys.getOrNull(oldPosition - oldLeading) == newKeys.getOrNull(newPosition - newLeading)
            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean =
                oldItems.getOrNull(oldPosition - oldLeading) == newItems.getOrNull(newPosition - newLeading)
        })
        items = newItems.toList()
        keys = newKeys
        loadingOlder = newLoadingOlder
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount() = items.size + leadingCount
    override fun getItemViewType(position: Int) = if (position < leadingCount) 1 else 0
    override fun getItemId(position: Int): Long = keys.getOrNull(position - leadingCount)
        ?.let { stableIds.getOrPut(it) { nextStableId++ } } ?: Long.MIN_VALUE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder<T> {
        val view = ComposeView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setParentCompositionContext(composition)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        }
        return Holder<T>(view).also { holder ->
            view.setContent {
                // RecyclerView rows have their own compositions; keep their palette live.
                MaterialTheme(colorScheme = theme.value) {
                val row = holder.row
                if (viewType == 1) {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                } else if (row != null) {
                    key(row.first) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 1.dp)) { content.value(row.second) }
                    }
                }
                }
            }
        }
    }

    override fun onBindViewHolder(holder: Holder<T>, position: Int) {
        val index = position - leadingCount
        holder.row = if (index < 0) null else keys[index] to items[index]
    }

    override fun onViewRecycled(holder: Holder<T>) {
        holder.row = null
        holder.itemView.alpha = 1f
        super.onViewRecycled(holder)
    }

    class Holder<T>(view: ComposeView) : RecyclerView.ViewHolder(view) {
        var row: Pair<String, T>? by mutableStateOf(null)
    }
}
