package no.nav.syfo.kafka.esyfovarsel

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.esyfovarsel.PublishVarselTask
import no.nav.syfo.esyfovarsel.VarselPublishService

@OptIn(ExperimentalCoroutinesApi::class)
class PublishVarselTaskTest :
    DescribeSpec({
        val leaderElection = mockk<LeaderElection>()
        val varselPublishService = mockk<VarselPublishService>()
        val publishVarselTask = PublishVarselTask(leaderElection, varselPublishService)

        beforeTest {
            clearAllMocks()
        }

        describe("runTask") {
            it("publishes pending varsler when instance is leader") {
                runTest {
                    coEvery { leaderElection.isLeader() } returns true
                    coEvery { varselPublishService.publishPendingVarsler() } returns Unit

                    val job = backgroundScope.launch {
                        publishVarselTask.runTask()
                    }

                    runCurrent()

                    coVerify(exactly = 1) { leaderElection.isLeader() }
                    coVerify(exactly = 1) { varselPublishService.publishPendingVarsler() }

                    job.cancelAndJoin()
                }
            }

            it("does not publish pending varsler when instance is not leader") {
                runTest {
                    coEvery { leaderElection.isLeader() } returns false

                    val job = backgroundScope.launch {
                        publishVarselTask.runTask()
                    }

                    runCurrent()

                    coVerify(exactly = 1) { leaderElection.isLeader() }
                    coVerify(exactly = 0) { varselPublishService.publishPendingVarsler() }

                    job.cancelAndJoin()
                }
            }
        }
    })
