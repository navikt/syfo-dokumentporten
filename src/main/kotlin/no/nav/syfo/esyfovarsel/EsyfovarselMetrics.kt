package no.nav.syfo.esyfovarsel

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val VARSEL_PUBLISHED = "${METRICS_NS}_varsel_published"
val COUNT_VARSEL_PUBLISHED: Counter = Counter.builder(VARSEL_PUBLISHED)
    .description("Counts the number of varsler published to esyfovarsel")
    .register(METRICS_REGISTRY)

const val VARSEL_PUBLISH_FAILED = "${METRICS_NS}_varsel_publish_failed"
val COUNT_VARSEL_PUBLISH_FAILED: Counter = Counter.builder(VARSEL_PUBLISH_FAILED)
    .description("Counts the number of varsler that failed to publish")
    .register(METRICS_REGISTRY)

const val VARSEL_PERMANENT_ERROR = "${METRICS_NS}_varsel_permanent_error"
val COUNT_VARSEL_PERMANENT_ERROR = Counter.builder(VARSEL_PERMANENT_ERROR)
    .description("Counts the number of varsler that failed to publish with a permanent error")
    .register(METRICS_REGISTRY)
