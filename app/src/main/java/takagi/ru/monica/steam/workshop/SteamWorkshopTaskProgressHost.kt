package takagi.ru.monica.steam.workshop

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassAlertDialog as AlertDialog

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import takagi.ru.monica.R

private data class WorkshopTaskProgressLoad(
    val loading: Boolean = true,
    val task: WorkshopBulkTask? = null,
    val controller: WorkshopBulkController? = null
)

/** Notification clicks can inspect their original task without switching the selected Steam account. */
@Composable
internal fun SteamWorkshopTaskProgressHost(taskKey: String?, onDismiss: () -> Unit) {
    if (taskKey == null) return
    val context = LocalContext.current
    val loaded by produceState(WorkshopTaskProgressLoad(), taskKey) {
        value = WorkshopTaskProgressLoad()
        try {
            val tasks = SteamWorkshopBulkTasks.get(context.applicationContext)
            val task = tasks.task(taskKey)
            val controller = tasks.controller(taskKey)
            value = WorkshopTaskProgressLoad(loading = false, task = task, controller = controller)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = WorkshopTaskProgressLoad(loading = false) }
    }
    val task = loaded.task
    val controller = loaded.controller
    if (task == null || controller == null) {
        AlertDialog(onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.workshop_background_channel)) },
            text = { if (loaded.loading) CircularProgressIndicator() else Text(stringResource(R.string.workshop_background_task_missing)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.workshop_done)) } })
        return
    }
    val state by controller.state.collectAsStateWithLifecycle()
    state?.let { current ->
        WorkshopBulkDialog(current, stop = controller::stop, retry = {
            controller.retry()
        }, dismiss = {
            if (!current.running) controller.dismissFinished()
            onDismiss()
        }, targetLabel = "${task.gameName} · ${task.accountName}")
    }
}
