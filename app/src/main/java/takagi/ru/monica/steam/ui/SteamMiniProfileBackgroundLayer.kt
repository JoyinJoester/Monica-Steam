package takagi.ru.monica.steam.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.PowerManager
import android.util.AttributeSet
import android.util.LruCache
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.profile.SteamMiniProfileBackgroundRepository
import takagi.ru.monica.steam.profile.SteamMiniProfilePreparedMedia

@Composable
internal fun SteamMiniProfileBackgroundLayer(
    steamId: String,
    enabled: Boolean,
    allowMotion: Boolean,
    onAvailabilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    val context = LocalContext.current
    val repository = remember(context) {
        SteamMiniProfileBackgroundRepository.get(context.applicationContext)
    }
    val media by produceState<SteamMiniProfilePreparedMedia?>(
        initialValue = null,
        key1 = steamId,
        key2 = enabled
    ) {
        value = if (!enabled) {
            null
        } else {
            try {
                repository.load(steamId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
        }
    }
    val poster by produceState<Bitmap?>(
        initialValue = null,
        key1 = media?.posterFile?.absolutePath
    ) {
        value = try {
            media?.posterFile?.let { file ->
                withContext(Dispatchers.IO) { SteamMiniProfilePosterMemoryCache.load(file) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }
    val motionAllowed = rememberSteamMiniProfileMotionAllowed(allowMotion)
    val mediaAvailable = media?.videoFile?.isFile == true
    LaunchedEffect(mediaAvailable) {
        onAvailabilityChanged(mediaAvailable)
    }
    val hasPlaybackSlot = rememberSteamMiniProfilePlaybackSlot(
        requested = motionAllowed && mediaAvailable
    )

    Box(modifier = modifier.clearAndSetSemantics { }) {
        poster?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        val videoFile = media?.videoFile
        if (videoFile != null && hasPlaybackSlot) {
            SteamMiniProfileVideo(
                file = videoFile,
                play = motionAllowed,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun rememberSteamMiniProfileMotionAllowed(requested: Boolean): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = remember(context) {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    var resumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var powerSave by remember { mutableStateOf(powerManager.isPowerSaveMode) }

    DisposableEffect(lifecycleOwner, powerManager) {
        val lifecycleObserver = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        val powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                powerSave = powerManager.isPowerSaveMode
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        runCatching {
            context.registerReceiver(
                powerReceiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            )
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            runCatching { context.unregisterReceiver(powerReceiver) }
        }
    }
    return requested && resumed && !powerSave
}

@Composable
private fun rememberSteamMiniProfilePlaybackSlot(requested: Boolean): Boolean {
    val granted by produceState(initialValue = false, key1 = requested) {
        if (!requested) {
            value = false
            return@produceState
        }
        SteamMiniProfilePlaybackSlots.acquire()
        try {
            value = true
            awaitCancellation()
        } finally {
            value = false
            SteamMiniProfilePlaybackSlots.release()
        }
    }
    return granted
}

@Composable
private fun SteamMiniProfileVideo(
    file: File,
    play: Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            SteamMiniProfileTextureView(context).apply {
                isClickable = false
                isFocusable = false
            }
        },
        update = { view -> view.setMedia(file, play) },
        onRelease = SteamMiniProfileTextureView::release,
        modifier = modifier.clearAndSetSemantics { }
    )
}

private object SteamMiniProfilePlaybackSlots {
    private val semaphore = Semaphore(2)

    suspend fun acquire() = semaphore.acquire()
    fun release() = semaphore.release()
}

private object SteamMiniProfilePosterMemoryCache {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun load(file: File): Bitmap? {
        val key = file.absolutePath + ':' + file.lastModified()
        cache.get(key)?.let { return it }
        return BitmapFactory.decodeFile(file.absolutePath)?.also { cache.put(key, it) }
    }
}

private class SteamMiniProfileTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var mediaPath: String? = null
    private var playRequested: Boolean = false
    private var prepared: Boolean = false
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    init {
        surfaceTextureListener = this
        isOpaque = false
        alpha = 0f
    }

    fun setMedia(file: File, play: Boolean) {
        playRequested = play
        val path = file.absolutePath
        if (mediaPath != path) {
            mediaPath = path
            createPlayerIfPossible()
        } else {
            updatePlayback()
        }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        surface?.release()
        surface = Surface(texture)
        createPlayerIfPossible()
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        applyCenterCrop()
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        releasePlayer()
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    fun release() {
        animate().cancel()
        releasePlayer()
        surface?.release()
        surface = null
        mediaPath = null
        alpha = 0f
    }

    private fun createPlayerIfPossible() {
        val path = mediaPath ?: return
        val targetSurface = surface ?: return
        releasePlayer()
        animate().cancel()
        alpha = 0f
        prepared = false
        player = runCatching {
            MediaPlayer().apply {
                isLooping = true
                setVolume(0f, 0f)
                setSurface(targetSurface)
                setDataSource(path)
                setOnVideoSizeChangedListener { _, width, height ->
                    this@SteamMiniProfileTextureView.videoWidth = width
                    this@SteamMiniProfileTextureView.videoHeight = height
                    applyCenterCrop()
                }
                setOnPreparedListener {
                    prepared = true
                    updatePlayback()
                }
                setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        animate().alpha(1f).setDuration(180L).start()
                    }
                    false
                }
                setOnErrorListener { _, _, _ ->
                    animate().cancel()
                    alpha = 0f
                    releasePlayer()
                    true
                }
                prepareAsync()
            }
        }.getOrElse {
            animate().cancel()
            alpha = 0f
            null
        }
    }

    private fun updatePlayback() {
        val active = player ?: return
        if (!prepared) return
        runCatching {
            if (playRequested) {
                if (!active.isPlaying) active.start()
            } else if (active.isPlaying) {
                active.pause()
            }
        }.onFailure {
            animate().cancel()
            alpha = 0f
            releasePlayer()
        }
    }

    private fun applyCenterCrop() {
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return
        val transform = calculateSteamMiniProfileCenterCrop(
            viewWidth = width,
            viewHeight = height,
            mediaWidth = videoWidth,
            mediaHeight = videoHeight,
        )
        val matrix = Matrix().apply {
            setScale(
                transform.scaleX,
                transform.scaleY,
                width / 2f,
                height / 2f,
            )
        }
        setTransform(matrix)
    }

    private fun releasePlayer() {
        prepared = false
        val current = player
        player = null
        current?.let {
            runCatching { current.stop() }
            runCatching { current.reset() }
            runCatching { current.release() }
        }
    }
}
