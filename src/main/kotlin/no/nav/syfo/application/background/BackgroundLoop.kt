package no.nav.syfo.application.background

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.util.logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val BACKGROUND_LOOP_RUNS = "${METRICS_NS}_background_loop_runs"
const val BACKGROUND_LOOP_FAILURES = "${METRICS_NS}_background_loop_failures"
const val BACKGROUND_LOOP_DURATION = "${METRICS_NS}_background_loop_duration"
const val BACKGROUND_LOOP_NAME_TAG = "name"

class BackgroundLoop(
    private val name: String,
    private val interval: Duration,
    meterRegistry: MeterRegistry,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val iteration: suspend () -> Unit,
) : AutoCloseable {
    private val logger = logger()
    private val lifecycleLock = Any()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(dispatcher + job)
    private var started = false
    private var closed = false

    init {
        require(name.isNotBlank()) { "Background loop name must not be blank" }
        require(interval.isPositive()) { "Background loop interval must be positive" }
    }

    private val runsCounter: Counter = Counter.builder(BACKGROUND_LOOP_RUNS)
        .description("Counts background loop iterations")
        .tag(BACKGROUND_LOOP_NAME_TAG, name)
        .register(meterRegistry)

    private val failuresCounter: Counter = Counter.builder(BACKGROUND_LOOP_FAILURES)
        .description("Counts failed background loop iterations")
        .tag(BACKGROUND_LOOP_NAME_TAG, name)
        .register(meterRegistry)

    private val durationTimer: Timer = Timer.builder(BACKGROUND_LOOP_DURATION)
        .description("Records background loop iteration duration")
        .tag(BACKGROUND_LOOP_NAME_TAG, name)
        .register(meterRegistry)

    fun start() {
        synchronized(lifecycleLock) {
            check(!closed) { "Background loop is closed: name=$name" }
            check(!started) { "Background loop is already started: name=$name" }
            started = true

            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                run()
            }
        }
    }

    override fun close() {
        val shouldClose = synchronized(lifecycleLock) {
            if (closed) {
                false
            } else {
                closed = true
                true
            }
        }
        if (!shouldClose) {
            return
        }

        job.cancel()
        runBlocking {
            if (withTimeoutOrNull(SHUTDOWN_TIMEOUT) { job.join() } == null) {
                logger.warn("Background loop shutdown timed out: name={}", name)
            }
        }
    }

    private suspend fun run() {
        while (currentCoroutineContext().isActive) {
            runsCounter.increment()
            val sample = Timer.start()
            try {
                iteration()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                failuresCounter.increment()
                logger.error("Background loop iteration failed: name={}", name, ex)
            } finally {
                sample.stop(durationTimer)
            }
            delay(interval)
        }
    }

    private companion object {
        val SHUTDOWN_TIMEOUT = 5.seconds
    }
}
