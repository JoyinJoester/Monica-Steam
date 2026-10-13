package takagi.ru.monica.steam.backup.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/** Document locations only: never stores credentials, ZIP bytes or an authentication grant. */
internal class SteamMaFileTransferState(
    exportDestination: MutableState<Uri?>,
    importSource: MutableState<Uri?>,
    val launchExport: () -> Unit,
    val launchImport: () -> Unit
) {
    var exportDestination by exportDestination
    var importSource by importSource
}

/** Keep this above both app and page lock gates so returning from SAF can re-lock safely. */
@Composable
internal fun rememberSteamMaFileTransferState(enabled: Boolean = true): SteamMaFileTransferState {
    val exportDestination = remember { mutableStateOf<Uri?>(null) }
    val importSource = remember { mutableStateOf<Uri?>(null) }
    val currentEnabled by rememberUpdatedState(enabled)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (currentEnabled) exportDestination.value = uri }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        uri -> if (currentEnabled) importSource.value = uri
    }
    LaunchedEffect(enabled) {
        if (!enabled) {
            exportDestination.value = null
            importSource.value = null
        }
    }
    return remember(exportLauncher, importLauncher) {
        SteamMaFileTransferState(
            exportDestination = exportDestination,
            importSource = importSource,
            launchExport = {
                if (currentEnabled) exportLauncher.launch("steam_mafiles_${System.currentTimeMillis()}.zip")
            },
            launchImport = {
                if (currentEnabled) importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            }
        )
    }
}
