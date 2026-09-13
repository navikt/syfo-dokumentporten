package no.nav.syfo.document.service

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

val COUNT_DOCUMENT_CLEANUP_PROCESSED: Counter =
    Counter.builder("${METRICS_NS}_document_cleanup_processed")
        .description("Counts documents processed by document cleanup")
        .register(METRICS_REGISTRY)

val COUNT_DOCUMENT_CLEANUP_CONTENT_DELETED: Counter =
    Counter.builder("${METRICS_NS}_document_cleanup_content_deleted")
        .description("Counts document content rows deleted by document cleanup")
        .register(METRICS_REGISTRY)

val COUNT_DOCUMENT_CLEANUP_RUN_COMPLETED: Counter =
    Counter.builder("${METRICS_NS}_document_cleanup_run_completed")
        .description("Counts completed document cleanup runs")
        .register(METRICS_REGISTRY)

val COUNT_DOCUMENT_CLEANUP_RUN_FAILED: Counter =
    Counter.builder("${METRICS_NS}_document_cleanup_run_failed")
        .description("Counts failed document cleanup runs")
        .register(METRICS_REGISTRY)

val COUNT_DOCUMENT_CLEANUP_RUN_CAPPED: Counter =
    Counter.builder("${METRICS_NS}_document_cleanup_run_capped")
        .description("Counts document cleanup runs that stopped at the batch cap")
        .register(METRICS_REGISTRY)
