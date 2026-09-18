package no.nav.syfo.document.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.document.service.DocumentCleanupService
import no.nav.syfo.util.logger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

const val DOCUMENT_CLEANUP_RUN_AT_UTC_HOUR = 2

class DocumentCleanupTask(
    private val leaderElection: LeaderElection,
    private val documentCleanupService: DocumentCleanupService,
    private val clock: Clock = Clock.systemUTC(),
    private val runAtUtcHour: Int = DOCUMENT_CLEANUP_RUN_AT_UTC_HOUR,
) {
    private val logger = logger()

    suspend fun runTask() = coroutineScope {
        while (isActive) {
            delay(durationUntilNextRun())
            try {
                if (leaderElection.isLeader()) {
                    documentCleanupService.cleanupExpiredDocuments()
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                logger.error("Document cleanup task iteration failed", ex)
            }
        }
    }

    private fun durationUntilNextRun(): Duration {
        val now = Instant.now(clock)
        val scheduledToday = now
            .atZone(ZoneOffset.UTC)
            .withHour(runAtUtcHour)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        val nextRun = if (scheduledToday.toInstant().isAfter(now)) {
            scheduledToday
        } else {
            scheduledToday.plusDays(1)
        }

        return java.time.Duration.between(now, nextRun.toInstant()).toKotlinDuration()
    }
}
