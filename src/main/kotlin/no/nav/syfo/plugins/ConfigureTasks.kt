package no.nav.syfo.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopPreparing
import kotlinx.coroutines.launch
import no.nav.syfo.altinn.dialogporten.task.SendDialogTask
import no.nav.syfo.esyfovarsel.PublishVarselTask
import org.koin.ktor.ext.inject

fun Application.configureBackgroundTasks() {
    val sendDialogTask by inject<SendDialogTask>()
    val publishVarselTask by inject<PublishVarselTask>()

    val sendDialogTaskJob = launch { sendDialogTask.runTask() }
    val publishVarselTaskJob = launch { publishVarselTask.runTask() }
    monitor.subscribe(ApplicationStopPreparing) {
        sendDialogTaskJob.cancel()
        publishVarselTaskJob.cancel()
    }
}
