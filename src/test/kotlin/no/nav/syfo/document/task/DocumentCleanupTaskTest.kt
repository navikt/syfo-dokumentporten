package no.nav.syfo.document.task

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.document.service.DocumentCleanupRunResult
import no.nav.syfo.document.service.DocumentCleanupService
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.hours

class DocumentCleanupTaskTest :
    DescribeSpec({
        val clock = Clock.fixed(Instant.parse("2026-06-30T00:00:00Z"), ZoneOffset.UTC)
        val runResult = DocumentCleanupRunResult(
            cutoff = Instant.parse("2026-01-01T00:00:00Z"),
            batchCount = 0,
            processedCount = 0,
            deletedContentCount = 0,
            reachedBatchCap = false,
        )

        it("runs cleanup when the instance is leader") {
            runTest {
                val leaderElection = mockk<LeaderElection>()
                val cleanupService = mockk<DocumentCleanupService>()
                coEvery { leaderElection.isLeader() } returns true
                coEvery { cleanupService.cleanupExpiredDocuments() } returns runResult
                val task = DocumentCleanupTask(leaderElection, cleanupService, clock, runAtUtcHour = 2)

                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    task.runTask()
                }

                advanceTimeBy(2.hours)
                runCurrent()

                coVerify(exactly = 1) { leaderElection.isLeader() }
                coVerify(exactly = 1) { cleanupService.cleanupExpiredDocuments() }
                job.cancelAndJoin()
            }
        }

        it("does not run cleanup when the instance is not leader") {
            runTest {
                val leaderElection = mockk<LeaderElection>()
                val cleanupService = mockk<DocumentCleanupService>()
                coEvery { leaderElection.isLeader() } returns false
                val task = DocumentCleanupTask(leaderElection, cleanupService, clock, runAtUtcHour = 2)

                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    task.runTask()
                }

                advanceTimeBy(2.hours)
                runCurrent()

                coVerify(exactly = 1) { leaderElection.isLeader() }
                coVerify(exactly = 0) { cleanupService.cleanupExpiredDocuments() }
                job.cancelAndJoin()
            }
        }

        it("retries on the next scheduled run after a transient leader-election failure") {
            runTest {
                val leaderElection = mockk<LeaderElection>()
                val cleanupService = mockk<DocumentCleanupService>()
                coEvery { leaderElection.isLeader() } throws RuntimeException("Transient elector failure") andThen true
                coEvery { cleanupService.cleanupExpiredDocuments() } returns runResult
                val task = DocumentCleanupTask(leaderElection, cleanupService, clock, runAtUtcHour = 2)

                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    task.runTask()
                }

                advanceTimeBy(2.hours)
                runCurrent()

                coVerify(exactly = 1) { leaderElection.isLeader() }
                advanceTimeBy(2.hours)
                runCurrent()

                coVerify(exactly = 2) { leaderElection.isLeader() }
                coVerify(exactly = 1) { cleanupService.cleanupExpiredDocuments() }
                job.cancelAndJoin()
            }
        }

        it("does not run cleanup before the scheduled hour") {
            runTest {
                val leaderElection = mockk<LeaderElection>()
                val cleanupService = mockk<DocumentCleanupService>()
                val task = DocumentCleanupTask(leaderElection, cleanupService, clock, runAtUtcHour = 2)

                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    task.runTask()
                }

                advanceTimeBy(1.hours)
                runCurrent()

                coVerify(exactly = 0) { leaderElection.isLeader() }
                coVerify(exactly = 0) { cleanupService.cleanupExpiredDocuments() }
                job.cancelAndJoin()
            }
        }
    })
