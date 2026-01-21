package no.nav.syfo.document.api.v1

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val DOCUMENT_RECIEVED = "${METRICS_NS}_document_recieved"
val COUNT_DOCUMENT_RECIEVED: Counter = Counter.builder(DOCUMENT_RECIEVED)
    .description("Counts the number of documents recieved")
    .register(METRICS_REGISTRY)

const val DOCUMENTS_READ_BY_EXTERNAL_IDPORTENUSER = "${METRICS_NS}_documents_read_by_external_idportenuser"
val COUNT_DOCUMENTS_READ_BY_EXTERNAL_IDPORTENUSER: Counter = Counter.builder(DOCUMENTS_READ_BY_EXTERNAL_IDPORTENUSER)
    .description("Counts the number of documents read by external idporten user")
    .register(METRICS_REGISTRY)

const val DOCUMENTS_READ_BY_EXTERNAL_SYSTEMUSER = "${METRICS_NS}_documents_read_by_external_systemuser"
val COUNT_DOCUMENTS_READ_BY_EXTERNAL_SYSTEMUSER: Counter = Counter.builder(DOCUMENTS_READ_BY_EXTERNAL_SYSTEMUSER)
    .description("Counts the number of documents read by external system user")
    .register(METRICS_REGISTRY)

const val DOCUMENTS_REREAD_BY_EXTERNAL_IDPORTENUSER = "${METRICS_NS}_documents_REread_by_external_idportenuser"
val COUNT_DOCUMENTS_REREAD_BY_EXTERNAL_IDPORTENUSER: Counter = Counter.builder(
    DOCUMENTS_REREAD_BY_EXTERNAL_IDPORTENUSER
)
    .description("Counts the number of documents reread by external idporten user")
    .register(METRICS_REGISTRY)

const val DOCUMENTS_REREAD_BY_EXTERNAL_SYSTEMUSER = "${METRICS_NS}_documents_reread_by_external_systemuser"
val COUNT_DOCUMENTS_REREAD_BY_EXTERNAL_SYSTEMUSER: Counter = Counter.builder(DOCUMENTS_REREAD_BY_EXTERNAL_SYSTEMUSER)
    .description("Counts the number of documents reread by external system user")
    .register(METRICS_REGISTRY)
