package no.nav.syfo.kafka.esyfovarsel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.util.logger

class PublishVarselTask(
    private val leaderElection: LeaderElection,
    private val varselPublishService: VarselPublishService,
) {
    private val logger = logger()

    suspend fun runTask() = coroutineScope {
        try {
            while (isActive) {
                if (leaderElection.isLeader()) {
                    try {
                        varselPublishService.publishPendingVarsler()
                    } catch (ex: Exception) {
                        logger.error("Could not publish varsler to esyfovarsel", ex)
                    }
                }
                delay(60 * 1000)
            }
        } catch (ex: CancellationException) {
            logger.info("Cancelled PublishVarselTask", ex)
        }
    }
}
