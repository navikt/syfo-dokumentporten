package no.nav.syfo.altinn.dialogporten.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import no.nav.syfo.altinn.dialogporten.service.DialogportenService
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.util.logger

class DeleteDialogTask(
    private val leaderElection: LeaderElection,
    private val dialogportenService: DialogportenService
) {
    private val logger = logger()

    suspend fun runTask() = coroutineScope {
        try {
            while (isActive) {
                if (leaderElection.isLeader() && false) {
                    try {
                        logger.info("Starting task for deleting documents in dialogporten")
                        dialogportenService.deleteDialogsInDialogporten()
                    } catch (ex: Exception) {
                        logger.error("Could not delete dialogs in dialogporten", ex)
                    }
                }
                // delay for  5 minutes before checking again
                delay(5 * 60 * 1000)
            }
        } catch (ex: CancellationException) {
            logger.info("Cancelled DeleteDialogTask", ex)
        }
    }
}
