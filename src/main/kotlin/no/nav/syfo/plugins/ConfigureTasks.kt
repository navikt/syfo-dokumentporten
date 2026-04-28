package no.nav.syfo.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.launch
import no.nav.syfo.altinn.dialogporten.task.SendDialogTask
import no.nav.syfo.kafka.esyfovarsel.IEsyfovarselProducer
import no.nav.syfo.kafka.esyfovarsel.PublishVarselTask
import org.koin.ktor.ext.inject

fun Application.configureBackgroundTasks() {
    val sendDialogTask by inject<SendDialogTask>()
    val publishVarselTask by inject<PublishVarselTask>()
    val esyfovarselProducer by inject<IEsyfovarselProducer>()

    val sendDialogTaskJob = launch { sendDialogTask.runTask() }
    val publishVarselTaskJob = launch { publishVarselTask.runTask() }
    monitor.subscribe(ApplicationStopping) {
        sendDialogTaskJob.cancel()
        publishVarselTaskJob.cancel()
        esyfovarselProducer.close()
    }
}
