package no.nav.syfo.application.background

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundLoopTest :
    DescribeSpec({
        it("runs immediately and then repeats at the configured interval") {
            runTest {
                var iterationCount = 0
                val registry = SimpleMeterRegistry()
                val loop = backgroundLoop(
                    name = "test-loop",
                    interval = 1.hours,
                    registry = registry,
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                ) {
                    iterationCount++
                }

                loop.start()
                iterationCount shouldBe 1
                registry.counterValue(BACKGROUND_LOOP_RUNS) shouldBeExactly 1.0
                registry.timerCount(BACKGROUND_LOOP_DURATION) shouldBe 1

                advanceTimeBy(1.hours)
                runCurrent()
                iterationCount shouldBe 2
                registry.counterValue(BACKGROUND_LOOP_RUNS) shouldBeExactly 2.0
                registry.timerCount(BACKGROUND_LOOP_DURATION) shouldBe 2

                loop.close()
            }
        }

        it("isolates ordinary iteration failures") {
            runTest {
                var iterationCount = 0
                val registry = SimpleMeterRegistry()
                val loop = backgroundLoop(
                    name = "test-loop",
                    interval = 1.hours,
                    registry = registry,
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                ) {
                    iterationCount++
                    if (iterationCount == 1) {
                        throw IllegalStateException("Expected test failure")
                    }
                }

                loop.start()
                registry.counterValue(BACKGROUND_LOOP_FAILURES) shouldBeExactly 1.0
                advanceTimeBy(1.hours)
                runCurrent()
                iterationCount shouldBe 2
                registry.counterValue(BACKGROUND_LOOP_RUNS) shouldBeExactly 2.0
                registry.counterValue(BACKGROUND_LOOP_FAILURES) shouldBeExactly 1.0
                registry.timerCount(BACKGROUND_LOOP_DURATION) shouldBe 2

                loop.close()
            }
        }

        it("propagates cancellation without recording an ordinary failure") {
            runTest {
                val registry = SimpleMeterRegistry()
                val loop = backgroundLoop(
                    name = "test-loop",
                    interval = 1.hours,
                    registry = registry,
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                ) {
                    throw CancellationException("Expected test cancellation")
                }

                loop.start()
                registry.counterValue(BACKGROUND_LOOP_RUNS) shouldBeExactly 1.0
                registry.counterValue(BACKGROUND_LOOP_FAILURES) shouldBeExactly 0.0
                registry.timerCount(BACKGROUND_LOOP_DURATION) shouldBe 1

                advanceTimeBy(1.hours)
                runCurrent()
                registry.counterValue(BACKGROUND_LOOP_RUNS) shouldBeExactly 1.0

                loop.close()
            }
        }

        it("cancels owned work when closed and close is idempotent") {
            runTest {
                var iterationCount = 0
                val loop = backgroundLoop(
                    name = "test-loop",
                    interval = 1.hours,
                    registry = SimpleMeterRegistry(),
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                ) {
                    iterationCount++
                }

                loop.start()
                loop.close()
                loop.close()
                advanceTimeBy(1.hours)
                runCurrent()

                iterationCount shouldBe 1
            }
        }

        it("rejects double start") {
            runTest {
                val loop = backgroundLoop(
                    name = "test-loop",
                    interval = 1.hours,
                    registry = SimpleMeterRegistry(),
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                ) {}

                loop.start()

                shouldThrow<IllegalStateException> {
                    loop.start()
                }

                loop.close()
            }
        }

        it("requires a nonblank name and positive interval") {
            shouldThrow<IllegalArgumentException> {
                backgroundLoop(
                    name = " ",
                    interval = 1.hours,
                    registry = SimpleMeterRegistry(),
                    dispatcher = UnconfinedTestDispatcher(),
                ) {}
            }
            shouldThrow<IllegalArgumentException> {
                backgroundLoop(
                    name = "test-loop",
                    interval = ZERO,
                    registry = SimpleMeterRegistry(),
                    dispatcher = UnconfinedTestDispatcher(),
                ) {}
            }
        }
    })

private fun backgroundLoop(
    name: String,
    interval: kotlin.time.Duration,
    registry: SimpleMeterRegistry,
    dispatcher: CoroutineDispatcher,
    iteration: suspend () -> Unit,
) = BackgroundLoop(
    name = name,
    interval = interval,
    meterRegistry = registry,
    dispatcher = dispatcher,
    iteration = iteration,
)

private const val LOOP_NAME = "test-loop"
private val LOOP_TAGS = Tags.of(BACKGROUND_LOOP_NAME_TAG, LOOP_NAME)

private fun SimpleMeterRegistry.counterValue(metric: String) = counter(metric, LOOP_TAGS).count()

private fun SimpleMeterRegistry.timerCount(metric: String) = timer(metric, LOOP_TAGS).count()
