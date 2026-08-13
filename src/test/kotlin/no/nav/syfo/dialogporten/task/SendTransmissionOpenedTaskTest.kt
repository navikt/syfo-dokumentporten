package no.nav.syfo.dialogporten.task

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import no.nav.syfo.altinn.dialogporten.service.DialogportenService
import no.nav.syfo.altinn.dialogporten.task.SendTransmissionOpenedTask
import no.nav.syfo.application.leaderelection.LeaderElection

class SendTransmissionOpenedTaskTest :
    DescribeSpec({
        val leaderElection = mockk<LeaderElection>()
        val dialogportenService = mockk<DialogportenService>()
        val task = SendTransmissionOpenedTask(leaderElection, dialogportenService)

        beforeTest {
            clearAllMocks()
        }

        it("sends opened transmissions when the instance is leader") {
            runTest {
                coEvery { leaderElection.isLeader() } returns true
                coEvery { dialogportenService.sendTransmissionOpenedActivities() } returns Unit

                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    task.runTask()
                }

                coVerify(exactly = 1) { dialogportenService.sendTransmissionOpenedActivities() }
                job.cancelAndJoin()
            }
        }

        it("does not send opened transmissions when the instance is not leader") {
            runTest {
                coEvery { leaderElection.isLeader() } returns false

                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    task.runTask()
                }

                coVerify(exactly = 0) { dialogportenService.sendTransmissionOpenedActivities() }
                job.cancelAndJoin()
            }
        }
    })
