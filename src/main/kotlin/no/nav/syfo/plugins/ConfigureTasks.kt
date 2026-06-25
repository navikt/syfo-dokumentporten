package no.nav.syfo.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopPreparing
import kotlinx.coroutines.launch
import no.nav.syfo.altinn.dialogporten.task.SendDialogTask
import no.nav.syfo.altinn.dialogporten.task.UpdateApiOnlyTask
import no.nav.syfo.application.Environment
import no.nav.syfo.esyfovarsel.PublishVarselTask
import org.koin.ktor.ext.inject

fun Application.configureBackgroundTasks() {
    val appEnv by inject<Environment>()

    val sendDialogTask by inject<SendDialogTask>()
    val publishVarselTask by inject<PublishVarselTask>()
    val updateApiOnlyTask by inject<UpdateApiOnlyTask>()

    val sendDialogTaskJob = launch { sendDialogTask.runTask() }
    val publishVarselTaskJob = launch { publishVarselTask.runTask() }

    if (appEnv.enableApiOnlyJob) {
        val updateApiOnlyTaskJob = launch { updateApiOnlyTask.runTask() }
        monitor.subscribe(ApplicationStopPreparing) {
            if (appEnv.enableApiOnlyJob) {
                updateApiOnlyTaskJob.cancel()
            }
        }
    }

    monitor.subscribe(ApplicationStopPreparing) {
        sendDialogTaskJob.cancel()
        publishVarselTaskJob.cancel()
    }
}
