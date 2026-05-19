package no.nav.syfo.esyfovarsel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.util.logger
import kotlin.time.Duration.Companion.minutes

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
                delay(1.minutes)
            }
        } catch (ex: CancellationException) {
            logger.info("Cancelled PublishVarselTask", ex)
        } finally {
            varselPublishService.close()
        }
    }
}
