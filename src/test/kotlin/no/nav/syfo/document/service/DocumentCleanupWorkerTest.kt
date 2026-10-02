package no.nav.syfo.document.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.document.db.DocumentCleanupBatchResult
import no.nav.syfo.document.db.DocumentCleanupRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration

class DocumentCleanupWorkerTest :
    DescribeSpec({
        it("uses four calendar months in UTC and processes all bounded batches") {
            val documentCleanupRepository = mockk<DocumentCleanupRepository>()
            val now = Instant.parse("2026-06-30T12:00:00Z")
            val expectedCutoff = Instant.parse("2026-02-28T12:00:00Z")
            coEvery {
                documentCleanupRepository.cleanupExpiredDocuments(expectedCutoff, DOCUMENT_CLEANUP_BATCH_SIZE)
            } returnsMany listOf(
                DocumentCleanupBatchResult.Completed(processedCount = 500, deletedContentCount = 498),
                DocumentCleanupBatchResult.Completed(processedCount = 3, deletedContentCount = 2),
            )
            val worker = DocumentCleanupWorker(
                documentCleanupRepository = documentCleanupRepository,
                clock = Clock.fixed(now, ZoneOffset.UTC),
                batchDelay = Duration.ZERO,
            )

            val result = worker.runOnce()

            result shouldBe DocumentCleanupRunResult(
                cutoff = expectedCutoff,
                batchCount = 2,
                processedCount = 503,
                deletedContentCount = 500,
                hitBatchCap = false,
                lockContended = false,
            )
            coVerify(exactly = 2) {
                documentCleanupRepository.cleanupExpiredDocuments(expectedCutoff, DOCUMENT_CLEANUP_BATCH_SIZE)
            }
        }

        it("marks an exact full batch cap as hit because backlog may remain") {
            val documentCleanupRepository = mockk<DocumentCleanupRepository>()
            val now = Instant.parse("2026-06-30T12:00:00Z")
            val expectedCutoff = Instant.parse("2026-02-28T12:00:00Z")
            val cappedRunsBefore = COUNT_DOCUMENT_CLEANUP_RUN_CAPPED.count()
            coEvery {
                documentCleanupRepository.cleanupExpiredDocuments(expectedCutoff, DOCUMENT_CLEANUP_BATCH_SIZE)
            } returns DocumentCleanupBatchResult.Completed(processedCount = 500, deletedContentCount = 499)
            val worker = DocumentCleanupWorker(
                documentCleanupRepository = documentCleanupRepository,
                clock = Clock.fixed(now, ZoneOffset.UTC),
                maxBatchesPerRun = 2,
                batchDelay = Duration.ZERO,
            )

            val result = worker.runOnce()

            result shouldBe DocumentCleanupRunResult(
                cutoff = expectedCutoff,
                batchCount = 2,
                processedCount = 1000,
                deletedContentCount = 998,
                hitBatchCap = true,
                lockContended = false,
            )
            COUNT_DOCUMENT_CLEANUP_RUN_CAPPED.count() shouldBe cappedRunsBefore + 1
            coVerify(exactly = 2) {
                documentCleanupRepository.cleanupExpiredDocuments(expectedCutoff, DOCUMENT_CLEANUP_BATCH_SIZE)
            }
        }

        it("does not report the batch cap when the final batch is partial") {
            val documentCleanupRepository = mockk<DocumentCleanupRepository>()
            val now = Instant.parse("2026-06-30T12:00:00Z")
            val expectedCutoff = Instant.parse("2026-02-28T12:00:00Z")
            val cappedRunsBefore = COUNT_DOCUMENT_CLEANUP_RUN_CAPPED.count()
            coEvery {
                documentCleanupRepository.cleanupExpiredDocuments(expectedCutoff, DOCUMENT_CLEANUP_BATCH_SIZE)
            } returnsMany listOf(
                DocumentCleanupBatchResult.Completed(processedCount = 500, deletedContentCount = 498),
                DocumentCleanupBatchResult.Completed(processedCount = 3, deletedContentCount = 2),
            )
            val worker = DocumentCleanupWorker(
                documentCleanupRepository = documentCleanupRepository,
                clock = Clock.fixed(now, ZoneOffset.UTC),
                maxBatchesPerRun = 2,
                batchDelay = Duration.ZERO,
            )

            val result = worker.runOnce()

            result shouldBe DocumentCleanupRunResult(
                cutoff = expectedCutoff,
                batchCount = 2,
                processedCount = 503,
                deletedContentCount = 500,
                hitBatchCap = false,
                lockContended = false,
            )
            COUNT_DOCUMENT_CLEANUP_RUN_CAPPED.count() shouldBe cappedRunsBefore
            coVerify(exactly = 2) {
                documentCleanupRepository.cleanupExpiredDocuments(expectedCutoff, DOCUMENT_CLEANUP_BATCH_SIZE)
            }
        }

        it("ends safely on lock contention without recording a failed run") {
            val documentCleanupRepository = mockk<DocumentCleanupRepository>()
            val now = Instant.parse("2026-06-30T12:00:00Z")
            val expectedCutoff = Instant.parse("2026-02-28T12:00:00Z")
            val contendedRunsBefore = COUNT_DOCUMENT_CLEANUP_LOCK_CONTENDED.count()
            val failedRunsBefore = COUNT_DOCUMENT_CLEANUP_RUN_FAILED.count()
            coEvery {
                documentCleanupRepository.cleanupExpiredDocuments(expectedCutoff, DOCUMENT_CLEANUP_BATCH_SIZE)
            } returns DocumentCleanupBatchResult.LockContended
            val worker = DocumentCleanupWorker(
                documentCleanupRepository = documentCleanupRepository,
                clock = Clock.fixed(now, ZoneOffset.UTC),
            )

            val result = worker.runOnce()

            result shouldBe DocumentCleanupRunResult(
                cutoff = expectedCutoff,
                batchCount = 0,
                processedCount = 0,
                deletedContentCount = 0,
                hitBatchCap = false,
                lockContended = true,
            )
            COUNT_DOCUMENT_CLEANUP_LOCK_CONTENDED.count() shouldBe contendedRunsBefore + 1
            COUNT_DOCUMENT_CLEANUP_RUN_FAILED.count() shouldBe failedRunsBefore
            coVerify(exactly = 1) {
                documentCleanupRepository.cleanupExpiredDocuments(expectedCutoff, DOCUMENT_CLEANUP_BATCH_SIZE)
            }
        }
    })
