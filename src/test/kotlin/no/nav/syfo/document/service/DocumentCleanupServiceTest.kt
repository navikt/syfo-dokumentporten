package no.nav.syfo.document.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.document.db.DocumentCleanupBatchResult
import no.nav.syfo.document.db.DocumentDAO
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration

class DocumentCleanupServiceTest :
    DescribeSpec({
        it("uses four calendar months in UTC and processes all bounded batches") {
            val documentDAO = mockk<DocumentDAO>()
            val now = Instant.parse("2026-06-30T12:00:00Z")
            val expectedCutoff = Instant.parse("2026-02-28T12:00:00Z")
            coEvery {
                documentDAO.cleanupExpiredDocuments(expectedCutoff, DOCUMENT_CLEANUP_BATCH_SIZE)
            } returnsMany listOf(
                DocumentCleanupBatchResult(processedCount = 500, deletedContentCount = 498),
                DocumentCleanupBatchResult(processedCount = 3, deletedContentCount = 2),
            )
            val service = DocumentCleanupService(
                documentDAO = documentDAO,
                clock = Clock.fixed(now, ZoneOffset.UTC),
                batchDelay = Duration.ZERO,
            )

            val result = service.cleanupExpiredDocuments()

            result shouldBe DocumentCleanupRunResult(
                cutoff = expectedCutoff,
                batchCount = 2,
                processedCount = 503,
                deletedContentCount = 500,
                reachedBatchCap = false,
            )
            coVerify(exactly = 2) {
                documentDAO.cleanupExpiredDocuments(expectedCutoff, DOCUMENT_CLEANUP_BATCH_SIZE)
            }
        }

        it("stops after the configured batch cap") {
            val documentDAO = mockk<DocumentDAO>()
            val now = Instant.parse("2026-06-30T12:00:00Z")
            val expectedCutoff = Instant.parse("2026-02-28T12:00:00Z")
            val cappedRunsBefore = COUNT_DOCUMENT_CLEANUP_RUN_CAPPED.count()
            coEvery {
                documentDAO.cleanupExpiredDocuments(expectedCutoff, DOCUMENT_CLEANUP_BATCH_SIZE)
            } returns DocumentCleanupBatchResult(processedCount = 500, deletedContentCount = 499)
            val service = DocumentCleanupService(
                documentDAO = documentDAO,
                clock = Clock.fixed(now, ZoneOffset.UTC),
                maxBatchesPerRun = 2,
                batchDelay = Duration.ZERO,
            )

            val result = service.cleanupExpiredDocuments()

            result shouldBe DocumentCleanupRunResult(
                cutoff = expectedCutoff,
                batchCount = 2,
                processedCount = 1000,
                deletedContentCount = 998,
                reachedBatchCap = true,
            )
            COUNT_DOCUMENT_CLEANUP_RUN_CAPPED.count() shouldBe cappedRunsBefore + 1
            coVerify(exactly = 2) {
                documentDAO.cleanupExpiredDocuments(expectedCutoff, DOCUMENT_CLEANUP_BATCH_SIZE)
            }
        }

        it("does not report the batch cap when the final batch is partial") {
            val documentDAO = mockk<DocumentDAO>()
            val now = Instant.parse("2026-06-30T12:00:00Z")
            val expectedCutoff = Instant.parse("2026-02-28T12:00:00Z")
            val cappedRunsBefore = COUNT_DOCUMENT_CLEANUP_RUN_CAPPED.count()
            coEvery {
                documentDAO.cleanupExpiredDocuments(expectedCutoff, DOCUMENT_CLEANUP_BATCH_SIZE)
            } returnsMany listOf(
                DocumentCleanupBatchResult(processedCount = 500, deletedContentCount = 498),
                DocumentCleanupBatchResult(processedCount = 3, deletedContentCount = 2),
            )
            val service = DocumentCleanupService(
                documentDAO = documentDAO,
                clock = Clock.fixed(now, ZoneOffset.UTC),
                maxBatchesPerRun = 2,
                batchDelay = Duration.ZERO,
            )

            val result = service.cleanupExpiredDocuments()

            result shouldBe DocumentCleanupRunResult(
                cutoff = expectedCutoff,
                batchCount = 2,
                processedCount = 503,
                deletedContentCount = 500,
                reachedBatchCap = false,
            )
            COUNT_DOCUMENT_CLEANUP_RUN_CAPPED.count() shouldBe cappedRunsBefore
            coVerify(exactly = 2) {
                documentDAO.cleanupExpiredDocuments(expectedCutoff, DOCUMENT_CLEANUP_BATCH_SIZE)
            }
        }
    })
