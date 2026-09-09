package no.nav.syfo.document.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.util.logger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

const val DOCUMENT_CLEANUP_BATCH_SIZE = 500
const val DOCUMENT_CLEANUP_MAX_BATCHES_PER_RUN = 1000
val DOCUMENT_CLEANUP_BATCH_DELAY = 100.milliseconds
private const val RETENTION_MONTHS = 4L

class DocumentCleanupService(
    private val documentDAO: DocumentDAO,
    private val clock: Clock = Clock.systemUTC(),
    private val batchSize: Int = DOCUMENT_CLEANUP_BATCH_SIZE,
    private val maxBatchesPerRun: Int = DOCUMENT_CLEANUP_MAX_BATCHES_PER_RUN,
    private val batchDelay: Duration = DOCUMENT_CLEANUP_BATCH_DELAY,
) {
    private val logger = logger()

    init {
        require(batchSize > 0) { "Batch size must be greater than zero" }
        require(maxBatchesPerRun > 0) { "Maximum batches per run must be greater than zero" }
    }

    suspend fun cleanupExpiredDocuments(): DocumentCleanupRunResult {
        val cutoff = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC))
            .minusMonths(RETENTION_MONTHS)
            .toInstant()
        var batchCount = 0
        var processedCount = 0
        var deletedContentCount = 0
        var reachedBatchCap = false

        try {
            while (true) {
                val batchResult = documentDAO.cleanupExpiredDocuments(cutoff, batchSize)
                if (batchResult.processedCount == 0) {
                    break
                }

                batchCount++
                processedCount += batchResult.processedCount
                deletedContentCount += batchResult.deletedContentCount
                COUNT_DOCUMENT_CLEANUP_PROCESSED.increment(batchResult.processedCount.toDouble())
                COUNT_DOCUMENT_CLEANUP_CONTENT_DELETED.increment(batchResult.deletedContentCount.toDouble())
                logger.info(
                    "Document cleanup batch completed: cutoff={}, batchCount={}, processedCount={}, " +
                        "deletedContentCount={}",
                    cutoff,
                    batchCount,
                    batchResult.processedCount,
                    batchResult.deletedContentCount,
                )

                if (batchResult.processedCount < batchSize) {
                    break
                }

                if (batchCount >= maxBatchesPerRun) {
                    reachedBatchCap = true
                    break
                }

                delay(batchDelay)
            }

            COUNT_DOCUMENT_CLEANUP_RUN_COMPLETED.increment()
            if (reachedBatchCap) {
                COUNT_DOCUMENT_CLEANUP_RUN_CAPPED.increment()
                logger.warn(
                    "Document cleanup run stopped at batch cap and backlog remains: cutoff={}, batchCount={}, " +
                        "processedCount={}, deletedContentCount={}",
                    cutoff,
                    batchCount,
                    processedCount,
                    deletedContentCount,
                )
            } else {
                logger.info(
                    "Document cleanup run completed: cutoff={}, batchCount={}, processedCount={}, " +
                        "deletedContentCount={}",
                    cutoff,
                    batchCount,
                    processedCount,
                    deletedContentCount,
                )
            }
            return DocumentCleanupRunResult(
                cutoff = cutoff,
                batchCount = batchCount,
                processedCount = processedCount,
                deletedContentCount = deletedContentCount,
                reachedBatchCap = reachedBatchCap,
            )
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            COUNT_DOCUMENT_CLEANUP_RUN_FAILED.increment()
            logger.error(
                "Document cleanup run failed: cutoff={}, batchCount={}, processedCount={}, deletedContentCount={}",
                cutoff,
                batchCount,
                processedCount,
                deletedContentCount,
                ex,
            )
            throw ex
        }
    }
}

data class DocumentCleanupRunResult(
    val cutoff: Instant,
    val batchCount: Int,
    val processedCount: Int,
    val deletedContentCount: Int,
    val reachedBatchCap: Boolean,
)
