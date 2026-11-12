package no.nav.syfo.dialogporten.task

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import no.nav.syfo.altinn.dialogporten.service.DialogportenService
import no.nav.syfo.altinn.dialogporten.task.SendTransmissionOpenedTask
import kotlin.time.Duration.Companion.milliseconds

class SendTransmissionOpenedTaskTest :
    DescribeSpec({
        val dialogportenService = mockk<DialogportenService>()
        val task = SendTransmissionOpenedTask(dialogportenService)

        beforeTest {
            clearAllMocks()
        }

        it("sends opened transmissions immediately") {
            runTest {
                coEvery { dialogportenService.sendTransmissionOpenedActivities() } returns Unit

                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    task.runTask()
                }

                coVerify(exactly = 1) { dialogportenService.sendTransmissionOpenedActivities() }
                job.cancelAndJoin()
            }
        }

        it("continues after a transient service exception") {
            runTest {
                coEvery { dialogportenService.sendTransmissionOpenedActivities() } throws
                    RuntimeException("Transient failure") andThen Unit

                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    task.runTask()
                }

                coVerify(exactly = 1) { dialogportenService.sendTransmissionOpenedActivities() }
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                advanceTimeBy(30_000.milliseconds)
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                runCurrent()
                coVerify(exactly = 2) { dialogportenService.sendTransmissionOpenedActivities() }
                job.cancelAndJoin()
            }
        }
    })
