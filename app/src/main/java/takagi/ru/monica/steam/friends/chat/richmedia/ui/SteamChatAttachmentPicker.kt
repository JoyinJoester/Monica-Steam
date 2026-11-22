package takagi.ru.monica.steam.friends.chat.richmedia.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.core.content.ContextCompat
import com.monica.telegramchat.TelegramAttachmentPickerView
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentPolicy

@Composable
internal fun rememberSteamChatGalleryPicker(
    onSelected: (String) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.toString()?.let(onSelected)
    }
    return remember(launcher) {
        {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        }
    }
}

@Composable
internal fun rememberSteamChatFilePicker(
    onSelected: (String) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.toString()?.let(onSelected)
    }
    return remember(launcher) {
        { launcher.launch(SteamChatAttachmentPolicy.pickerMimeTypes) }
    }
}

@Composable
internal fun SteamChatAttachmentPickerPanel(
    onGalleryClick: () -> Unit,
    onFileClick: () -> Unit,
    onSelected: (String) -> Unit,
    sourceView: android.view.View? = null,
    modifier: Modifier = Modifier
) {
    val colors = takagi.ru.monica.steam.friends.chat.telegram.telegramChatColors()
    val context = LocalContext.current
    val gallery = stringResource(R.string.steam_chat_attachment_gallery)
    val gallerySummary = stringResource(R.string.steam_chat_attachment_gallery_summary)
    val file = stringResource(R.string.steam_chat_attachment_file)
    val fileSummary = stringResource(R.string.steam_chat_attachment_file_summary)
    var revision by remember { mutableIntStateOf(0) }
    var folder by remember { mutableStateOf<Uri?>(null) }
    var parents by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var entries by remember { mutableStateOf<List<TelegramAttachmentPickerView.Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= 34) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        else if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val grant = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { revision++ }
    val directory = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { folder = uri; parents = emptyList() }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val hasAccess = revision.let { permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } }
    LaunchedEffect(revision, folder) {
        loading = true; failure = null
        try { entries = loadTelegramAttachmentEntries(context, folder, parents) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { entries = emptyList(); failure = "无法读取附件，请重新选择文件夹或从系统文件选择" }
        finally { loading = false }
    }
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ATTACHMENT_PANEL_MIN_HEIGHT, max = ATTACHMENT_PANEL_MAX_HEIGHT)
            .fillMaxHeight(ATTACHMENT_PANEL_FRACTION)
            .clip(ATTACHMENT_PANEL_SHAPE)
            .background(Color(colors.native.menuBackground), ATTACHMENT_PANEL_SHAPE),
        factory = { TelegramAttachmentPickerView(it) },
        onRelease = { it.setCallbacks({}, {}, {}, {}, {}); it.setGalleryFallback({}); it.setSourceView(null) },
        update = { view ->
            view.setColors(colors.native)
            view.setSourceView(sourceView)
            view.setLabels(gallery, gallerySummary, file, fileSummary)
            view.setGalleryFallback(onGalleryClick)
            view.setItems(entries, hasAccess, loading, failure)
            view.setCallbacks({ onSelected(it.uri) }, { entry ->
                if (entry.name == "上一级文件夹" && parents.isNotEmpty()) {
                    folder = parents.last(); parents = parents.dropLast(1)
                } else { folder?.let { parents = parents + it }; folder = Uri.parse(entry.uri) }
            }, { grant.launch(permissions) }, onFileClick, { directory.launch(folder) })
        }
    )
}

// Telegram lets the picker own most of the sheet: a single slim header, then content.
private val ATTACHMENT_PANEL_SHAPE = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
private val ATTACHMENT_PANEL_MIN_HEIGHT = 300.dp
private val ATTACHMENT_PANEL_MAX_HEIGHT = 560.dp
private const val ATTACHMENT_PANEL_FRACTION = 0.75f
