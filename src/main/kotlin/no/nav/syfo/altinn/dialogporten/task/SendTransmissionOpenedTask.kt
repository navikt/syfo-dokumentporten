package no.nav.syfo.altinn.dialogporten.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import no.nav.syfo.altinn.dialogporten.service.DialogportenService
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.util.logger

class SendTransmissionOpenedTask(
    private val leaderElection: LeaderElection,
    private val dialogportenService: DialogportenService,
) {
    private val logger = logger()

    suspend fun runTask() = coroutineScope {
        try {
            while (isActive) {
                if (leaderElection.isLeader()) {
                    try {
                        dialogportenService.sendTransmissionOpenedActivities()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        logger.error("Could not send TransmissionOpened activities to Dialogporten", ex)
                    }
                }
                delay(30 * 1000)
            }
        } catch (ex: CancellationException) {
            logger.info("Cancelled SendTransmissionOpenedTask", ex)
            throw ex
        }
    }
}
