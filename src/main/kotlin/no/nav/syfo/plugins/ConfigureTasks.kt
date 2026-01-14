package no.nav.syfo.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.launch
import no.nav.syfo.altinn.dialogporten.task.DeleteDialogTask
import no.nav.syfo.altinn.dialogporten.task.SendDialogTask
import org.koin.ktor.ext.inject

fun Application.configureBackgroundTasks() {
    val sendDialogTask by inject<SendDialogTask>()
    val deleteDialogTask by inject<DeleteDialogTask>()

    val sendDialogTaskJob = launch { sendDialogTask.runTask() }
    monitor.subscribe(ApplicationStopping) {
        sendDialogTaskJob.cancel()
    }

    val deleteDialogTaskJob = launch { deleteDialogTask.runTask() }
    monitor.subscribe(ApplicationStopping) {
        deleteDialogTaskJob.cancel()
    }
}
