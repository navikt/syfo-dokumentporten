package no.nav.syfo.document.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import no.nav.syfo.document.db.DocumentCleanupBatchResult
import no.nav.syfo.document.db.DocumentCleanupRepository
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

class DocumentCleanupWorker(
    private val documentCleanupRepository: DocumentCleanupRepository,
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

    suspend fun runOnce(): DocumentCleanupRunResult {
        val cutoff = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC))
            .minusMonths(RETENTION_MONTHS)
            .toInstant()
        var batchCount = 0
        var processedCount = 0
        var deletedContentCount = 0
        var hitBatchCap = false
        var lockContended = false

        try {
            while (true) {
                when (val batchResult = documentCleanupRepository.cleanupExpiredDocuments(cutoff, batchSize)) {
                    DocumentCleanupBatchResult.LockContended -> {
                        lockContended = true
                        COUNT_DOCUMENT_CLEANUP_LOCK_CONTENDED.increment()
                        logger.info("Document cleanup run skipped because another replica holds the cleanup lock")
                        break
                    }

                    is DocumentCleanupBatchResult.Completed -> {
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
                            hitBatchCap = true
                            break
                        }

                        delay(batchDelay)
                    }
                }
            }

            if (!lockContended) {
                COUNT_DOCUMENT_CLEANUP_RUN_COMPLETED.increment()
                if (hitBatchCap) {
                    COUNT_DOCUMENT_CLEANUP_RUN_CAPPED.increment()
                    logger.warn(
                        "Document cleanup run stopped at batch cap; backlog may remain: cutoff={}, batchCount={}, " +
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
            }
            return DocumentCleanupRunResult(
                cutoff = cutoff,
                batchCount = batchCount,
                processedCount = processedCount,
                deletedContentCount = deletedContentCount,
                hitBatchCap = hitBatchCap,
                lockContended = lockContended,
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
    val hitBatchCap: Boolean,
    val lockContended: Boolean,
)
